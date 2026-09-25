package com.roomify.repository;

import com.roomify.entity.FurnitureDecision;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FurnitureDecisionRepository
        extends JpaRepository<FurnitureDecision, Long> {

    Optional<FurnitureDecision> findByDetectedObject_Id(Long objectId);
}