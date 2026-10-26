package com.roomify.service;

import com.roomify.exception.InvalidStateException;
import com.roomify.storage.StoredObject;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
public class DesignAssetPersistence {
    private final JdbcTemplate jdbc;
    public DesignAssetPersistence(JdbcTemplate jdbc) {this.jdbc=jdbc;}
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public boolean claim(UUID projectId,Long designId) {
        var rows=jdbc.queryForList("SELECT status FROM design_results WHERE design_id=? AND project_id=? FOR UPDATE",String.class,designId,projectId);
        if(rows.isEmpty()) throw new java.util.NoSuchElementException("Design not found for this project.");
        if(!"SUCCEEDED".equals(rows.getFirst())) throw new InvalidStateException("A valid design plan is required before storing its image.");
        var states=jdbc.queryForList("SELECT status FROM design_assets WHERE design_result_id=?",String.class,designId);
        if(!states.isEmpty()) {
            if("SUCCEEDED".equals(states.getFirst())) return false;
            if("UPLOADING".equals(states.getFirst())) throw new InvalidStateException("The design image is already being stored.");
            jdbc.update("UPDATE design_assets SET status='UPLOADING',error_code=NULL,updated_at=CURRENT_TIMESTAMP WHERE design_result_id=?",designId);
        } else jdbc.update("INSERT INTO design_assets(design_result_id,project_id,status) VALUES(?,?,'UPLOADING')",designId,projectId);
        return true;
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void complete(Long id,StoredObject object,String type,long size,int width,int height,String source) {
        int changed=jdbc.update("""
            UPDATE design_assets SET status='SUCCEEDED',bucket=?,storage_key=?,content_type=?,file_size_bytes=?,
            width=?,height=?,source=?,error_code=NULL,updated_at=CURRENT_TIMESTAMP WHERE design_result_id=? AND status='UPLOADING'
            """,object.bucket(),object.key(),type,size,width,height,source,id);
        if(changed!=1) throw new InvalidStateException("Image upload is no longer pending.");
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void fail(Long id) {
        jdbc.update("""
            UPDATE design_assets SET status='FAILED',error_code='GENERATED_IMAGE_STORAGE_FAILED',updated_at=CURRENT_TIMESTAMP
            WHERE design_result_id=? AND status='UPLOADING'
            """,id);
    }
}
