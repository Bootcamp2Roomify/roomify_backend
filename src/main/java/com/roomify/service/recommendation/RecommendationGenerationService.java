package com.roomify.service.recommendation;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.roomify.ai.AiDesignProvider;
import com.roomify.ai.AiProviderConfigurationException;
import com.roomify.ai.AiProviderQuotaExhaustedException;
import com.roomify.ai.AiProviderRetryableException;
import com.roomify.config.AiProperties;
import com.roomify.dto.ProjectPreferencesResponse;
import com.roomify.dto.design.StoredDesignResponse;
import com.roomify.dto.recommendation.*;
import com.roomify.exception.InvalidStateException;
import com.roomify.exception.RecommendationFailureException;
import com.roomify.service.DesignQueryService;
import com.roomify.service.ProjectPreferencesService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * ROOM-99 shopping recommendations and ROOM-100 DIY guides, generated from the latest saved design.
 * The backend computes all totals; the model only proposes items. No transaction spans a provider call.
 */
@Service
public class RecommendationGenerationService {

    static final int ATTEMPTS = 2;
    private static final List<String> HIGH_RISK_TERMS = List.of("rewire", "wiring", "hardwire", "hard-wire",
            "circuit breaker", "electrical panel", "junction box", "gas line", "gas pipe", "plumbing",
            "load-bearing", "structural", "demolish", "asbestos", "lead paint", "chainsaw");
    private static final List<String> RENTAL_DAMAGE_TERMS = List.of("drill into the wall", "drill holes in the wall",
            "drill into walls", "nail into the wall", "paint the wall", "repaint the wall", "paint the walls",
            "built-in", "permanent flooring");

    private final AiDesignProvider provider;
    private final AiProperties properties;
    private final RecommendationPromptFactory prompts;
    private final RecommendationContractCodec codec;
    private final RecommendationResultService results;
    private final DesignQueryService designs;
    private final ProjectPreferencesService preferences;
    private final ObjectMapper mapper;

    public RecommendationGenerationService(AiDesignProvider provider, AiProperties properties,
            RecommendationPromptFactory prompts, RecommendationContractCodec codec, RecommendationResultService results,
            DesignQueryService designs, ProjectPreferencesService preferences, ObjectMapper mapper) {
        this.provider = provider;
        this.properties = properties;
        this.prompts = prompts;
        this.codec = codec;
        this.results = results;
        this.designs = designs;
        this.preferences = preferences;
        this.mapper = mapper.copy().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }

