package com.example.lightsafe.publicdata;

public enum PublicDataSource {

    /**
     * 전국CCTV표준데이터.
     */
    CCTV,

    /**
     * 전국보안등표준데이터(가로등).
     */
    SECURITY_LIGHT,

    /**
     * 좌표 없는 보안등 주소를 좌표로 바꾸는 작업.
     */
    GEOCODING
}
