package com.example.lightsafe.friends;

import com.example.lightsafe.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FriendRepository extends JpaRepository<Friend, Long> {

    // 1. 특정 두 사람 간의 친구 관계가 이미 존재하는지 찾기 (중복 요청 방지용)
    Optional<Friend> findByUserAndFriendUser(User user, User friendUser);

    // 2. 내가 보낸 친구 요청 목록 찾기 (user가 나인 경우)
    List<Friend> findByUserAndStatus(User user, FriendStatus status);

    // 3. 내가 받은 친구 요청 목록 찾기 (friendUser가 나인 경우)
    List<Friend> findByFriendUserAndStatus(User friendUser, FriendStatus status);
}