package com.example.lightsafe.safe;

import com.example.lightsafe.common.exception.BadRequestException;

import java.util.List;

/**
 * 지도에서 지금 보이는 사각 범위.
 *
 * CCTV 25만 개, 보안등 180만 개를 통째로 내려주면 응답이 수십 MB 가 되고
 * 화면에는 어차피 보이는 것만 그립니다. 그래서 조회는 항상 이 범위로 자릅니다.
 */
public record MapBounds(
        double minLatitude,
        double maxLatitude,
        double minLongitude,
        double maxLongitude
) {

    /**
     * 한 번에 요청할 수 있는 최대 범위(도 단위).
     *
     * 위도 1도는 약 111km 입니다. 0.5도면 사방 55km 정도라
     * 도시 하나가 통째로 들어옵니다. 이보다 넓으면 점을 그릴 축척이 아닙니다.
     */
    private static final double MAX_SPAN_DEGREE = 0.5;

    public static MapBounds of(
            Double minLatitude,
            Double maxLatitude,
            Double minLongitude,
            Double maxLongitude
    ) {
        if (minLatitude == null
                || maxLatitude == null
                || minLongitude == null
                || maxLongitude == null) {

            throw new BadRequestException(
                    "지도 범위(minLatitude, maxLatitude, minLongitude, maxLongitude)를 모두 보내 주세요."
            );
        }

        if (minLatitude > maxLatitude || minLongitude > maxLongitude) {
            throw new BadRequestException(
                    "지도 범위의 최솟값이 최댓값보다 큽니다."
            );
        }

        if (maxLatitude - minLatitude > MAX_SPAN_DEGREE
                || maxLongitude - minLongitude > MAX_SPAN_DEGREE) {

            throw new BadRequestException(
                    "한 번에 조회할 수 있는 지도 범위를 넘었습니다. 지도를 확대한 뒤 다시 시도해 주세요."
            );
        }

        return new MapBounds(
                minLatitude,
                maxLatitude,
                minLongitude,
                maxLongitude
        );
    }

    /**
     * 한 지점을 중심으로 반경 만큼 넓힌 사각 범위.
     */
    public static MapBounds aroundPoint(
            double latitude,
            double longitude,
            double radiusMeters
    ) {
        double latitudeMargin = radiusMeters / 111_000.0;

        double longitudeMargin =
                radiusMeters
                        / (111_000.0 * Math.cos(Math.toRadians(latitude)));

        return new MapBounds(
                latitude - latitudeMargin,
                latitude + latitudeMargin,
                longitude - longitudeMargin,
                longitude + longitudeMargin
        );
    }

    /**
     * 경로 주변을 살펴보기 위한 범위.
     *
     * 경로 전체를 감싸는 사각형에 여유분(미터)을 더해 만듭니다.
     */
    public static MapBounds around(
            List<LocationDto> path,
            double marginMeters
    ) {
        double minLatitude = Double.MAX_VALUE;
        double maxLatitude = -Double.MAX_VALUE;
        double minLongitude = Double.MAX_VALUE;
        double maxLongitude = -Double.MAX_VALUE;

        for (LocationDto point : path) {
            minLatitude = Math.min(minLatitude, point.getLatitude());
            maxLatitude = Math.max(maxLatitude, point.getLatitude());
            minLongitude = Math.min(minLongitude, point.getLongitude());
            maxLongitude = Math.max(maxLongitude, point.getLongitude());
        }

        double latitudeMargin = marginMeters / 111_000.0;

        double longitudeMargin =
                marginMeters
                        / (111_000.0 * Math.cos(Math.toRadians(minLatitude)));

        return new MapBounds(
                minLatitude - latitudeMargin,
                maxLatitude + latitudeMargin,
                minLongitude - longitudeMargin,
                maxLongitude + longitudeMargin
        );
    }
}
