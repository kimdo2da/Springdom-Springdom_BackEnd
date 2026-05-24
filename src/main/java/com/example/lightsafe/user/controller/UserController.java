package com.example.lightsafe.user.controller;

import com.example.lightsafe.user.domain.User;
import com.example.lightsafe.user.dto.ApiResponse;
import com.example.lightsafe.user.dto.UserLoginRequest;
import com.example.lightsafe.user.dto.UserRegisterRequest;
import com.example.lightsafe.user.dto.UserUpdateRequest;
import com.example.lightsafe.user.repository.UserRepository;
import com.example.lightsafe.user.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @PostMapping("/register")
    public ApiResponse<Map<String, Long>> register(@RequestBody UserRegisterRequest request) {
        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            return new ApiResponse<>(false, "CONFLICT", "이미 존재하는 아이디입니다.");
        }
        String encodedPassword = passwordEncoder.encode(request.getPassword());
        User newUser = new User(null, request.getUsername(), request.getEmail(), encodedPassword, request.getNickname());
        newUser.setBio("");
        newUser.setGithubUrl("");

        User savedUser = userRepository.save(newUser);
        Map<String, Long> data = new HashMap<>();
        data.put("userId", savedUser.getUserId());
        return new ApiResponse<>(true, data, "OK");
    }

    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@RequestBody UserLoginRequest request) {
        Optional<User> userOptional = userRepository.findByUsername(request.getUsernameOrEmail());

        if (userOptional.isEmpty() || !passwordEncoder.matches(request.getPassword(), userOptional.get().getPassword())) {
            return new ApiResponse<>(false, "UNAUTHORIZED", "아이디 또는 비밀번호가 일치하지 않습니다.");
        }

        User user = userOptional.get();
        Map<String, Object> data = new HashMap<>();
        String realToken = jwtUtil.generateToken(user.getUserId(), user.getUsername());

        data.put("accessToken", realToken);
        data.put("userId", user.getUserId());
        data.put("username", user.getUsername());
        return new ApiResponse<>(true, data, "로그인 성공");
    }

    @GetMapping("/{userId}")
    public ApiResponse<Map<String, Object>> getProfile(@PathVariable Long userId) {
        Optional<User> userOptional = userRepository.findById(userId);
        if (userOptional.isEmpty()) {
            return new ApiResponse<>(false, "NOT_FOUND", "존재하지 않는 유저입니다.");
        }
        User user = userOptional.get();
        Map<String, Object> data = new HashMap<>();
        data.put("userId", user.getUserId());
        data.put("username", user.getUsername());
        data.put("email", user.getEmail());
        data.put("nickname", user.getNickname());
        return new ApiResponse<>(true, data, "프로필 조회 성공");
    }

    @PutMapping("/{userId}")
    public ApiResponse<Map<String, Object>> updateProfile(@PathVariable Long userId, @RequestBody UserUpdateRequest request) {
        Optional<User> userOptional = userRepository.findById(userId);
        if (userOptional.isEmpty()) {
            return new ApiResponse<>(false, "NOT_FOUND", "존재하지 않는 유저입니다.");
        }
        User user = userOptional.get();
        if (request.getNickname() != null && !request.getNickname().isBlank()) {
            user.setNickname(request.getNickname());
        }
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }
        Map<String, Object> data = new HashMap<>();
        data.put("userId", user.getUserId());
        data.put("nickname", user.getNickname());
        return new ApiResponse<>(true, data, "프로필 수정 성공");
    }

    @DeleteMapping("/{userId}")
    public ApiResponse<String> deleteUser(@PathVariable Long userId) {
        Optional<User> userOptional = userRepository.findById(userId);
        if (userOptional.isEmpty()) {
            return new ApiResponse<>(false, "NOT_FOUND", "존재하지 않는 유저입니다.");
        }
        userRepository.deleteById(userId);
        return new ApiResponse<>(true, null, "회원 탈퇴 성공");
    }

    @PostMapping("/logout")
    public ApiResponse<String> logout() {
        return new ApiResponse<>(true, null, "로그아웃 성공");
    }

    @GetMapping("")
    public ApiResponse<List<Map<String, Object>>> getAllUsers() {
        List<User> users = userRepository.findAll();
        List<Map<String, Object>> data = new ArrayList<>();
        for (User user : users) {
            Map<String, Object> uMap = new HashMap<>();
            uMap.put("userId", user.getUserId());
            uMap.put("username", user.getUsername());
            uMap.put("email", user.getEmail());
            uMap.put("nickname", user.getNickname());
            uMap.put("bio", user.getBio());
            uMap.put("githubUrl", user.getGithubUrl());
            uMap.put("createdAt", user.getCreatedAt());
            data.add(uMap);
        }
        return new ApiResponse<>(true, data, "전체 사용자 조회 성공");
    }
}