package com.tiktok.video.visual.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

public record RenderCardsResponse(
    @JsonProperty("cardPaths") Map<String, String> cardPaths,
    @JsonProperty("cardCount") int cardCount,
    @JsonProperty("success") boolean success,
    @JsonProperty("message") String message
) {
    public static RenderCardsResponse ok(Map<String, String> cardPaths) {
        return new RenderCardsResponse(cardPaths, cardPaths.size(), true, "OK");
    }

    public static RenderCardsResponse error(String message) {
        return new RenderCardsResponse(Map.of(), 0, false, message);
    }
}
