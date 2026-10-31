package com.tiktok.video.script.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tiktok.video.core.contract.ScriptExtractor;
import com.tiktok.video.core.exception.ScriptExtractionException;
import com.tiktok.video.core.model.Scene;
import com.tiktok.video.core.model.TikTokScript;
import com.tiktok.video.core.model.VocabularyItem;
import com.tiktok.video.script.ipa.IpaEnricher;
import com.tiktok.video.script.ipa.StandardIpaEnricher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class LlmScriptExtractor implements ScriptExtractor {

    private static final Logger log = LoggerFactory.getLogger(LlmScriptExtractor.class);

    private static final String SYSTEM_PROMPT = """
        You are an expert IELTS instructor and vertical TikTok scriptwriter.
        Extract vocabulary lesson content from the provided transcript into a strict JSON object.
        Do NOT wrap the response in markdown code blocks. Return ONLY raw JSON.
        
        Strict JSON Schema:
        {
          "topic": "Family",
          "targetBand": "Band 7+",
          "hook": "Engaging 1-2 sentence hook for vertical TikTok opening",
          "vocabularyItems": [
            {
              "word": "target word",
              "ipa": "IPA notation with stress markers",
              "partOfSpeech": "noun or verb",
              "definition": "clear concise definition",
              "collocation": "collocation phrase",
              "collocationMeaning": "brief meaning of collocation",
              "exampleSentence": "illustrative example sentence"
            }
          ],
          "callToAction": "Action step for viewers to post sentences in comments"
        }
        """;

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;
    private final IpaEnricher ipaEnricher;

    public LlmScriptExtractor(LlmClient llmClient, ObjectMapper objectMapper, IpaEnricher ipaEnricher) {
        this.llmClient = Objects.requireNonNull(llmClient, "llmClient cannot be null");
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
        this.ipaEnricher = ipaEnricher == null ? new StandardIpaEnricher() : ipaEnricher;
    }

    public LlmScriptExtractor(LlmClient llmClient) {
        this(llmClient, new ObjectMapper(), new StandardIpaEnricher());
    }

    @Override
    public TikTokScript extract(String transcriptText) throws ScriptExtractionException {
        if (transcriptText == null || transcriptText.isBlank()) {
            throw new IllegalArgumentException("transcriptText cannot be null or blank");
        }

        String rawJson;
        try {
            rawJson = llmClient.generate(SYSTEM_PROMPT, transcriptText);
        } catch (LlmException e) {
            throw new ScriptExtractionException("Failed during LLM inference: " + e.getMessage(), e);
        }

        try {
            JsonNode root = objectMapper.readTree(rawJson);
            String topic = root.path("topic").asText("Family");
            String targetBand = root.path("targetBand").asText("Band 7+");
            String hookText = root.path("hook").asText("Welcome to IELTS Master Band 7+ vocabulary lesson on Family!");
            String ctaText = root.path("callToAction").asText("Your action step for today is to write two sentences using each word in the comments below!");

            JsonNode vocabArray = root.path("vocabularyItems");
            if (!vocabArray.isArray() || vocabArray.isEmpty()) {
                throw new ScriptExtractionException("LLM output is missing 'vocabularyItems' array: " + rawJson);
            }

            List<Scene> scenes = new ArrayList<>();
            int seq = 0;

            // Scene 0: HOOK
            scenes.add(Scene.hook("scene_00_hook", seq++, hookText, "Welcome to IELTS Master Band 7+: " + topic));

            // Vocabulary Scenes
            for (JsonNode node : vocabArray) {
                String word = node.path("word").asText("");
                if (word.isBlank()) continue;

                String ipa = node.path("ipa").asText("");
                String pos = node.path("partOfSpeech").asText("");
                String def = node.path("definition").asText("");
                String col = node.path("collocation").asText("");
                String colMeaning = node.path("collocationMeaning").asText("");
                String ex = node.path("exampleSentence").asText("");

                VocabularyItem item = VocabularyItem.builder()
                    .word(word)
                    .ipa(ipa)
                    .partOfSpeech(pos)
                    .definition(def)
                    .collocation(col)
                    .collocationMeaning(colMeaning)
                    .exampleSentence(ex)
                    .build();

                VocabularyItem enrichedItem = ipaEnricher.enrich(item);

                String wordStem = enrichedItem.word().toLowerCase().replaceAll("[^a-z0-9]", "");
                String definition = safeText(enrichedItem.definition(), "Definition is being introduced.");
                String collocation = safeText(enrichedItem.collocation(), "No collocation provided.");
                String collocationMeaning = safeText(enrichedItem.collocationMeaning(), "Collocation meaning unavailable.");
                String example = safeText(enrichedItem.exampleSentence(), "No example provided.");

                String wordSpoken = String.format("Word %d is %s. Definition: %s.",
                    seq, enrichedItem.word(), definition);
                String wordSubtitle = String.format("%s / %s",
                    enrichedItem.word(), definition);
                scenes.add(Scene.vocabulary(
                    String.format("scene_%02d_%s_word", seq, wordStem),
                    seq++, wordSpoken, wordSubtitle, enrichedItem));

                String collocationSpoken = String.format("Collocation for %s is %s. It means %s.",
                    enrichedItem.word(), collocation, collocationMeaning);
                String collocationSubtitle = String.format("%s → %s", collocation, collocationMeaning);
                scenes.add(Scene.vocabularyCollocation(
                    String.format("scene_%02d_%s_collocation", seq, wordStem),
                    seq++, collocationSpoken, collocationSubtitle, enrichedItem));

                String exampleSpoken = String.format("Example sentence with %s: %s",
                    enrichedItem.word(), example);
                String exampleSubtitle = String.format("Ví dụ: %s", example);
                scenes.add(Scene.vocabularyExample(
                    String.format("scene_%02d_%s_example", seq, wordStem),
                    seq++, exampleSpoken, exampleSubtitle, enrichedItem));
            }

            // Scene CTA
            scenes.add(Scene.callToAction(String.format("scene_%02d_cta", seq), seq, ctaText, "Action Step: Post 2 sentences in the comments!"));

            return new TikTokScript(topic, targetBand, scenes);
        } catch (ScriptExtractionException e) {
            throw e;
        } catch (Exception e) {
            throw new ScriptExtractionException("Failed to parse LLM JSON into TikTokScript: " + e.getMessage(), e, rawJson);
        }
    }

    private String safeText(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value.trim();
    }
}
