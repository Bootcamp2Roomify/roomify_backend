package com.roomify.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.InputStream;

@Service
@ConditionalOnProperty(name = "storage.type", havingValue = "s3")
public class S3StorageAdapter implements StorageService {

    private final S3Client s3Client;
    private final String bucket;

    public S3StorageAdapter(
        S3Client s3Client,
        @Value("${storage.s3.bucket}") String bucket
    ) {
        this.s3Client = s3Client;
        this.bucket = bucket;
    }

    @Override
    public StoredObject upload(
        String objectKey,
        String contentType,
        long size,
        InputStream inputStream
    ) {
        PutObjectRequest request = PutObjectRequest.builder()
            .bucket(bucket)
            .key(objectKey)
            .contentType(contentType)
            .contentLength(size)
            .build();

        s3Client.putObject(
            request,
            RequestBody.fromInputStream(inputStream, size)
        );

        return new StoredObject(bucket, objectKey);
    }

    @Override
    public void delete(String objectKey) {
        DeleteObjectRequest request = DeleteObjectRequest.builder()
            .bucket(bucket)
            .key(objectKey)
            .build();

        s3Client.deleteObject(request);
    }

    @Override
    public byte[] download(String objectKey) {
        GetObjectRequest request = GetObjectRequest.builder()
            .bucket(bucket)
            .key(objectKey)
            .build();

        return s3Client.getObjectAsBytes(request).asByteArray();
    }
}
