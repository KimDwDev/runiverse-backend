package com.runiverse.running_service.presentation.user.response;

import java.math.BigDecimal;
import java.time.LocalDate;

// 온보딩 전에도 네 값을 null로 실어 보내야 해 @JsonInclude(NON_NULL)을 쓰지 않는다
public record MyProfileResponse(
        String introduction,
        String gender,
        LocalDate birthday,
        BigDecimal weightKg,
        BigDecimal heightCm
) {

}
