package com.example.lightsafe.safe;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ConvenienceStoreDto {
    private String storeId;
    private String storeName;
    private String address;
    private Double latitude;
    private Double longitude;
    private String phone;
    private String placeUrl; // 카카오맵 상세페이지 링크
}
