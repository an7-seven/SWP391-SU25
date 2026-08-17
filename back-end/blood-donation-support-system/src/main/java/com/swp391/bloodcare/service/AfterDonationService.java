package com.swp391.bloodcare.service;

import com.swp391.bloodcare.dto.AfterDonationBloodDTO;
import com.swp391.bloodcare.dto.BloodBagDTO;
import com.swp391.bloodcare.dto.request.BloodBagCreateRequest;
import com.swp391.bloodcare.entity.*;
import com.swp391.bloodcare.repository.*;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import static com.swp391.bloodcare.service.BloodBagService.generateBloodBagId;

@Service
public class AfterDonationService {
    private final AfterDonationRepository afterRepo;
    private final HealthCheckRepository healthCheckRepo;
    private final BloodRepository bloodRepo;
    private final BloodDonationHistoryService bloodDonationHistoryService;
    private final ProfileRepository profileRepo;
    private final BloodBagRepository bloodBagRepo;
    private final ComponentRepository componentRepository;

    public AfterDonationService(
            AfterDonationRepository afterRepo,
            HealthCheckRepository healthCheckRepo,
            BloodRepository bloodRepo,
            BloodDonationHistoryService bloodDonationHistoryService,
            ProfileRepository profileRepo,
            BloodBagRepository bloodBagRepo,
            ComponentRepository componentRepository ) {
        this.afterRepo = afterRepo;
        this.healthCheckRepo = healthCheckRepo;
        this.bloodRepo = bloodRepo;
        this.bloodDonationHistoryService = bloodDonationHistoryService;
        this.profileRepo = profileRepo;
        this.bloodBagRepo = bloodBagRepo;
        this.componentRepository = componentRepository;
    }
    public List<AfterDonationBloodDTO> getAll() {
        return afterRepo.findAll().stream().map(this::toDTO).toList();
    }

    @Transactional
    public AfterDonationBloodDTO create(AfterDonationBloodDTO dto) {
        HealthCheck healthCheck = healthCheckRepo.findByHealthCheckId(dto.getHealthCheckId())
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy HealthCheck"));
        if(!healthCheck.getDonationRegistration().getStatus().name().equals("COMPLETED")){
            throw new IllegalStateException("Đơn này chưa xác nhận đã hiến máu");

        }
        if (afterRepo.findByHealthCheck_HealthCheckId(dto.getHealthCheckId()).isPresent()) {
            throw new IllegalStateException("Đã tồn tại dữ liệu sau hiến cho HealthCheck này");
        }

        AfterDonationBlood entity = toEntity(dto);
        entity.setIdAfterDonation(generateAfterDonationId());
        entity.setHealthCheck(healthCheck);

        if (Boolean.TRUE.equals(dto.getInfectiousDiseasesChecked()) && Boolean.TRUE.equals(dto.getIsBloodUsable())) {
            entity.setStatus(AfterDonationBlood.Status.PASSED);
        } else {
            entity.setStatus(AfterDonationBlood.Status.FAILED);
        }

        if (dto.getBloodId() != null) {
            Blood blood = bloodRepo.findByBloodCode(dto.getBloodId())
                    .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy Blood"));

            Profile profile = healthCheck.getDonationRegistration().getAccount().getProfile();
            if (profile.getBloodCode() == null) {
                profile.setBloodCode(blood);
                profileRepo.save(profile);
            }

            entity.setBlood(blood);
        }

        bloodDonationHistoryService.updateFromAfterDonation(entity);

        return toDTO(afterRepo.save(entity));
    }


