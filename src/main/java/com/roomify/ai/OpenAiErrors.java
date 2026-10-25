package com.roomify.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Extracts OpenAI's error type/code/param for logs. Messages are not logged: they can quote input. */
final class OpenAiErrors {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private OpenAiErrors() {
    }

    static String describe(String body) {
        try {
            JsonNode error = MAPPER.readTree(body == null ? "" : body).path("error");
            return "type=" + error.path("type").asText("-") + " code=" + error.path("code").asText("-")
                    + " param=" + error.path("param").asText("-");
        } catch (Exception unreadable) {
            return "unparseable error body";
        }
    }

    /** OpenAI answers 429 both for rate limits (retryable) and for an account with no credit left (not). */
    static boolean isQuotaExhausted(String body) {
        try {
            JsonNode error = MAPPER.readTree(body == null ? "" : body).path("error");
            String type = error.path("type").asText("");
            String code = error.path("code").asText("");
            return "insufficient_quota".equals(type) || "insufficient_quota".equals(code)
                    || "credit_balance_exhausted".equals(code);
        } catch (Exception unreadable) {
            return false;
        }
    }
}
