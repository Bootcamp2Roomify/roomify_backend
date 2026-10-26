package com.roomify.service;

import com.roomify.storage.*;
import com.roomify.exception.DesignFailureException;
import com.roomify.dto.design.StoredDesignResponse;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.assertj.core.api.Assertions.*;

class GeneratedImageConsistencyTest {
    @Test void databaseCompletionFailureDeletesObjectAndMarksAssetFailed() throws Exception {
        var storage=mock(StorageService.class);var persistence=mock(DesignAssetPersistence.class);var queries=mock(DesignQueryService.class);
        UUID project=UUID.randomUUID();Long id=7L;
        when(persistence.claim(project,id)).thenReturn(true);
        when(storage.upload(anyString(),anyString(),anyLong(),any())).thenAnswer(c->new StoredObject("bucket",c.getArgument(0)));
        doThrow(new IllegalStateException("database unavailable")).when(persistence).complete(eq(id),any(),anyString(),anyLong(),anyInt(),anyInt(),anyString());
        var service=new GeneratedDesignImageService(storage,persistence,queries,null,10000);
        var out=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(1,1,BufferedImage.TYPE_INT_RGB),"png",out);
        assertThatThrownBy(()->service.store(project,id,"image/png",out.toByteArray())).isInstanceOf(DesignFailureException.class);
        verify(storage).delete(startsWith("rooms/"+project+"/generated/7/"));verify(persistence).fail(id);
    }
    @Test void cleanupFailureStillRecordsFailureWithoutLeakingProviderDetails() throws Exception {
        var storage=mock(StorageService.class);var persistence=mock(DesignAssetPersistence.class);var queries=mock(DesignQueryService.class);
        when(persistence.claim(any(),anyLong())).thenReturn(true);
        when(storage.upload(anyString(),anyString(),anyLong(),any())).thenThrow(new IllegalStateException("SECRET"));
        doThrow(new IllegalStateException("SECRET")).when(storage).delete(anyString());
        var out=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(1,1,BufferedImage.TYPE_INT_RGB),"png",out);
        var service=new GeneratedDesignImageService(storage,persistence,queries,null,10000);
        assertThatThrownBy(()->service.store(UUID.randomUUID(),7L,"image/png",out.toByteArray()))
            .isInstanceOf(DesignFailureException.class).hasMessageNotContaining("SECRET");
        verify(persistence).fail(7L);
    }
}
