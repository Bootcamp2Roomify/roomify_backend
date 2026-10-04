package com.roomify.service;

import com.roomify.entity.RoomImage;
import com.roomify.entity.RoomProject;
import com.roomify.entity.RoomProjectStatus;
import com.roomify.exception.NoActiveImageException;
import com.roomify.repository.RoomImageRepository;
import com.roomify.repository.RoomProjectRepository;
import com.roomify.storage.RoomImageStoragePolicy;
import com.roomify.storage.StorageService;
import com.roomify.storage.StoredObject;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class RoomImageService {

    private final RoomProjectRepository roomProjectRepository;
    private final RoomImageRepository roomImageRepository;
    private final StorageService storageService;
    private final RoomImageStoragePolicy storagePolicy;

    public RoomImageService(
        RoomProjectRepository roomProjectRepository,
        RoomImageRepository roomImageRepository,
        StorageService storageService,
        RoomImageStoragePolicy storagePolicy
    ) {
        this.roomProjectRepository = roomProjectRepository;
        this.roomImageRepository = roomImageRepository;
        this.storageService = storageService;
        this.storagePolicy = storagePolicy;
    }

    public RoomImage upload(UUID projectId, MultipartFile file) {
        RoomProject project = roomProjectRepository.findById(projectId)
            .orElseThrow(() -> new IllegalArgumentException("Room project not found."));

        String contentType = file.getContentType();
        long size = file.getSize();
        storagePolicy.validate(contentType, size);

        String objectKey = storagePolicy.generateObjectKey(projectId, contentType);
        String originalFilename = file.getOriginalFilename();

        if (originalFilename == null || originalFilename.isBlank()) {
            originalFilename = "room-image";
        }

        byte[] bytes;

        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read uploaded image.", e);
        }

        BufferedImage decoded = decode(bytes);

        if (decoded == null) {
            throw new IllegalArgumentException("Image file could not be read.");
        }

        StoredObject storedObject = storageService.upload(
            objectKey,
            contentType,
            size,
            new ByteArrayInputStream(bytes)
        );

        RoomImage existing = roomImageRepository.findByProjectId(projectId).orElse(null);

        if (existing == null) {
            RoomImage roomImage = new RoomImage(
                projectId,
                storedObject.bucket(),
                storedObject.key(),
                originalFilename,
                contentType,
                size
            );
            roomImage.setDimensions(decoded.getWidth(), decoded.getHeight());

            try {
                RoomImage saved = roomImageRepository.saveAndFlush(roomImage);
                markImageUploaded(project);
                return saved;
            } catch (RuntimeException e) {
                storageService.delete(storedObject.key());
                throw e;
            }
        }

        String oldKey = existing.getStorageKey();

        existing.replace(
            storedObject.bucket(),
            storedObject.key(),
            originalFilename,
            contentType,
            size
        );
        existing.setDimensions(decoded.getWidth(), decoded.getHeight());

        try {
            RoomImage saved = roomImageRepository.saveAndFlush(existing);
            markImageUploaded(project);
            storageService.delete(oldKey);
            return saved;
        } catch (RuntimeException e) {
            storageService.delete(storedObject.key());
            throw e;
        }
    }

    public RoomImage getActiveImage(UUID projectId) {
        if (!roomProjectRepository.existsById(projectId)) {
            throw new NoSuchElementException("Room project not found: " + projectId);
        }

        RoomImage image = roomImageRepository.findByProjectId(projectId)
            .orElseThrow(() -> new NoActiveImageException(
                "Project has no room image."
            ));

        // Images uploaded before dimensions were recorded.
        if (image.getWidth() == null || image.getHeight() == null) {
            BufferedImage decoded = decode(storageService.download(image.getStorageKey()));

            if (decoded != null) {
                image.setDimensions(decoded.getWidth(), decoded.getHeight());
                image = roomImageRepository.save(image);
            }
        }

        return image;
    }

    public byte[] getActiveImageContent(RoomImage image) {
        return storageService.download(image.getStorageKey());
    }

    private BufferedImage decode(byte[] bytes) {
        try {
            return ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (IOException e) {
            return null;
        }
    }

    // A new image makes the project ready for (re-)analysis.
    private void markImageUploaded(RoomProject project) {
        RoomProjectStatus status = project.getStatus();

        if (status == RoomProjectStatus.CREATED
            || status == RoomProjectStatus.ANALYZED
            || status == RoomProjectStatus.ANALYSIS_FAILED) {
            project.setStatus(RoomProjectStatus.IMAGE_UPLOADED);
            roomProjectRepository.save(project);
        }
    }
}
