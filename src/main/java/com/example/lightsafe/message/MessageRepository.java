package com.example.lightsafe.message;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {
    // 수신자(Receiver)의 ID가 나이면서 최신순으로 정렬된 쪽지 목록 조회
    List<Message> findByReceiver_UserIdOrderByCreatedAtDesc(Long receiverId);

    // 3. 발신자(Sender)의 ID가 나이면서 최신순으로 정렬된 쪽지 목록 조회
    List<Message> findBySender_UserIdOrderByCreatedAtDesc(Long senderId);

    // 5. 수신자(Receiver)가 나이면서 읽지 않은(isRead = false) 쪽지 개수 카운트
    long countByReceiver_UserIdAndIsReadFalse(Long receiverId);

    // 1. 받은 쪽지함: 수신자가 나이면서, '수신자가 삭제하지 않은(False)' 쪽지
    List<Message> findByReceiver_UserIdAndIsDeletedByReceiverFalseOrderByCreatedAtDesc(Long receiverId);

    // 2. 보낸 쪽지함: 발신자가 나이면서, '발신자가 삭제하지 않은(False)' 쪽지
    List<Message> findBySender_UserIdAndIsDeletedBySenderFalseOrderByCreatedAtDesc(Long senderId);

    // 3. 안 읽은 쪽지 카운트: 수신자가 나이고, 안 읽었고, 내가 삭제하지 않은 쪽지
    long countByReceiver_UserIdAndIsReadFalseAndIsDeletedByReceiverFalse(Long receiverId);
}