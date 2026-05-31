package com.example.lightsafe.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    // 유저 이름
    Optional<User> findByUsername(String username);
    // 이메일로 유저 정보 검색
    Optional<User> findByEmail(String email);
}