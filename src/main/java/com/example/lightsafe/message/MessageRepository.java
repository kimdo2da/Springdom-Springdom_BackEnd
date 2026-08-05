package com.example.lightsafe.message;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    // 받은 쪽지함: 수신자가 나이고, 내가 삭제하지 않은 쪽지
    List<Message> findByReceiver_UserIdAndIsDeletedByReceiverFalseOrderByCreatedAtDesc(
            Long receiverId
    );

    // 보낸 쪽지함: 발신자가 나이고, 내가 삭제하지 않은 쪽지
    List<Message> findBySender_UserIdAndIsDeletedBySenderFalseOrderByCreatedAtDesc(
            Long senderId
    );

    // 안 읽은 쪽지 개수: 수신자가 나이고, 안 읽었고, 내가 삭제하지 않은 쪽지
    long countByReceiver_UserIdAndIsReadFalseAndIsDeletedByReceiverFalse(
            Long receiverId
    );
}