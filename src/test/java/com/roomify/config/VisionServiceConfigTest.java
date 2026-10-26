package com.roomify.config;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class VisionServiceConfigTest {

    private HttpServer server;
    private final AtomicReference<String> authorization = new AtomicReference<>();

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/health", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void sendsBearerTokenWhenConfigured() {
        call(properties("secret-token"));

        assertThat(authorization.get()).isEqualTo("Bearer secret-token");
    }

    @Test
    void sendsNoAuthorizationHeaderWithoutToken() {
        call(properties(""));

        assertThat(authorization.get()).isNull();
    }

    private VisionServiceProperties properties(String token) {
        VisionServiceProperties properties = new VisionServiceProperties();
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setApiToken(token);
        return properties;
    }

    private void call(VisionServiceProperties properties) {
        new VisionServiceConfig().visionRestClient(properties)
                .get().uri("/health").retrieve().toBodilessEntity();
    }
}
