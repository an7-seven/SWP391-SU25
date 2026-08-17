package com.swp391.bloodcare.service;

import com.swp391.bloodcare.dto.NotificationDTO;
import com.swp391.bloodcare.entity.*;

import com.swp391.bloodcare.repository.AccountRepository;
import com.swp391.bloodcare.repository.DonationRegistrationRepository;
import com.swp391.bloodcare.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.scheduling.annotation.Scheduled;

import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final AccountRepository accountRepository;
    private final DonationRegistrationRepository donationRegistrationRepository;

    @Autowired
    private EmailService emailService;

    public void sendNotification(NotificationDTO dto) {
        Account account = getAccountOrThrow(dto.getAccountId());
        Notification notification = toEntity(dto, account);
        notificationRepository.save(notification);
    }

    public int sendNotifications(List<NotificationDTO> dtoList) {
        if (dtoList.isEmpty()) return 0;

        List<String> accountIds = dtoList.stream()
                .map(NotificationDTO::getAccountId)
                .distinct()
                .collect(Collectors.toList());

        Map<String, Account> accountMap = accountRepository.findAllById(accountIds).stream()
                .collect(Collectors.toMap(Account::getAccountId, a -> a));

        List<Notification> notifications = dtoList.stream()
                .map(dto -> {
                    Account acc = accountMap.get(dto.getAccountId());
                    return acc != null ? toEntity(dto, acc) : null;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        notificationRepository.saveAll(notifications);
        return notifications.size();
    }

    public void sendSystemNotification(String accountId, String title, String content) {
        Account account = getAccountOrThrow(accountId);
        Notification notification = Notification.builder()
                .notificationId(generateNotificationId())
                .account(account)
                .title(title)
                .content(content)
                .img(null)
                .createDate(new Date())
                .build();

        notificationRepository.save(notification);
    }

    public void notifyAchievementUnlocked(String accountId, String achievementName) {
        String title = "🎉 Chúc mừng bạn!";
        String content = "Bạn vừa đạt được thành tựu: " + achievementName + ". Hãy tiếp tục cố gắng nhé!";
        sendSystemNotification(accountId, title, content);
    }



    @Scheduled(cron = "0 0 8 * * ?") // 8h sáng mỗi ngày
    public void sendVaccinationReminders() {
        LocalDate today = LocalDate.now();

        List<DonationRegistration> todayRegistrations = donationRegistrationRepository.findByDonationDate(today);

        for (DonationRegistration reg : todayRegistrations) {
            Account acc = reg.getAccount();
            if (acc != null) {
                sendSystemNotification(
                        acc.getAccountId(),
                        "📅 Nhắc lịch hiến máu",
                        "Bạn có lịch hiến máu hôm nay (" + today.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")) + "). Hãy đến đúng giờ nhé!"
                );
            }
        }
    }



    public List<NotificationDTO> getNotificationsByAccount(String accountId) {
        Account account = getAccountOrThrow(accountId);
        return account.getNotifications().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }


    private Account getAccountOrThrow(String accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy account: " + accountId));
    }

    private Notification toEntity(NotificationDTO dto, Account account) {
        return Notification.builder()
                .notificationId(dto.getNotificationId() != null ? dto.getNotificationId() : generateNotificationId())
                .account(account)
                .title(dto.getTitle())
                .content(dto.getContent())
                .img(dto.getImg())
                .createDate(dto.getCreateDate() != null ? dto.getCreateDate() : new Date())
                .build();
    }

    private NotificationDTO toDTO(Notification entity) {
        return NotificationDTO.builder()
                .notificationId(entity.getNotificationId())
                .accountId(entity.getAccount().getAccountId())
                .title(entity.getTitle())
                .content(entity.getContent())
                .img(entity.getImg())
                .createDate(entity.getCreateDate())
                .build();
    }

    private String generateNotificationId() {
        String timestamp = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
        int randomNum = new Random().nextInt(900) + 100;
        return "NT-" + timestamp + "-" + randomNum;
    }

    // ==== Hỗ trợ request ====

    public void sendBloodRequestNotification(BloodRequest request, List<Account> potentialDonors, String confirmLink) {
        String requestInfo = buildRequestInfo(request);

        for (Account donor : potentialDonors) {
            // Gửi email
            if (donor.getEmail()!= null) {
                String emailSubject = "Yêu cầu hiến máu khẩn cấp - BloodCare";
                String emailContent = buildEmailContent(request, donor, requestInfo,confirmLink);
                emailService.sendEmail(donor.getEmail(), emailSubject, emailContent);
            }
        }
    }

    public void sendApprovalNotification(BloodRequest request) {
        String recipientEmail = request.getAccount().getEmail();
        String recipientPhone = request.getAccount().getProfile().getPhone();

        if (recipientEmail != null) {
            String subject = "Đơn xin máu đã được chấp nhận - " + request.getIdBloodRequest();
            String content = buildApprovalEmailContent(request);
            emailService.sendEmail(recipientEmail, subject, content);
        }

    }

    public void sendRejectionNotification(BloodRequest request) {
        String recipientEmail = request.getAccount().getEmail();
        String recipientPhone = request.getAccount().getProfile().getPhone();

        if (recipientEmail != null) {
            String subject = "Thông báo về đơn xin máu - " + request.getIdBloodRequest();
            String content = buildRejectionEmailContent(request);
            emailService.sendEmail(recipientEmail, subject, content);
        }

    }

    private String buildRequestInfo(BloodRequest request) {
        return String.format(
                "Nhóm máu: %s\nThành phần: %s\nThể tích: %s ml\nVị trí: %s\nKhẩn cấp: %s",
                request.getBloodCode().getBloodCode(),
                request.getComponent().getType(),
                request.getVolume().getMl(),
                request.getAccount().getProfile().getAddress().toString(),
                request.isEmergency() ? "Có" : "Không"
        );
    }

    private String buildEmailContent(BloodRequest request, Account donor, String requestInfo, String confirmLink) {
        return String.format(
                "Chào %s,\n\n" +
                        "Chúng tôi có một yêu cầu hiến máu từ %s.\n\n" +
                        "Chi tiết yêu cầu:\n%s\n\n" +
                        "Nếu bạn có thể hiến máu, vui lòng xác nhận tại đường dẫn sau:\n%s\n\n" +
                        "Hoặc bạn cũng có thể liên hệ trực tiếp với người cần máu:\n" +
                        "Tên: %s\n" +
                        "Số điện thoại: %s\n" +
                        "Email: %s\n\n" +
                        "Hoặc liên hệ với chúng tôi qua hotline: 1900-xxx-xxx\n\n" +
                        "Cảm ơn sự tử tế của bạn!\n\n" +
                        "Trân trọng,\nHệ thống quản lý máu BloodCare",
                donor.getProfile().getName(),
                request.getAccount().getProfile().getName(),
                requestInfo,
                confirmLink,  // dòng mới được chèn vào
                request.getAccount().getProfile().getName(),
                request.getAccount().getProfile().getPhone() != null ? request.getAccount().getProfile().getPhone() : "Chưa cung cấp",
                request.getAccount().getEmail() != null ? request.getAccount().getEmail() : "Chưa cung cấp"
        );
    }

    private String buildApprovalEmailContent(BloodRequest request) {
        return String.format(
                "Chào %s,\n\n" +
                        "Đơn xin máu của bạn (ID: %s) đã được chấp nhận.\n\n" +
                        "Chi tiết:\n" +
                        "Nhóm máu: %s\n" +
                        "Thành phần: %s\n" +
                        "Thể tích: %s ml\n" +
                        "Ngày yêu cầu: %s\n" +
                        "Mã túi máu: %s\n\n" +
                        "Vui lòng liên hệ với chúng tôi để sắp xếp việc nhận máu.\n" +
                        "Hotline: 1900-xxx-xxx\n\n" +
                        "Trân trọng,\nHệ thống quản lý máu BloodCare",
                request.getAccount().getProfile().getName(),
                request.getIdBloodRequest(),
                request.getBloodCode().getBloodCode(),
                request.getComponent().getType(),
                request.getVolume().getMl(),
                request.getRequestDate(),
                request.getBloodBag() != null ? request.getBloodBag().getBagId() : "Chưa xác định"
        );
    }

    private String buildRejectionEmailContent(BloodRequest request) {
        return String.format(
                "Chào %s,\n\n" +
                        "Rất tiếc, đơn xin máu của bạn (ID: %s) không thể được chấp nhận.\n\n" +
                        "Lý do: %s\n\n" +
                        "Chúng tôi đang tìm kiếm những người hiến máu phù hợp trong khu vực của bạn.\n" +
                        "Chúng tôi sẽ liên hệ với bạn ngay khi có thông tin.\n\n" +
                        "Để được hỗ trợ, vui lòng liên hệ hotline: 1900-xxx-xxx\n\n" +
                        "Trân trọng,\nHệ thống quản lý máu BloodCare",
                request.getAccount().getProfile().getName(),
                request.getIdBloodRequest(),
                request.getRejectionReason() != null ? request.getRejectionReason() : "Không có máu phù hợp"
        );
    }

    public void sendDonorNotification(BloodBag bag) {
        AfterDonationBlood afterDonation = bag.getAfterDonationBlood();
        if (afterDonation == null) return;

        HealthCheck healthCheck = afterDonation.getHealthCheck();
        if (healthCheck == null) return;

        DonationRegistration registration = healthCheck.getDonationRegistration();
        if (registration == null) return;

        Account donor = registration.getAccount();
        if (donor == null || donor.getEmail() == null || donor.getProfile() == null || donor.getProfile().getName() == null) {
            return;
        }

        String subject = "Cảm ơn bạn vì hành động cao cả!";
        String content = String.format("""
        Xin chào %s,

        Chúng tôi xin trân trọng thông báo rằng túi máu của bạn (Mã: %s) đã được sử dụng để cứu giúp một bệnh nhân.

        Cảm ơn bạn vì sự đóng góp quý giá cho cộng đồng.

        Trân trọng,
        Đội ngũ BloodCare
        """,
                donor.getProfile().getName(), bag.getBagId());

        emailService.sendEmail(donor.getEmail(), subject, content);
        sendSystemNotification(
                donor.getAccountId(),
                "Túi máu của bạn đã được sử dụng",
                "Túi máu (Mã: " + bag.getBagId() + ") của bạn đã được sử dụng để giúp một bệnh nhân. Cảm ơn bạn!"
        );
    }

}
