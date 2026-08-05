package com.example.lightsafe.user;

import com.example.lightsafe.common.exception.ForbiddenException;
import com.example.lightsafe.common.exception.NotFoundException;
import com.example.lightsafe.friends.FriendRepository;
import com.example.lightsafe.notification.NotificationRepository;
import com.example.lightsafe.post.*;
import com.example.lightsafe.safe.BookmarkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserWithdrawalService {

    private static final String DELETED_POST_TITLE =
            "삭제된 게시글입니다.";

    private static final String DELETED_POST_CONTENT =
            "탈퇴한 사용자가 작성한 게시글입니다.";

    private static final String DELETED_COMMENT_CONTENT =
            "삭제된 댓글입니다.";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final PostLikeRepository postLikeRepository;
    private final PostAttachmentRepository
            postAttachmentRepository;
    private final FileStorageService fileStorageService;

    private final FriendRepository friendRepository;
    private final BookmarkRepository bookmarkRepository;
    private final NotificationRepository notificationRepository;

    private final UserWithdrawalCleanupRepository
            cleanupRepository;

    @Transactional
    public void withdraw(
            Long targetUserId,
            Long loginUserId
    ) {
        User loginUser =
                userRepository
                        .findByUserIdAndDeletedFalse(
                                loginUserId
                        )
                        .orElseThrow(() ->
                                new NotFoundException(
                                        "로그인 사용자 정보를 찾을 수 없습니다."
                                )
                        );

        User targetUser =
                userRepository
                        .findById(
                                targetUserId
                        )
                        .orElseThrow(() ->
                                new NotFoundException(
                                        "존재하지 않는 유저입니다."
                                )
                        );

        if (targetUser.isDeleted()) {
            throw new NotFoundException(
                    "이미 탈퇴했거나 존재하지 않는 유저입니다."
            );
        }

        boolean selfWithdrawal =
                loginUserId.equals(
                        targetUserId
                );

        boolean admin =
                "ADMIN".equals(
                        loginUser.getRole()
                );

        if (!selfWithdrawal
                && !admin) {

            throw new ForbiddenException(
                    "본인 또는 관리자만 탈퇴 처리할 수 있습니다."
            );
        }

        /*
         * 1. 첨부파일을 임시 휴지통으로 이동
         */
        List<PostAttachment> attachments =
                postAttachmentRepository
                        .findByPost_User_UserId(
                                targetUserId
                        );

        List<FileStorageService.StagedFile>
                stagedFiles =
                stageAttachmentFiles(
                        attachments
                );

        registerFileTransactionSynchronization(
                stagedFiles
        );

        /*
         * 2. 첨부파일 DB 행 삭제
         */
        postAttachmentRepository
                .deleteAllByPostAuthorId(
                        targetUserId
                );

        /*
         * 3. 탈퇴 사용자가 눌렀던 좋아요 삭제
         *    영향을 받은 게시글의 likeCount를 실제 행 수로 재계산
         */
        List<Long> affectedPostIds =
                new ArrayList<>(
                        new LinkedHashSet<>(
                                postLikeRepository
                                        .findLikedPostIdsByUserId(
                                                targetUserId
                                        )
                        )
                );

        postLikeRepository
                .deleteAllByUserId(
                        targetUserId
                );

        for (Long postId : affectedPostIds) {
            long actualLikeCount =
                    postLikeRepository
                            .countByPost_PostId(
                                    postId
                            );

            int safeLikeCount =
                    actualLikeCount
                            > Integer.MAX_VALUE
                            ? Integer.MAX_VALUE
                            : (int) actualLikeCount;

            postRepository.updateLikeCount(
                    postId,
                    safeLikeCount
            );
        }

        /*
         * 4. 댓글·게시글 행은 유지하고 내용만 익명화
         */
        commentRepository
                .anonymizeCommentsByUserId(
                        targetUserId,
                        DELETED_COMMENT_CONTENT
                );

        postRepository
                .anonymizePostsByUserId(
                        targetUserId,
                        DELETED_POST_TITLE,
                        DELETED_POST_CONTENT
                );

        /*
         * 5. 개인 데이터 완전 삭제
         */
        friendRepository
                .deleteAllRelationshipsByUserId(
                        targetUserId
                );

        bookmarkRepository
                .deleteAllByUserId(
                        targetUserId
                );

        notificationRepository
                .deleteAllReceivedByUserId(
                        targetUserId
                );

        cleanupRepository
                .deleteRouteHistory(
                        targetUserId
                );

        cleanupRepository
                .deleteFavoritePlaces(
                        targetUserId
                );

        /*
         * 6. User 행은 유지하면서 개인정보 익명화
         *
         * 긴급신고와 쪽지의 FK는 이 사용자 행을 계속 참조합니다.
         */
        anonymizeUser(
                targetUser
        );

        userRepository.save(
                targetUser
        );
    }

    private List<FileStorageService.StagedFile>
    stageAttachmentFiles(
            List<PostAttachment> attachments
    ) {
        List<FileStorageService.StagedFile>
                stagedFiles =
                new ArrayList<>();

        try {
            for (PostAttachment attachment
                    : attachments) {

                stagedFiles.add(
                        fileStorageService
                                .stageForDeletion(
                                        attachment
                                                .getStoredFilename()
                                )
                );
            }

            return stagedFiles;

        } catch (RuntimeException exception) {
            restoreStagedFiles(
                    stagedFiles
            );

            throw exception;
        }
    }

    private void registerFileTransactionSynchronization(
            List<FileStorageService.StagedFile>
                    stagedFiles
    ) {
        if (stagedFiles.isEmpty()) {
            return;
        }

        if (!TransactionSynchronizationManager
                .isSynchronizationActive()) {

            restoreStagedFiles(
                    stagedFiles
            );

            throw new IllegalStateException(
                    "회원탈퇴 파일 처리를 위한 트랜잭션이 활성화되지 않았습니다."
            );
        }

        TransactionSynchronizationManager
                .registerSynchronization(
                        new TransactionSynchronization() {

                            @Override
                            public void afterCommit() {
                                for (FileStorageService.StagedFile stagedFile
                                        : stagedFiles) {

                                    try {
                                        fileStorageService
                                                .permanentlyDeleteStagedFile(
                                                        stagedFile
                                                );

                                    } catch (RuntimeException exception) {
                                        /*
                                         * DB에서는 이미 삭제되었지만
                                         * 파일은 공개 업로드 경로가 아니라
                                         * 숨김 휴지통으로 이동된 상태입니다.
                                         */
                                        log.error(
                                                "탈퇴 사용자 첨부파일 최종 삭제 실패: {}",
                                                stagedFile.stagedPath(),
                                                exception
                                        );
                                    }
                                }
                            }

                            @Override
                            public void afterCompletion(
                                    int status
                            ) {
                                if (status
                                        == TransactionSynchronization
                                        .STATUS_COMMITTED) {

                                    return;
                                }

                                restoreStagedFiles(
                                        stagedFiles
                                );
                            }
                        }
                );
    }

    private void restoreStagedFiles(
            List<FileStorageService.StagedFile>
                    stagedFiles
    ) {
        for (int index =
             stagedFiles.size() - 1;
             index >= 0;
             index--) {

            FileStorageService.StagedFile
                    stagedFile =
                    stagedFiles.get(index);

            try {
                fileStorageService
                        .restoreStagedFile(
                                stagedFile
                        );

            } catch (RuntimeException exception) {
                log.error(
                        "탈퇴 롤백 첨부파일 복구 실패: {}",
                        stagedFile.stagedPath(),
                        exception
                );
            }
        }
    }

    private void anonymizeUser(
            User user
    ) {
        String randomValue =
                UUID.randomUUID()
                        .toString()
                        .replace("-", "")
                        .substring(0, 12);

        String uniqueSuffix =
                user.getUserId()
                        + "_"
                        + randomValue;

        user.setUsername(
                "deleted_"
                        + uniqueSuffix
        );

        user.setEmail(
                "deleted_"
                        + uniqueSuffix
                        + "@deleted.local"
        );

        user.setNickname(
                "탈퇴한 사용자"
        );

        user.setPhone(
                null
        );

        user.setPassword(
                passwordEncoder.encode(
                        UUID.randomUUID()
                                .toString()
                )
        );

        /*
         * 과거 허위신고 횟수와 블랙리스트 기록은 유지합니다.
         */
        user.setRole(
                "USER"
        );

        user.setDeleted(
                true
        );

        user.setDeletedAt(
                LocalDateTime.now()
        );
    }
}