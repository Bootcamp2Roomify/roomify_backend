package com.roomify.service;

import com.roomify.dto.design.StoredDesignResponse.GeneratedAsset;
import com.roomify.exception.DesignFailureException;
import com.roomify.storage.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import javax.imageio.ImageIO;
import java.io.*;
import java.util.*;

/** ROOM-94 adapters can pass normalized provider bytes directly to store(). */
@Service
public class GeneratedDesignImageService {
    private final StorageService storage;
    private final DesignAssetPersistence persistence;
    private final DesignQueryService queries;
    private final JdbcTemplate jdbc;
    private final long maxBytes;
    public GeneratedDesignImageService(StorageService storage,DesignAssetPersistence persistence,
            DesignQueryService queries,JdbcTemplate jdbc,@Value("${design-image.max-size-bytes:10485760}") long maxBytes) {
        this.storage=storage;this.persistence=persistence;this.queries=queries;this.jdbc=jdbc;this.maxBytes=maxBytes;
    }
    public GeneratedAsset store(UUID projectId,Long designId,String contentType,byte[] bytes) {
        return store(projectId,designId,contentType,bytes,"ai");
    }
    /** source is "ai" for provider output and "demo" for a bundled demo-library image. */
    public GeneratedAsset store(UUID projectId,Long designId,String contentType,byte[] bytes,String source) {
        // Check ownership before processing the provider bytes.
        queries.get(projectId,designId);
        int[] dimensions=dimensions(contentType,bytes);
        if(!persistence.claim(projectId,designId)) return queries.get(projectId,designId).image();
        String extension="image/png".equals(contentType)?"png":"jpg";
        String key="rooms/%s/generated/%s/%s.%s".formatted(projectId,designId,UUID.randomUUID(),extension);
        try {
            StoredObject stored=storage.upload(key,contentType,bytes.length,new ByteArrayInputStream(bytes));
            if(stored==null || !key.equals(stored.key()) || stored.bucket()==null || stored.bucket().isBlank())
                throw new IllegalStateException("Invalid stored object metadata.");
            persistence.complete(designId,stored,contentType,bytes.length,dimensions[0],dimensions[1],source);
        } catch(RuntimeException error) {
            // A partial write or database failure must not expose a successful image.
            try {storage.delete(key);} catch(RuntimeException cleanupFailure) { /* Safe prefix permits later orphan cleanup. */ }
            persistence.fail(designId);
            throw new DesignFailureException("GENERATED_IMAGE_STORAGE_FAILED",designId);
        }
        return queries.get(projectId,designId).image();
    }
    public record Content(byte[] bytes,String contentType) {}
    public Content content(UUID projectId,Long designId) {
        var rows=jdbc.query("""
            SELECT storage_key,content_type FROM design_assets
            WHERE project_id=? AND design_result_id=? AND status='SUCCEEDED'
            """,(rs,row)->new String[]{rs.getString(1),rs.getString(2)},projectId,designId);
        if(rows.isEmpty()) throw new NoSuchElementException("Generated design image not found.");
        try {return new Content(storage.download(rows.getFirst()[0]),rows.getFirst()[1]);}
        catch(RuntimeException error) {throw new DesignFailureException("GENERATED_IMAGE_UNAVAILABLE",designId);}
    }
    private int[] dimensions(String type,byte[] bytes) {
        if(bytes==null || bytes.length==0 || bytes.length>maxBytes || !Set.of("image/png","image/jpeg").contains(type==null?"":type))
            throw new IllegalArgumentException("Provide a JPEG or PNG within the generated-image size limit.");
        try(var input=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers=ImageIO.getImageReaders(input);
            if(!readers.hasNext()) throw new IllegalArgumentException("Generated image could not be read.");
            var reader=readers.next();
            try {
                reader.setInput(input,true,true);
                String actual="PNG".equalsIgnoreCase(reader.getFormatName())?"image/png":"JPEG".equalsIgnoreCase(reader.getFormatName())?"image/jpeg":"unsupported";
                int width=reader.getWidth(0),height=reader.getHeight(0);
                if(!actual.equals(type)||width<=0||height<=0||(long)width*height>40000000)
                    throw new IllegalArgumentException("Generated image format or dimensions are invalid.");
                // Decode after the pixel limit to reject truncated/corrupt image data.
                if(reader.read(0)==null) throw new IllegalArgumentException("Generated image could not be read.");
                return new int[]{width,height};
            } finally {reader.dispose();}
        } catch(IOException e) {throw new IllegalArgumentException("Generated image could not be read.");}
    }
}
