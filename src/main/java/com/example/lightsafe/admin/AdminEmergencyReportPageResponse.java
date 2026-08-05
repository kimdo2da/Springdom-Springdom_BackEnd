package com.example.lightsafe.admin;

import com.example.lightsafe.emergency.EmergencyReportResponse;
import org.springframework.data.domain.Page;

import java.util.List;

public record AdminEmergencyReportPageResponse(
        List<EmergencyReportResponse> reports,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {

    public static AdminEmergencyReportPageResponse from(
            Page<EmergencyReportResponse> reportPage
    ) {
        return new AdminEmergencyReportPageResponse(
                reportPage.getContent(),
                reportPage.getNumber(),
                reportPage.getSize(),
                reportPage.getTotalElements(),
                reportPage.getTotalPages(),
                reportPage.isFirst(),
                reportPage.isLast()
        );
    }
}