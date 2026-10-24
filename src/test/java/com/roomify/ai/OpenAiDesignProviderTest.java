package com.roomify.ai;

import com.roomify.config.AiProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Request shape must work for both classic chat models and reasoning models. */
class OpenAiDesignProviderTest {

    private static final String OK = """
            {"choices":[{"message":{"role":"assistant","content":"{\\"ok\\":true}"}}],
             "usage":{"prompt_tokens":120,"completion_tokens":40,"completion_tokens_details":{"reasoning_tokens":16}}}
            """;

    private record Fixture(OpenAiDesignProvider provider, MockRestServiceServer server) {
    }

    private Fixture fixture(AiProperties properties) {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.test/v1");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        return new Fixture(new OpenAiDesignProvider(builder.build(), properties), server);
    }

    private AiProperties properties(String model) {
        var properties = new AiProperties();
        properties.setApiKey("test-key");
        properties.setModel(model);
        properties.setMaxOutputTokens(4000);
        properties.setMaxRetries(0);
        return properties;
    }

    @Test
    void classicModelSendsTemperatureAndMaxCompletionTokens() {
        var fixture = fixture(properties("gpt-4o-mini"));
        fixture.server().expect(requestTo("https://api.test/v1/chat/completions"))
                .andExpect(jsonPath("$.model").value("gpt-4o-mini"))
                .andExpect(jsonPath("$.max_completion_tokens").value(4000))
                .andExpect(jsonPath("$.max_tokens").doesNotExist())
                .andExpect(jsonPath("$.temperature").value(0.1))
                .andExpect(jsonPath("$.reasoning_effort").doesNotExist())
                .andExpect(jsonPath("$.response_format.type").value("json_object"))
                .andRespond(withSuccess(OK, MediaType.APPLICATION_JSON));
        assertThat(fixture.provider().generateJson("system", "user")).isEqualTo("{\"ok\":true}");
        fixture.server().verify();
    }

    @Test
    void reasoningModelOmitsTemperatureAndSendsEffort() {
        var properties = properties("gpt-6-luna");
        properties.setTemperature(null);
        properties.setReasoningEffort("low");
        var fixture = fixture(properties);
        fixture.server().expect(requestTo("https://api.test/v1/chat/completions"))
                .andExpect(jsonPath("$.temperature").doesNotExist())
                .andExpect(jsonPath("$.reasoning_effort").value("low"))
                .andRespond(withSuccess(OK, MediaType.APPLICATION_JSON));
        fixture.provider().generateJson("system", "user");
        fixture.server().verify();
    }

    @Test
    void blankReasoningEffortIsNotSent() {
        var properties = properties("gpt-4o-mini");
        properties.setReasoningEffort(" ");
        assertThat(properties.getReasoningEffort()).isNull();
    }

    @Test
    void unsupportedParameterIsNotRetriedAndFailsClearly() {
        var properties = properties("gpt-x");
        properties.setMaxRetries(2);
        var fixture = fixture(properties);
        fixture.server().expect(requestTo("https://api.test/v1/chat/completions"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"type\":\"invalid_request_error\",\"code\":\"unsupported_parameter\",\"param\":\"temperature\",\"message\":\"x\"}}"));
        assertThatThrownBy(() -> fixture.provider().generateJson("system", "user"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("400");
        fixture.server().verify();
    }

    @Test
    void rejectedKeyIsAConfigurationErrorAndIsNotRetried() {
        var properties = properties("gpt-4o-mini");
        properties.setMaxRetries(2);
        var fixture = fixture(properties);
        fixture.server().expect(requestTo("https://api.test/v1/chat/completions"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"type\":\"invalid_request_error\",\"code\":\"invalid_api_key\"}}"));
        assertThatThrownBy(() -> fixture.provider().generateJson("system", "user"))
                .isInstanceOf(AiProviderConfigurationException.class);
        fixture.server().verify();
    }

    @Test
    void placeholderKeyFailsBeforeAnyNetworkCall() {
        var properties = properties("gpt-4o-mini");
        properties.setApiKey("your_openai_api_key");
        var fixture = fixture(properties);
        assertThatThrownBy(() -> fixture.provider().generateJson("system", "user"))
                .isInstanceOf(AiProviderConfigurationException.class);
        fixture.server().verify();
    }

    @Test
    void describesOpenAiErrorsWithoutTheirMessage() {
        assertThat(OpenAiErrors.describe("{\"error\":{\"type\":\"invalid_request_error\",\"code\":\"unsupported_parameter\",\"param\":\"max_tokens\",\"message\":\"secret prompt text\"}}"))
                .isEqualTo("type=invalid_request_error code=unsupported_parameter param=max_tokens")
                .doesNotContain("secret");
        assertThat(OpenAiErrors.describe("not json")).isEqualTo("unparseable error body");
    }
}
