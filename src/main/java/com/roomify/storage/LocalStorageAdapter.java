package com.roomify.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Service
@ConditionalOnProperty(name = "storage.type", havingValue = "local", matchIfMissing = true)
public class LocalStorageAdapter implements StorageService {

    private final Path root;
    private final String bucket;

    public LocalStorageAdapter(
        @Value("${storage.local.root:./storage}") String root,
        @Value("${storage.local.bucket:local}") String bucket
    ) {
        this.root = Path.of(root).toAbsolutePath().normalize();
        this.bucket = bucket;
    }

    @Override
    public StoredObject upload(
        String objectKey,
        String contentType,
        long size,
        InputStream inputStream
    ) {
        Path target = root.resolve(objectKey).normalize();

        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("Invalid storage key.");
        }

        try {
            Files.createDirectories(target.getParent());
            Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
            return new StoredObject(bucket, objectKey);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to store file.", e);
        }
    }

    @Override
    public void delete(String objectKey) {
        Path target = root.resolve(objectKey).normalize();

        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("Invalid storage key.");
        }

        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to delete stored file.", e);
        }
    }

    @Override
    public byte[] download(String objectKey) {
        Path target = root.resolve(objectKey).normalize();

        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("Invalid storage key.");
        }

        try {
            return Files.readAllBytes(target);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read stored file.", e);
        }
    }
}
