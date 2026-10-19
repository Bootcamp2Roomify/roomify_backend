package com.roomify.repository;

import com.roomify.entity.FurnitureDecision;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FurnitureDecisionRepository
        extends JpaRepository<FurnitureDecision, Long> {

    Optional<FurnitureDecision> findByDetectedObject_Id(Long objectId);

    List<FurnitureDecision> findByDetectedObject_ProjectIdOrderByIdAsc(UUID projectId);
}