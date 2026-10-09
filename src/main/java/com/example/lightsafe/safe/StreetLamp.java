package com.example.lightsafe.safe;

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
@Table(name = "street_lamps")
public class StreetLamp {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "lamp_id")
    private Long lampId;

    @Column(name = "latitude", nullable = false, precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(name = "longitude", nullable = false, precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "address", length = 255)
    private String address;

    @Column(name = "lamp_count")
    private Integer lampCount = 1;

    @Column(name = "source_hash", columnDefinition = "BINARY(16)")
    private byte[] sourceHash;

    @Column(name = "road_address", length = 255)
    private String roadAddress;

    @Column(name = "lot_address", length = 255)
    private String lotAddress;

    @Column(name = "location_name", length = 255)
    private String locationName;

    @Column(name = "instt_name", length = 100)
    private String insttName;

    @Column(name = "install_type", length = 30)
    private String installType;

    @Column(name = "coord_source", nullable = false, length = 10)
    private String coordSource = "API";

    @Column(name = "reference_date")
    private LocalDate referenceDate;
}