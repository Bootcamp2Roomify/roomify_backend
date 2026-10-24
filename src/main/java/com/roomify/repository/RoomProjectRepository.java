package com.roomify.repository;

import com.roomify.entity.RoomProject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;

import java.util.UUID;

public interface RoomProjectRepository extends JpaRepository<RoomProject, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT project FROM RoomProject project WHERE project.id = :id")
    Optional<RoomProject> findByIdForUpdate(@Param("id") UUID id);
}