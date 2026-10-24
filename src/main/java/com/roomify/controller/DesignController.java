package com.roomify.controller;

import com.roomify.dto.design.StoredDesignResponse;
import com.roomify.service.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects/{projectId}/designs")
public class DesignController {
    private final DesignService designs;
    private final DesignQueryService queries;
    private final GeneratedDesignImageService images;
    private final PrototypeService prototypes;
    public DesignController(DesignService designs,DesignQueryService queries,GeneratedDesignImageService images,
            PrototypeService prototypes) {
        this.designs=designs;this.queries=queries;this.images=images;this.prototypes=prototypes;
    }
    @PostMapping public ResponseEntity<StoredDesignResponse> generate(@PathVariable UUID projectId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(designs.generate(projectId));
    }
    @GetMapping("/latest") public ResponseEntity<StoredDesignResponse> latest(@PathVariable UUID projectId) {
        var latest=queries.latest(projectId);
        return latest==null?ResponseEntity.noContent().build():ResponseEntity.ok(latest);
    }
    @GetMapping("/{designId}") public StoredDesignResponse get(@PathVariable UUID projectId,@PathVariable Long designId) {
        return queries.get(projectId,designId);
    }
    @PostMapping(value="/{designId}/image",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public StoredDesignResponse.GeneratedAsset upload(@PathVariable UUID projectId,@PathVariable Long designId,
            @RequestPart("file") MultipartFile file) throws IOException {
        return images.store(projectId,designId,file.getContentType(),file.getBytes());
    }
    @PostMapping("/{designId}/prototype")
    public ResponseEntity<StoredDesignResponse.GeneratedAsset> prototype(@PathVariable UUID projectId,@PathVariable Long designId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(prototypes.generate(projectId,designId));
    }
    @GetMapping("/{designId}/image/content") public ResponseEntity<byte[]> content(@PathVariable UUID projectId,@PathVariable Long designId) {
        var image=images.content(projectId,designId);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(image.contentType()))
            .header("X-Content-Type-Options","nosniff").cacheControl(CacheControl.noStore()).body(image.bytes());
    }
}
