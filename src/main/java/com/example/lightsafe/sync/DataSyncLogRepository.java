package com.example.lightsafe.sync;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DataSyncLogRepository
        extends JpaRepository<DataSyncLog, Long> {
}