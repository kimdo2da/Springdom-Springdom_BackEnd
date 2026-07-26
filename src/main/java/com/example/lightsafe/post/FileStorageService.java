package com.example.lightsafe.post;

import com.example.lightsafe.common.exception.BadRequestException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
public class FileStorageService {

    private static final long MAX_FILE_SIZE_BYTES =
            10L * 1024L * 1024L;

    private static final Set<String> ALLOWED_EXTENSIONS =
            Set.of(
                    "jpg",
                    "jpeg",
                    "png",
                    "gif",
                    "webp",
                    "pdf",
                    "txt"
            );

    private static final Set<String> ALLOWED_CONTENT_TYPES =
            Set.of(
                    "image/jpeg",
                    "image/png",
                    "image/gif",
                    "image/webp",
                    "application/pdf",
                    "text/plain"
            );

    private static final Set<String> BLOCKED_EXECUTABLE_EXTENSIONS =
            Set.of(
                    "exe",
                    "bat",
                    "cmd",
                    "com",
                    "scr",
                    "msi",
                    "ps1",
                    "vbs",
                    "sh",
                    "jar",
                    "war",
                    "js",
                    "html",
                    "htm",
                    "php",
                    "jsp",
                    "asp",
                    "aspx"
            );

    private final Path uploadDir;
    private final Path withdrawalTrashDir;

    public FileStorageService(
            @Value("${file.upload-dir:uploads}")
            String uploadDir
    ) {
        this.uploadDir =
                Paths.get(uploadDir)
                        .toAbsolutePath()
                        .normalize();

        this.withdrawalTrashDir =
                this.uploadDir
                        .resolve(".withdrawal-trash")
                        .normalize();

        try {
            Files.createDirectories(
                    this.uploadDir
            );
            Files.createDirectories(
                    this.withdrawalTrashDir
            );

        } catch (Exception e) {
            throw new IllegalStateException(
                    "파일 업로드 폴더를 생성할 수 없습니다.",
                    e
            );
        }
    }

    public StoredFile store(
            MultipartFile file
    ) {
        validateFile(
                file
        );

        String originalFilename =
                StringUtils.cleanPath(
                        Objects.requireNonNull(
                                file.getOriginalFilename()
                        )
                );

        String extension =
                getExtension(
                        originalFilename
                );

        String storedFilename =
                UUID.randomUUID()
                        + "."
                        + extension;

        Path targetPath =
                resolveInsideUploadDir(
                        storedFilename
                );

        try (InputStream inputStream =
                     file.getInputStream()) {

            Files.copy(
                    inputStream,
                    targetPath,
                    StandardCopyOption.REPLACE_EXISTING
            );

            return new StoredFile(
                    originalFilename,
                    storedFilename,
                    normalizeContentType(
                            file.getContentType()
                    ),
                    file.getSize()
            );

        } catch (Exception e) {
            throw new BadRequestException(
                    "첨부파일 저장에 실패했습니다."
            );
        }
    }

    public Path load(
            String storedFilename
    ) {
        if (storedFilename == null
                || storedFilename.isBlank()) {

            throw new BadRequestException(
                    "파일명이 올바르지 않습니다."
            );
        }

        return resolveInsideUploadDir(
                storedFilename
        );
    }

    public boolean deleteStoredFile(
            String storedFilename
    ) {
        try {
            Path path =
                    load(
                            storedFilename
                    );

            boolean deleted =
                    Files.deleteIfExists(
                            path
                    );

            if (!deleted) {
                log.warn(
                        "첨부파일 삭제 대상이 존재하지 않습니다. storedFilename={}",
                        storedFilename
                );
            }

            return deleted;

        } catch (Exception e) {
            log.warn(
                    "첨부파일 삭제에 실패했습니다. storedFilename={}",
                    storedFilename,
                    e
            );

            return false;
        }
    }

    /*
     * 회원탈퇴처럼 DB 트랜잭션 commit/rollback과 실제 파일 삭제를
     * 안전하게 맞춰야 하는 경우 사용합니다.
     */
    public StagedFile stageForDeletion(
            String storedFilename
    ) {
        Path originalPath =
                load(
                        storedFilename
                );

        String stagedFilename =
                UUID.randomUUID()
                        + "-"
                        + storedFilename;

        Path stagedPath =
                withdrawalTrashDir
                        .resolve(
                                stagedFilename
                        )
                        .normalize();

        if (!stagedPath.startsWith(
                withdrawalTrashDir
        )) {
            throw new BadRequestException(
                    "파일 경로가 올바르지 않습니다."
            );
        }

        try {
            if (!Files.exists(
                    originalPath
            )) {
                log.warn(
                        "삭제 준비할 첨부파일이 존재하지 않습니다. storedFilename={}",
                        storedFilename
                );

                return new StagedFile(
                        storedFilename,
                        stagedFilename,
                        originalPath,
                        stagedPath
                );
            }

            Files.move(
                    originalPath,
                    stagedPath,
                    StandardCopyOption.REPLACE_EXISTING
            );

            return new StagedFile(
                    storedFilename,
                    stagedFilename,
                    originalPath,
                    stagedPath
            );

        } catch (Exception e) {
            log.warn(
                    "첨부파일 삭제 준비에 실패했습니다. storedFilename={}",
                    storedFilename,
                    e
            );

            throw new BadRequestException(
                    "첨부파일 삭제 준비에 실패했습니다."
            );
        }
    }

