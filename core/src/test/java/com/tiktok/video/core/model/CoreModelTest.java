package com.tiktok.video.core.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Core Domain Models & Records Tests")
class CoreModelTest {

    private final ObjectMapper mapper = new ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT);

    @Test
    @DisplayName("SceneType predicate methods and Jackson round-trip")
    void testSceneType() throws Exception {
        assertTrue(SceneType.HOOK.isHook());
        assertFalse(SceneType.HOOK.isVocabulary());
        assertFalse(SceneType.HOOK.isCallToAction());

        assertTrue(SceneType.VOCABULARY.isVocabulary());
        assertFalse(SceneType.VOCABULARY.isHook());

        assertTrue(SceneType.CALL_TO_ACTION.isCallToAction());

        String json = mapper.writeValueAsString(SceneType.VOCABULARY);
        assertTrue(json.contains("VOCABULARY"));
        SceneType deserialized = mapper.readValue(json, SceneType.class);
        assertEquals(SceneType.VOCABULARY, deserialized);
    }

    @Test
    @DisplayName("VocabularyItem builder, displayTitle, and validation")
    void testVocabularyItem() throws Exception {
        VocabularyItem item = VocabularyItem.builder()
            .word("Sibling")
            .ipa("/ˈsɪb.lɪŋ/")
            .partOfSpeech("noun")
            .definition("a brother or sister")
            .collocation("sibling rivalry")
            .collocationMeaning("competition between brothers and sisters")
            .exampleSentence("Siblings can have a profound impact on a child's life.")
            .build();

        assertEquals("Sibling", item.word());
        assertEquals("/ˈsɪb.lɪŋ/", item.ipa());
        assertEquals("Sibling /ˈsɪb.lɪŋ/", item.displayTitle());

        // Blank word throws exception
        assertThrows(IllegalArgumentException.class, () ->
            new VocabularyItem("", "", "", "", "", "", ""));

        // Serialization round-trip
        String json = mapper.writeValueAsString(item);
        VocabularyItem deserialized = mapper.readValue(json, VocabularyItem.class);
        assertEquals(item, deserialized);
    }

    @Test
    @DisplayName("Scene factories, optional vocabulary item, and validation")
    void testScene() throws Exception {
        VocabularyItem item = new VocabularyItem(
            "Relative", "/ˈrel.ə.tɪv/", "noun",
            "a member of your family", "distant relative",
            "not closely related", "I used to spend time with my relatives."
        );

        Scene vocabScene = Scene.vocabulary("scene-1", 1, "Spoken text", "Subtitle text", item);
        assertTrue(vocabScene.isVocabulary());
        assertFalse(vocabScene.isHook());
        assertTrue(vocabScene.optVocabularyItem().isPresent());
        assertEquals("Relative", vocabScene.optVocabularyItem().get().word());

        Scene hookScene = Scene.hook("hook", 0, "Welcome!", "Welcome!");
        assertTrue(hookScene.isHook());
        assertTrue(hookScene.optVocabularyItem().isEmpty());

        // Invalid negative index
        assertThrows(IllegalArgumentException.class, () ->
            new Scene("id", SceneType.HOOK, "text", "sub", null, -1));

        // Serialization round-trip
        String json = mapper.writeValueAsString(vocabScene);
        assertFalse(json.contains("\"hook\""), "Helper boolean methods must not be serialized");
        Scene deserialized = mapper.readValue(json, Scene.class);
        assertEquals(vocabScene, deserialized);
    }

    @Test
    @DisplayName("TikTokScript immutability, helper filters, and serialization")
    void testTikTokScript() throws Exception {
        Scene hook = Scene.hook("hook", 0, "Hello IELTS!", "Hello!");
        VocabularyItem v1 = new VocabularyItem("Sibling", "/ˈsɪb.lɪŋ/", "noun", "def", "col", "mean", "ex");
        Scene vocab = Scene.vocabulary("v1", 1, "Word 1", "Word 1", v1);
        Scene cta = Scene.callToAction("cta", 2, "Subscribe!", "Subscribe!");

        TikTokScript script = new TikTokScript("Family", "Band 7+", List.of(hook, vocab, cta));

        assertEquals(3, script.totalScenes());
        assertTrue(script.hookScene().isPresent());
        assertEquals("hook", script.hookScene().get().id());
        assertTrue(script.ctaScene().isPresent());
        assertEquals(1, script.vocabularyScenes().size());
        assertTrue(script.findSceneById("v1").isPresent());
        assertTrue(script.findSceneById("unknown").isEmpty());

        // Immutability check
        assertThrows(UnsupportedOperationException.class, () ->
            script.scenes().add(hook));

        // Serialization round-trip
        String json = mapper.writeValueAsString(script);
        TikTokScript deserialized = mapper.readValue(json, TikTokScript.class);
        assertEquals(script, deserialized);
    }

    @Test
    @DisplayName("SceneAudioTimeline interval calculations, validation, and serialization")
    void testSceneAudioTimeline() throws Exception {
        Path p = Path.of("/workspace/audio/scene-1.wav");
        SceneAudioTimeline timeline = SceneAudioTimeline.of("scene-1", p, 3.5, 4.2);

        assertEquals(3.5, timeline.startSeconds(), 0.0001);
        assertEquals(7.7, timeline.endSeconds(), 0.0001);
        assertEquals(4.2, timeline.durationSeconds(), 0.0001);
        assertTrue(timeline.containsTime(5.0));
        assertFalse(timeline.containsTime(2.0));

        // Invariant: endSeconds < startSeconds
        assertThrows(IllegalArgumentException.class, () ->
            new SceneAudioTimeline("s", p, 5.0, 4.0, 1.0));

        // Invariant: negative startSeconds
        assertThrows(IllegalArgumentException.class, () ->
            new SceneAudioTimeline("s", p, -1.0, 4.0, 5.0));

        // Serialization round-trip
        String json = mapper.writeValueAsString(timeline);
        assertTrue(json.contains("/workspace/audio/scene-1.wav"), "Path must be serialized as clean string");
        SceneAudioTimeline deserialized = mapper.readValue(json, SceneAudioTimeline.class);
        assertEquals(timeline, deserialized);
    }

    @Test
    @DisplayName("AudioTimelineManifest lookup and immutability")
    void testAudioTimelineManifest() throws Exception {
        Path master = Path.of("/workspace/audio/master.wav");
        SceneAudioTimeline t0 = SceneAudioTimeline.of("hook", Path.of("/workspace/audio/hook.wav"), 0.0, 3.0);
        SceneAudioTimeline t1 = SceneAudioTimeline.of("v1", Path.of("/workspace/audio/v1.wav"), 3.3, 5.0);

        AudioTimelineManifest manifest = new AudioTimelineManifest(master, 8.3, List.of(t0, t1), 48000);
        assertEquals(2, manifest.sceneCount());
        assertFalse(manifest.isEmpty());
        assertTrue(manifest.getTimeline("hook").isPresent());
        assertTrue(manifest.getTimeline("v1").isPresent());
        assertTrue(manifest.getTimeline("missing").isEmpty());

        // Immutability
        assertThrows(UnsupportedOperationException.class, () ->
            manifest.sceneTimelines().add(t0));

        // Serialization round-trip
        String json = mapper.writeValueAsString(manifest);
        assertFalse(json.contains("\"empty\""), "isEmpty helper method must not be serialized");
        AudioTimelineManifest deserialized = mapper.readValue(json, AudioTimelineManifest.class);
        assertEquals(manifest, deserialized);
    }

    @Test
    @DisplayName("RenderBlueprint defaults, builder, and serialization")
    void testRenderBlueprint() throws Exception {
        Path master = Path.of("/workspace/audio/master.wav");
        AudioTimelineManifest manifest = new AudioTimelineManifest(master, 10.0, List.of(), 48000);

        RenderBlueprint blueprint = RenderBlueprint.builder()
            .audioTimeline(manifest)
            .subtitlePath(Path.of("/workspace/subtitles/sub.ass"))
            .visualCardPaths(Map.of("v1", Path.of("/workspace/visual/v1.png")))
            .backgroundVideoPath(Path.of("/workspace/assets/bg.mp4"))
            .bgmAudioPath(Path.of("/workspace/assets/bgm.mp3"))
            .outputVideoPath(Path.of("/workspace/output/tiktok.mp4"))
            .build();

        assertEquals(1080, blueprint.width());
        assertEquals(1920, blueprint.height());
        assertEquals(30.0, blueprint.fps());
        assertEquals("1080x1920", blueprint.resolution());
        assertTrue(blueprint.hasBgm());
        assertTrue(blueprint.hasSubtitles());
        assertEquals(Path.of("/workspace/visual/v1.png"), blueprint.getCardForScene("v1"));

        // Serialization round-trip
        String json = mapper.writeValueAsString(blueprint);
        assertTrue(json.contains("/workspace/visual/v1.png"));
        RenderBlueprint deserialized = mapper.readValue(json, RenderBlueprint.class);
        assertEquals(blueprint, deserialized);
    }

    @Test
    @DisplayName("RenderResult success and failure states")
    void testRenderResult() throws Exception {
        Path out = Path.of("/workspace/out.mp4");
        RenderResult success = RenderResult.success(out, 60.0, 10_000_000L, 5000L);
        assertTrue(success.success());
        assertFalse(success.isFailure());
        assertNull(success.errorMessage());

        RenderResult failure = RenderResult.failure("FFmpeg error", 1000L);
        assertFalse(failure.success());
        assertTrue(failure.isFailure());
        assertEquals("FFmpeg error", failure.errorMessage());

        // Serialization round-trip
        String jsonSuccess = mapper.writeValueAsString(success);
        assertFalse(jsonSuccess.contains("\"failure\""), "isFailure helper method must not be serialized");
        RenderResult desSuccess = mapper.readValue(jsonSuccess, RenderResult.class);
        assertEquals(success, desSuccess);
    }

    @Test
    @DisplayName("Tolerate unknown JSON properties")
    void testUnknownPropertiesIgnored() throws Exception {
        String jsonWithExtra = """
        {
            "word": "Sibling",
            "ipa": "/ˈsɪb.lɪŋ/",
            "partOfSpeech": "noun",
            "definition": "brother or sister",
            "extraMetadata": "ignore_me",
            "llmConfidence": 0.99
        }
        """;
        VocabularyItem item = mapper.readValue(jsonWithExtra, VocabularyItem.class);
        assertEquals("Sibling", item.word());
        assertEquals("/ˈsɪb.lɪŋ/", item.ipa());
    }

    @Test
    @DisplayName("Full 7-scene IELTS Band 7+ lesson script serialization round-trip")
    void testFullIeltsLessonScriptRoundTrip() throws Exception {
        VocabularyItem v1 = new VocabularyItem(
            "Sibling", "/ˈsɪb.lɪŋ/", "noun", "a brother or sister",
            "sibling rivalry", "competition between brothers or sisters",
            "Siblings can have a profound impact on a child's life."
        );
        VocabularyItem v2 = new VocabularyItem(
            "Relative", "/ˈrel.ə.tɪv/", "noun", "a person in the same family or a family member",
            "distant relative", "a relative who is not closely related",
            "With the rise of nuclear families the amount of time spent with extended relatives has decreased."
        );
        VocabularyItem v3 = new VocabularyItem(
            "Adolescence", "/ˌæd.əˈles.ns/", "noun", "the period between childhood and adulthood",
            "early adolescence", "the beginning of teenage years",
            "Adolescence can be a difficult time both for the child and the parent."
        );
        VocabularyItem v4 = new VocabularyItem(
            "Nurture", "/ˈnɜː.tʃər/", "verb", "to help someone or something develop and grow",
            "nurture talent", "encourage and develop natural skills",
            "Growing up in a healthy and nurturing environment can prepare children for future success."
        );
        VocabularyItem v5 = new VocabularyItem(
            "Relationship", "/rɪˈleɪ.ʃn.ʃɪp/", "noun", "the way in which two people or groups behave towards each other",
            "close relationship", "a strong personal bond between people",
            "Maintaining a strong relationship with your friends becomes more difficult later in life."
        );

        Scene hook = Scene.hook("hook", 0, "Hello and welcome to this IELTS Master Band 7+ vocabulary lesson on Family!", "IELTS Band 7+ Vocabulary: Family");
        Scene s1 = Scene.vocabulary("vocab-sibling", 1, "Word 1 is Sibling. A sibling means a brother or sister.", "Word 1: Sibling - Brother or sister", v1);
        Scene s2 = Scene.vocabulary("vocab-relative", 2, "Word 2 is Relative. A relative is a family member.", "Word 2: Relative - Family member", v2);
        Scene s3 = Scene.vocabulary("vocab-adolescence", 3, "Word 3 is Adolescence. Adolescence is the teenage period.", "Word 3: Adolescence - Teenage years", v3);
        Scene s4 = Scene.vocabulary("vocab-nurture", 4, "Word 4 is Nurture. To nurture means to help develop and grow.", "Word 4: Nurture - Help develop and grow", v4);
        Scene s5 = Scene.vocabulary("vocab-relationship", 5, "Word 5 is Relationship. A relationship describes how people connect.", "Word 5: Relationship - Connection", v5);
        Scene cta = Scene.callToAction("cta", 6, "Your action step today is to write two sentences using these words in the comments below!", "Action Step: Write 2 sentences in comments!");

        TikTokScript fullScript = new TikTokScript("Family Vocabulary", "Band 7+", List.of(hook, s1, s2, s3, s4, s5, cta));

        assertEquals(7, fullScript.totalScenes());
        assertEquals(5, fullScript.vocabularyScenes().size());

        String json = mapper.writeValueAsString(fullScript);
        TikTokScript deserialized = mapper.readValue(json, TikTokScript.class);
        assertEquals(fullScript, deserialized);
    }
}