    @Transactional
    public Map<String, Object> separateManually(BloodBagCreateRequest request) {
        Map<String, String> result = new HashMap<>();

        String afterDonationId = request.getAfterDonationId();

        AfterDonationBlood after = afterRepo.findAfterDonationBloodByIdAfterDonation(afterDonationId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn máu: " + afterDonationId));

        if (after.getStatus() != AfterDonationBlood.Status.PASSED) {
            throw new IllegalStateException("Đơn máu " + afterDonationId + " không hợp lệ hoặc đã được tách trước đó");
        }

        for (BloodBagDTO dto : request.getBloodBags()) {
            if (!componentRepository.existsById(dto.getComponentId())) {
                throw new IllegalArgumentException("Không tìm thấy thành phần máu: " + dto.getComponentId());
            }

            if (!bloodRepo.existsById(dto.getBloodCode())) {
                throw new IllegalArgumentException("Không tìm thấy nhóm máu: " + dto.getBloodCode());
            }
        }

        after.setStatus(AfterDonationBlood.Status.SEPARATED);
        afterRepo.save(after);
        bloodDonationHistoryService.updateFromAfterDonation(after);
        result.put(afterDonationId, "Cập nhật trạng thái: ĐÃ TÁCH");

        for (BloodBagDTO dto : request.getBloodBags()) {
            Blood blood = bloodRepo.findById(dto.getBloodCode())
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy nhóm máu: " + dto.getBloodCode()));

            Component component = componentRepository.findById(dto.getComponentId())
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy thành phần máu: " + dto.getComponentId()));

            if (dto.getCollectedDate() == null) {
                throw new IllegalArgumentException("Ngày tách máu (collectedDate) không được để trống");
            }

            LocalDate collectedDate = dto.getCollectedDate().toInstant()
                    .atZone(ZoneId.systemDefault()).toLocalDate();

            LocalDate expirationDate = collectedDate.plusDays(component.getExpirationDays());

            BloodBag.Status status = expirationDate.isBefore(LocalDate.now())
                    ? BloodBag.Status.EXPIRED
                    : BloodBag.Status.VALID;

            BloodBag bag = BloodBag.builder()
                    .bagId(generateBloodBagId())
                    .volume(BloodBag.Volume.fromInt(dto.getVolume()))
                    .collectedDate(dto.getCollectedDate())
                    .expirationDate(java.sql.Date.valueOf(expirationDate))
                    .component(component)
                    .status(status)
                    .blood(blood)
                    .afterDonationBlood(after)
                    .build();

            bloodBagRepo.save(bag);
        }

        return Map.of("message", "Đã tách thành công túi máu thủ công", "after", result);
    }

    private String generateAfterDonationId() {
        String timestamp = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
        int rand = new Random().nextInt(900) + 100;
        return "AD-" + timestamp + "-" + rand;
    }

    @Scheduled(cron = "0 * * * * *")
    @Transactional
    public void autoSeparateExpired() {
        List<AfterDonationBlood> afterList = afterRepo.findAll();

        for (AfterDonationBlood after : afterList) {
            if (after.getStatus() == AfterDonationBlood.Status.SEPARATED) continue;
            if (!Boolean.TRUE.equals(after.getIsBloodUsable())) continue;

            LocalDateTime createdAt = after.getHealthCheck()
                    .getDonationRegistration()
                    .getDateCreated()
                    .toInstant()
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime();

            if (Duration.between(createdAt, LocalDateTime.now()).toHours() > 24) {
                Component component = componentRepository.findById("101").orElseThrow();
                BloodBag wholeBag = BloodBag.builder()
                        .bagId(generateBloodBagId())
                        .blood(after.getBlood())
                        .volume(BloodBag.Volume.ML_250)
                        .component(component)
                        .expirationDate(java.sql.Date.valueOf(LocalDate.now().plusDays(35)))
                        .collectedDate(new Date())
                        .status(BloodBag.Status.VALID)
                        .build();

                bloodBagRepo.save(wholeBag);
                after.setStatus(AfterDonationBlood.Status.SEPARATED);
                afterRepo.save(after);
                bloodDonationHistoryService.updateFromAfterDonation(after);
            }
        }
    }

    @Transactional
    public AfterDonationBloodDTO update(String id, AfterDonationBloodDTO dto) {
        AfterDonationBlood entity = afterRepo.findAfterDonationBloodByIdAfterDonation(id)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy bản ghi AfterDonationBlood"));

        entity.setInfectiousDiseasesChecked(dto.getInfectiousDiseasesChecked());
        entity.setIsBloodUsable(dto.getIsBloodUsable());
        entity.setNote(dto.getNote());

        if (Boolean.TRUE.equals(dto.getInfectiousDiseasesChecked()) && Boolean.TRUE.equals(dto.getIsBloodUsable())) {
            entity.setStatus(AfterDonationBlood.Status.PASSED);
        } else {
            entity.setStatus(AfterDonationBlood.Status.FAILED);
        }

        // Cập nhật nhóm máu nếu cần
        if (dto.getBloodId() != null) {
            Blood blood = bloodRepo.findByBloodCode(dto.getBloodId())
                    .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy Blood"));
            entity.setBlood(blood);
        }

        // Cập nhật lịch sử hiến máu
        bloodDonationHistoryService.updateFromAfterDonation(entity);

        return toDTO(afterRepo.save(entity));
    }


    @Transactional
    public AfterDonationBloodDTO delete(String id) {
        AfterDonationBlood existing = afterRepo.findAfterDonationBloodByIdAfterDonation(id)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy bản ghi để xóa"));
        afterRepo.delete(existing);
        return toDTO(existing);
    }

    public Map<String, Object> deleteMultiple(List<String> ids) {
        List<String> deleted = new ArrayList<>();
        Map<String, String> errors = new HashMap<>();

        for (String id : ids) {
            try {
                AfterDonationBlood existing = afterRepo.findAfterDonationBloodByIdAfterDonation(id)
                        .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy ID: " + id));
                afterRepo.delete(existing);
                deleted.add(id);
            } catch (Exception e) {
                errors.put(id, e.getMessage());
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("deleted", deleted);
        result.put("errors", errors);
        return result;
    }

    public AfterDonationBloodDTO getByHealthCheckId(String healthCheckId) {
        AfterDonationBlood entity = afterRepo.findByHealthCheck_HealthCheckId(healthCheckId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy bản ghi cho healthCheck"));
        return toDTO(entity);
    }

    private AfterDonationBlood toEntity(AfterDonationBloodDTO dto) {
        AfterDonationBlood entity = new AfterDonationBlood();
        entity.setInfectiousDiseasesChecked(dto.getInfectiousDiseasesChecked());
        entity.setIsBloodUsable(dto.getIsBloodUsable());
        entity.setHealthCheck(
                healthCheckRepo.findByHealthCheckId(dto.getHealthCheckId())
                        .orElseThrow(() -> new RuntimeException("Không tìm thấy HealthCheck với ID: " + dto.getHealthCheckId()))
        );
        entity.setNote(dto.getNote());
        return entity;
    }

    private AfterDonationBloodDTO toDTO(AfterDonationBlood entity) {
        AfterDonationBloodDTO dto = new AfterDonationBloodDTO();
        dto.setIdAfterDonation(entity.getIdAfterDonation());
        dto.setInfectiousDiseasesChecked(entity.getInfectiousDiseasesChecked());
        dto.setIsBloodUsable(entity.getIsBloodUsable());
        dto.setNote(entity.getNote());
        if (entity.getHealthCheck() != null)
            dto.setHealthCheckId(entity.getHealthCheck().getHealthCheckId());
        if (entity.getBlood() != null)
            dto.setBloodId(entity.getBlood().getBloodCode());
        dto.setStatus(entity.getStatus());
        return dto;
    }
}