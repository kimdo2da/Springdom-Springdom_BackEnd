package com.example.lightsafe.safe;

import com.example.lightsafe.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "bookmark",
        indexes = {
                @Index(
                        name = "idx_bookmark_user_id",
                        columnList = "user_id"
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
public class Bookmark {

    @Id
    @GeneratedValue(
            strategy = GenerationType.IDENTITY
    )
    @Column(name = "id")
    private Long id;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "user_id",
            nullable = false,
            foreignKey = @ForeignKey(
                    name = "fk_bookmark_users"
            )
    )
    private User user;

    @Column(
            name = "route_name",
            nullable = false,
            length = 255
    )
    private String routeName;

    @Column(
            name = "start_latitude",
            nullable = false
    )
    private double startLatitude;

    @Column(
            name = "start_longitude",
            nullable = false
    )
    private double startLongitude;

    @Column(
            name = "end_latitude",
            nullable = false
    )
    private double endLatitude;

    @Column(
            name = "end_longitude",
            nullable = false
    )
    private double endLongitude;

    @Column(
            name = "safety_score",
            nullable = false
    )
    private int safetyScore;
}