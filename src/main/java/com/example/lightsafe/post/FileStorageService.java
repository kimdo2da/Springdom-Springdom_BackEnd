package com.example.lightsafe.post;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.UUID;

@Service
public class FileStorageService {

    private final Path uploadDir;

    public FileStorageService(@Value("${file.upload-dir:uploads}") String uploadDir) {
        this.uploadDir = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    public StoredFile store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("첨부파일이 비어있습니다");
        }

        String original = (file.getOriginalFilename() == null) ? "file" : file.getOriginalFilename();
        String stored = generateStoredFilename(original);

        try {
            Files.createDirectories(uploadDir);
            Path target = uploadDir.resolve(stored).normalize();
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("첨부파일 저장에 실패했습니다");
        }

        return new StoredFile(stored, original, file.getContentType(), file.getSize());
    }

    public Path load(String storedFilename) {
        return uploadDir.resolve(storedFilename).normalize();
    }

    private String generateStoredFilename(String originalFilename) {
        String ext = "";
        int idx = originalFilename.lastIndexOf('.');
        if (idx > -1 && idx < originalFilename.length() - 1) {
            ext = originalFilename.substring(idx);
        }
        return UUID.randomUUID() + ext;
    }

    public record StoredFile(
            String storedFilename,
            String originalFilename,
            String contentType,
            long size
    ) {}
}
