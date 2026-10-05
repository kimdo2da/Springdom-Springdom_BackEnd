package com.example.lightsafe.sync;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PublicDataSyncScheduler {

    private final CctvSyncService cctvSyncService;
    private final SecurityLightSyncService securityLightSyncService;
    private final PoliceFacilitySyncService policeFacilitySyncService;


    /*
     * CCTV
     * 매월 5일 오전 03:00
     */
    @Scheduled(
            cron = "${scheduler.cctv.cron:0 0 3 5 * *}",
            zone = "Asia/Seoul"
    )
    @SchedulerLock(
            name = "monthlyCctvSync",
            lockAtMostFor = "PT12H",
            lockAtLeastFor = "PT1M"
    )
    public void syncCctvsMonthly() {

        log.info(
                "[SCHEDULER] CCTV 월간 자동 동기화 시작"
        );

        try {

            SyncResultResponse result =
                    cctvSyncService.syncCctvs();

            log.info(
                    "[SCHEDULER] CCTV 월간 자동 동기화 완료. fetchedCount={}, savedCount={}, status={}",
                    result.fetchedCount(),
                    result.savedCount(),
                    result.status()
            );

        } catch (Exception e) {

            log.error(
                    "[SCHEDULER] CCTV 월간 자동 동기화 실패. error={}",
                    e.getMessage(),
                    e
            );
        }
    }


    /*
     * 보안등
     * 매월 6일 오전 03:00
     */
    @Scheduled(
            cron = "${scheduler.security-light.cron:0 0 3 6 * *}",
            zone = "Asia/Seoul"
    )
    @SchedulerLock(
            name = "monthlySecurityLightSync",
            lockAtMostFor = "PT12H",
            lockAtLeastFor = "PT1M"
    )
    public void syncSecurityLightsMonthly() {

        log.info(
                "[SCHEDULER] 보안등 월간 자동 동기화 시작"
        );

        try {

            SyncResultResponse result =
                    securityLightSyncService
                            .syncSecurityLights();

            log.info(
                    "[SCHEDULER] 보안등 월간 자동 동기화 완료. fetchedCount={}, savedCount={}, status={}",
                    result.fetchedCount(),
                    result.savedCount(),
                    result.status()
            );

        } catch (Exception e) {

            log.error(
                    "[SCHEDULER] 보안등 월간 자동 동기화 실패. error={}",
                    e.getMessage(),
                    e
            );
        }
    }


    /*
     * 치안시설
     * 매월 7일 오전 03:00
     */
    @Scheduled(
            cron = "${scheduler.police-facility.cron:0 0 3 7 * *}",
            zone = "Asia/Seoul"
    )
    @SchedulerLock(
            name = "monthlyPoliceFacilitySync",
            lockAtMostFor = "PT12H",
            lockAtLeastFor = "PT1M"
    )
    public void syncPoliceFacilitiesMonthly() {

        log.info(
                "[SCHEDULER] 치안시설 월간 자동 동기화 시작"
        );

        try {

            SyncResultResponse result =
                    policeFacilitySyncService
                            .syncPoliceFacilities();

            log.info(
                    "[SCHEDULER] 치안시설 월간 자동 동기화 완료. fetchedCount={}, savedCount={}, status={}",
                    result.fetchedCount(),
                    result.savedCount(),
                    result.status()
            );

        } catch (Exception e) {

            /*
             * 생활안전정보 IF_0036이 현재처럼
             * 500 APPLICATION_ERROR를 반환하더라도
             * 이 작업만 실패하고 다른 scheduler에는 영향이 없다.
             */
            log.error(
                    "[SCHEDULER] 치안시설 월간 자동 동기화 실패. error={}",
                    e.getMessage(),
                    e
            );
        }
    }
}