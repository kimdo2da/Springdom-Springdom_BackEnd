package com.example.lightsafe.publicdata;

/**
 * 좌표 검증과 주소 손질에 쓰는 공통 도구.
 */
public final class CoordinateSupport {

    /**
     * 대한민국을 넉넉히 감싸는 사각형. 마라도 아래에서 독도 밖까지 포함합니다.
     *
     * 원본 데이터에 위·경도가 뒤바뀌었거나 자릿수가 틀린 행이 섞여 있어서,
     * 이 범위를 벗어나면 버립니다.
     */
    public static final double MIN_LATITUDE = 32.0;

    public static final double MAX_LATITUDE = 39.5;

    public static final double MIN_LONGITUDE = 124.0;

    public static final double MAX_LONGITUDE = 132.5;

    private CoordinateSupport() {
    }

    public static boolean isInKorea(
            Double latitude,
            Double longitude
    ) {
        if (latitude == null || longitude == null) {
            return false;
        }

        if (latitude.isNaN() || longitude.isNaN()) {
            return false;
        }

        if (latitude == 0.0 || longitude == 0.0) {
            return false;
        }

        return latitude >= MIN_LATITUDE
                && latitude <= MAX_LATITUDE
                && longitude >= MIN_LONGITUDE
                && longitude <= MAX_LONGITUDE;
    }

    public static Double parseCoordinate(
            String value
    ) {
        if (value == null) {
            return null;
        }

        String trimmed =
                value.replace("\"", "").trim();

        if (trimmed.isEmpty()) {
            return null;
        }

        try {
            return Double.parseDouble(trimmed);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static Integer parseCount(
            String value
    ) {
        if (value == null) {
            return null;
        }

        String trimmed =
                value.replace("\"", "").trim();

        if (trimmed.isEmpty()) {
            return null;
        }

        try {
            int parsed = Integer.parseInt(trimmed);
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static String clean(
            String value
    ) {
        if (value == null) {
            return null;
        }

        String trimmed =
                value.replace("\"", "").trim();

        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 주소 맨 앞 낱말을 시·도 이름으로 씁니다.
     */
    public static String extractSido(
            String address
    ) {
        String cleaned = clean(address);

        if (cleaned == null) {
            return null;
        }

        int space = cleaned.indexOf(' ');
        String sido =
                space > 0 ? cleaned.substring(0, space) : cleaned;

        return sido.length() > 40 ? sido.substring(0, 40) : sido;
    }

    /**
     * DB 컬럼 길이를 넘지 않게 자릅니다.
     */
    public static String truncate(
            String value,
            int maxLength
    ) {
        if (value == null) {
            return null;
        }

        return value.length() <= maxLength
                ? value
                : value.substring(0, maxLength);
    }
}
