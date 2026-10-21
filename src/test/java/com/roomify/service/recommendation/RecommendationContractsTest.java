package com.roomify.service.recommendation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roomify.dto.recommendation.DIYGuide;
import com.roomify.dto.recommendation.RecommendationSet;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecommendationContractsTest {
    private final ObjectMapper mapper = new ObjectMapper();

    static String fixture(String name) throws Exception {
        return Files.readString(Path.of("contracts/recommendations/v1/examples/" + name + ".json"));
    }

    @Test void consumesSetWithDecimalPricesAndNullableAlternative() throws Exception {
        var set = mapper.readValue(fixture("recommendation-set"), RecommendationSet.class);
        assertThat(set.currency()).isEqualTo("USD");
        assertThat(set.budget()).isEqualByComparingTo("100.00");
        assertThat(set.items()).extracting(item -> item.name()).containsExactly("Desk lamp", "Storage basket");
        assertThat(set.items().getFirst().estimatedPrice()).isEqualByComparingTo("19.99");
        assertThat(set.items().getFirst().cheaperAlternative().estimatedPrice()).isEqualByComparingTo("12.50");
        assertThat(set.items().get(1).cheaperAlternative()).isNull();
        assertThatThrownBy(() -> set.items().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test void consumesOrderedDiyStepsWithoutDatabaseIdentifiers() throws Exception {
        var guide = mapper.readValue(fixture("diy-guide"), DIYGuide.class);
        assertThat(guide.steps()).containsExactly("Measure the shelf.", "Wrap the shelf with removable film.");
        assertThat(guide.estimatedCost()).isEqualByComparingTo("8.50");
        assertThat(guide.safetyNotes()).containsExactly("Work on a stable surface; keep the cutting blade away from your body.");
        assertThatThrownBy(() -> guide.steps().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
}
