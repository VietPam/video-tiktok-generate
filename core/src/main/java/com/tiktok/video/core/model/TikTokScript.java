package com.tiktok.video.core.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Optional;

/**
 * Top-level domain model representing a structured TikTok IELTS lesson script.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record TikTokScript(
    @JsonProperty("topic") String topic,
    @JsonProperty("targetBand") String targetBand,
    @JsonProperty("scenes") List<Scene> scenes
) {
    public TikTokScript {
        topic = (topic == null || topic.isBlank()) ? "General Vocabulary" : topic.trim();
        targetBand = (targetBand == null || targetBand.isBlank()) ? "Band 7+" : targetBand.trim();
        scenes = (scenes == null) ? List.of() : List.copyOf(scenes);
    }

    @JsonIgnore
    public List<Scene> vocabularyScenes() {
        return scenes.stream()
            .filter(s -> s.sceneType() == SceneType.VOCABULARY)
            .toList();
    }

    @JsonIgnore
    public Optional<Scene> hookScene() {
        return scenes.stream()
            .filter(Scene::isHook)
            .findFirst();
    }

    @JsonIgnore
    public Optional<Scene> ctaScene() {
        return scenes.stream()
            .filter(Scene::isCallToAction)
            .findFirst();
    }

    @JsonIgnore
    public Optional<Scene> findSceneById(String sceneId) {
        if (sceneId == null) return Optional.empty();
        return scenes.stream()
            .filter(s -> sceneId.equals(s.id()))
            .findFirst();
    }

    @JsonIgnore
    public int totalScenes() {
        return scenes.size();
    }
}
