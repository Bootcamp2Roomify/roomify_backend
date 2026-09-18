package com.roomify.repository;

import com.roomify.entity.RoomProject;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoomProjectRepository
        extends JpaRepository<RoomProject, Long> {

    Optional<RoomProject> findByIdAndUserId(Long id, Long userId);
}