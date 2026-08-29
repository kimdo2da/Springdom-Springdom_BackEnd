package com.example.lightsafe.publicdata;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 공공데이터 수집 이력.
 *
 * 매월 자동 수집이 돌았는지, 몇 건을 받아 몇 건을 저장했는지 남깁니다.
 * 관리자 화면에서 이 표를 보고 최신화 여부를 확인합니다.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "public_data_sync_history")
public class PublicDataSyncHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "public_data_sync_history_id")
    private Long publicDataSyncHistoryId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 30)
    private PublicDataSource source;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PublicDataSyncStatus status;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    /**
     * 원본에서 읽어 온 행 수.
     */
    @Column(name = "fetched_count")
    private int fetchedCount;

    /**
     * 좌표가 있어 바로 저장한 지점 수(중복 제거 후).
     */
    @Column(name = "saved_count")
    private int savedCount;

    /**
     * 좌표가 없어 지오코딩 대기열로 보낸 지점 수.
     */
    @Column(name = "queued_count")
    private int queuedCount;

    /**
     * 이번 실행에서 주소를 좌표로 바꿔 추가한 지점 수.
     */
    @Column(name = "geocoded_count")
    private int geocodedCount;

    @Column(name = "message", length = 1000)
    private String message;

    public PublicDataSyncHistory(
            PublicDataSource source
    ) {
        this.source = source;
        this.status = PublicDataSyncStatus.RUNNING;
        this.startedAt = LocalDateTime.now();
    }
}
