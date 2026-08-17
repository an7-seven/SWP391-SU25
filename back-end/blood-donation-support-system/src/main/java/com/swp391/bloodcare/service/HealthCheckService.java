package com.swp391.bloodcare.service;

import com.swp391.bloodcare.dto.HealthCheckDTO;
import com.swp391.bloodcare.entity.BloodDonationEvent;
import com.swp391.bloodcare.entity.DonationRegistration;
import com.swp391.bloodcare.entity.HealthCheck;
import com.swp391.bloodcare.entity.Profile;
import com.swp391.bloodcare.repository.DonationRegistrationRepository;
import com.swp391.bloodcare.repository.HealthCheckRepository;
import com.swp391.bloodcare.repository.ProfileRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.*;

import static com.swp391.bloodcare.dto.HealthCheckDTO.toDTO;

@Service
@RequiredArgsConstructor
public class HealthCheckService {

    private final HealthCheckRepository healthCheckRepository;
    private final DonationRegistrationRepository donationRegistrationRepository;
    private final BloodDonationHistoryService bloodDonationHistoryService;
    private final ProfileService profileService;
    private final ProfileRepository profileRepository;
    private final EventService eventService;


    @Transactional
    public HealthCheckDTO createHealthCheck(String registrationId, HealthCheckDTO dto) {
        DonationRegistration reg = donationRegistrationRepository.findByRegistrationId(registrationId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy đơn đăng ký hiến máu"));

        if (healthCheckRepository.findByDonationRegistration_RegistrationId(registrationId).isPresent()) {
            throw new IllegalStateException("Đã tồn tại kiểm tra sức khỏe cho đơn này");
        }

        if (!LocalDate.now().isEqual(reg.getDonationDate())) {
            throw new IllegalStateException("Chỉ được tạo HealthCheck vào đúng ngày hiến máu");
        }

        validateVolumeToTake(dto);

        HealthCheck healthCheck = HealthCheckDTO.toEntity(dto);
        healthCheck.setHealthCheckId(generateHealthCheckId());
        healthCheck.setDonationRegistration(reg);
        reg.setHealthCheck(healthCheck);

        if (Boolean.TRUE.equals(dto.getIsFitToDonate())) {
            reg.setStatus(DonationRegistration.Status.CHECKING);
            reg.setVolumeToTake(DonationRegistration.Volume.fromInt(dto.getVolumeToTake()));
        } else {
            reg.setStatus(DonationRegistration.Status.CANCELLED);
            reg.setVolumeToTake(DonationRegistration.Volume.fromInt(0));
        }

        donationRegistrationRepository.save(reg);
        healthCheckRepository.save(healthCheck);
        bloodDonationHistoryService.updateFromHealthCheck(healthCheck);

        return toDTO(healthCheck);
    }


    private void validateVolumeToTake(HealthCheckDTO dto) {
        if (Boolean.TRUE.equals(dto.getIsFitToDonate())) {
            if (dto.getVolumeToTake() == null || !List.of(250, 350, 450).contains(dto.getVolumeToTake())) {
                throw new IllegalArgumentException("Thể tích chỉ được là 250, 350 hoặc 450ml nếu đủ điều kiện hiến máu");
            }
        } else {
            if (dto.getVolumeToTake() != null && dto.getVolumeToTake() != 0) {
                throw new IllegalArgumentException("Nếu không đủ điều kiện hiến máu thì thể tích phải là 0 hoặc để trống");
            }
        }
    }


    @Transactional
    public HealthCheckDTO updateHealthCheckById(String healthCheckId, HealthCheckDTO dto) {
        HealthCheck existing = healthCheckRepository.findByHealthCheckId(healthCheckId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy bản ghi HealthCheck với ID: " + healthCheckId));

        DonationRegistration reg = existing.getDonationRegistration();
        if (reg == null) {
            throw new IllegalStateException("HealthCheck không liên kết với đơn đăng ký nào");
        }

        if (reg.getStatus() == DonationRegistration.Status.COMPLETED) {
            throw new IllegalStateException("Không thể cập nhật vì đơn đã hoàn thành");
        }

        validateVolumeToTake(dto);

        // Update fields
        existing.setWeight(dto.getWeight());
        existing.setTemperature(dto.getTemperature());
        existing.setBloodPressure(dto.getBloodPressure());
        existing.setPulse(dto.getPulse());
        existing.setHemoglobin(dto.getHemoglobin());
        existing.setNote(dto.getNote());
        existing.setIsFitToDonate(dto.getIsFitToDonate());

        // Xử lý volume và status
        if (Boolean.TRUE.equals(dto.getIsFitToDonate())) {
            reg.setStatus(DonationRegistration.Status.CHECKING);
            reg.setVolumeToTake(DonationRegistration.Volume.fromInt(dto.getVolumeToTake()));
        } else {
            reg.setStatus(DonationRegistration.Status.CANCELLED);
            reg.setVolumeToTake(DonationRegistration.Volume.ML_0);
        }

        // Đảm bảo liên kết đúng hai chiều
        existing.setDonationRegistration(reg);
        reg.setHealthCheck(existing);

        donationRegistrationRepository.save(reg);
        healthCheckRepository.save(existing);
        bloodDonationHistoryService.updateFromHealthCheck(existing);

        return toDTO(existing);
    }



    @Transactional
    public void updateStatus(String healthCheckId) {
        HealthCheck healthCheck = healthCheckRepository.findByHealthCheckId(healthCheckId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy HealthCheck với ID: " + healthCheckId));

        DonationRegistration reg = healthCheck.getDonationRegistration();
        if (reg == null) {
            throw new IllegalStateException("HealthCheck không liên kết với đơn đăng ký");
        }

        Profile profile = reg.getAccount().getProfile();
        if (profile == null) {
            throw new IllegalStateException("Tài khoản chưa có Profile");
        }

        String accountId = reg.getAccount().getAccountId();
        DonationRegistration.Status currentStatus = reg.getStatus();

        DonationRegistration.Volume volumeEnum = reg.getVolumeToTake();
        if (volumeEnum == null) {
            throw new IllegalStateException("Thể tích máu (volumeToTake) trong đơn đăng ký bị null");
        }

        long addVolume = volumeEnum.getMl();

        BloodDonationEvent event = reg.getEvent();
        if (event == null) {
            throw new IllegalStateException("Đơn đăng ký không liên kết với sự kiện hiến máu");
        }

        switch (currentStatus) {
            case CANCELLED:
            case CHECKING:
                reg.setStatus(DonationRegistration.Status.COMPLETED);
                profileService.increaseBloodDonationCount(accountId);
                profile.setRestDate(LocalDate.now().plusDays(84));
                eventService.increaseActualVolumeForEvent(event.getEventId(), addVolume);
                break;

            case COMPLETED:
                reg.setStatus(DonationRegistration.Status.CANCELLED);
                profileService.decreaseBloodDonationCount(accountId);
                profile.setRestDate(null);
                eventService.increaseActualVolumeForEvent(event.getEventId(), -addVolume);
                break;

            default:
                throw new IllegalStateException("Không thể cập nhật trạng thái cho trạng thái hiện tại: " + currentStatus);
        }

        reg.setHealthCheck(healthCheck);
        healthCheck.setDonationRegistration(reg);

        donationRegistrationRepository.save(reg);
        profileRepository.save(profile);
        healthCheckRepository.save(healthCheck);
        bloodDonationHistoryService.updateFromHealthCheck(healthCheck);
    }




    @Transactional
    public HealthCheckDTO deleteHealthCheck(String healthCheckId) {
        HealthCheck existing = healthCheckRepository.findByHealthCheckId(healthCheckId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy bản ghi HealthCheck để xoá"));

        if (existing.getDonationRegistration() != null) {
            existing.getDonationRegistration().setHealthCheck(null);
            existing.setDonationRegistration(null); // CỰC QUAN TRỌNG
        }

        healthCheckRepository.delete(existing);
        return toDTO(existing);
    }


    @Transactional
    public Map<String, Object> deleteMultipleHealthChecksSafe(List<String> ids) {
        List<String> deleted = new ArrayList<>();
        Map<String, String> errors = new HashMap<>();

        for (String id : ids) {
            try {
                HealthCheck healthCheck = healthCheckRepository.findByHealthCheckId(id)
                        .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy bản ghi với ID: " + id));
                healthCheckRepository.delete(healthCheck);
                deleted.add(id);
            } catch (EntityNotFoundException e) {
                errors.put(id, e.getMessage());
            } catch (Exception e) {
                errors.put(id, "Lỗi không xác định: " + e.getMessage());
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("deleted", deleted);
        result.put("errors", errors);
        return result;
    }


    public List<HealthCheckDTO> getAllHealthChecks() {
        return healthCheckRepository.findAll().stream()
                .map(HealthCheckDTO::toDTO)
                .toList();
    }


    public HealthCheckDTO getHealthCheckByRegistration(String registrationId) {
        HealthCheck healthCheck = healthCheckRepository
                .findByDonationRegistration_RegistrationId(registrationId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy bản ghi HealthCheck với mã đăng ký: " + registrationId));
        return toDTO(healthCheck);
    }


    public static String generateHealthCheckId() {
        String timestamp = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
        int randomNum = new Random().nextInt(900) + 100;
        return "HC-" + timestamp + "-" + randomNum;
    }
}
