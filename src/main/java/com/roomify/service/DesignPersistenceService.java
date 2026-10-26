package com.roomify.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.roomify.dto.design.*;
import com.roomify.exception.InvalidStateException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class DesignPersistenceService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final DesignSnapshotService snapshots;
    public DesignPersistenceService(JdbcTemplate jdbc,ObjectMapper mapper,DesignSnapshotService snapshots) {
        this.jdbc=jdbc;this.mapper=mapper;this.snapshots=snapshots;
    }
    public record Started(Long designId, UUID requestId, DesignRequestSnapshot snapshot) {}

    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public Started begin(UUID projectId,String provider,String model) {
        var states=jdbc.queryForList("SELECT status FROM room_projects WHERE project_id=? FOR UPDATE",String.class,projectId);
        if(states.isEmpty()) throw new NoSuchElementException("Room project not found.");
        if(!Set.of("PREFERENCES_READY","DESIGN_READY").contains(states.getFirst()))
            throw new InvalidStateException("Project must have saved preferences before generating a design.");
        if(Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM design_requests WHERE project_id=? AND status='PENDING')",Boolean.class,projectId)))
            throw new InvalidStateException("A design is already being generated for this project.");
        var snapshot=snapshots.capture(projectId);
        UUID requestId=UUID.randomUUID();
        jdbc.update("""
            INSERT INTO design_requests(request_id,project_id,input_snapshot,request_version,provider,model_name,prompt_version,status)
            VALUES(?,?,CAST(? AS jsonb),?,?,?,?, 'PENDING')
            """,requestId,projectId,json(snapshot),snapshot.requestVersion(),metadata(provider),metadata(model),DesignPromptFactory.VERSION);
        Long id=jdbc.queryForObject("""
            INSERT INTO design_results(project_id,request_id,plan_json,estimated_total_cost,currency,model_name,status)
            VALUES(?,?,NULL,NULL,?,?,'PENDING') RETURNING design_id
            """,Long.class,projectId,requestId,snapshot.budget().currency(),metadata(model));
        return new Started(id,requestId,snapshot);
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void succeed(Started started,DesignPlan plan) {
        UUID projectId=started.snapshot().projectId();
        jdbc.queryForList("SELECT project_id FROM room_projects WHERE project_id=? FOR UPDATE",UUID.class,projectId);
        // A generation started before an edit must not mark the edited project ready.
        if(!json(snapshots.capture(projectId)).equals(json(started.snapshot())))
            throw new InvalidStateException("Design inputs changed during generation.");
        int changed=jdbc.update("""
            UPDATE design_results SET status='SUCCEEDED',plan_json=CAST(? AS jsonb),estimated_total_cost=?,
            completed_at=CURRENT_TIMESTAMP WHERE design_id=? AND request_id=? AND status='PENDING'
            """,json(plan),plan.estimatedTotalCost(),started.designId(),started.requestId());
        if(changed!=1) throw new InvalidStateException("Design attempt is no longer pending.");
        jdbc.update("UPDATE design_requests SET status='SUCCEEDED',completed_at=CURRENT_TIMESTAMP WHERE request_id=?",started.requestId());
        jdbc.update("UPDATE room_projects SET status='DESIGN_READY',updated_at=CURRENT_TIMESTAMP WHERE project_id=?",projectId);
    }
    /** Records that this attempt was answered from the demo library rather than the configured provider. */
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void markDemo(Started started,String model) {
        jdbc.update("UPDATE design_requests SET provider=?,model_name=? WHERE request_id=? AND status='PENDING'",
            com.roomify.ai.DemoLibrary.PROVIDER,metadata(model),started.requestId());
        jdbc.update("UPDATE design_results SET model_name=? WHERE design_id=? AND status='PENDING'",metadata(model),started.designId());
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void fail(Started started,String code) {
        // Codes are fixed by the caller; raw exception text is never persisted.
        jdbc.update("""
            UPDATE design_results SET status='FAILED',error_code=?,completed_at=CURRENT_TIMESTAMP
            WHERE design_id=? AND status='PENDING'
            """,code,started.designId());
        jdbc.update("""
            UPDATE design_requests SET status='FAILED',error_code=?,completed_at=CURRENT_TIMESTAMP
            WHERE request_id=? AND status='PENDING'
            """,code,started.requestId());
    }
    private String metadata(String value) {
        return value!=null&&value.matches("[A-Za-z0-9][A-Za-z0-9._:/-]{0,99}")?value:"unconfigured";
    }
    private String json(Object value) {
        try {return mapper.writeValueAsString(value);}
        catch(JsonProcessingException e) {throw new IllegalStateException("Cannot serialize design data.");}
    }
}
