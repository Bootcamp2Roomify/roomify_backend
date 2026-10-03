package com.roomify.service;

import com.roomify.entity.DetectedObject;
import com.roomify.entity.FurnitureDecision;
import com.roomify.entity.FurnitureDecisionType;
import com.roomify.repository.DetectedObjectRepository;
import com.roomify.repository.FurnitureDecisionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class FurnitureDecisionService {

    private final DetectedObjectRepository detectedObjectRepository;
    private final FurnitureDecisionRepository decisionRepository;

    public FurnitureDecisionService(
            DetectedObjectRepository detectedObjectRepository,
            FurnitureDecisionRepository decisionRepository
    ) {
        this.detectedObjectRepository = detectedObjectRepository;
        this.decisionRepository = decisionRepository;
    }

    @Transactional
    public FurnitureDecision setDecision(
            UUID projectId,
            UUID objectId,
            FurnitureDecisionType decisionType
    ) {
        DetectedObject detectedObject = detectedObjectRepository
                .findByObjectUuidAndProjectId(objectId, projectId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Detected object was not found in this project."
                ));

        FurnitureDecision decision = decisionRepository
                .findByDetectedObject_Id(detectedObject.getId())
                .orElseGet(() ->
                        new FurnitureDecision(detectedObject, decisionType)
                );

        decision.updateDecision(decisionType);

        return decisionRepository.saveAndFlush(decision);
    }
}