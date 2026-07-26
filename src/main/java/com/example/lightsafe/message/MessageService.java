package com.example.lightsafe.message;

import com.example.lightsafe.common.exception.BadRequestException;
import com.example.lightsafe.common.exception.ForbiddenException;
import com.example.lightsafe.common.exception.NotFoundException;
import com.example.lightsafe.friends.FriendService;
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
    private final FriendService friendService;

    // 쪽지 발송
    public void sendMessage(Long senderId, Long receiverId, String content) {
        if (content == null || content.trim().isEmpty()) {
            throw new BadRequestException("쪽지 내용을 입력해 주세요.");
        }

        User sender = userRepository.findById(senderId)
                .orElseThrow(() ->
                        new NotFoundException(
                                "존재하지 않는 발신자입니다."
                        )
                );

        User receiver = userRepository.findById(receiverId)
                .orElseThrow(() ->
                        new NotFoundException(
                                "존재하지 않는 수신자입니다."
                        )
                );

        /*
         * 친구 여부 검증은 Controller가 아니라
         * MessageService 내부에서 수행합니다.
         */
        friendService.validateFriendship(
                receiverId,
                senderId
        );

        Message message = Message.builder()
                .sender(sender)
                .receiver(receiver)
                .content(content.trim())
                .isRead(false)
                .isDeletedBySender(false)
                .isDeletedByReceiver(false)
                .build();

        messageRepository.save(message);
    }

    // 받은 쪽지함 조회
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getReceivedMessages(Long loginUserId) {
        List<Message> messages =
                messageRepository
                        .findByReceiver_UserIdAndIsDeletedByReceiverFalseOrderByCreatedAtDesc(
                                loginUserId
                        );

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
        List<Message> messages =
                messageRepository
                        .findBySender_UserIdAndIsDeletedBySenderFalseOrderByCreatedAtDesc(
                                loginUserId
                        );

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
        return messageRepository
                .countByReceiver_UserIdAndIsReadFalseAndIsDeletedByReceiverFalse(
                        loginUserId
                );
    }

    // 쪽지 단건 상세 조회 및 읽음 처리
    @Transactional
    public Map<String, Object> getMessageDetail(
            Long messageId,
            Long loginUserId
    ) {
        Message msg = messageRepository.findById(messageId)
                .orElseThrow(() ->
                        new NotFoundException(
                                "존재하지 않는 쪽지입니다. id=" + messageId
                        )
                );

        boolean isSender =
                msg.getSender()
                        .getUserId()
                        .equals(loginUserId);

        boolean isReceiver =
                msg.getReceiver()
                        .getUserId()
                        .equals(loginUserId);

        if (!isSender && !isReceiver) {
            throw new ForbiddenException(
                    "이 쪽지를 열람할 권한이 없습니다."
            );
        }

        /*
         * 내가 삭제한 쪽지는 상세 URL로 다시 열 수 없습니다.
         */
        if (isSender && msg.isDeletedBySender()) {
            throw new NotFoundException(
                    "존재하지 않는 쪽지입니다. id=" + messageId
            );
        }

        if (isReceiver && msg.isDeletedByReceiver()) {
            throw new NotFoundException(
                    "존재하지 않는 쪽지입니다. id=" + messageId
            );
        }

        if (isReceiver && !msg.isRead()) {
            msg.setRead(true);
        }

        Map<String, Object> data = new HashMap<>();

        data.put("messageId", msg.getMessageId());
        data.put("senderNickname", msg.getSender().getNickname());
        data.put("receiverNickname", msg.getReceiver().getNickname());
        data.put("content", msg.getContent());
        data.put("isRead", msg.isRead());
        data.put("createdAt", msg.getCreatedAt());

        return data;
    }

    // 쪽지 삭제
    @Transactional
    public void deleteMessage(
            Long messageId,
            Long loginUserId
    ) {
        Message msg = messageRepository.findById(messageId)
                .orElseThrow(() ->
                        new NotFoundException(
                                "존재하지 않는 쪽지입니다. id=" + messageId
                        )
                );

        boolean isSender =
                msg.getSender()
                        .getUserId()
                        .equals(loginUserId);

        boolean isReceiver =
                msg.getReceiver()
                        .getUserId()
                        .equals(loginUserId);

        if (isSender) {
            msg.setDeletedBySender(true);
        } else if (isReceiver) {
            msg.setDeletedByReceiver(true);
        } else {
            throw new ForbiddenException(
                    "이 쪽지를 삭제할 권한이 없습니다."
            );
        }

        if (msg.isDeletedBySender() && msg.isDeletedByReceiver()) {
            messageRepository.delete(msg);
        }
    }
}