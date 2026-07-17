package com.example.lightsafe.friends;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EmergencyAllowUpdateRequest {

    @NotNull(message = "긴급 위치 공유 설정값은 필수입니다.")
    private Boolean isEmergencyAllowed;
}