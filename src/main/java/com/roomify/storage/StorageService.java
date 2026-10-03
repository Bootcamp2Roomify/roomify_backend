package com.roomify.storage;
import java.io.InputStream;

public interface StorageService {
    StoredObject upload(
        String objectKey,
        String contentType,
        long size,
        InputStream inputStream
    );

    void delete(String objectKey);

    byte[] download(String objectKey);
}
