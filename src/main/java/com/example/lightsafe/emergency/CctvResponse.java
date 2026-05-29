package com.example.lightsafe.emergency;

public record CctvResponse(
        Long cctvId,
        String cctvName,
        Double latitude,
        Double longitude,
        String address,
        String purpose
) {
    public static CctvResponse from(Cctv cctv) {
        if (cctv == null) return null;

        return new CctvResponse(
                cctv.getCctvId(),
                cctv.getCctvName(),
                cctv.getLatitude().doubleValue(),
                cctv.getLongitude().doubleValue(),
                cctv.getAddress(),
                cctv.getPurpose()
        );
    }
}