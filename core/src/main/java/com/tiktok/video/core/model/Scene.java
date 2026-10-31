package com.tiktok.video.core.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;
import java.util.Optional;

/**
 * Represents a single scene in the TikTok video timeline, containing spoken audio text,
 * on-screen subtitle text, and optional vocabulary item payload.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record Scene(
    @JsonProperty("id") String id,
    @JsonProperty("sceneType") SceneType sceneType,
    @JsonProperty("spokenText") String spokenText,
    @JsonProperty("onScreenSubtitle") String onScreenSubtitle,
    @JsonProperty("vocabularyItem") VocabularyItem vocabularyItem,
    @JsonProperty("sequenceIndex") int sequenceIndex
) {
    public Scene {
        Objects.requireNonNull(id, "id cannot be null");
        if (id.isBlank()) {
            throw new IllegalArgumentException("id cannot be blank");
        }
        Objects.requireNonNull(sceneType, "sceneType cannot be null");
        Objects.requireNonNull(spokenText, "spokenText cannot be null");
        if (spokenText.isBlank()) {
            throw new IllegalArgumentException("spokenText cannot be blank");
        }
        id = id.trim();
        spokenText = spokenText.trim();
        onScreenSubtitle = (onScreenSubtitle == null || onScreenSubtitle.isBlank()) ? spokenText : onScreenSubtitle.trim();
        if (sequenceIndex < 0) {
            throw new IllegalArgumentException("sequenceIndex cannot be negative: " + sequenceIndex);
        }
    }

    @JsonIgnore
    public boolean isVocabulary() {
        return sceneType == SceneType.VOCABULARY;
    }

    @JsonIgnore
    public boolean isHook() {
        return sceneType == SceneType.HOOK;
    }

    @JsonIgnore
    public boolean isCallToAction() {
        return sceneType == SceneType.CALL_TO_ACTION;
    }

    @JsonIgnore
    public Optional<VocabularyItem> optVocabularyItem() {
        return Optional.ofNullable(vocabularyItem);
    }

    public static Scene hook(String id, int sequenceIndex, String spokenText, String onScreenSubtitle) {
        return new Scene(id, SceneType.HOOK, spokenText, onScreenSubtitle, null, sequenceIndex);
    }

    public static Scene vocabulary(String id, int sequenceIndex, String spokenText, String onScreenSubtitle, VocabularyItem item) {
        Objects.requireNonNull(item, "vocabularyItem cannot be null for VOCABULARY scene");
        return new Scene(id, SceneType.VOCABULARY, spokenText, onScreenSubtitle, item, sequenceIndex);
    }

    public static Scene vocabularyCollocation(String id, int sequenceIndex, String spokenText, String onScreenSubtitle, VocabularyItem item) {
        Objects.requireNonNull(item, "vocabularyItem cannot be null for VOCABULARY scene");
        return new Scene(id, SceneType.VOCABULARY_COLLOCATION, spokenText, onScreenSubtitle, item, sequenceIndex);
    }

    public static Scene vocabularyExample(String id, int sequenceIndex, String spokenText, String onScreenSubtitle, VocabularyItem item) {
        Objects.requireNonNull(item, "vocabularyItem cannot be null for VOCABULARY scene");
        return new Scene(id, SceneType.VOCABULARY_EXAMPLE, spokenText, onScreenSubtitle, item, sequenceIndex);
    }

    public static Scene callToAction(String id, int sequenceIndex, String spokenText, String onScreenSubtitle) {
        return new Scene(id, SceneType.CALL_TO_ACTION, spokenText, onScreenSubtitle, null, sequenceIndex);
    }
}
