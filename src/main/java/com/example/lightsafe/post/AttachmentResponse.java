package com.example.lightsafe.post;

public record AttachmentResponse(
        Long attachmentId,
        String originalFilename,
        String contentType,
        Long size,
        String url
) {
    public static AttachmentResponse from(PostAttachment attachment) {
        return new AttachmentResponse(
                attachment.getAttachmentId(),
                attachment.getOriginalFilename(),
                attachment.getContentType(),
                attachment.getFileSize(),
                "/posts/attachments/" + attachment.getAttachmentId()
        );
    }
}
