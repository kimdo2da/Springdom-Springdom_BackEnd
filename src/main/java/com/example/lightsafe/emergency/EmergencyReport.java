package com.example.lightsafe.emergency;

import com.example.lightsafe.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "emergency_reports")
public class EmergencyReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "report_id")
    private Long reportId;

    @Column(name = "latitude", nullable = false, precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(name = "longitude", nullable = false, precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "report_status", nullable = false, length = 20)
    private String reportStatus = "RECEIVED";

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "is_false_report", nullable = false)
    private Boolean isFalseReport = false;

    @CreationTimestamp
    @Column(name = "reported_at", nullable = false, updatable = false)
    private LocalDateTime reportedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "nearest_cctv_id")
    private Cctv nearestCctv;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "danger_zone_id", nullable = false)
    private DangerZone dangerZone;
}