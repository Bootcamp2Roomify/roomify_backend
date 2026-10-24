package com.roomify.service.recommendation;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static com.roomify.service.recommendation.RecommendationContractsTest.fixture;
import static org.assertj.core.api.Assertions.*;

class RecommendationSchemaTest {
    private final ObjectMapper mapper = new ObjectMapper().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    private final RecommendationContractCodec codec = new RecommendationContractCodec();

    @Test void portableSchemasAcceptExamplesAndRejectMalformedExchangePayloads() throws Exception {
        for (String name : new String[]{"recommendation-set", "diy-guide"}) {
            var schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(mapper.readTree(
                    Files.readString(Path.of("contracts/recommendations/v1/" + name + ".schema.json"))));
            assertThat(schema.validate(mapper.readTree(fixture(name)))).isEmpty();
            try (var invalid = Files.list(Path.of("contracts/recommendations/v1/examples/invalid"))) {
                for (Path file : invalid.filter(path -> path.getFileName().toString().startsWith(name)).toList()) {
                    String json = Files.readString(file);
                    assertThat(schema.validate(mapper.readTree(json))).as(file.toString()).isNotEmpty();
                    assertThatThrownBy(() -> {
                        if (name.equals("recommendation-set")) codec.readSet(json); else codec.readGuide(json);
                    }).as(file.toString()).isInstanceOf(IllegalArgumentException.class);
                }
            }
        }
    }

    @Test void domainTotalsRequireBackendValidationBeyondSchema() throws Exception {
        String invalid = fixture("recommendation-set").replace("29.99", "29.98");
        var schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(mapper.readTree(
                Files.readString(Path.of("contracts/recommendations/v1/recommendation-set.schema.json"))));
        assertThat(schema.validate(mapper.readTree(invalid))).isEmpty();
        assertThatThrownBy(() -> codec.readSet(invalid)).isInstanceOf(IllegalArgumentException.class);
    }
}
