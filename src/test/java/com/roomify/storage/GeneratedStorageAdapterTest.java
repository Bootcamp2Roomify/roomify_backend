package com.roomify.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.amazon.awssdk.core.*;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GeneratedStorageAdapterTest {
    @TempDir Path root;
    @Test void localGeneratedObjectCanBeRetrievedLater() {
        var adapter=new LocalStorageAdapter(root.toString(),"local");String key="rooms/project/generated/12/example.png";
        byte[] bytes={1,2,3};adapter.upload(key,"image/png",bytes.length,new ByteArrayInputStream(bytes));
        assertThat(new LocalStorageAdapter(root.toString(),"local").download(key)).containsExactly(bytes);
        adapter.delete(key);
    }
    @Test void s3UsesConfiguredBucketKeyAndContentMetadata() {
        var client=mock(S3Client.class);var adapter=new S3StorageAdapter(client,"configured-bucket");
        String key="rooms/project/generated/12/example.png";byte[] bytes={1,2,3};
        var stored=adapter.upload(key,"image/png",3,new ByteArrayInputStream(bytes));
        var request=org.mockito.ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(client).putObject(request.capture(),any(RequestBody.class));
        assertThat(request.getValue().bucket()).isEqualTo("configured-bucket");assertThat(request.getValue().key()).isEqualTo(key);
        assertThat(request.getValue().contentType()).isEqualTo("image/png");assertThat(request.getValue().contentLength()).isEqualTo(3L);
        when(client.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(ResponseBytes.fromByteArray(GetObjectResponse.builder().build(),bytes));
        assertThat(adapter.download(stored.key())).containsExactly(bytes);
    }
}
