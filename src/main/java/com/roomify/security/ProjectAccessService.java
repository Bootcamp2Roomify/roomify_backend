package com.roomify.security;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class ProjectAccessService {

    private final JdbcTemplate jdbc;

    public ProjectAccessService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean isOwner(UUID projectId, long userId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM room_projects WHERE project_id = ? AND user_id = ?)",
                Boolean.class, projectId, userId));
    }
}
