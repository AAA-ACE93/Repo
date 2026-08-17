package com.escrow.integration.storage;

import com.escrow.exception.BaseException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Slf4j
@Service
public class LocalStorageServiceImpl implements StorageService {

    private final Path fileStorageLocation = Paths.get("uploads").toAbsolutePath().normalize();

    public LocalStorageServiceImpl() {
        try {
            Files.createDirectories(this.fileStorageLocation);
        } catch (Exception ex) {
            log.error("Could not create the directory where the uploaded files will be stored.", ex);
        }
    }

    @Override
    public String storeFile(MultipartFile file, String pathPrefix) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Cannot store empty file");
        }

        String fileName = UUID.randomUUID().toString() + "_" + file.getOriginalFilename();
        try {
            Path targetLocation = this.fileStorageLocation.resolve(fileName);
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);
            return fileName;
        } catch (IOException ex) {
            log.error("Failed to store file {}", fileName, ex);
            throw new RuntimeException("Failed to store file " + fileName, ex);
        }
    }

    @Override
    public byte[] loadFile(String storageKey) {
        try {
            Path filePath = this.fileStorageLocation.resolve(storageKey).normalize();
            return Files.readAllBytes(filePath);
        } catch (IOException ex) {
            log.error("Could not read file {}", storageKey, ex);
            throw new RuntimeException("Could not read file " + storageKey, ex);
        }
    }
}
