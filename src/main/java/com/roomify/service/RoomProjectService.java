package com.roomify.service;

import com.roomify.entity.RoomProject;
import com.roomify.entity.RoomProjectStatus;
import com.roomify.exception.InvalidStateException;
import com.roomify.repository.RoomProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class RoomProjectService {

    private final RoomProjectRepository repository;

    public RoomProjectService(RoomProjectRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public RoomProject createProject() {
        return repository.save(new RoomProject());
    }

    @Transactional(readOnly = true)
    public RoomProject getProject(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new NoSuchElementException(
                        "Room project not found: " + id));
    }

    @Transactional
    public RoomProject transitionTo(UUID id, RoomProjectStatus nextStatus) {
        RoomProject project = getProject(id);
        RoomProjectStatus currentStatus = project.getStatus();

        boolean allowed = switch (currentStatus) {
            case CREATED -> nextStatus == RoomProjectStatus.IMAGE_UPLOADED;
            case IMAGE_UPLOADED -> nextStatus == RoomProjectStatus.ANALYZED;
            case ANALYZED -> nextStatus == RoomProjectStatus.PREFERENCES_READY;
            case PREFERENCES_READY -> nextStatus == RoomProjectStatus.DESIGN_READY;
            case DESIGN_READY -> false;
        };

        if (!allowed) {
            throw new InvalidStateException(
                    "Cannot change project from " + currentStatus
                            + " to " + nextStatus);
        }

        project.setStatus(nextStatus);
        return repository.save(project);
    }
}