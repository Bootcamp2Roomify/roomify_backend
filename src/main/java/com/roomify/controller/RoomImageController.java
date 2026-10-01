package com.roomify.controller;

import com.roomify.dto.RoomImageResponse;
import com.roomify.entity.RoomImage;
import com.roomify.service.RoomImageService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/projects/{projectId}/image")
public class RoomImageController {

    private final RoomImageService roomImageService;

    public RoomImageController(RoomImageService roomImageService) {
        this.roomImageService = roomImageService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<RoomImageResponse> upload(
        @PathVariable Long projectId,
        @RequestParam("file") MultipartFile file
    ) {
        RoomImage image = roomImageService.upload(projectId, file);
        return ResponseEntity.ok(RoomImageResponse.from(image));
    }
}