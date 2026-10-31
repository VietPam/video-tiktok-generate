package com.tiktok.video.core.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Enumeration representing the structural types of scenes in a TikTok video.
 */
public enum SceneType {
    @JsonProperty("HOOK")
    HOOK,

    @JsonProperty("VOCABULARY")
    VOCABULARY,

    @JsonProperty("VOCABULARY_COLLOCATION")
    VOCABULARY_COLLOCATION,

    @JsonProperty("VOCABULARY_EXAMPLE")
    VOCABULARY_EXAMPLE,

    @JsonProperty("CALL_TO_ACTION")
    CALL_TO_ACTION;

    @JsonIgnore
    public boolean isVocabulary() {
        return this == VOCABULARY || this == VOCABULARY_COLLOCATION || this == VOCABULARY_EXAMPLE;
    }

    @JsonIgnore
    public boolean isHook() {
        return this == HOOK;
    }

    @JsonIgnore
    public boolean isCallToAction() {
        return this == CALL_TO_ACTION;
    }
}
