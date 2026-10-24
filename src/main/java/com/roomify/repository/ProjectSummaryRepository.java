package com.roomify.repository;

import com.roomify.dto.ProjectSummaryResponse;
import com.roomify.entity.RentalStatus;
import com.roomify.entity.RoomProjectStatus;
import com.roomify.entity.RoomType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class ProjectSummaryRepository {

    private final JdbcTemplate jdbc;

    public ProjectSummaryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Most recently updated first; fetches one extra row so the caller can tell if more exist. */
    public List<ProjectSummaryResponse> findForUser(long userId, int limit, int offset) {
        return jdbc.query("""
                SELECT p.project_id, p.name, p.room_type, p.rental_status, p.status, p.created_at, p.updated_at,
                       EXISTS (SELECT 1 FROM room_images i WHERE i.project_id = p.project_id) AS has_image,
                       EXISTS (SELECT 1 FROM design_results d
                               WHERE d.project_id = p.project_id AND d.status = 'SUCCEEDED') AS has_design
                FROM room_projects p
                WHERE p.user_id = ?
                ORDER BY p.updated_at DESC, p.project_id
                LIMIT ? OFFSET ?
                """, (rs, row) -> new ProjectSummaryResponse(
                rs.getObject("project_id", UUID.class),
                rs.getString("name"),
                RoomType.valueOf(rs.getString("room_type")),
                RentalStatus.valueOf(rs.getString("rental_status")),
                RoomProjectStatus.valueOf(rs.getString("status")),
                rs.getBoolean("has_image"),
                rs.getBoolean("has_design"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()), userId, limit, offset);
    }
}
