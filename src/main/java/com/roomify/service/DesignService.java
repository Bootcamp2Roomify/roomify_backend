package com.roomify.service;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.cfg.*;
import com.fasterxml.jackson.databind.type.LogicalType;
import com.roomify.ai.*;
import com.roomify.config.AiProperties;
import com.roomify.dto.design.*;
import com.roomify.exception.*;
import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
public class DesignService {
    private final DesignPersistenceService persistence;
    private final DesignQueryService queries;
    private final AiDesignProvider provider;
    private final AiProperties properties;
    private final DesignPromptFactory prompts;
    private final DesignPlanValidator validator;
    private final ObjectMapper mapper;
    public DesignService(DesignPersistenceService persistence,DesignQueryService queries,
            AiDesignProvider provider,AiProperties properties,DesignPromptFactory prompts,
            DesignPlanValidator validator,ObjectMapper mapper) {
        this.persistence=persistence;this.queries=queries;this.provider=provider;this.properties=properties;
        this.prompts=prompts;this.validator=validator;
        this.mapper=mapper.copy().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT);
        for(var type:new LogicalType[]{LogicalType.Integer,LogicalType.Float})
            this.mapper.coercionConfigFor(type).setCoercion(CoercionInputShape.String,CoercionAction.Fail);
        for(var shape:new CoercionInputShape[]{CoercionInputShape.Integer,CoercionInputShape.Float,CoercionInputShape.Boolean})
            this.mapper.coercionConfigFor(LogicalType.Textual).setCoercion(shape,CoercionAction.Fail);
    }
    // No database transaction remains open while waiting for the external provider.
    public StoredDesignResponse generate(UUID projectId) {
        var started=persistence.begin(projectId,properties.getProvider(),provider.getModelName());
        try {
            DesignPlan plan=null;
            String retryReason=null;
            for(int attempt=0;attempt<2;attempt++) {
                String raw=provider.generateJson(prompts.systemPrompt(),prompts.userPrompt(started.snapshot(),retryReason));
                try {
                    if(raw==null || raw.length()>100000) throw new IllegalArgumentException("Output was empty or too large.");
                    plan=mapper.readValue(raw,DesignPlan.class);validator.validate(plan,started.snapshot());break;
                } catch(IllegalArgumentException invalid) {plan=null;retryReason=invalid.getMessage();
                } catch(Exception unparseable) {plan=null;retryReason="Output was not valid JSON for the schema.";}
            }
            if(plan==null) throw new DesignFailureException("INVALID_DESIGN_PLAN",started.designId());
            persistence.succeed(started,plan);
        } catch(RuntimeException error) {
            String code=error instanceof DesignFailureException?"INVALID_DESIGN_PLAN"
                :error instanceof AiProviderConfigurationException?"AI_CONFIGURATION_ERROR"
                :error instanceof AiProviderQuotaExhaustedException?"AI_QUOTA_EXHAUSTED"
                :error instanceof AiProviderRetryableException?"AI_PROVIDER_UNAVAILABLE"
                :error instanceof InvalidStateException?"DESIGN_INPUT_CHANGED":"DESIGN_GENERATION_FAILED";
            persistence.fail(started,code);
            throw new DesignFailureException(code,started.designId());
        }
        return queries.get(projectId,started.designId());
    }
}
