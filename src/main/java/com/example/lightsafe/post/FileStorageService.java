package com.example.lightsafe.post;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.util.UUID;

@Service
public class FileStorageService {

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
    }

    public StoredFile store(
            MultipartFile file
    ) {
        if (file == null
                || file.isEmpty()) {

            throw new IllegalArgumentException(
                    "첨부파일이 비어있습니다."
            );
        }

        String original =
                file.getOriginalFilename() == null
                        ? "file"
                        : file.getOriginalFilename();

        String stored =
                generateStoredFilename(
                        original
                );

        try {
            Files.createDirectories(
                    uploadDir
            );

            Path target =
                    resolveStoredPath(
                            stored
                    );

            try (InputStream inputStream =
                         file.getInputStream()) {

                Files.copy(
                        inputStream,
                        target,
                        StandardCopyOption.REPLACE_EXISTING
                );
            }

        } catch (IOException e) {
            throw new UncheckedIOException(
                    "첨부파일 저장에 실패했습니다.",
                    e
            );
        }

        return new StoredFile(
                stored,
                original,
                file.getContentType(),
                file.getSize()
        );
    }

    public Path load(
            String storedFilename
    ) {
        return resolveStoredPath(
                storedFilename
        );
    }

    /**
     * 회원탈퇴 처리 중 실제 파일을 임시 휴지통으로 이동합니다.
     *
     * DB 트랜잭션이 롤백되면 restoreStagedFile()로 복구하고,
     * 커밋되면 permanentlyDeleteStagedFile()로 최종 삭제합니다.
     */
    public StagedFile stageForDeletion(
            String storedFilename
    ) {
        Path originalPath =
                resolveStoredPath(
                        storedFilename
                );

        if (!Files.exists(originalPath)) {
            return new StagedFile(
                    originalPath,
                    null,
                    false
            );
        }

        try {
            Files.createDirectories(
                    withdrawalTrashDir
            );

            Path stagedPath =
                    withdrawalTrashDir
                            .resolve(
                                    UUID.randomUUID().toString()
                            )
                            .normalize();

            Files.move(
                    originalPath,
                    stagedPath,
                    StandardCopyOption.REPLACE_EXISTING
            );

            return new StagedFile(
                    originalPath,
                    stagedPath,
                    true
            );

        } catch (IOException e) {
            throw new UncheckedIOException(
                    "탈퇴 사용자의 첨부파일을 임시 삭제 처리하지 못했습니다.",
                    e
            );
        }
    }

    public void restoreStagedFile(
            StagedFile stagedFile
    ) {
        if (stagedFile == null
                || !stagedFile.moved()
                || stagedFile.stagedPath() == null) {

            return;
        }

        if (!Files.exists(
                stagedFile.stagedPath()
        )) {
            return;
        }

        try {
            Files.createDirectories(
                    stagedFile.originalPath()
                            .getParent()
            );

            Files.move(
                    stagedFile.stagedPath(),
                    stagedFile.originalPath(),
                    StandardCopyOption.REPLACE_EXISTING
            );

        } catch (IOException e) {
            throw new UncheckedIOException(
                    "롤백된 첨부파일을 복구하지 못했습니다.",
                    e
            );
        }
    }

    public void permanentlyDeleteStagedFile(
            StagedFile stagedFile
    ) {
        if (stagedFile == null
                || !stagedFile.moved()
                || stagedFile.stagedPath() == null) {

            return;
        }

        try {
            Files.deleteIfExists(
                    stagedFile.stagedPath()
            );

        } catch (IOException e) {
            throw new UncheckedIOException(
                    "임시 보관된 첨부파일을 최종 삭제하지 못했습니다.",
                    e
            );
        }
    }

    private Path resolveStoredPath(
            String storedFilename
    ) {
        if (storedFilename == null
                || storedFilename.isBlank()) {

            throw new IllegalArgumentException(
                    "저장 파일명이 비어 있습니다."
            );
        }

        Path resolvedPath =
                uploadDir
                        .resolve(storedFilename)
                        .normalize();

        if (!resolvedPath.startsWith(
                uploadDir
        )) {
            throw new IllegalArgumentException(
                    "잘못된 첨부파일 경로입니다."
            );
        }

        return resolvedPath;
    }

    private String generateStoredFilename(
            String originalFilename
    ) {
        String extension = "";

        int index =
                originalFilename.lastIndexOf('.');

        if (index > -1
                && index
                < originalFilename.length() - 1) {

            extension =
                    originalFilename.substring(
                            index
                    );
        }

        return UUID.randomUUID()
                + extension;
    }

    public record StoredFile(
            String storedFilename,
            String originalFilename,
            String contentType,
            long size
    ) {
    }

    public record StagedFile(
            Path originalPath,
            Path stagedPath,
            boolean moved
    ) {
    }
}