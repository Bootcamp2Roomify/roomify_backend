package com.roomify.service.recommendation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.roomify.dto.recommendation.GenerationMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static com.roomify.service.recommendation.RecommendationContractsTest.fixture;
import static org.assertj.core.api.Assertions.*;

class RecommendationContractCodecTest {
    private final RecommendationContractCodec codec = new RecommendationContractCodec();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test void roundTripsDecimalAmountsAndStructuredAlternative() throws Exception {
        var set = codec.readSet(fixture("recommendation-set"));
        var restored = codec.readSet(codec.writeSet(set));
        assertThat(restored.estimatedTotal()).isEqualByComparingTo("29.99");
        assertThat(restored.remainingBudget()).isEqualByComparingTo("70.01");
        assertThat(restored.items().getFirst().cheaperAlternative().searchQuery()).isEqualTo("basic desk lamp");
        assertThat(codec.readGuide(codec.writeGuide(codec.readGuide(fixture("diy-guide")))).steps()).hasSize(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "0.001", "10000000000", "\"100\"", "true", "null", "1e400"})
    void rejectsInvalidMoneyBeforeCoercion(String amount) throws Exception {
        String json = fixture("recommendation-set").replace("100.00", amount);
        assertThatThrownBy(() -> codec.readSet(json)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"currency", "schemaVersion", "items", "budget"})
    void rejectsAbsentRequiredSetField(String field) throws Exception {
        var json = (ObjectNode) mapper.readTree(fixture("recommendation-set"));
        json.remove(field);
        assertThatThrownBy(() -> codec.readSet(json.toString())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void rejectsUnknownFieldsDuplicateKeysAndTrailingDocuments() throws Exception {
        String json = fixture("recommendation-set");
        assertThatThrownBy(() -> codec.readSet(json.replace("\"currency\":", "\"secret\":\"hidden\",\"currency\":")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageNotContaining("hidden");
        assertThatThrownBy(() -> codec.readSet(json.replace("\"currency\":", "\"currency\":\"TWD\",\"currency\":")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> codec.readSet(json + " {}" )).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"usd", "EUR", "DOLLAR", "ZZZ"})
    void rejectsUnsupportedOrMalformedCurrency(String currency) throws Exception {
        assertThatThrownBy(() -> codec.readSet(fixture("recommendation-set").replace("USD", currency)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void rejectsWrongTotalsAndVersionsRatherThanRepairingThem() throws Exception {
        for (String json : new String[]{fixture("recommendation-set").replace("29.99", "29.98"),
                fixture("recommendation-set").replace("70.01", "70.02"),
                fixture("recommendation-set").replace("1.0", "2.0")}) {
            assertThatThrownBy(() -> codec.readSet(json)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test void rejectsAbsentAlternativeWrongAlternativeTypeAndNonCheaperPrice() throws Exception {
        ObjectNode json = (ObjectNode) mapper.readTree(fixture("recommendation-set"));
        ((ObjectNode) json.withArray("items").get(1)).remove("cheaperAlternative");
        assertThatThrownBy(() -> codec.readSet(json.toString())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> codec.readSet(fixture("recommendation-set").replace("12.50", "19.99")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> codec.readSet(fixture("recommendation-set").replace("\"cheaperAlternative\": null", "\"cheaperAlternative\": \"cheap\"")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void acceptsEmptyUnaffordableSetAndExactDecimalSum() {
        var empty = codec.readSet("""
            {"schemaVersion":"1.0","currency":"TWD","budget":0,"estimatedTotal":0,
             "remainingBudget":0,"items":[]}
            """);
        assertThat(empty.items()).isEmpty();
        var set = codec.readSet("""
            {"schemaVersion":"1.0","currency":"USD","budget":0.3000,"estimatedTotal":0.30,"remainingBudget":0,
             "items":[{"name":"A","category":"storage","reason":"organize","placement":null,"estimatedPrice":0.10,
             "cheaperAlternative":null,"searchQuery":"A","diyEligible":false},
             {"name":"B","category":"storage","reason":"organize","placement":null,"estimatedPrice":0.20,
             "cheaperAlternative":null,"searchQuery":"B","diyEligible":true}]}
            """);
        assertThat(set.estimatedTotal()).isEqualByComparingTo(new BigDecimal("0.30"));
    }

    @Test void rejectsUnsafeOrUnstructuredDiyShape() throws Exception {
        for (String replacement : new String[]{"\"steps\":\"just build it\"", "\"steps\":[]", "\"steps\":[\" \" ]"}) {
            ObjectNode json = (ObjectNode) mapper.readTree(fixture("diy-guide"));
            json.remove("steps");
            String invalid = json.toString().replaceFirst("\\{", "{" + replacement + ",");
            assertThatThrownBy(() -> codec.readGuide(invalid)).isInstanceOf(IllegalArgumentException.class);
        }
        ObjectNode missing = (ObjectNode) mapper.readTree(fixture("diy-guide"));
        missing.remove("safetyNotes");
        assertThatThrownBy(() -> codec.readGuide(missing.toString())).isInstanceOf(IllegalArgumentException.class);
        missing.putArray("safetyNotes");
        assertThatThrownBy(() -> codec.readGuide(missing.toString())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void rejectsPrimitiveCoercionAndTooManyItems() throws Exception {
        String input = fixture("recommendation-set");
        assertThatThrownBy(() -> codec.readSet(input.replace("\"diyEligible\": false", "\"diyEligible\": 1")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> codec.readSet(input.replace("\"name\": \"Desk lamp\"", "\"name\": 3")))
                .isInstanceOf(IllegalArgumentException.class);
        ObjectNode json = (ObjectNode) mapper.readTree(input);
        var items = json.withArray("items");
        while (items.size() < 7) items.add(items.get(1).deepCopy());
        assertThatThrownBy(() -> codec.readSet(json.toString())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void metadataRequestIdMayBeOmittedButRequiredFieldsRemainRequired() {
        assertThat(codec.readMetadata("{\"provider\":\"local\",\"model\":\"fixture\",\"schemaVersion\":\"1.0\"}").requestId()).isNull();
        assertThatThrownBy(() -> codec.readMetadata("{\"model\":\"fixture\",\"schemaVersion\":\"1.0\"}"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void validatesTypedPayloadAndMetadataAsWellAsJson() throws Exception {
        var valid = codec.readSet(fixture("recommendation-set"));
        var invalid = new com.roomify.dto.recommendation.RecommendationSet("1.0", "USD", valid.budget(),
                new BigDecimal("0.001"), valid.remainingBudget(), valid.items());
        assertThatThrownBy(() -> codec.writeSet(invalid)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> codec.validate(new GenerationMetadata("", "model", "1.0", null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(codec.writeMetadata(new GenerationMetadata("local", "fixture", "1.0", null)))
                .contains("\"provider\":\"local\"").doesNotContain("prompt", "headers");
    }
}
