package com.runiverse.running_service.presentation.user.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record OnboardingRequest(
        @NotBlank(message = "닉네임은 필수입니다.")
        @Size(
                min = 2,
                max = 16,
                message = "닉네임은 2자 이상 16자 이하여야 합니다."
        )
        @Pattern(
                regexp = "^[가-힣a-zA-Z0-9_]+$",
                message = "닉네임은 한글, 영문, 숫자, _만 사용할 수 있습니다."
        )
        String nickname,

        @NotBlank(message = "성별은 필수입니다.")
        @Pattern(
                regexp = "^(?i)(MALE|FEMALE)$",
                message = "성별은 MALE 또는 FEMALE이어야 합니다."
        )
        String gender,

        @NotNull(message = "생년월일은 필수입니다.")
        @PastOrPresent(message = "생년월일은 미래일 수 없습니다.")
        LocalDate birthday,

        @NotNull(message = "평균 페이스는 필수입니다.")
        @Min(value = 120, message = "평균 페이스는 120초 이상이어야 합니다.")
        @Max(value = 1800, message = "평균 페이스는 1800초 이하여야 합니다.")
        Integer averagePaceSecondsPerKm,

        @NotNull(message = "몸무게는 필수입니다.")
        @DecimalMin(value = "20.0", message = "몸무게는 20kg 이상이어야 합니다.")
        @DecimalMax(value = "300.0", message = "몸무게는 300kg 이하여야 합니다.")
        BigDecimal weightKg,

        @NotNull(message = "키는 필수입니다.")
        @DecimalMin(value = "20.0", message = "키는 20cm 이상이어야 합니다.")
        @DecimalMax(value = "300.0", message = "키는 300cm 이하여야 합니다.")
        BigDecimal heightCm
) {

}
