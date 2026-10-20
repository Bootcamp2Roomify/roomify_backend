package com.roomify.repository;

import com.roomify.entity.DesignResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DesignResultRepository extends JpaRepository<DesignResult, Long> {

    Optional<DesignResult> findByProjectId(UUID projectId);

    boolean existsByProjectId(UUID projectId);
}