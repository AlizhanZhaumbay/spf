package com.example.spf.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
public class FileStorageService {

    @Value("${file.upload-dir}")
    private String uploadDir;

    @Value("${server.port}")
    private int serverPort;

    @Value("${server.url}")
    private String serverUrl;

    public String saveFile(MultipartFile file) throws IOException {
        File directory = new File(uploadDir);
        if (!directory.exists()) {
            directory.mkdirs();
        }

        String uniqueFileName = UUID.randomUUID() + "_" + file.getOriginalFilename();
        Path filePath = Path.of(uploadDir, uniqueFileName);

        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        return serverUrl + serverPort + "/uploads/" + uniqueFileName;
    }

    public boolean deleteFile(String fileName) {
        File file = new File(uploadDir + fileName);
        return file.exists() && file.delete();
    }
}

