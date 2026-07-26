package com.example.lightsafe.post;

import com.example.lightsafe.user.User;
import com.example.lightsafe.user.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.lightsafe.common.exception.ForbiddenException;
import com.example.lightsafe.common.exception.NotFoundException;

import java.util.Objects;

@Service
public class PostAttachmentService {

    private final PostAttachmentRepository postAttachmentRepository;
    private final FileStorageService fileStorageService;
    private final UserService userService;

    public PostAttachmentService(
            PostAttachmentRepository postAttachmentRepository,
            FileStorageService fileStorageService,
            UserService userService
    ) {
        this.postAttachmentRepository = postAttachmentRepository;
        this.fileStorageService = fileStorageService;
        this.userService = userService;
    }

    @Transactional
    public void deleteAttachment(Long attachmentId) {
        PostAttachment attachment = postAttachmentRepository.findById(attachmentId)
                .orElseThrow(() ->
                        new NotFoundException(
                                "첨부파일이 존재하지 않습니다. id="
                                        + attachmentId
                        )
                );

        User user = userService.getCurrentUser();

        if (user == null
                || !Objects.equals(
                attachment.getPost()
                        .getUser()
                        .getUserId(),
                user.getUserId()
        )) {

            throw new ForbiddenException(
                    "본인 게시글의 첨부파일만 삭제할 수 있습니다."
            );
        }

        fileStorageService.deleteStoredFile(
                attachment.getStoredFilename()
        );

        postAttachmentRepository.deleteById(
                attachmentId
        );
    }
}
