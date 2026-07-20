package com.example.lightsafe.admin;

import com.example.lightsafe.post.PostRepository;
import com.example.lightsafe.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminDashboardService {

    private static final ZoneId KOREA_ZONE =
            ZoneId.of("Asia/Seoul");

    private final PostRepository postRepository;
    private final UserRepository userRepository;

    public AdminDashboardSummaryResponse getSummary() {
        LocalDate today =
                LocalDate.now(KOREA_ZONE);

        LocalDateTime startOfToday =
                today.atStartOfDay();

        LocalDateTime startOfTomorrow =
                today
                        .plusDays(1)
                        .atStartOfDay();

        long totalPosts =
                postRepository.count();

        long todayPosts =
                postRepository
                        .countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                                startOfToday,
                                startOfTomorrow
                        );

        long totalUsers =
                userRepository.count();

        return new AdminDashboardSummaryResponse(
                totalPosts,
                todayPosts,
                totalUsers
        );
    }
}