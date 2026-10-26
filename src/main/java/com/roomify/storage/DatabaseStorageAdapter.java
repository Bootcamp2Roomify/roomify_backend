package com.roomify.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;

/**
 * Keeps object bytes in Postgres (stored_objects) for hosts with no persistent disk and no S3 credentials,
 * e.g. the free Hugging Face Space. Fine for a demo; Neon's free tier caps storage at 0.5 GB.
 */
@Service
@ConditionalOnProperty(name = "storage.type", havingValue = "database")
public class DatabaseStorageAdapter implements StorageService {

    static final String BUCKET = "database";

    private final JdbcTemplate jdbc;

    public DatabaseStorageAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public StoredObject upload(String objectKey, String contentType, long size, InputStream inputStream) {
        if (objectKey == null || objectKey.isBlank()) {
            throw new IllegalArgumentException("Invalid storage key.");
        }
        byte[] bytes;
        try {
            bytes = inputStream.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to store file.", e);
        }
        jdbc.update("""
                INSERT INTO stored_objects(object_key, content_type, size_bytes, content) VALUES (?, ?, ?, ?)
                ON CONFLICT (object_key) DO UPDATE SET content_type = EXCLUDED.content_type,
                    size_bytes = EXCLUDED.size_bytes, content = EXCLUDED.content
                """, objectKey, contentType, bytes.length, bytes);
        return new StoredObject(BUCKET, objectKey);
    }

    @Override
    public void delete(String objectKey) {
        jdbc.update("DELETE FROM stored_objects WHERE object_key = ?", objectKey);
    }

    @Override
    public byte[] download(String objectKey) {
        var rows = jdbc.query("SELECT content FROM stored_objects WHERE object_key = ?",
                (rs, row) -> rs.getBytes(1), objectKey);
        if (rows.isEmpty()) {
            throw new IllegalStateException("Failed to read stored file.");
        }
        return rows.getFirst();
    }
}
