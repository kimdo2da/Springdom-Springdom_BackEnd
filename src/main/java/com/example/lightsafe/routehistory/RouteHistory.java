package com.example.lightsafe.routehistory;

import com.example.lightsafe.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "route_history",
        indexes = {
                @Index(
                        name = "fk_route_history_users1_idx",
                        columnList = "user_id"
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
public class RouteHistory {

    @Id
    @GeneratedValue(
            strategy = GenerationType.IDENTITY
    )
    @Column(name = "route_history_id")
    private Long routeHistoryId;

    @Column(
            name = "start_latitude",
            nullable = false,
            precision = 10,
            scale = 7
    )
    private BigDecimal startLatitude;

    @Column(
            name = "start_longitude",
            nullable = false,
            precision = 10,
            scale = 7
    )
    private BigDecimal startLongitude;

    @Column(
            name = "end_latitude",
            nullable = false,
            precision = 10,
            scale = 7
    )
    private BigDecimal endLatitude;

    @Column(
            name = "end_longitude",
            nullable = false,
            precision = 10,
            scale = 7
    )
    private BigDecimal endLongitude;

    @Column(
            name = "route_name",
            length = 100
    )
    private String routeName;

    @Column(
            name = "searched_at",
            nullable = false
    )
    private LocalDateTime searchedAt;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "user_id",
            nullable = false,
            foreignKey = @ForeignKey(
                    name = "fk_route_history_users1"
            )
    )
    private User user;
}