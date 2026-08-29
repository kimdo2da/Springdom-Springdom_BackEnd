package com.example.lightsafe.publicdata;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 공공데이터 수집 총괄.
 *
 * 예전에는 자치구별 CSV 파일을 저장소에 넣어 두고 서버가 뜰 때 읽었습니다.
 * 그래서 서울 25개 구 중 24개만 있었고, 강남·용산·마포·송파는 원본에 좌표가 없어
 * 지도에 한 개도 나오지 않았습니다.
 *
 * 이제는 공공데이터에서 전국 데이터를 직접 받아 표에 저장합니다.
 * 공공데이터가 한 달에 한 번 갱신되므로 매월 5일 새벽에 자동으로 다시 받습니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PublicDataSyncService {

    private final PublicDataProperties properties;

    private final SecurityLightApiClient securityLightApiClient;

    private final CctvDataClient cctvDataClient;

    private final PublicDataWriter publicDataWriter;

    private final GeocodingService geocodingService;

    private final PublicDataSyncHistoryRepository historyRepository;

    /**
     * 수집은 무겁고 오래 걸립니다. 두 개가 동시에 돌면 표가 뒤엉키므로 한 번에 하나만 돌립니다.
     */
    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * 수동 수집을 돌릴 일꾼 한 명. 요청 스레드를 20분씩 붙잡지 않기 위해 씁니다.
     * 데몬이라 서버를 내릴 때 붙잡지 않습니다.
     */
    private final ExecutorService worker =
            Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "public-data-sync");
                thread.setDaemon(true);
                return thread;
            });

    // ------------------------------------------------------------------
    // 예약 실행
    // ------------------------------------------------------------------

    /**
     * 매월 5일 새벽 3시(기본값)에 전국 데이터를 다시 받습니다.
     *
     * 공공데이터가 월 1회 갱신되고 API 호출 한도도 있어서
     * 자주 받을 이유가 없습니다.
     */
    @Scheduled(
            cron = "${publicdata.sync-cron:0 0 3 5 * *}",
            zone = "Asia/Seoul"
    )
    public void syncMonthly() {
        log.info("매월 정기 공공데이터 수집을 시작합니다.");

        if (!running.compareAndSet(false, true)) {
            log.warn("다른 수집 작업이 돌고 있어 이번 정기 수집을 건너뜁니다.");
            return;
        }

        try {
            runCctvSync();
            runSecurityLightSync();
        } finally {
            running.set(false);
        }
    }

    /**
     * 지오코딩 대기열을 조금씩 비웁니다.
     *
     * 카카오 일일 한도 때문에 한 번에 다 못 하므로 주기적으로 이어서 처리합니다.
     */
    @Scheduled(
            initialDelayString = "${publicdata.geocoding.drain-interval-millis:3600000}",
            fixedDelayString = "${publicdata.geocoding.drain-interval-millis:3600000}"
    )
    public void drainGeocodingQueue() {
        if (properties.getGeocoding().getDrainIntervalMillis() <= 0) {
            return;
        }

        if (!running.compareAndSet(false, true)) {
            log.debug("다른 수집 작업이 돌고 있어 지오코딩을 건너뜁니다.");
            return;
        }

        try {
            runGeocoding();
        } catch (Exception e) {
            log.warn("지오코딩 대기열 처리 중 오류가 발생했습니다.", e);
        } finally {
            running.set(false);
        }
    }

    /**
     * 표가 비어 있으면 서버가 뜬 뒤 한 번 채웁니다.
     *
     * 개발용 편의 기능이라 기본값은 꺼져 있습니다.
     * publicdata.sync-on-empty-startup=true 로 켭니다.
     */
    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void syncOnEmptyStartup() {
        if (!properties.isSyncOnEmptyStartup()) {
            return;
        }

        if (publicDataWriter.countCctvs() > 0
                && publicDataWriter.countSecurityLights() > 0) {

            log.info("공공데이터가 이미 들어 있어 시작 시 수집을 건너뜁니다.");
            return;
        }

        log.info("공공데이터가 비어 있어 시작 시 1회 수집합니다.");

        if (!running.compareAndSet(false, true)) {
            return;
        }

        try {
            runCctvSync();
            runSecurityLightSync();
        } finally {
            running.set(false);
        }
    }

    // ------------------------------------------------------------------
    // 수동 실행
    // ------------------------------------------------------------------

    public boolean isRunning() {
        return running.get();
    }

    /**
     * 관리자가 손으로 돌리는 수집.
     *
     * 보안등 전체 수집은 API 를 1900번 가까이 부르느라 20분이 넘게 걸립니다.
     * 요청 스레드를 그만큼 붙잡으면 프록시든 브라우저든 먼저 끊어 버리므로,
     * 시작만 시키고 바로 돌아옵니다. 진행 상황은 GET /admin/public-data 로 봅니다.
     *
     * @return 시작했으면 true, 이미 다른 작업이 돌고 있으면 false
     */
    public boolean startAsync(PublicDataSource source) {
        if (!running.compareAndSet(false, true)) {
            return false;
        }

        worker.submit(() -> {
            try {
                switch (source) {
                    case CCTV -> runCctvSync();
                    case SECURITY_LIGHT -> runSecurityLightSync();
                    case GEOCODING -> runGeocoding();
                }
            } catch (Exception e) {
                log.error("공공데이터 수집 중 예상하지 못한 오류입니다. source={}", source, e);
            } finally {
                running.set(false);
            }
        });

        return true;
    }

    // ------------------------------------------------------------------
    // 실제 작업
    // ------------------------------------------------------------------

    private PublicDataSyncHistory runCctvSync() {
        PublicDataSyncHistory history =
                historyRepository.save(
                        new PublicDataSyncHistory(PublicDataSource.CCTV)
                );

        try {
            publicDataWriter.clearCctvs();

            int[] counters = new int[2]; // 0: 읽은 행, 1: 저장한 행

            cctvDataClient.fetchAll(page -> {
                List<RawFacility> valid = new ArrayList<>(page.size());

                for (RawFacility facility : page) {
                    counters[0]++;

                    if (!CoordinateSupport.isInKorea(
                            facility.latitude(),
                            facility.longitude()
                    )) {
                        continue;
                    }

                    valid.add(facility);
                }

                counters[1] += publicDataWriter.insertCctvs(valid);
            });

            /*
             * 같은 자리에 카메라가 여러 대 달린 행이 12만 건쯤 되고 그건 유일 인덱스가
             * 걸러 냅니다. 드라이버가 건별 결과를 안 주므로 실제 저장 건수는 표를 셉니다.
             */
            int saved = (int) publicDataWriter.countCctvs();

            history.setFetchedCount(counters[0]);
            history.setSavedCount(saved);
            history.setStatus(PublicDataSyncStatus.SUCCESS);
            history.setMessage(
                    "전국 CCTV "
                            + counters[0]
                            + "행을 읽어 서로 다른 좌표 "
                            + saved
                            + "개를 저장했습니다."
            );

            log.info(
                    "CCTV 수집 완료. 읽은 행 {}, 저장 {}",
                    counters[0],
                    saved
            );

        } catch (Exception e) {
            history.setStatus(PublicDataSyncStatus.FAILED);
            history.setMessage(
                    CoordinateSupport.truncate(
                            "CCTV 수집 실패: " + e.getMessage(),
                            1000
                    )
            );
            log.error("CCTV 수집에 실패했습니다.", e);
        }

        history.setFinishedAt(LocalDateTime.now());

        return historyRepository.save(history);
    }

    private PublicDataSyncHistory runSecurityLightSync() {
        PublicDataSyncHistory history =
                historyRepository.save(
                        new PublicDataSyncHistory(PublicDataSource.SECURITY_LIGHT)
                );

        try {
            publicDataWriter.clearSecurityLights();

            int[] counters = new int[3]; // 0: 읽은 행, 1: 저장, 2: 대기열

            securityLightApiClient.fetchAll(page -> {
                List<RawFacility> withCoordinate =
                        new ArrayList<>(page.size());

                List<RawFacility> withoutCoordinate =
                        new ArrayList<>();

                for (RawFacility facility : page) {
                    counters[0]++;

                    if (CoordinateSupport.isInKorea(
                            facility.latitude(),
                            facility.longitude()
                    )) {
                        withCoordinate.add(facility);
                        continue;
                    }

                    /*
                     * 좌표가 없고 주소만 있는 지점은 버리지 않고 대기열로 보냅니다.
                     * 부산 동구처럼 자치단체가 통째로 좌표를 안 채운 곳이 있어서,
                     * 여기서 버리면 그 동네만 지도가 비어 버립니다.
                     */
                    if (facility.hasAddress()) {
                        withoutCoordinate.add(facility);
                    }
                }

                counters[1] +=
                        publicDataWriter.insertSecurityLights(
                                withCoordinate,
                                false
                        );

                counters[2] +=
                        publicDataWriter.queueForGeocoding(withoutCoordinate);
            });

            int saved = (int) publicDataWriter.countSecurityLights();

            history.setFetchedCount(counters[0]);
            history.setSavedCount(saved);
            history.setQueuedCount(counters[2]);

            /*
             * 좌표를 채우는 작업은 카카오 한도에 걸려 한 번에 안 끝날 수 있습니다.
             * 여기서 한도만큼 처리하고, 남은 것은 주기 작업이 이어서 처리합니다.
             */
            int geocoded = geocodingService.drainQueue();
            history.setGeocodedCount(geocoded);

            long remaining =
                    properties.getGeocoding().isEnabled()
                            ? countRemainingAddresses()
                            : counters[2];

            history.setStatus(
                    remaining > 0
                            ? PublicDataSyncStatus.PARTIAL
                            : PublicDataSyncStatus.SUCCESS
            );

            history.setMessage(
                    "전국 보안등 "
                            + counters[0]
                            + "행을 읽어 서로 다른 좌표 "
                            + saved
                            + "개를 저장하고, 주소만 있는 "
                            + counters[2]
                            + "건 중 "
                            + geocoded
                            + "건을 좌표로 바꿨습니다. 남은 주소 "
                            + remaining
                            + "개"
            );

            log.info(
                    "보안등 수집 완료. 읽은 행 {}, 저장 {}, 대기열 {}, 지오코딩 {}",
                    counters[0],
                    saved,
                    counters[2],
                    geocoded
            );

        } catch (Exception e) {
            history.setStatus(PublicDataSyncStatus.FAILED);
            history.setMessage(
                    CoordinateSupport.truncate(
                            "보안등 수집 실패: " + e.getMessage(),
                            1000
                    )
            );
            log.error("보안등 수집에 실패했습니다.", e);
        }

        history.setFinishedAt(LocalDateTime.now());

        return historyRepository.save(history);
    }

    private PublicDataSyncHistory runGeocoding() {
        PublicDataSyncHistory history =
                historyRepository.save(
                        new PublicDataSyncHistory(PublicDataSource.GEOCODING)
                );

        try {
            int geocoded = geocodingService.drainQueue();
            long remaining = countRemainingAddresses();

            history.setGeocodedCount(geocoded);
            history.setStatus(
                    remaining > 0
                            ? PublicDataSyncStatus.PARTIAL
                            : PublicDataSyncStatus.SUCCESS
            );
            history.setMessage(
                    "보안등 " + geocoded + "개를 좌표로 바꿨습니다. 남은 주소 " + remaining + "개"
            );

        } catch (Exception e) {
            history.setStatus(PublicDataSyncStatus.FAILED);
            history.setMessage(
                    CoordinateSupport.truncate(
                            "지오코딩 실패: " + e.getMessage(),
                            1000
                    )
            );
            log.error("지오코딩에 실패했습니다.", e);
        }

        history.setFinishedAt(LocalDateTime.now());

        return historyRepository.save(history);
    }

    private long countRemainingAddresses() {
        try {
            return geocodingService.countPendingAddresses();
        } catch (Exception e) {
            return 0L;
        }
    }
}
