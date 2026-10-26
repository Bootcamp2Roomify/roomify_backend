package com.roomify.ai;

import com.roomify.dto.ProjectPreferencesResponse;
import com.roomify.dto.design.BudgetAllocation;
import com.roomify.dto.design.DesignPlan;
import com.roomify.dto.design.DesignRequestSnapshot;
import com.roomify.dto.design.LayoutChange;
import com.roomify.dto.recommendation.CheaperAlternative;
import com.roomify.dto.recommendation.DIYGuide;
import com.roomify.dto.recommendation.GenerationMetadata;
import com.roomify.dto.recommendation.RecommendationItem;
import com.roomify.dto.recommendation.RecommendationSet;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Pre-written results used when AI_DEMO_MODE=true or when a live AI call fails and AI_DEMO_FALLBACK is on.
 * Text is fitted to the request (the user's own furniture decisions, budget, currency and rental flag) so it
 * passes the same validators as model output; the concept image is a bundled stock photo for the room type,
 * not an edit of the user's photo. Everything produced here is stored with provider "demo".
 */
@Component
public class DemoLibrary {

    public static final String PROVIDER = "demo";
    public static final String SOURCE_AI = "ai";
    public static final String SOURCE_DEMO = "demo";

    private static final BigDecimal PLAN_SHARE = new BigDecimal("0.85");
    private static final BigDecimal REPLACEMENT_WEIGHT = new BigDecimal("0.20");
    private static final BigDecimal LOW_CONFIDENCE = new BigDecimal("0.75");

    public enum DemoCase {
        LIVING_ROOM("living-room", "Scandinavian",
                "A brighter, calmer living room: a clear walkway from the door, one soft zone around the sofa, and warm layered light.",
                List.of("Leave about 80 cm of walkway between the door and the seating area.",
                        "Anchor the seating with one rug large enough for the front legs of the sofa and chairs.",
                        "Use two light sources at different heights instead of a single ceiling light."),
                List.of(new Addition("Textiles", "rug", "Low-pile wool-blend rug", "Defines the seating zone and softens echo", "0.35"),
                        new Addition("Lighting", "floor lamp", "Linen-shade floor lamp", "Warm evening light beside the sofa", "0.20"),
                        new Addition("Furniture", "coffee table", "Round light-wood coffee table", "Rounded edges keep the walkway easy", "0.30"),
                        new Addition("Decor", "plant", "Two easy-care potted plants", "Adds colour and life near the window", "0.15")),
                List.of(new Suggestion("Low-pile wool-blend rug", "Textiles", "Defines the seating area and softens the room.", "Under the front legs of the sofa", "0.25", true, "low pile wool blend rug 160x230"),
                        new Suggestion("Linen-shade floor lamp", "Lighting", "Warm, glare-free light for evenings.", "Beside the sofa, away from the walkway", "0.18", false, "linen shade floor lamp"),
                        new Suggestion("Round light-wood coffee table", "Furniture", "No sharp corners in a tight room.", "Centred in front of the sofa", "0.22", false, "round oak coffee table 80 cm"),
                        new Suggestion("Leaning wall frame set", "Decor", "Art without nails: frames lean on a shelf or the floor.", "On the sideboard or floor against the wall", "0.10", true, "large leaning picture frame"))),
        BEDROOM("bedroom", "Cozy minimalist",
                "A restful bedroom: the bed as the calm centre, clear surfaces, closed storage and soft, low light.",
                List.of("Keep at least 60 cm free on the side of the bed you get in from.",
                        "Move bright or busy items out of the line of sight from the pillow.",
                        "Put a reading light within reach of the bed so the ceiling light can stay off."),
                List.of(new Addition("Textiles", "bedding", "Washed-linen duvet cover set", "Softer, calmer look for the biggest surface in the room", "0.35"),
                        new Addition("Lighting", "bedside lamp", "Warm bedside lamp", "Low light for reading and winding down", "0.20"),
                        new Addition("Storage", "storage boxes", "Under-bed storage boxes", "Clears the floor and surfaces", "0.25"),
                        new Addition("Textiles", "curtains", "Blackout curtain panels on a tension rod", "Darker room for better sleep", "0.20")),
                List.of(new Suggestion("Washed-linen duvet cover set", "Textiles", "Changes the feel of the room for little money.", "On the bed", "0.25", false, "washed linen duvet cover set"),
                        new Suggestion("Warm bedside lamp", "Lighting", "Lets you read without the ceiling light.", "On the bedside table", "0.15", false, "warm bedside table lamp"),
                        new Suggestion("Under-bed storage boxes", "Storage", "Hidden storage keeps the floor clear.", "Under the bed", "0.15", true, "under bed storage box with lid"),
                        new Suggestion("Blackout curtain panels with tension rod", "Textiles", "Blocks early light without fixing anything to the wall.", "Across the window recess", "0.20", false, "blackout curtains tension rod"))),
        HOME_OFFICE("home-office", "Modern",
                "A focused home office: the desk faces the light, cables and paper have a home, and the chair supports long sessions.",
                List.of("Place the desk side-on to the window to avoid screen glare.",
                        "Keep everything used daily within arm's reach; store the rest in closed boxes.",
                        "Leave room to push the chair back fully without hitting furniture."),
                List.of(new Addition("Furniture", "desk chair", "Adjustable desk chair", "Supports long study sessions", "0.40"),
                        new Addition("Lighting", "desk lamp", "LED desk lamp with dimmer", "Even task light on the desk", "0.20"),
                        new Addition("Storage", "shelf", "Freestanding bookshelf", "Keeps books and paper off the desk", "0.25"),
                        new Addition("Organisation", "cable tray", "Clamp-on cable tray", "Hides cables without tools or wall fixings", "0.15")),
                List.of(new Suggestion("Adjustable desk chair", "Furniture", "Good support matters more than any other office item.", "At the desk", "0.30", false, "adjustable ergonomic desk chair"),
                        new Suggestion("LED desk lamp with dimmer", "Lighting", "Even light reduces eye strain.", "Left side of the desk for right-handed users", "0.12", false, "led desk lamp dimmable"),
                        new Suggestion("Freestanding bookshelf", "Storage", "Gets books and paper off the desk.", "Beside the desk", "0.20", true, "freestanding bookshelf 5 tier"),
                        new Suggestion("Clamp-on cable tray", "Organisation", "Hides cables under the desk.", "Under the back edge of the desk", "0.06", false, "clamp on under desk cable tray"))),
        STUDENT_ROOM("student-room", "Minimalist",
                "A small room that works for sleep and study: separate zones for each, light colours and storage that keeps the floor clear.",
                List.of("Separate the sleep and study zones, even if only with a rug or a shelf.",
                        "Keep the desk near the window and the bed in the darker corner.",
                        "Use vertical, freestanding storage to free floor space."),
                List.of(new Addition("Storage", "shelf", "Narrow freestanding shelf", "Divides the sleep and study zones", "0.30"),
                        new Addition("Lighting", "desk lamp", "Clamp-on desk lamp", "Task light that uses no desk space", "0.15"),
                        new Addition("Textiles", "rug", "Washable cotton rug", "Marks the study zone and warms the floor", "0.25"),
                        new Addition("Storage", "storage boxes", "Stackable fabric storage boxes", "Closed storage for small items", "0.30")),
                List.of(new Suggestion("Narrow freestanding shelf", "Storage", "Doubles as a divider between bed and desk.", "Between the bed and the desk", "0.25", true, "narrow freestanding bookshelf"),
                        new Suggestion("Clamp-on desk lamp", "Lighting", "Bright task light that saves desk space.", "Clamped to the back of the desk", "0.10", false, "clamp desk lamp led"),
                        new Suggestion("Washable cotton rug", "Textiles", "Marks the study zone and is easy to clean.", "Under the desk chair", "0.15", false, "washable cotton rug 120x170"),
                        new Suggestion("Stackable fabric storage boxes", "Storage", "Closed storage hides small clutter.", "On the shelf and under the desk", "0.12", false, "stackable fabric storage boxes")));

        private final String key;
        private final String style;
        private final String summary;
        private final List<String> layoutNotes;
        private final List<Addition> additions;
        private final List<Suggestion> suggestions;

        DemoCase(String key, String style, String summary, List<String> layoutNotes,
                List<Addition> additions, List<Suggestion> suggestions) {
            this.key = key;
            this.style = style;
            this.summary = summary;
            this.layoutNotes = layoutNotes;
            this.additions = additions;
            this.suggestions = suggestions;
        }

        public String key() {
            return key;
        }

        public String modelName() {
            return "roomify-demo-" + key;
        }
    }

    record Addition(String category, String objectClass, String item, String purpose, String weight) {
    }

    record Suggestion(String name, String category, String reason, String placement, String share,
            boolean diyEligible, String searchQuery) {
    }

    public record DemoImage(byte[] bytes, String contentType) {
    }

    /** Room purpose from the saved preferences; anything unknown gets the small student room. */
    public DemoCase caseFor(String roomPurpose) {
        if (roomPurpose == null) return DemoCase.STUDENT_ROOM;
        return switch (roomPurpose) {
            case "LIVING" -> DemoCase.LIVING_ROOM;
            case "SLEEP" -> DemoCase.BEDROOM;
            case "STUDY" -> DemoCase.HOME_OFFICE;
            default -> DemoCase.STUDENT_ROOM;
        };
    }

    public GenerationMetadata metadata(DemoCase demo) {
        return new GenerationMetadata(PROVIDER, demo.modelName(), "1.0", null);
    }

    public DesignPlan plan(DesignRequestSnapshot snapshot) {
        DemoCase demo = caseFor(snapshot.preferences().roomPurpose());
        boolean rental = snapshot.preferences().rentalFriendly();
        String style = styleName(snapshot.preferences().style(), demo);

        List<LayoutChange> actions = new ArrayList<>();
        List<DesignPlan.ItemNeeded> needed = new ArrayList<>();
        List<String> names = new ArrayList<>();
        List<String> categories = new ArrayList<>();
        List<String> purposes = new ArrayList<>();
        List<BigDecimal> weights = new ArrayList<>();
        for (var object : snapshot.objects()) {
            String label = object.label();
            String safe = safeLabel(label);
            boolean uncertain = object.confidence() == null || object.confidence().compareTo(LOW_CONFIDENCE) < 0;
            String check = uncertain ? " It was detected with lower confidence, so please verify it is really a " + label + "." : "";
            switch (object.decision()) {
                case KEEP -> actions.add(new LayoutChange(object.objectId(), label, object.decision(), LayoutChange.Type.KEEP,
                        "Keep this piece and give it a clear zone that matches the " + style.toLowerCase(Locale.ROOT) + " look.",
                        "You chose to keep it." + check));
                case REPLACE -> {
                    actions.add(new LayoutChange(object.objectId(), label, object.decision(), LayoutChange.Type.REPLACE,
                            "Replace this piece with a lighter, " + style.toLowerCase(Locale.ROOT) + " one in the same spot.",
                            "You chose to replace it; a lighter piece makes the room feel larger." + check));
                    needed.add(new DesignPlan.ItemNeeded(object.objectId(), "New " + safe, 1));
                    names.add("New " + safe);
                    categories.add("Furniture");
                    purposes.add("Replaces the current " + safe);
                    weights.add(REPLACEMENT_WEIGHT);
                }
                case REMOVE -> actions.add(new LayoutChange(object.objectId(), label, object.decision(), LayoutChange.Type.REMOVE,
                        "Remove this piece to free up floor space.",
                        "You chose to remove it." + check));
                case UNSURE -> actions.add(new LayoutChange(object.objectId(), label, object.decision(), LayoutChange.Type.UNSURE,
                        "Verify whether the " + safe + " earns its space before deciding to keep or replace it.",
                        "You were unsure, so the plan works with or without it." + check));
            }
        }
        for (Addition addition : demo.additions) {
            if (actions.size() >= 100 || names.size() >= 30) break;
            actions.add(new LayoutChange(null, addition.objectClass(), null, LayoutChange.Type.ADD,
                    "Add a " + addition.item().toLowerCase(Locale.ROOT) + ".", addition.purpose() + "."));
            needed.add(new DesignPlan.ItemNeeded(null, addition.item(), 1));
            names.add(addition.item());
            categories.add(addition.category());
            purposes.add(addition.purpose());
            weights.add(new BigDecimal(addition.weight()));
        }

        BigDecimal spend = snapshot.budget().amount().multiply(PLAN_SHARE).setScale(2, RoundingMode.DOWN);
        List<BigDecimal> costs = split(spend, weights);
        List<BudgetAllocation> allocation = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            allocation.add(new BudgetAllocation(categories.get(i), names.get(i), purposes.get(i), costs.get(i)));
        }
        String currency = snapshot.budget().currency();
        List<String> constraints = new ArrayList<>(List.of(
                "Total stays within your budget of " + snapshot.budget().amount().setScale(2, RoundingMode.UNNECESSARY) + " " + currency + "."));
        List<String> rentalNotes = new ArrayList<>();
        if (rental) {
            constraints.add("Rental-friendly: only freestanding or removable pieces.");
            rentalNotes.add("Everything here is freestanding or removable; use adhesive hooks and leaning frames instead of fixing anything to the walls.");
        }
        return new DesignPlan("1", demo.summary, style, actions, demo.layoutNotes, allocation, sum(costs), currency,
                List.of("Demo mode: this plan comes from Roomify's pre-written demo library, not a live AI model. "
                                + "It follows your furniture decisions and budget, but the wording is generic.",
                        "Prices are rough estimates; check current prices before buying."),
                needed, rentalNotes, constraints);
    }

    public DemoImage image(DemoCase demo) {
        try (InputStream in = new ClassPathResource("demo/" + demo.key() + ".jpg").getInputStream()) {
            return new DemoImage(in.readAllBytes(), "image/jpeg");
        } catch (IOException missing) {
            throw new UncheckedIOException("Demo image is missing for " + demo.key(), missing);
        }
    }

    public RecommendationSet recommendations(ProjectPreferencesResponse preferences) {
        DemoCase demo = caseFor(preferences.roomPurpose());
        BigDecimal budget = preferences.budgetAmount().setScale(2, RoundingMode.UNNECESSARY);
        List<RecommendationItem> items = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (Suggestion suggestion : demo.suggestions) {
            BigDecimal price = budget.multiply(new BigDecimal(suggestion.share())).setScale(2, RoundingMode.DOWN);
            BigDecimal cheaper = price.multiply(new BigDecimal("0.6")).setScale(2, RoundingMode.DOWN);
            CheaperAlternative alternative = cheaper.compareTo(price) < 0 && price.signum() > 0
                    ? new CheaperAlternative("Second-hand " + suggestion.name().toLowerCase(Locale.ROOT),
                            "Same function for less; check local resale listings.", "used " + suggestion.searchQuery(), cheaper)
                    : null;
            items.add(new RecommendationItem(suggestion.name(), suggestion.category(), suggestion.reason(),
                    suggestion.placement(), price, alternative, suggestion.searchQuery(), suggestion.diyEligible()));
            total = total.add(price);
        }
        return new RecommendationSet("1.0", preferences.currency(), budget, total, budget.subtract(total), items);
    }

    /** A beginner, tool-light project: refresh a cheap or second-hand base piece instead of buying new. */
    public DIYGuide guide(String itemName, String currency, BigDecimal purchasePrice) {
        BigDecimal cost = purchasePrice.multiply(new BigDecimal("0.5")).setScale(2, RoundingMode.DOWN);
        String item = itemName.toLowerCase(Locale.ROOT);
        return new DIYGuide("1.0", currency,
                List.of("A plain second-hand or unfinished " + item, "Peel-and-stick wrap or fabric in your colour", "Felt furniture pads"),
                List.of("Tape measure", "Fine sandpaper", "Craft knife", "Cleaning cloth"),
                cost, "Beginner", "1-2 hours",
                List.of("Measure the spot so the base piece you buy fits with room to walk around it.",
                        "Find a plain " + item + " second-hand or unfinished.",
                        "Clean it and lightly sand any glossy surfaces so the wrap sticks.",
                        "Apply the wrap or fabric slowly from one edge, smoothing out bubbles as you go, and trim the excess.",
                        "Add felt pads underneath and move it into place."),
                List.of("Cut away from your body and retract the blade when you put the knife down.",
                        "Sand in a ventilated space and wipe dust away with a damp cloth."));
    }

    /** Splits total by weight, rounding down, with the remainder on the last share so the sum is exact. */
    static List<BigDecimal> split(BigDecimal total, List<BigDecimal> weights) {
        List<BigDecimal> parts = new ArrayList<>();
        if (weights.isEmpty()) return parts;
        BigDecimal weightSum = weights.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal assigned = BigDecimal.ZERO;
        for (int i = 0; i < weights.size(); i++) {
            BigDecimal part = i == weights.size() - 1 ? total.subtract(assigned)
                    : total.multiply(weights.get(i)).divide(weightSum, 2, RoundingMode.DOWN);
            parts.add(part);
            assigned = assigned.add(part);
        }
        return parts;
    }

    private static BigDecimal sum(List<BigDecimal> values) {
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.UNNECESSARY);
    }

    /** User-edited labels go into validated text, so words the rental guardrail rejects are left out. */
    private static String safeLabel(String label) {
        return label.matches("(?is).*(built-?in|repaint|drill|rewir|re-wire|flooring|hardwood|demolition|keep|retain|style|store|sell|discard).*")
                ? "piece" : label;
    }

    private static String styleName(String style, DemoCase demo) {
        if (style == null || style.isBlank() || "NO_PREFERENCE".equals(style)) return demo.style;
        String words = style.replace('_', ' ').toLowerCase(Locale.ROOT);
        return Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }
}
