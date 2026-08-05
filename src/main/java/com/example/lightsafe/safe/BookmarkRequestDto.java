package com.example.lightsafe.safe;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BookmarkRequestDto {

    @NotBlank(
            message = "경로 이름은 필수입니다."
    )
    @Size(
            max = 255,
            message = "경로 이름은 255자 이하여야 합니다."
    )
    private String routeName;

    @NotNull(
            message = "출발지 위도는 필수입니다."
    )
    @DecimalMin(
            value = "-90.0",
            message = "출발지 위도는 -90 이상이어야 합니다."
    )
    @DecimalMax(
            value = "90.0",
            message = "출발지 위도는 90 이하여야 합니다."
    )
    private Double startLatitude;

    @NotNull(
            message = "출발지 경도는 필수입니다."
    )
    @DecimalMin(
            value = "-180.0",
            message = "출발지 경도는 -180 이상이어야 합니다."
    )
    @DecimalMax(
            value = "180.0",
            message = "출발지 경도는 180 이하여야 합니다."
    )
    private Double startLongitude;

    @NotNull(
            message = "도착지 위도는 필수입니다."
    )
    @DecimalMin(
            value = "-90.0",
            message = "도착지 위도는 -90 이상이어야 합니다."
    )
    @DecimalMax(
            value = "90.0",
            message = "도착지 위도는 90 이하여야 합니다."
    )
    private Double endLatitude;

    @NotNull(
            message = "도착지 경도는 필수입니다."
    )
    @DecimalMin(
            value = "-180.0",
            message = "도착지 경도는 -180 이상이어야 합니다."
    )
    @DecimalMax(
            value = "180.0",
            message = "도착지 경도는 180 이하여야 합니다."
    )
    private Double endLongitude;

    @NotNull(
            message = "안전 점수는 필수입니다."
    )
    @Min(
            value = 0,
            message = "안전 점수는 0 이상이어야 합니다."
    )
    private Integer safetyScore;
}
