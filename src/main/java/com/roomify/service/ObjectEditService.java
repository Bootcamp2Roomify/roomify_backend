package com.roomify.service;

import com.roomify.dto.DetectedObjectResponse;
import com.roomify.entity.DetectedObject;
import com.roomify.entity.FurnitureDecision;
import com.roomify.entity.FurnitureDecisionType;
import com.roomify.entity.RoomProject;
import com.roomify.entity.RoomProjectStatus;
import com.roomify.exception.InvalidStateException;
import com.roomify.exception.NoActiveImageException;
import com.roomify.repository.DetectedObjectRepository;
import com.roomify.repository.FurnitureDecisionRepository;
import com.roomify.repository.RoomImageRepository;
import com.roomify.repository.RoomProjectRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.NoSuchElementException;
import java.util.UUID;

/** MVP Feature 4: correct the detected furniture list before generating a redesign. */
@Service
public class ObjectEditService {

    static final int MAX_ACTIVE_OBJECTS = 100;

    private final RoomProjectRepository projects;
    private final RoomImageRepository images;
    private final DetectedObjectRepository objects;
    private final FurnitureDecisionRepository decisions;

    public ObjectEditService(RoomProjectRepository projects, RoomImageRepository images,
            DetectedObjectRepository objects, FurnitureDecisionRepository decisions) {
        this.projects = projects;
        this.images = images;
        this.objects = objects;
        this.decisions = decisions;
    }

    @Transactional
    public DetectedObjectResponse add(UUID projectId, String label, FurnitureDecisionType decision) {
        // Same row lock as upload and analysis, so a concurrent re-analysis cannot drop the new item.
        RoomProject project = projects.findByIdForUpdate(projectId)
                .orElseThrow(() -> new NoSuchElementException("Room project not found: " + projectId));
        var image = images.findTopByProjectIdOrderByCreatedAtDesc(projectId)
                .orElseThrow(() -> new NoActiveImageException("Upload a room image before adding furniture."));
        if (objects.findByProjectIdAndActiveTrueOrderByIdAsc(projectId).size() >= MAX_ACTIVE_OBJECTS) {
            throw new InvalidStateException("A room can list at most " + MAX_ACTIVE_OBJECTS + " items.");
        }
        // Adding items by hand is how a person continues after analysis fails or finds nothing.
        if (project.getStatus() == RoomProjectStatus.IMAGE_UPLOADED
                || project.getStatus() == RoomProjectStatus.ANALYSIS_FAILED) {
            project.setStatus(RoomProjectStatus.ANALYZED);
            projects.saveAndFlush(project);
        }
        FurnitureDecisionType chosen = decision == null ? FurnitureDecisionType.UNSURE : decision;
        DetectedObject saved = objects.saveAndFlush(DetectedObject.manual(projectId, image.getId(), label.trim()));
        decisions.saveAndFlush(new FurnitureDecision(saved, chosen));
        return DetectedObjectResponse.from(saved, chosen);
    }

    @Transactional
    public DetectedObjectResponse relabel(UUID projectId, UUID objectId, String label) {
        DetectedObject object = activeObject(projectId, objectId);
        object.relabel(label.trim());
        FurnitureDecisionType decision = decisions.findByDetectedObject_Id(object.getId())
                .map(FurnitureDecision::getDecision).orElse(FurnitureDecisionType.UNSURE);
        return DetectedObjectResponse.from(objects.saveAndFlush(object), decision);
    }

    @Transactional
    public void remove(UUID projectId, UUID objectId) {
        DetectedObject object = activeObject(projectId, objectId);
        object.deactivate();
        objects.saveAndFlush(object);
    }

    private DetectedObject activeObject(UUID projectId, UUID objectId) {
        return objects.findByObjectUuidAndProjectId(objectId, projectId)
                .filter(DetectedObject::isActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Detected object was not found in this project."));
    }
}
