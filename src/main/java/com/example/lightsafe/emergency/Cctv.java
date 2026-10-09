package com.example.lightsafe.emergency;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "cctvs")
public class Cctv {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cctv_id")
    private Long cctvId;

    @Column(name = "cctv_name", length = 100)
    private String cctvName;

    @Column(name = "latitude", nullable = false, precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(name = "longitude", nullable = false, precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "address", length = 255)
    private String address;

    @Column(name = "purpose", length = 50)
    private String purpose;

    @Column(name = "mng_no", length = 40)
    private String mngNo;

    @Column(name = "camera_count", nullable = false)
    private Integer cameraCount = 1;

    @Column(name = "institution", length = 120)
    private String institution;

    @Column(name = "reference_date")
    private LocalDate referenceDate;
}