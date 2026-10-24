package com.roomify.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roomify.dto.design.*;
import com.roomify.dto.design.StoredDesignResponse.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.sql.*;
import java.time.Instant;
import java.util.*;

@Service
public class DesignQueryService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    public DesignQueryService(JdbcTemplate jdbc,ObjectMapper mapper) {this.jdbc=jdbc;this.mapper=mapper;}
    private static final String SELECT="""
        SELECT d.*,r.provider,r.request_version,r.prompt_version,
            a.status AS image_status,a.error_code AS image_error,a.content_type,a.file_size_bytes,a.width,a.height
        FROM design_results d LEFT JOIN design_requests r ON r.request_id=d.request_id
        LEFT JOIN design_assets a ON a.design_result_id=d.design_id
        """;
    public StoredDesignResponse get(UUID projectId,Long id) {
        var rows=jdbc.query(SELECT+" WHERE d.project_id=? AND d.design_id=?",this::map,projectId,id);
        if(rows.isEmpty()) throw new NoSuchElementException("Design not found for this project.");
        return rows.getFirst();
    }
    public StoredDesignResponse latest(UUID projectId) {
        if(Boolean.FALSE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM room_projects WHERE project_id=?)",Boolean.class,projectId)))
            throw new NoSuchElementException("Room project not found.");
        var rows=jdbc.query(SELECT+" WHERE d.project_id=? ORDER BY d.created_at DESC,d.design_id DESC LIMIT 1",this::map,projectId);
        return rows.isEmpty()?null:rows.getFirst();
    }
    private StoredDesignResponse map(ResultSet rs,int row) throws SQLException {
        DesignPlan plan=null;
        String version=rs.getString("request_version");
        // V6 legacy plans remain in the database; they are not silently presented as v1 output.
        if("SUCCEEDED".equals(rs.getString("status")) && "1".equals(version)) {
            try {plan=mapper.readValue(rs.getString("plan_json"),DesignPlan.class);}
            catch(Exception e) {throw new IllegalStateException("Stored design could not be read.");}
        }
        var project=rs.getObject("project_id",UUID.class);Long id=rs.getLong("design_id");
        String imageStatus=rs.getString("image_status");
        GeneratedAsset asset=imageStatus==null?null:new GeneratedAsset(imageStatus,rs.getString("image_error"),
            rs.getString("content_type"),(Long)rs.getObject("file_size_bytes"),(Integer)rs.getObject("width"),(Integer)rs.getObject("height"),
            "SUCCEEDED".equals(imageStatus)?"/api/projects/"+project+"/designs/"+id+"/image/content":null);
        return new StoredDesignResponse(id,rs.getObject("request_id",UUID.class),project,rs.getString("status"),
            rs.getString("error_code"),plan,rs.getString("provider"),rs.getString("model_name"),version,
            rs.getString("prompt_version"),instant(rs,"created_at"),instant(rs,"completed_at"),asset);
    }
    private Instant instant(ResultSet rs,String name) throws SQLException {
        var value=rs.getTimestamp(name);return value==null?null:value.toInstant();
    }
}
