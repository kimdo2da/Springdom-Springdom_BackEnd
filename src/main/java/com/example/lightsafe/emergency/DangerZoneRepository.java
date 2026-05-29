package com.example.lightsafe.emergency;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DangerZoneRepository extends JpaRepository<DangerZone, Long> {

    List<DangerZone> findByIsActiveTrueOrderByCreatedAtDesc();

    List<DangerZone> findByIsActiveTrue();
}