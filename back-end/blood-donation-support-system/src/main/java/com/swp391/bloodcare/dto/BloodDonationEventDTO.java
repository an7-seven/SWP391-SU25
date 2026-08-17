package com.swp391.bloodcare.dto;

import com.swp391.bloodcare.entity.BloodDonationEvent;
import jakarta.validation.constraints.*;
import lombok.*;

import java.util.Date;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BloodDonationEventDTO {

    private String eventId;

    @NotBlank(message = "Tên sự kiện không được để trống")
    @Size(max = 255, message = "Tên sự kiện không được vượt quá 255 ký tự")
    private String nameOfEvent;

    @PastOrPresent(message = "Ngày tạo không được ở tương lai")
    private Date creationDate;

    @NotNull(message = "Ngày bắt đầu không được để trống")
    private Date startDate;

    @NotNull(message = "Ngày kết thúc không được để trống")
    private Date endDate;

    @NotNull(message = "Lượng máu kỳ vọng không được để trống")
    @Positive(message = "Lượng máu kỳ vọng phải lớn hơn 0")
    private Long expectedBloodVolume;

    @NotNull(message = "Lượng máu thực tế không được để trống")
    @Min(value = 0, message = "Lượng máu thực tế phải >= 0")
    private Long actualVolume = 0L;


    @NotBlank(message = "Địa điểm không được để trống")
    @Size(max = 255, message = "Địa điểm không được quá 255 ký tự")
    private String location;

    private BloodDonationEvent.Status status;

    @NotNull(message = "Kinh phí không được để trống")
    @Min(value = 0, message = "Kinh phí dự kiến phải >= 0")
    private Long expectedCost;

    @Size(max = 50, message = "ID người tạo không được vượt quá 50 ký tự")
    private String accountId;

    public static BloodDonationEventDTO toDTO(BloodDonationEvent event) {
        return BloodDonationEventDTO.builder()
                .eventId(event.getEventId())
                .nameOfEvent(event.getNameOfEvent())
                .creationDate(event.getCreationDate())
                .startDate(event.getStartDate())
                .endDate(event.getEndDate())
                .expectedBloodVolume(event.getExpectedBloodVolume())
                .actualVolume(event.getActualVolume())
                .location(event.getLocation())
                .status(event.getStatus())
                .expectedCost(event.getExectedCost())
                .accountId(event.getAccount() != null ? event.getAccount().getAccountId() : null)
                .build();
    }
}
