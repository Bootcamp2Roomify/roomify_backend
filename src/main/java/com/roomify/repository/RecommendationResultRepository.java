package com.roomify.repository;

import com.roomify.dto.recommendation.RecommendationSet;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** SQL access for immutable snapshots. The service owns validation and transaction boundaries. */
@Repository
public class RecommendationResultRepository {
    private final JdbcTemplate jdbc;
    public RecommendationResultRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record SetRow(long id, UUID publicId, UUID projectId, Long designId,
                         String payload, String metadata, Instant createdAt) {}
    public record ItemRow(long id, UUID publicId, long setId, int position, String payload) {}
    public record GuideRow(UUID publicId, String payload, String metadata, Instant createdAt) {}

    private static final RowMapper<SetRow> SET = (rs, row) -> new SetRow(rs.getLong("id"),
            rs.getObject("public_id", UUID.class), rs.getObject("project_id", UUID.class),
            rs.getLong("design_result_id"), rs.getString("payload"), rs.getString("provider_metadata"),
            rs.getTimestamp("created_at").toInstant());
    private static final RowMapper<ItemRow> ITEM = (rs, row) -> new ItemRow(rs.getLong("id"),
            rs.getObject("public_id", UUID.class), rs.getLong("recommendation_set_id"),
            rs.getInt("position"), rs.getString("payload"));

    public long insertSet(UUID id, UUID project, Long design, RecommendationSet value, String payload, String metadata) {
        return jdbc.queryForObject("""
            INSERT INTO recommendation_sets(public_id, project_id, design_result_id, schema_version,
                currency, budget, estimated_total, remaining_budget, payload, provider_metadata)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb)) RETURNING id
            """, Long.class, id, project, design, value.schemaVersion(), value.currency(), value.budget(),
                value.estimatedTotal(), value.remainingBudget(), payload, metadata);
    }

    public void insertItem(UUID id, long set, int position, String payload) {
        jdbc.update("""
            INSERT INTO recommendation_items(public_id, recommendation_set_id, position, payload)
            VALUES (?, ?, ?, CAST(? AS jsonb))
            """, id, set, position, payload);
    }

    public void insertGuide(UUID id, long item, String payload, String metadata) {
        jdbc.update("""
            INSERT INTO recommendation_diy_guides(public_id, recommendation_item_id, payload, provider_metadata)
            VALUES (?, ?, CAST(? AS jsonb), CAST(? AS jsonb))
            """, id, item, payload, metadata);
    }

    public Optional<SetRow> latest(UUID project) {
        return jdbc.query("SELECT * FROM recommendation_sets WHERE project_id = ? ORDER BY id DESC LIMIT 1", SET, project).stream().findFirst();
    }
    public Optional<SetRow> set(long id) {
        return jdbc.query("SELECT * FROM recommendation_sets WHERE id = ?", SET, id).stream().findFirst();
    }
    public List<ItemRow> items(long set) {
        return jdbc.query("SELECT * FROM recommendation_items WHERE recommendation_set_id = ? ORDER BY position", ITEM, set);
    }
    public Optional<ItemRow> item(UUID project, UUID item) {
        return jdbc.query("""
            SELECT i.* FROM recommendation_items i JOIN recommendation_sets s ON s.id = i.recommendation_set_id
            WHERE s.project_id = ? AND i.public_id = ?
            """, ITEM, project, item).stream().findFirst();
    }
    public Optional<GuideRow> latestGuide(long item) {
        return jdbc.query("""
            SELECT public_id, payload, provider_metadata, created_at FROM recommendation_diy_guides
            WHERE recommendation_item_id = ? ORDER BY id DESC LIMIT 1
            """, (rs, row) -> new GuideRow(rs.getObject("public_id", UUID.class), rs.getString("payload"),
                rs.getString("provider_metadata"), rs.getTimestamp("created_at").toInstant()), item).stream().findFirst();
    }
}
