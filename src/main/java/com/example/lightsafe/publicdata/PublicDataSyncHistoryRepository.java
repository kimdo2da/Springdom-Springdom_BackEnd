package com.example.lightsafe.publicdata;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PublicDataSyncHistoryRepository
        extends JpaRepository<PublicDataSyncHistory, Long> {

    Optional<PublicDataSyncHistory>
    findFirstBySourceOrderByStartedAtDesc(PublicDataSource source);

    List<PublicDataSyncHistory> findTop20ByOrderByStartedAtDesc();

    boolean existsByStatus(PublicDataSyncStatus status);
}
