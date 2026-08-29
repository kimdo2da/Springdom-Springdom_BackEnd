package com.example.lightsafe.publicdata;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GeocodeCacheRepository
        extends JpaRepository<GeocodeCache, Long> {

    Optional<GeocodeCache> findByAddress(String address);

    long countByFoundTrue();
}
