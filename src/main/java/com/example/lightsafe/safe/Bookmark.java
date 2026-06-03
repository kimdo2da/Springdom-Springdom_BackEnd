package com.example.lightsafe.safe;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class Bookmark {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String routeName;
    private double startLatitude;  // db 컬럼명도 맞춰줍니다.
    private double startLongitude;
    private double endLatitude;
    private double endLongitude;
    private int safetyScore;
}