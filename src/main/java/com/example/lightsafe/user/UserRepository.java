package com.example.lightsafe.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository
        extends JpaRepository<User, Long> {

    Optional<User> findByUsername(
            String username
    );

    Optional<User> findByEmail(
            String email
    );

    Optional<User> findByUserIdAndDeletedFalse(
            Long userId
    );

    boolean existsByUserIdAndDeletedFalse(
            Long userId
    );

    List<User> findAllByDeletedFalseOrderByUserIdAsc();

    Optional<User> findByUsernameOrEmail(
            String username,
            String email
    );

    long countByDeletedFalse();
}