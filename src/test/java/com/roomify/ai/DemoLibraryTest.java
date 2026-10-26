package com.roomify.ai;

import com.roomify.dto.ProjectPreferencesResponse;
import com.roomify.dto.design.DesignRequestSnapshot;
import com.roomify.dto.design.DesignRequestSnapshot.*;
import com.roomify.entity.FurnitureDecisionType;
import com.roomify.service.DesignPlanValidator;
import com.roomify.service.recommendation.RecommendationContractCodec;
import jakarta.validation.Validation;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** The demo library must pass the same guardrails as model output for any budget, decision mix and rental flag. */
class DemoLibraryTest {

    private final DemoLibrary demo = new DemoLibrary();
    private final DesignPlanValidator validator =
            new DesignPlanValidator(Validation.buildDefaultValidatorFactory().getValidator());
    private final RecommendationContractCodec codec = new RecommendationContractCodec();

    @ParameterizedTest
    @CsvSource({"LIVING,living-room", "SLEEP,bedroom", "STUDY,home-office", "STUDY_AND_SLEEP,student-room",
            "MULTIPURPOSE,student-room", ",student-room"})
    void roomPurposePicksTheMatchingCase(String purpose, String key) throws Exception {
        var demoCase = demo.caseFor(purpose);
        assertThat(demoCase.key()).isEqualTo(key);
        var image = demo.image(demoCase);
        assertThat(image.contentType()).isEqualTo("image/jpeg");
        assertThat(ImageIO.read(new ByteArrayInputStream(image.bytes())).getWidth()).isEqualTo(1024);
    }

    @ParameterizedTest
    @CsvSource({"LIVING,300,USD,false", "SLEEP,150.55,USD,true", "STUDY,9000,TWD,false", "STUDY_AND_SLEEP,0,USD,true",
            "MULTIPURPOSE,1,TWD,true", "LIVING,600,USD,true"})
    void planFitsTheBudgetDecisionsAndRentalRules(String purpose, String budget, String currency, boolean rental) {
        var snapshot = snapshot(purpose, new BigDecimal(budget), currency, rental, List.of(
                furniture("sofa", "0.92", FurnitureDecisionType.KEEP),
                furniture("built-in wardrobe", "0.40", FurnitureDecisionType.KEEP),
                furniture("chair", "0.81", FurnitureDecisionType.REPLACE),
                furniture("storage box", "0.66", FurnitureDecisionType.REMOVE),
                furniture("tv", null, FurnitureDecisionType.UNSURE)));

        var plan = demo.plan(snapshot);

        validator.validate(plan, snapshot);
        assertThat(plan.estimatedTotalCost()).isLessThanOrEqualTo(new BigDecimal(budget));
        assertThat(plan.currency()).isEqualTo(currency);
        assertThat(plan.warnings().getFirst()).startsWith("Demo mode:");
    }

    @Test
    void planWithNoFurnitureStillValidates() {
        var snapshot = snapshot("LIVING", new BigDecimal("250"), "USD", false, List.of());
        validator.validate(demo.plan(snapshot), snapshot);
    }

    @ParameterizedTest
    @CsvSource({"LIVING,300,false", "SLEEP,99.99,true", "STUDY,0,false", "MULTIPURPOSE,12000,true"})
    void suggestionsAndDiyGuidesMeetTheContract(String purpose, String budget, boolean rental) {
        var preferences = new ProjectPreferencesResponse(UUID.randomUUID(), "MODERN", new BigDecimal(budget), "USD",
                List.of(), purpose, rental, null, new BigDecimal("1000000"));

        var set = demo.recommendations(preferences);

        codec.validate(set);
        assertThat(set.items()).hasSizeBetween(3, 6);
        assertThat(set.estimatedTotal()).isLessThanOrEqualTo(set.budget());
        assertThat(set.items()).anyMatch(item -> Boolean.TRUE.equals(item.diyEligible()));
        for (var item : set.items()) {
            var guide = demo.guide(item.name(), set.currency(), item.estimatedPrice());
            codec.validate(guide);
            assertThat(guide.estimatedCost()).isLessThanOrEqualTo(item.estimatedPrice());
            assertThat(guide.difficulty()).isEqualTo("Beginner");
        }
        assertThat(demo.metadata(demo.caseFor(purpose)).provider()).isEqualTo("demo");
    }

    @Test
    void splitIsExactAndNeverExceedsTheTotal() {
        var parts = DemoLibrary.split(new BigDecimal("100.01"),
                List.of(new BigDecimal("0.35"), new BigDecimal("0.2"), new BigDecimal("0.3")));
        assertThat(parts.stream().reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("100.01");
        assertThat(parts).allMatch(part -> part.signum() >= 0 && part.scale() == 2);
    }

    private static DesignRequestSnapshot snapshot(String purpose, BigDecimal budget, String currency, boolean rental,
            List<Furniture> objects) {
        return new DesignRequestSnapshot("1", UUID.randomUUID(),
                new ImageReference(1L, "bucket", "rooms/x.jpg", "image/jpeg", 800, 600), objects,
                new Preferences("NO_PREFERENCE", List.of(), purpose, rental, null), new Budget(budget, currency));
    }

    private static Furniture furniture(String label, String confidence, FurnitureDecisionType decision) {
        return new Furniture(UUID.randomUUID(), label, confidence == null ? null : new BigDecimal(confidence),
                new Bounds(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ONE), decision);
    }
}
