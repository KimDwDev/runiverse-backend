package com.runiverse.running_service.presentation.user.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ProfileImageUploadUrlRequest(
        @NotBlank(message = "이미지 형식은 필수입니다.")
        @Pattern(
                regexp = "^(?i)(image/jpeg|image/png|image/webp)$",
                message = "이미지는 JPEG, PNG, WEBP 형식만 업로드할 수 있습니다."
        )
        String mimeType
) {

}
