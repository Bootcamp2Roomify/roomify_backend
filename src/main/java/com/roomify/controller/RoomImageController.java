package com.roomify.controller;

import com.roomify.dto.ActiveRoomImageResponse;
import com.roomify.dto.RoomImageResponse;
import com.roomify.entity.RoomImage;
import com.roomify.service.RoomImageService;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.UUID;

@RestController
@RequestMapping("/api/projects/{projectId}/image")
public class RoomImageController {

    private final RoomImageService roomImageService;

    public RoomImageController(RoomImageService roomImageService) {
        this.roomImageService = roomImageService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<RoomImageResponse> upload(
        @PathVariable UUID projectId,
        @RequestParam("file") MultipartFile file
    ) {
        RoomImage image = roomImageService.upload(projectId, file);
        return ResponseEntity.ok(RoomImageResponse.from(image));
    }

    @GetMapping
    public ActiveRoomImageResponse getActiveImage(@PathVariable UUID projectId) {
        RoomImage image = roomImageService.getActiveImage(projectId);
        String imageUrl = ServletUriComponentsBuilder.fromCurrentRequestUri()
            .path("/content")
            .toUriString();

        return ActiveRoomImageResponse.from(image, imageUrl);
    }

    @GetMapping("/content")
    public ResponseEntity<byte[]> getActiveImageContent(@PathVariable UUID projectId) {
        RoomImage image = roomImageService.getActiveImage(projectId);

        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(image.getMimeType()))
            .cacheControl(CacheControl.noCache())
            .body(roomImageService.getActiveImageContent(image));
    }
}
