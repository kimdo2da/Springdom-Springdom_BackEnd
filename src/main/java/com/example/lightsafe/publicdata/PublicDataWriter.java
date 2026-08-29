package com.example.lightsafe.publicdata;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 수집한 지점을 표에 밀어 넣습니다.
 *
 * 180만 건을 JPA 엔티티로 저장하면 영속성 컨텍스트가 감당하지 못하므로
 * JDBC 묶음 입력을 씁니다. 좌표 중복은 유일 인덱스에 맡기고
 * INSERT IGNORE 로 조용히 건너뜁니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PublicDataWriter {

    private static final int BATCH_SIZE = 1000;

    private static final String INSERT_LAMP = """
            INSERT IGNORE INTO street_lamps
                (latitude, longitude, address, lamp_count, sido, geocoded)
            VALUES (?, ?, ?, ?, ?, ?)
            """;

    private static final String INSERT_CCTV = """
            INSERT IGNORE INTO cctvs
                (cctv_name, latitude, longitude, address, purpose, camera_count, sido)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String INSERT_QUEUE = """
            INSERT INTO lamp_geocode_queue
                (address, lamp_count, sido)
            VALUES (?, ?, ?)
            """;

    private final JdbcTemplate jdbcTemplate;

    // ------------------------------------------------------------------
    // 비우기
    // ------------------------------------------------------------------

    @Transactional
    public void clearSecurityLights() {
        jdbcTemplate.update("DELETE FROM lamp_geocode_queue");
        jdbcTemplate.update("DELETE FROM street_lamps");

        log.info("보안등 표와 지오코딩 대기열을 비웠습니다.");
    }

    /**
     * CCTV 를 비웁니다.
     *
     * emergency_reports.nearest_cctv_id 가 이 표를 가리키고 있어서
     * 통째로 지우면 지난 신고 기록이 깨집니다. 참조된 행은 남깁니다.
     */
    @Transactional
    public void clearCctvs() {
        int deleted =
                jdbcTemplate.update("""
                        DELETE FROM cctvs
                         WHERE cctv_id NOT IN (
                                   SELECT nearest_cctv_id
                                     FROM emergency_reports
                                    WHERE nearest_cctv_id IS NOT NULL
                               )
                        """);

        Integer kept =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM cctvs",
                        Integer.class
                );

        log.info(
                "CCTV 표를 비웠습니다. 삭제 {}행, 신고에 연결돼 남긴 행 {}행",
                deleted,
                kept == null ? 0 : kept
        );
    }

    // ------------------------------------------------------------------
    // 넣기
    // ------------------------------------------------------------------

    @Transactional
    public int insertSecurityLights(
            List<RawFacility> facilities,
            boolean geocoded
    ) {
        if (facilities.isEmpty()) {
            return 0;
        }

        int inserted = 0;

        for (int start = 0; start < facilities.size(); start += BATCH_SIZE) {
            List<RawFacility> chunk =
                    facilities.subList(
                            start,
                            Math.min(start + BATCH_SIZE, facilities.size())
                    );

            int[] results =
                    jdbcTemplate.batchUpdate(
                            INSERT_LAMP,
                            chunk,
                            chunk.size(),
                            (ps, facility) -> {
                                ps.setDouble(1, facility.latitude());
                                ps.setDouble(2, facility.longitude());
                                ps.setString(
                                        3,
                                        CoordinateSupport.truncate(facility.address(), 255)
                                );
                                if (facility.count() == null) {
                                    ps.setNull(4, java.sql.Types.INTEGER);
                                } else {
                                    ps.setInt(4, facility.count());
                                }
                                ps.setString(
                                        5,
                                        CoordinateSupport.truncate(facility.sido(), 40)
                                );
                                ps.setBoolean(6, geocoded);
                            }
                    )[0];

            inserted += countAffected(results);
        }

        return inserted;
    }

    @Transactional
    public int insertCctvs(
            List<RawFacility> facilities
    ) {
        if (facilities.isEmpty()) {
            return 0;
        }

        int inserted = 0;

        for (int start = 0; start < facilities.size(); start += BATCH_SIZE) {
            List<RawFacility> chunk =
                    facilities.subList(
                            start,
                            Math.min(start + BATCH_SIZE, facilities.size())
                    );

            int[] results =
                    jdbcTemplate.batchUpdate(
                            INSERT_CCTV,
                            chunk,
                            chunk.size(),
                            (ps, facility) -> {
                                ps.setString(
                                        1,
                                        CoordinateSupport.truncate(facility.name(), 100)
                                );
                                ps.setDouble(2, facility.latitude());
                                ps.setDouble(3, facility.longitude());
                                ps.setString(
                                        4,
                                        CoordinateSupport.truncate(facility.address(), 255)
                                );
                                ps.setString(
                                        5,
                                        CoordinateSupport.truncate(facility.purpose(), 50)
                                );
                                if (facility.count() == null) {
                                    ps.setNull(6, java.sql.Types.INTEGER);
                                } else {
                                    ps.setInt(6, facility.count());
                                }
                                ps.setString(
                                        7,
                                        CoordinateSupport.truncate(facility.sido(), 40)
                                );
                            }
                    )[0];

            inserted += countAffected(results);
        }

        return inserted;
    }

    /**
     * 좌표가 없는 보안등을 지오코딩 대기열에 넣습니다.
     */
    @Transactional
    public int queueForGeocoding(
            List<RawFacility> facilities
    ) {
        if (facilities.isEmpty()) {
            return 0;
        }

        int queued = 0;

        for (int start = 0; start < facilities.size(); start += BATCH_SIZE) {
            List<RawFacility> chunk =
                    facilities.subList(
                            start,
                            Math.min(start + BATCH_SIZE, facilities.size())
                    );

            jdbcTemplate.batchUpdate(
                    INSERT_QUEUE,
                    chunk,
                    chunk.size(),
                    (ps, facility) -> {
                        ps.setString(
                                1,
                                CoordinateSupport.truncate(facility.address(), 255)
                        );
                        if (facility.count() == null) {
                            ps.setNull(2, java.sql.Types.INTEGER);
                        } else {
                            ps.setInt(2, facility.count());
                        }
                        ps.setString(
                                3,
                                CoordinateSupport.truncate(facility.sido(), 40)
                        );
                    }
            );

            queued += chunk.size();
        }

        return queued;
    }

    public long countSecurityLights() {
        Long count =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM street_lamps",
                        Long.class
                );

        return count == null ? 0L : count;
    }

    public long countCctvs() {
        Long count =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM cctvs",
                        Long.class
                );

        return count == null ? 0L : count;
    }

    /**
     * 이번 묶음에서 실제로 들어간 행 수.
     *
     * rewriteBatchedStatements=true 를 켜면 드라이버가 여러 줄을 한 문장으로 합쳐 보내고
     * 건별 결과 대신 SUCCESS_NO_INFO(-2)를 돌려줍니다. 그래서 이 값만으로는
     * INSERT IGNORE 가 중복으로 건너뛴 행을 구분할 수 없습니다.
     *
     * 진짜 저장 건수는 작업이 끝난 뒤 표를 세어 씁니다.
     * 이 값은 '보낸 행 수'로만 보세요.
     */
    private int countAffected(int[] results) {
        int affected = 0;

        for (int result : results) {
            if (result > 0 || result == java.sql.Statement.SUCCESS_NO_INFO) {
                affected++;
            }
        }

        return affected;
    }
}
