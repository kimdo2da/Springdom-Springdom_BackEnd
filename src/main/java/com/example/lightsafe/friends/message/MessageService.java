package com.example.lightsafe.friends.message;

import com.example.lightsafe.user.User;
import com.example.lightsafe.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
public class MessageService {

    private final MessageRepository messageRepository;
    private final UserRepository userRepository;

    // 쪽지 발송
    public void sendMessage(Long senderId, Long receiverId, String content) {
        // 1. 발신자 및 수신자 유효성 검증
        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 발신자입니다."));
        User receiver = userRepository.findById(receiverId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 수신자입니다."));

        // 2. 쪽지 내용 공백 검증
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("쪽지 내용을 입력해 주세요.");
        }

        // 3. 쪽지 엔티티 생성 및 저장
        Message message = Message.builder()
                .sender(sender)
                .receiver(receiver)
                .content(content.trim())
                .isRead(false)
                .build();

        messageRepository.save(message);
    }

    // 받은 쪽지함 조회
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getReceivedMessages(Long loginUserId) {
        // 1. 수신자가 삭제하지 않은 쪽지 목록을 최신순으로 조회
        List<Message> messages = messageRepository.findByReceiver_UserIdAndIsDeletedByReceiverFalseOrderByCreatedAtDesc(loginUserId);

        // 2. 클라이언트 응답용 데이터 맵핑
        List<Map<String, Object>> data = new ArrayList<>();
        for (Message msg : messages) {
            Map<String, Object> map = new HashMap<>();
            map.put("messageId", msg.getMessageId());
            map.put("senderId", msg.getSender().getUserId());
            map.put("senderNickname", msg.getSender().getNickname());
            map.put("content", msg.getContent());
            map.put("isRead", msg.isRead());
            map.put("createdAt", msg.getCreatedAt());
            data.add(map);
        }
        return data;
    }

    // 보낸 쪽지함 조회
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getSentMessages(Long loginUserId) {
        // 1. 발신자가 삭제하지 않은 쪽지 목록을 최신순으로 조회
        List<Message> messages = messageRepository.findBySender_UserIdAndIsDeletedBySenderFalseOrderByCreatedAtDesc(loginUserId);

        // 2. 클라이언트 응답용 데이터 맵핑
        List<Map<String, Object>> data = new ArrayList<>();
        for (Message msg : messages) {
            Map<String, Object> map = new HashMap<>();
            map.put("messageId", msg.getMessageId());
            map.put("receiverId", msg.getReceiver().getUserId());
            map.put("receiverNickname", msg.getReceiver().getNickname());
            map.put("content", msg.getContent());
            map.put("isRead", msg.isRead());
            map.put("createdAt", msg.getCreatedAt());
            data.add(map);
        }
        return data;
    }

    // 안 읽은 쪽지 총 개수 조회
    @Transactional(readOnly = true)
    public long getUnreadMessageCount(Long loginUserId) {
        return messageRepository.countByReceiver_UserIdAndIsReadFalse(loginUserId);
    }

    // 쪽지 단건 상세 조회 및 읽음 처리
    @Transactional
    public Map<String, Object> getMessageDetail(Long messageId, Long loginUserId) {
        // 1. 쪽지 존재 여부 확인
        Message msg = messageRepository.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 쪽지입니다. id=" + messageId));

        // 2. 권한 검증: 발신자 또는 수신자가 아닌 경우 열람 차단
        boolean isSender = msg.getSender().getUserId().equals(loginUserId);
        boolean isReceiver = msg.getReceiver().getUserId().equals(loginUserId);
        if (!isSender && !isReceiver) {
            throw new SecurityException("이 쪽지를 열람할 권한이 없습니다.");
        }

        // 3. 읽음 처리: 수신자가 처음 열람하는 경우 읽음 상태로 변경
        if (isReceiver && !msg.isRead()) {
            msg.setRead(true);
        }

        // 4. 클라이언트 응답용 데이터 맵핑
        Map<String, Object> data = new HashMap<>();
        data.put("messageId", msg.getMessageId());
        data.put("senderNickname", msg.getSender().getNickname());
        data.put("receiverNickname", msg.getReceiver().getNickname());
        data.put("content", msg.getContent());
        data.put("isRead", msg.isRead());
        data.put("createdAt", msg.getCreatedAt());

        return data;
    }

    // 쪽지 삭제 (소프트 삭제 적용)
    @Transactional
    public void deleteMessage(Long messageId, Long loginUserId) {
        // 1. 쪽지 존재 여부 확인
        Message msg = messageRepository.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 쪽지입니다. id=" + messageId));

        boolean isSender = msg.getSender().getUserId().equals(loginUserId);
        boolean isReceiver = msg.getReceiver().getUserId().equals(loginUserId);

        // 2. 요청자 권한 확인 및 삭제 상태(플래그) 변경
        if (isSender) {
            msg.setDeletedBySender(true);
        } else if (isReceiver) {
            msg.setDeletedByReceiver(true);
        } else {
            throw new SecurityException("이 쪽지를 삭제할 권한이 없습니다.");
        }

        // 3. 양측 모두 삭제 처리한 경우 DB에서 영구 삭제
        if (msg.isDeletedBySender() && msg.isDeletedByReceiver()) {
            messageRepository.delete(msg);
        }
    }
}