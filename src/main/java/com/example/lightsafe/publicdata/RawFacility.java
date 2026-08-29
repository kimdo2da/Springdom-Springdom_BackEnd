package com.example.lightsafe.publicdata;

/**
 * 공공데이터에서 막 읽어 온 한 지점. 저장 전 단계의 값입니다.
 *
 * @param latitude  원본에 좌표가 없으면 null
 * @param longitude 원본에 좌표가 없으면 null
 * @param address   지오코딩에 쓸 주소(지번 우선, 없으면 도로명)
 * @param count     카메라 대수 또는 보안등 설치 개수
 * @param sido      시·도 이름. 통계와 확인용
 * @param name      설치 위치명
 * @param purpose   설치 목적(CCTV 전용)
 */
public record RawFacility(
        Double latitude,
        Double longitude,
        String address,
        Integer count,
        String sido,
        String name,
        String purpose
) {

    public boolean hasCoordinate() {
        return latitude != null && longitude != null;
    }

    public boolean hasAddress() {
        return address != null && !address.isBlank();
    }
}