    public StoredRecommendationSet generateSet(UUID projectId) {
        StoredDesignResponse design = designs.latest(projectId);
        if (design == null || !"SUCCEEDED".equals(design.status()) || design.plan() == null) {
            throw new InvalidStateException("Generate a redesign plan before recommendations.");
        }
        ProjectPreferencesResponse saved = preferences.find(projectId);
        if (saved == null) throw new InvalidStateException("Save style and budget preferences first.");

        String retryReason = null;
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            String raw = call(prompts.recommendationSystemPrompt(),
                    prompts.recommendationUserPrompt(design.plan(), saved, retryReason));
            try {
                RecommendationSet set = toSet(raw, saved);
                return results.storeSet(projectId, design.designId(), set, metadata());
            } catch (IllegalArgumentException invalid) {
                retryReason = invalid.getMessage();
            }
        }
        throw new RecommendationFailureException("INVALID_RECOMMENDATIONS");
    }

    public StoredDIYGuide generateGuide(UUID projectId, UUID itemId) {
        StoredRecommendationSet set = results.latest(projectId);
        StoredRecommendationItem item = set.items().stream().filter(candidate -> candidate.itemId().equals(itemId))
                .findFirst().orElseThrow(() -> new NoSuchElementException("Recommendation item not found."));
        if (!Boolean.TRUE.equals(item.diyEligible())) {
            throw new InvalidStateException("This item has no beginner-friendly DIY option.");
        }
        try {
            return results.latestGuide(projectId, itemId);
        } catch (NoSuchElementException none) {
            // No saved guide yet; generate one below.
        }
        ProjectPreferencesResponse saved = preferences.find(projectId);
        boolean rental = saved != null && saved.rentalFriendly();
        var asPayload = new RecommendationItem(item.name(), item.category(), item.reason(), item.placement(),
                item.estimatedPrice(), item.cheaperAlternative(), item.searchQuery(), item.diyEligible());

        String retryReason = null;
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            String raw = call(prompts.diySystemPrompt(), prompts.diyUserPrompt(asPayload, set.currency(), rental, retryReason));
            try {
                DIYGuide guide = toGuide(raw, set.currency(), item.estimatedPrice(), rental);
                return results.storeGuide(projectId, itemId, guide, metadata());
            } catch (IllegalArgumentException invalid) {
                retryReason = invalid.getMessage();
            }
        }
        throw new RecommendationFailureException("INVALID_DIY_GUIDE");
    }

    private String call(String system, String user) {
        try {
            String raw = provider.generateJson(system, user);
            if (raw == null || raw.length() > 100_000) throw new RecommendationFailureException("INVALID_RECOMMENDATIONS");
            return raw;
        } catch (AiProviderConfigurationException misconfigured) {
            throw new RecommendationFailureException("AI_CONFIGURATION_ERROR");
        } catch (AiProviderQuotaExhaustedException exhausted) {
            throw new RecommendationFailureException("AI_QUOTA_EXHAUSTED");
        } catch (AiProviderRetryableException unavailable) {
            throw new RecommendationFailureException("AI_PROVIDER_UNAVAILABLE");
        }
    }

    RecommendationSet toSet(String raw, ProjectPreferencesResponse saved) {
        GeneratedItems generated = read(raw, GeneratedItems.class);
        if (generated.items() == null || generated.items().size() < 3 || generated.items().size() > 6) {
            throw new IllegalArgumentException("Return between 3 and 6 recommended items."
            );
        }
        List<RecommendationItem> items = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (RecommendationItem item : generated.items()) {
            if (item == null || item.estimatedPrice() == null) throw new IllegalArgumentException("Every item needs an estimatedPrice.");
            CheaperAlternative alternative = item.cheaperAlternative() == null ? null : new CheaperAlternative(
                    item.cheaperAlternative().name(), item.cheaperAlternative().reason(),
                    item.cheaperAlternative().searchQuery(), money(item.cheaperAlternative().estimatedPrice()));
            var normalized = new RecommendationItem(item.name(), item.category(), item.reason(), item.placement(),
                    money(item.estimatedPrice()), alternative, item.searchQuery(), item.diyEligible());
            if (alternative != null && alternative.estimatedPrice().compareTo(normalized.estimatedPrice()) >= 0) {
                throw new IllegalArgumentException("Each cheaperAlternative must cost less than its item.");
            }
            if (saved.rentalFriendly() && containsAny(normalized.name() + " " + normalized.reason(), RENTAL_DAMAGE_TERMS)) {
                throw new IllegalArgumentException("Rental-friendly recommendations must be removable and damage-free.");
            }
            items.add(normalized);
            total = total.add(normalized.estimatedPrice());
        }
        BigDecimal budget = saved.budgetAmount().setScale(2, RoundingMode.UNNECESSARY);
        if (total.compareTo(budget) > 0) {
            throw new IllegalArgumentException("The items total " + total + " " + saved.currency()
                    + " which exceeds the budget of " + budget + "; drop or swap items to fit.");
        }
        var set = new RecommendationSet("1.0", saved.currency(), budget, total, budget.subtract(total), items);
        try {
            codec.validate(set);
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("Every item needs nonblank name, category, reason and searchQuery and a boolean diyEligible.");
        }
        return set;
    }

    DIYGuide toGuide(String raw, String currency, BigDecimal purchasePrice, boolean rental) {
        DIYGuide parsed = read(raw, DIYGuide.class);
        if (parsed.estimatedCost() == null) throw new IllegalArgumentException("estimatedCost is required.");
        var guide = new DIYGuide("1.0", currency, parsed.materials(), parsed.tools(), money(parsed.estimatedCost()),
                parsed.difficulty(), parsed.estimatedTime(), parsed.steps(), parsed.safetyNotes());
        try {
            codec.validate(guide);
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("Provide materials, tools, nonempty steps and safetyNotes, difficulty and estimatedTime.");
        }
        String difficulty = guide.difficulty().toLowerCase(Locale.ROOT);
        if (!difficulty.startsWith("beginner") && !difficulty.startsWith("easy")) {
            throw new IllegalArgumentException("Only Beginner or Easy projects are allowed.");
        }
        if (guide.estimatedCost().compareTo(purchasePrice) > 0) {
            throw new IllegalArgumentException("The DIY cost must not exceed the purchase price of " + purchasePrice + ".");
        }
        String text = String.join(" ", guide.steps()) + " " + String.join(" ", guide.materials()) + " "
                + String.join(" ", guide.tools());
        if (containsAny(text, HIGH_RISK_TERMS)) {
            throw new IllegalArgumentException("Remove electrical, plumbing, gas or structural work.");
        }
        if (rental && containsAny(text, RENTAL_DAMAGE_TERMS)) {
            throw new IllegalArgumentException("Rental-friendly projects cannot drill, nail or paint walls.");
        }
        return guide;
    }

    private <T> T read(String raw, Class<T> type) {
        try {
            return mapper.readValue(raw, type);
        } catch (Exception unreadable) {
            // Parser messages can echo model output, so they are never passed on.
            throw new IllegalArgumentException("Output was not valid JSON for the requested shape.");
        }
    }

    private static BigDecimal money(BigDecimal value) {
        if (value == null || value.signum() < 0) throw new IllegalArgumentException("Prices must be zero or more.");
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static boolean containsAny(String text, List<String> terms) {
        String normalized = text.toLowerCase(Locale.ROOT);
        return terms.stream().anyMatch(normalized::contains);
    }

    private GenerationMetadata metadata() {
        return new GenerationMetadata(properties.getProvider(), provider.getModelName(), "1.0", null);
    }

    record GeneratedItems(List<RecommendationItem> items) {
    }
}
