package com.example.lightsafe.user;

import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // 임시 로그인 사용자 처리
    // 나중에 JWT/Security 구현되면 이 메서드만 실제 로그인 사용자 조회 방식으로 교체하면 됨
    public User getCurrentUser() {
        return userRepository.findById(1L).orElse(null);
    }
}