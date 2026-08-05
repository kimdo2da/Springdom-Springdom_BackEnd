package com.example.lightsafe.routehistory;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RouteHistoryCreateRequest(

        @Size(max = 100, message = "routeName은 최대 100자까지 입력할 수 있습니다.")
        String routeName,

        @NotNull(message = "startLatitude는 필수입니다.")
        @DecimalMin(value = "-90.0", message = "startLatitude는 -90 이상이어야 합니다.")
        @DecimalMax(value = "90.0", message = "startLatitude는 90 이하여야 합니다.")
        Double startLatitude,

        @NotNull(message = "startLongitude는 필수입니다.")
        @DecimalMin(value = "-180.0", message = "startLongitude는 -180 이상이어야 합니다.")
        @DecimalMax(value = "180.0", message = "startLongitude는 180 이하여야 합니다.")
        Double startLongitude,

        @NotNull(message = "endLatitude는 필수입니다.")
        @DecimalMin(value = "-90.0", message = "endLatitude는 -90 이상이어야 합니다.")
        @DecimalMax(value = "90.0", message = "endLatitude는 90 이하여야 합니다.")
        Double endLatitude,

        @NotNull(message = "endLongitude는 필수입니다.")
        @DecimalMin(value = "-180.0", message = "endLongitude는 -180 이상이어야 합니다.")
        @DecimalMax(value = "180.0", message = "endLongitude는 180 이하여야 합니다.")
        Double endLongitude
) {
}