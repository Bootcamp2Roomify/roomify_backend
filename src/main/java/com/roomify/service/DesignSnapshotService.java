package com.roomify.service;

import com.roomify.dto.design.DesignRequestSnapshot;
import com.roomify.dto.design.DesignRequestSnapshot.*;
import com.roomify.entity.FurnitureDecisionType;
import com.roomify.exception.InvalidStateException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
public class DesignSnapshotService {
    private final JdbcTemplate jdbc;
    private final ProjectPreferencesService preferences;
    public DesignSnapshotService(JdbcTemplate jdbc, ProjectPreferencesService preferences) {
        this.jdbc=jdbc; this.preferences=preferences;
    }
    public DesignRequestSnapshot capture(UUID projectId) {
        var p=preferences.get(projectId);
        if(p==null) throw new InvalidStateException("Save preferences before generating a design.");
        var images=jdbc.query("""
            SELECT image_id,bucket,storage_key,mime_type,width,height FROM room_images
            WHERE project_id=? ORDER BY created_at DESC,image_id DESC LIMIT 1
            """,(rs,row)->new ImageReference(rs.getLong("image_id"),rs.getString("bucket"),
                rs.getString("storage_key"),rs.getString("mime_type"),
                (Integer)rs.getObject("width"),(Integer)rs.getObject("height")),projectId);
        if(images.isEmpty()) throw new InvalidStateException("Upload a room image before generating a design.");
        var image=images.getFirst();
        var objects=jdbc.query("""
            SELECT d.*,COALESCE(f.decision,'UNSURE') AS saved_decision FROM detected_objects d
            LEFT JOIN furniture_decisions f ON f.object_id=d.object_id
            WHERE d.project_id=? AND d.image_id=? AND d.active=TRUE ORDER BY d.object_id
            """,(rs,row)->{
                var x=rs.getBigDecimal("x_min"); var y=rs.getBigDecimal("y_min");
                var xmax=rs.getBigDecimal("x_max"); var ymax=rs.getBigDecimal("y_max");
                var bounds=x==null||y==null||xmax==null||ymax==null?null:new Bounds(x,y,xmax.subtract(x),ymax.subtract(y));
                return new Furniture(rs.getObject("object_uuid",UUID.class),rs.getString("object_class"),
                    rs.getBigDecimal("confidence"),bounds,FurnitureDecisionType.valueOf(rs.getString("saved_decision")));
            },projectId,image.imageId());
        if(objects.size()>100) throw new InvalidStateException("At most 100 active detections are supported for design generation.");
        return new DesignRequestSnapshot("1",projectId,image,objects,
            new Preferences(p.style(),p.preferredColors(),p.roomPurpose(),p.rentalFriendly(),p.specialRequirements()),
            new Budget(p.budgetAmount(),p.currency()));
    }
}
