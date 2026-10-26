package com.roomify.storage;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.io.ByteArrayInputStream;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Own context (different storage bean): keep its pool small and close it afterwards.
@SpringBootTest(properties = {"storage.type=database", "spring.datasource.hikari.maximum-pool-size=2"})
@DirtiesContext
class DatabaseStorageAdapterTest {

    @Autowired StorageService storage;

    @Test
    void storesReplacesReadsAndDeletesObjectBytes() {
        assertThat(storage).isInstanceOf(DatabaseStorageAdapter.class);
        String key = "rooms/" + UUID.randomUUID() + "/original.png";

        var stored = storage.upload(key, "image/png", 3, new ByteArrayInputStream(new byte[]{1, 2, 3}));
        assertThat(stored.bucket()).isEqualTo("database");
        assertThat(stored.key()).isEqualTo(key);
        assertThat(storage.download(key)).containsExactly(1, 2, 3);

        storage.upload(key, "image/png", 2, new ByteArrayInputStream(new byte[]{9, 8}));
        assertThat(storage.download(key)).containsExactly(9, 8);

        storage.delete(key);
        assertThatThrownBy(() -> storage.download(key)).isInstanceOf(IllegalStateException.class);
    }
}
