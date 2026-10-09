package com.example.lightsafe.safe;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(
        name = "police_facilities",
        indexes = {
                @Index(
                        name = "idx_police_bbox",
                        columnList = "latitude, longitude"
                )
        },
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_police_object",
                        columnNames = "object_id"
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
public class PoliceFacility {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "object_id", nullable = false, length = 32)
    private String objectId;

    @Column(length = 100)
    private String name;

    @Column(length = 30)
    private String kind;

    @Column(length = 50)
    private String agency;

    @Column(length = 50)
    private String station;

    @Column(length = 255)
    private String address;

    @Column(length = 30)
    private String tel;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal longitude;
}