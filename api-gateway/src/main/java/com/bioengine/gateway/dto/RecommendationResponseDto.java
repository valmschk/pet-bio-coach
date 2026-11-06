//нужен чтобы превратить данные из grpc-формата в обычный JSON

package com.bioengine.gateway.dto;

import com.fasterxml.jackson.annotation.JsonProperty;


public class RecommendationResponseDto {

    private final String userId;
    private final String recommendation;
    private final boolean fromCache;
    private final long generationTimeMs;

    public RecommendationResponseDto(
            @JsonProperty("userId") String userId,
            @JsonProperty("recommendation") String recommendation,
            @JsonProperty("fromCache") boolean fromCache,
            @JsonProperty("generationTimeMs") long generationTimeMs) {
        this.userId = userId;
        this.recommendation = recommendation;
        this.fromCache = fromCache;
        this.generationTimeMs = generationTimeMs;
    }

    public String getUserId() { return userId; }
    public String getRecommendation() { return recommendation; }
    public boolean isFromCache() { return fromCache; }
    public long getGenerationTimeMs() { return generationTimeMs; }
}
