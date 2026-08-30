package com.example.lightsafe.publicdata;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 공공데이터 수집 설정.
 *
 * application.properties 의 publicdata.* 값을 담습니다.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "publicdata")
public class PublicDataProperties {

    private SecurityLight securityLight = new SecurityLight();

    private Cctv cctv = new Cctv();

    private Geocoding geocoding = new Geocoding();

    /**
     * 매월 자동 수집 크론식. 기본값은 매월 5일 새벽 3시.
     */
    private String syncCron = "0 0 3 5 * *";

    /**
     * 서버가 뜰 때 데이터가 비어 있으면 자동으로 1회 수집할지 여부.
     */
    private boolean syncOnEmptyStartup = false;

    @Getter
    @Setter
    public static class SecurityLight {

        /**
         * 전국보안등표준데이터 오픈 API.
         */
        private String endpoint =
                "https://api.data.go.kr/openapi/tn_pubr_public_scrty_lmp_api";

        /**
         * 공공데이터포털 일반 인증키(URL 인코딩된 값 그대로).
         */
        private String serviceKey = "";

        /**
         * 한 번에 받아올 행 수. 포털 상한이 1000 이라 그 이상은 빈 응답이 옵니다.
         */
        private int pageSize = 1000;

        /**
         * 페이지 조회 실패 시 재시도 횟수.
         */
        private int maxRetry = 3;

        /**
         * 호출 사이 최소 간격(ms). 포털 초당 호출 제한을 넘지 않기 위한 값.
         */
        private long requestIntervalMillis = 50;

        /**
         * 안전장치. 0 이하이면 제한 없음.
         */
        private int maxPages = 0;
    }

    @Getter
    @Setter
    public static class Cctv {

        /**
         * 전국CCTV표준데이터 배포 파일(CSV, CP949).
         *
         * 이 데이터셋은 오픈 API 가 아니라 파일로 배포됩니다.
         * publicdata.cctv.mode=api 로 바꾸면 표준 오픈 API 를 사용합니다.
         */
        private String fileUrl =
                "https://file.localdata.go.kr/file/download/cctv_info/info";

        /**
         * file | api
         */
        private String mode = "file";

        /**
         * publicdata.cctv.mode=api 일 때 사용할 오픈 API.
         * 공공데이터포털에서 별도 활용신청이 승인돼야 동작합니다.
         */
        private String endpoint =
                "https://api.data.go.kr/openapi/tn_pubr_public_cctv_api";

        private String serviceKey = "";

        private int pageSize = 1000;

        private int maxRetry = 3;

        private long requestIntervalMillis = 50;

        private int maxPages = 0;

        /**
         * 배포 파일 인코딩.
         */
        private String fileCharset = "MS949";

        /**
         * 파일 다운로드 타임아웃(ms).
         */
        private int downloadTimeoutMillis = 300000;
    }

    @Getter
    @Setter
    public static class Geocoding {

        /**
         * 주소만 있고 좌표가 없는 보안등을 좌표로 변환할지 여부.
         */
        private boolean enabled = true;

        /**
         * 국토교통부 브이월드 지오코더.
         *
         * 여기가 1순위입니다. 우리 보안등 주소는 대부분 지번주소라서,
         * 국가 주소 원장을 그대로 쓰는 브이월드와 잘 맞습니다.
         * 실측 300개 기준 87.7% 를 여기서 해결합니다.
         */
        private String vworldEndpoint = "https://api.vworld.kr/req/address";

        /**
         * 브이월드 인증키.
         *
         * 비워 두면 브이월드를 건너뛰고 카카오만 씁니다.
         */
        private String vworldKey = "";

        /**
         * 브이월드가 못 찾은 주소를 카카오에 한 번 더 물어볼지 여부.
         *
         * 브이월드는 국가 주소 원장에 있는 표기만 찾습니다. 지자체가 올린 주소에는
         * '묵1동'(행정동), '거창읍대동리602-6'(붙여 씀) 같은 표기가 섞여 있는데
         * 카카오는 이런 것도 찾아냅니다. 실측으로 브이월드 실패분의 54% 를 회수했고,
         * 둘을 합치면 94.3% 입니다.
         *
         * 여기까지 오는 주소는 전체의 12% 뿐이라 카카오 호출량은 예전의 1/8 입니다.
         */
        private boolean kakaoFallbackEnabled = true;

        /**
         * 한 번 실행할 때 물어볼 주소 수 상한.
         *
         * 브이월드 개발용 키는 하루 4만 건입니다. 주소 하나에 지번·도로명 두 번까지
         * 쓰므로 넉넉잡아 1.3건으로 보면 하루 3만 주소가 상한입니다.
         * 2000개 × 24시간 = 4.8만이라 상한을 넘으므로, 넘치는 분은 자동으로
         * 카카오(하루 10만 건)로 넘어갑니다. 둘을 합치면 하루치가 부족하지 않습니다.
         *
         * 큐에 남은 주소는 다음 실행에서 이어서 처리합니다. 좌표 없는 주소가
         * 12만~18만 개라 다 채우는 데 사흘쯤 걸리지만, 결과를 geocode_cache 에
         * 남기므로 한 번만 치르면 됩니다.
         */
        private int maxAddressesPerRun = 2000;

        /**
         * 호출 사이 최소 간격(ms).
         */
        private long requestIntervalMillis = 30;

        /**
         * 지오코딩 큐를 비우는 반복 작업 주기(ms).
         *
         * 끄고 싶으면 이 값을 0 으로 두지 말고 enabled=false 로 하세요.
         * @Scheduled 의 fixedDelay 는 양수여야 해서 0 을 넣으면 서버가 뜨지 않습니다.
         */
        private long drainIntervalMillis = 3600000;
    }
}