    public void restoreStagedFile(
            StagedFile stagedFile
    ) {
        if (stagedFile == null) {
            return;
        }

        try {
            if (!Files.exists(
                    stagedFile.stagedPath()
            )) {
                return;
            }

            Files.move(
                    stagedFile.stagedPath(),
                    stagedFile.originalPath(),
                    StandardCopyOption.REPLACE_EXISTING
            );

        } catch (Exception e) {
            log.warn(
                    "첨부파일 복구에 실패했습니다. storedFilename={}",
                    stagedFile.originalFilename(),
                    e
            );
        }
    }

    public void permanentlyDeleteStagedFile(
            StagedFile stagedFile
    ) {
        if (stagedFile == null) {
            return;
        }

        try {
            boolean deleted =
                    Files.deleteIfExists(
                            stagedFile.stagedPath()
                    );

            if (!deleted) {
                log.warn(
                        "최종 삭제할 임시 첨부파일이 존재하지 않습니다. stagedFilename={}",
                        stagedFile.stagedFilename()
                );
            }

        } catch (Exception e) {
            log.warn(
                    "임시 첨부파일 최종 삭제에 실패했습니다. stagedFilename={}",
                    stagedFile.stagedFilename(),
                    e
            );
        }
    }

    private void validateFile(
            MultipartFile file
    ) {
        if (file == null
                || file.isEmpty()) {

            throw new BadRequestException(
                    "첨부파일이 비어 있습니다."
            );
        }

        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new BadRequestException(
                    "첨부파일은 최대 10MB까지 업로드할 수 있습니다."
            );
        }

        String originalFilename =
                StringUtils.cleanPath(
                        Objects.requireNonNullElse(
                                file.getOriginalFilename(),
                                ""
                        )
                );

        if (originalFilename.isBlank()) {
            throw new BadRequestException(
                    "파일명이 올바르지 않습니다."
            );
        }

        if (originalFilename.contains("..")
                || originalFilename.contains("/")
                || originalFilename.contains("\\")) {

            throw new BadRequestException(
                    "파일명이 올바르지 않습니다."
            );
        }

        if (originalFilename.length() > 255) {
            throw new BadRequestException(
                    "파일명은 255자 이하여야 합니다."
            );
        }

        String extension =
                getExtension(
                        originalFilename
                );

        if (BLOCKED_EXECUTABLE_EXTENSIONS.contains(
                extension
        )) {
            throw new BadRequestException(
                    "실행파일은 업로드할 수 없습니다."
            );
        }

        if (!ALLOWED_EXTENSIONS.contains(
                extension
        )) {
            throw new BadRequestException(
                    "허용되지 않은 파일 확장자입니다. 허용값: jpg, jpeg, png, gif, webp, pdf, txt"
            );
        }

        String contentType =
                normalizeContentType(
                        file.getContentType()
                );

        if (!ALLOWED_CONTENT_TYPES.contains(
                contentType
        )) {
            throw new BadRequestException(
                    "허용되지 않은 파일 형식입니다."
            );
        }
    }

    private String getExtension(
            String filename
    ) {
        int dotIndex =
                filename.lastIndexOf(
                        "."
                );

        if (dotIndex < 0
                || dotIndex == filename.length() - 1) {

            throw new BadRequestException(
                    "파일 확장자가 필요합니다."
            );
        }

        return filename
                .substring(
                        dotIndex + 1
                )
                .toLowerCase(
                        Locale.ROOT
                );
    }

    private String normalizeContentType(
            String contentType
    ) {
        if (contentType == null
                || contentType.isBlank()) {

            throw new BadRequestException(
                    "파일 형식을 확인할 수 없습니다."
            );
        }

        return contentType
                .split(";")[0]
                .trim()
                .toLowerCase(
                        Locale.ROOT
                );
    }

    private Path resolveInsideUploadDir(
            String storedFilename
    ) {
        Path targetPath =
                uploadDir
                        .resolve(
                                storedFilename
                        )
                        .normalize();

        if (!targetPath.startsWith(
                uploadDir
        )) {
            throw new BadRequestException(
                    "파일 경로가 올바르지 않습니다."
            );
        }

        return targetPath;
    }

    public record StoredFile(
            String originalFilename,
            String storedFilename,
            String contentType,
            long size
    ) {
    }

    public record StagedFile(
            String originalFilename,
            String stagedFilename,
            Path originalPath,
            Path stagedPath
    ) {
    }
}