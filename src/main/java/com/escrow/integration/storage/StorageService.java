package com.escrow.integration.storage;

import org.springframework.web.multipart.MultipartFile;

public interface StorageService {
    String storeFile(MultipartFile file, String pathPrefix);
    byte[] loadFile(String storageKey);
}
