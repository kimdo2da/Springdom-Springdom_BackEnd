package com.example.lightsafe.sync;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "data_sync_log")
@Getter
@Setter
@NoArgsConstructor
public class DataSyncLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String dataset;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(nullable = false, length = 10)
    private String status;

    @Column(name = "fetched_count")
    private Integer fetchedCount;

    @Column(name = "saved_count")
    private Integer savedCount;

    @Column(length = 500)
    private String message;
}