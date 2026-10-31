package com.tiktok.video.core.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TikTokScriptSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("Should serialize and deserialize TikTokScript round-trip with Jackson")
    void shouldSerializeAndDeserializeRoundTrip() throws Exception {
        VocabularyItem vocabItem = new VocabularyItem(
                "Sibling",
                "/ˈsɪb.lɪŋ/",
                "noun",
                "a brother or sister",
                "sibling rivalry",
                "competition between brothers or sisters",
                "Siblings can have a profound impact on a child's life."
        );

        Scene hook = new Scene(
                "hook-01",
                SceneType.HOOK,
                "Welcome to IELTS Band 7+ vocabulary on Family!",
                "Welcome to IELTS Band 7+ vocabulary on Family!",
                null,
                0
        );

        Scene vocabScene = new Scene(
                "vocab-01",
                SceneType.VOCABULARY,
                "Word number one is sibling, meaning a brother or sister.",
                "Sibling /ˈsɪb.lɪŋ/: a brother or sister",
                vocabItem,
                1
        );

        Scene cta = new Scene(
                "cta-01",
                SceneType.CALL_TO_ACTION,
                "Comment your sentence below using sibling rivalry!",
                "Comment below with your sentence!",
                null,
                2
        );

        TikTokScript script = new TikTokScript("Family", "Band 7+", List.of(hook, vocabScene, cta));

        String json = objectMapper.writeValueAsString(script);
        TikTokScript deserialized = objectMapper.readValue(json, TikTokScript.class);

        assertThat(deserialized).isEqualTo(script);
        assertThat(deserialized.topic()).isEqualTo("Family");
        assertThat(deserialized.targetBand()).isEqualTo("Band 7+");
        assertThat(deserialized.scenes()).hasSize(3);
        assertThat(deserialized.vocabularyScenes()).containsExactly(vocabScene);
        assertThat(deserialized.hookScene()).contains(hook);
        assertThat(deserialized.ctaScene()).contains(cta);
    }

    @Test
    @DisplayName("Scenes list should be immutable")
    void scenesListShouldBeImmutable() {
        Scene hook = new Scene("h", SceneType.HOOK, "text", "sub", null, 0);
        TikTokScript script = new TikTokScript("Topic", "7+", List.of(hook));

        assertThatThrownBy(() -> script.scenes().add(hook))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
