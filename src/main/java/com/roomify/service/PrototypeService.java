package com.roomify.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roomify.ai.AiProviderConfigurationException;
import com.roomify.ai.AiProviderRetryableException;
import com.roomify.ai.DesignImageProvider;
import com.roomify.ai.GeneratedDesignImage;
import com.roomify.dto.design.DesignRequestSnapshot;
import com.roomify.dto.design.StoredDesignResponse;
import com.roomify.dto.design.StoredDesignResponse.GeneratedAsset;
import com.roomify.exception.DesignFailureException;
import com.roomify.exception.InvalidStateException;
import com.roomify.storage.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * ROOM-95: renders the visual prototype for a saved design from the exact photo the plan was made for.
 * No database transaction is held while the provider works; the asset is claimed only when bytes exist.
 */
@Service
public class PrototypeService {

    private static final Logger logger = LoggerFactory.getLogger(PrototypeService.class);

    private final DesignQueryService queries;
    private final GeneratedDesignImageService images;
    private final DesignImageProvider provider;
    private final PrototypePromptFactory prompts;
    private final StorageService storage;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public PrototypeService(DesignQueryService queries, GeneratedDesignImageService images, DesignImageProvider provider,
            PrototypePromptFactory prompts, StorageService storage, JdbcTemplate jdbc, ObjectMapper mapper) {
        this.queries = queries;
        this.images = images;
        this.provider = provider;
        this.prompts = prompts;
        this.storage = storage;
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public GeneratedAsset generate(UUID projectId, Long designId) {
        StoredDesignResponse design = queries.get(projectId, designId);
        if (!"SUCCEEDED".equals(design.status()) || design.plan() == null) {
            throw new InvalidStateException("Generate a valid redesign plan before its visual prototype.");
        }
        if (design.image() != null && "SUCCEEDED".equals(design.image().status())) {
            return design.image();
        }
        DesignRequestSnapshot snapshot = snapshot(designId);
        byte[] original;
        try {
            original = storage.download(snapshot.image().storageKey());
        } catch (RuntimeException unavailable) {
            throw new DesignFailureException("ORIGINAL_IMAGE_UNAVAILABLE", designId);
        }
        String contentType = snapshot.image().contentType();
        GeneratedDesignImage generated;
        try {
            generated = provider.generate(original, "room." + ("image/png".equals(contentType) ? "png" : "jpg"),
                    contentType, prompts.build(design.plan(), snapshot));
        } catch (AiProviderConfigurationException misconfigured) {
            throw new DesignFailureException("AI_CONFIGURATION_ERROR", designId);
        } catch (AiProviderRetryableException unavailable) {
            throw new DesignFailureException("AI_PROVIDER_UNAVAILABLE", designId);
        } catch (RuntimeException failed) {
            logger.warn("Visual prototype generation failed for design {}: {}", designId, failed.getClass().getSimpleName());
            throw new DesignFailureException("PROTOTYPE_GENERATION_FAILED", designId);
        }
        return images.store(projectId, designId, generated.contentType(), generated.imageBytes());
    }

    private DesignRequestSnapshot snapshot(Long designId) {
        String json = jdbc.queryForObject("""
                SELECT r.input_snapshot::text FROM design_results d
                JOIN design_requests r ON r.request_id = d.request_id
                WHERE d.design_id = ?
                """, String.class, designId);
        try {
            return mapper.readValue(json, DesignRequestSnapshot.class);
        } catch (Exception unreadable) {
            throw new DesignFailureException("DESIGN_SNAPSHOT_UNREADABLE", designId);
        }
    }
}
