package com.example.lightsafe.safe;

public record PoliceFacilityResponse(
        Long id,
        String objectId,
        String name,
        String kind,
        String agency,
        String station,
        String address,
        String tel,
        double latitude,
        double longitude
) {
    public static PoliceFacilityResponse from(
            PoliceFacility facility
    ) {
        return new PoliceFacilityResponse(
                facility.getId(),
                facility.getObjectId(),
                facility.getName(),
                facility.getKind(),
                facility.getAgency(),
                facility.getStation(),
                facility.getAddress(),
                facility.getTel(),
                facility.getLatitude().doubleValue(),
                facility.getLongitude().doubleValue()
        );
    }
}