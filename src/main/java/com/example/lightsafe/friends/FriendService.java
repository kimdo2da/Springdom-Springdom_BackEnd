package com.example.lightsafe.friends;

import com.example.lightsafe.user.User;
import com.example.lightsafe.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional // 로직 실행 중 에러가 발생하면 DB를 자동으로 롤백해주는 안전장치
public class FriendService {

    private final FriendRepository friendRepository;
    private final UserRepository userRepository;

    /**
     * 1. 전체 친구 목록 조회
     * ACCEPTED 상태인 모든 양방향 친구 관계를 조회하여 정형화된 데이터 리스트로 가공합니다.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getFriendList(Long loginUserId) {
        User me = userRepository.findById(loginUserId)
                .orElseThrow(() -> new IllegalArgumentException("유저를 찾을 수 없습니다."));

        List<Friend> myRequests = friendRepository.findByUserAndStatus(me, FriendStatus.ACCEPTED);
        List<Friend> receivedRequests = friendRepository.findByFriendUserAndStatus(me, FriendStatus.ACCEPTED);

        List<Map<String, Object>> data = new ArrayList<>();

        // 내가 요청해서 친구가 된 사람들 정보 가공
        for (Friend f : myRequests) {
            Map<String, Object> map = new HashMap<>();
            map.put("friendsId", f.getFriendsId());
            map.put("friendUserId", f.getFriendUser().getUserId());
            map.put("friendNickname", f.getFriendUser().getNickname());
            map.put("isEmergencyAllowed", f.isEmergencyAllowed());
            map.put("becameFriendAt", f.getCreatedAt());
            data.add(map);
        }

        // 내가 요청받아서 친구가 된 사람들 정보 가공
        for (Friend f : receivedRequests) {
            Map<String, Object> map = new HashMap<>();
            map.put("friendsId", f.getFriendsId());
            map.put("friendUserId", f.getUser().getUserId());
            map.put("friendNickname", f.getUser().getNickname());
            map.put("isEmergencyAllowed", f.isEmergencyAllowed());
            map.put("becameFriendAt", f.getCreatedAt());
            data.add(map);
        }

        return data;
    }

    /**
     * 2. 친구 요청 보내기
     * 자기 자신 검증 및 중복 요청(이미 친구이거나 대기 중)을 체크한 후 PENDING 상태로 저장합니다.
     */
    public void sendFriendRequest(Long loginUserId, Long targetUserId) {
        if (loginUserId.equals(targetUserId)) {
            throw new IllegalArgumentException("자기 자신에게 친구 요청을 보낼 수 없습니다.");
        }

        User sender = userRepository.findById(loginUserId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 발신 유저입니다."));
        User receiver = userRepository.findById(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 수신 유저입니다."));

        Optional<Friend> existing1 = friendRepository.findByUserAndFriendUser(sender, receiver);
        Optional<Friend> existing2 = friendRepository.findByUserAndFriendUser(receiver, sender);

        if (existing1.isPresent() || existing2.isPresent()) {
            throw new IllegalStateException("이미 친구이거나 처리 중인 요청이 존재합니다.");
        }

        Friend friendRequest = Friend.builder()
                .user(sender)
                .friendUser(receiver)
                .status(FriendStatus.PENDING)
                .isEmergencyAllowed(false)
                .build();

        friendRepository.save(friendRequest);
    }

    /**
     * 3. 받은 요청 목록 조회
     * 수신자가 '나'이고 상태가 PENDING인 대기 중인 요청 목록을 조회합니다.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getReceivedRequests(Long loginUserId) {
        User me = userRepository.findById(loginUserId)
                .orElseThrow(() -> new IllegalArgumentException("유저를 찾을 수 없습니다."));

        List<Friend> receivedList = friendRepository.findByFriendUserAndStatus(me, FriendStatus.PENDING);
        List<Map<String, Object>> data = new ArrayList<>();

        for (Friend f : receivedList) {
            Map<String, Object> map = new HashMap<>();
            map.put("requestId", f.getFriendsId());
            map.put("senderId", f.getUser().getUserId());
            map.put("senderNickname", f.getUser().getNickname());
            map.put("createdAt", f.getCreatedAt());
            data.add(map);
        }

        return data;
    }

    /**
     * 4. 보낸 요청 목록 조회
     * 발신자가 '나'이고 상태가 PENDING인 대기 중인 요청 목록을 조회합니다.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getSentRequests(Long loginUserId) {
        User me = userRepository.findById(loginUserId)
                .orElseThrow(() -> new IllegalArgumentException("유저를 찾을 수 없습니다."));

        List<Friend> sentList = friendRepository.findByUserAndStatus(me, FriendStatus.PENDING);
        List<Map<String, Object>> data = new ArrayList<>();

        for (Friend f : sentList) {
            Map<String, Object> map = new HashMap<>();
            map.put("requestId", f.getFriendsId());
            map.put("receiverId", f.getFriendUser().getUserId());
            map.put("receiverNickname", f.getFriendUser().getNickname());
            map.put("createdAt", f.getCreatedAt());
            data.add(map);
        }

        return data;
    }

    /**
     * 5. 친구 요청 수락
     * 요청을 받은 당사자인지 검증한 후 상태를 ACCEPTED로 변경합니다.
     */
    public void acceptFriendRequest(Long requestId, Long loginUserId) {
        Friend friendRequest = friendRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 친구 요청입니다."));

        if (!friendRequest.getFriendUser().getUserId().equals(loginUserId)) {
            throw new SecurityException("이 친구 요청을 수락할 권한이 없습니다.");
        }

        if (friendRequest.getStatus() != FriendStatus.PENDING) {
            throw new IllegalArgumentException("대기 중인 요청만 수락할 수 있습니다.");
        }

        friendRequest.setStatus(FriendStatus.ACCEPTED);
        friendRepository.save(friendRequest);
    }

    /**
     * 6. 친구 요청 거절
     * 요청을 받은 당사자인지 검증한 후 상태를 REJECTED로 변경합니다.
     */
    public void rejectFriendRequest(Long requestId, Long loginUserId) {
        Friend friendRequest = friendRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 친구 요청입니다."));

        if (!friendRequest.getFriendUser().getUserId().equals(loginUserId)) {
            throw new SecurityException("이 친구 요청을 거절할 권한이 없습니다.");
        }

        if (friendRequest.getStatus() != FriendStatus.PENDING) {
            throw new IllegalArgumentException("대기 중인 요청만 거절할 수 있습니다.");
        }

        friendRequest.setStatus(FriendStatus.REJECTED);
        friendRepository.save(friendRequest);
    }

    /**
     * 7. 친구 요청 취소
     * 요청을 보낸 본인인지 확인한 후 DB에서 해당 관계 내역을 완전히 삭제합니다.
     */
    public void cancelFriendRequest(Long requestId, Long loginUserId) {
        Friend friendRequest = friendRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 친구 요청입니다."));

        if (!friendRequest.getUser().getUserId().equals(loginUserId)) {
            throw new SecurityException("본인이 보낸 요청만 취소할 수 있습니다.");
        }

        if (friendRequest.getStatus() != FriendStatus.PENDING) {
            throw new IllegalArgumentException("대기 중인 요청만 취소할 수 있습니다.");
        }

        friendRepository.delete(friendRequest);
    }

    /**
     * 8. 친구 삭제 (절교)
     * 양방향 조회를 통해 성립되어 있는 진짜 친구 관계(ACCEPTED)를 찾아 삭제합니다.
     */
    public void deleteFriend(Long targetUserId, Long loginUserId) {
        User me = userRepository.findById(loginUserId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 발신 유저입니다."));
        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 타겟 유저입니다."));

        Optional<Friend> existing1 = friendRepository.findByUserAndFriendUser(me, target);
        Optional<Friend> existing2 = friendRepository.findByUserAndFriendUser(target, me);

        Friend friendRelation = existing1.orElse(existing2.orElse(null));

        if (friendRelation == null || friendRelation.getStatus() != FriendStatus.ACCEPTED) {
            throw new IllegalArgumentException("친구 상태가 아니므로 삭제할 수 없습니다.");
        }

        friendRepository.delete(friendRelation);
    }

    /**
     * 9. 친구 쪽지 검증
     * 두 유저가 현재 실제 친구 상태(ACCEPTED)인지 사전에 검증합니다.
     */
    @Transactional(readOnly = true)
    public void validateFriendship(Long targetUserId, Long loginUserId) {
        User me = userRepository.findById(loginUserId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 발신 유저입니다."));
        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 타겟 유저입니다."));

        Optional<Friend> existing1 = friendRepository.findByUserAndFriendUser(me, target);
        Optional<Friend> existing2 = friendRepository.findByUserAndFriendUser(target, me);
        Friend friendRelation = existing1.orElse(existing2.orElse(null));

        if (friendRelation == null || friendRelation.getStatus() != FriendStatus.ACCEPTED) {
            throw new SecurityException("친구에게만 쪽지를 보낼 수 있습니다.");
        }
    }

    /**
     * 10. 긴급 위치 공유 허용 여부 토글 (Toggle)
     * 친구 관계자 중 한 명인지 검증 후, 현재 설정을 반대로 반전시키고 바뀐 설정을 반환합니다.
     * @return 변경 후의 위치 공유 상태 메시지 ("위치 공유 허용" 또는 "위치 공유 차단")
     */
    public String toggleEmergencyAllow(Long friendsId, Long loginUserId) {
        Friend friendRelation = friendRepository.findById(friendsId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 친구 관계입니다."));

        boolean isMyRelation = friendRelation.getUser().getUserId().equals(loginUserId) ||
                friendRelation.getFriendUser().getUserId().equals(loginUserId);

        if (!isMyRelation) {
            throw new SecurityException("본인의 친구 관계 설정만 변경할 수 있습니다.");
        }

        if (friendRelation.getStatus() != FriendStatus.ACCEPTED) {
            throw new IllegalArgumentException("수락된 친구 상태에서만 설정을 변경할 수 있습니다.");
        }

        // 토글: true -> false, false -> true 반전
        friendRelation.setEmergencyAllowed(!friendRelation.isEmergencyAllowed());
        friendRepository.save(friendRelation);

        return friendRelation.isEmergencyAllowed() ? "위치 공유 허용" : "위치 공유 차단";
    }
}