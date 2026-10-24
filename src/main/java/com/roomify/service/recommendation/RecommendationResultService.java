package com.roomify.service.recommendation;

import com.roomify.dto.recommendation.*;
import com.roomify.repository.DesignResultRepository;
import com.roomify.repository.RecommendationResultRepository;
import com.roomify.repository.RoomProjectRepository;
import com.roomify.service.ProjectPreferencesService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class RecommendationResultService {
    private final RecommendationResultRepository repository;
    private final RoomProjectRepository projects;
    private final DesignResultRepository designs;
    private final ProjectPreferencesService preferences;
    private final RecommendationContractCodec codec;

    public RecommendationResultService(RecommendationResultRepository repository, RoomProjectRepository projects,
            DesignResultRepository designs, ProjectPreferencesService preferences, RecommendationContractCodec codec) {
        this.repository = repository; this.projects = projects; this.designs = designs;
        this.preferences = preferences; this.codec = codec;
    }

    @Transactional
    public StoredRecommendationSet storeSet(UUID projectId, Long designResultId, RecommendationSet payload, GenerationMetadata metadata) {
        codec.validate(payload); codec.validate(metadata); requireProject(projectId);
        if (designResultId == null) throw missing();
        var design = designs.findById(designResultId).filter(value -> value.getProjectId().equals(projectId)).orElseThrow(this::missing);
        var savedPreferences = preferences.find(projectId);
        if (savedPreferences == null || !savedPreferences.currency().equals(payload.currency())
                || !design.getCurrency().equals(payload.currency())
                || payload.budget().compareTo(savedPreferences.budgetAmount()) > 0
                || payload.budget().compareTo(preferences.maxBudget()) > 0) {
            throw new IllegalArgumentException("Recommendations must match the project's saved currency and budget.");
        }
        long set = repository.insertSet(UUID.randomUUID(), projectId, designResultId, payload,
                codec.writeSet(payload), codec.writeMetadata(metadata));
        for (int position = 0; position < payload.items().size(); position++) {
            repository.insertItem(UUID.randomUUID(), set, position, codec.writeItem(payload.items().get(position)));
        }
        return response(repository.set(set).orElseThrow(this::missing));
    }

    @Transactional
    public StoredDIYGuide storeGuide(UUID projectId, UUID itemId, DIYGuide payload, GenerationMetadata metadata) {
        codec.validate(payload); codec.validate(metadata);
        var item = repository.item(projectId, itemId).orElseThrow(this::missing);
        var set = response(repository.set(item.setId()).orElseThrow(this::missing));
        var savedItem = set.items().get(item.position());
        if (!Boolean.TRUE.equals(savedItem.diyEligible()) || !set.currency().equals(payload.currency())) {
            throw new IllegalArgumentException("DIY guides must match an eligible item's saved currency.");
        }
        UUID guideId = UUID.randomUUID();
        repository.insertGuide(guideId, item.id(), codec.writeGuide(payload), codec.writeMetadata(metadata));
        return guideResponse(projectId, itemId, repository.guide(guideId).orElseThrow(this::missing));
    }

    @Transactional(readOnly = true)
    public StoredRecommendationSet latest(UUID projectId) {
        requireProject(projectId);
        return response(repository.latest(projectId).orElseThrow(this::missing));
    }

    @Transactional(readOnly = true)
    public StoredDIYGuide latestGuide(UUID projectId, UUID itemId) {
        var item = repository.item(projectId, itemId).orElseThrow(this::missing);
        return guideResponse(projectId, itemId, repository.latestGuide(item.id()).orElseThrow(this::missing));
    }

    private StoredRecommendationSet response(RecommendationResultRepository.SetRow row) {
        var payload = codec.readSet(row.payload());
        var items = repository.items(row.id());
        if (items.size() != payload.items().size()) throw inconsistent();
        var responseItems = new ArrayList<StoredRecommendationItem>();
        for (int position = 0; position < items.size(); position++) {
            var item = payload.items().get(position);
            var stored = items.get(position);
            if (stored.position() != position || !codec.writeItem(codec.readItem(stored.payload())).equals(codec.writeItem(item))) {
                throw inconsistent();
            }
            responseItems.add(new StoredRecommendationItem(stored.publicId(), item.name(), item.category(),
                    item.reason(), item.estimatedPrice(), item.cheaperAlternative(), item.searchQuery(), item.diyEligible()));
        }
        return new StoredRecommendationSet(row.publicId(), row.projectId(), row.designId(), payload.schemaVersion(),
                payload.currency(), payload.budget(), payload.estimatedTotal(), payload.remainingBudget(),
                responseItems, codec.readMetadata(row.metadata()), row.createdAt());
    }

    private StoredDIYGuide guideResponse(UUID projectId, UUID itemId, RecommendationResultRepository.GuideRow row) {
        var guide = codec.readGuide(row.payload());
        return new StoredDIYGuide(row.publicId(), itemId, projectId, guide.schemaVersion(), guide.currency(),
                guide.materials(), guide.tools(), guide.estimatedCost(), guide.difficulty(), guide.estimatedTime(),
                guide.steps(), guide.safetyNotes(), codec.readMetadata(row.metadata()), row.createdAt());
    }

    private void requireProject(UUID projectId) {
        if (projectId == null || !projects.existsById(projectId)) throw missing();
    }
    private NoSuchElementException missing() { return new NoSuchElementException("Saved recommendation result not found."); }
    private IllegalStateException inconsistent() { return new IllegalStateException("Stored recommendation items are inconsistent."); }
}
