package com.tiktok.video.script.llm;

import com.tiktok.video.core.exception.ScriptExtractionException;
import com.tiktok.video.core.model.TikTokScript;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LlmScriptExtractorTest {

    @Mock
    private LlmClient mockLlmClient;

    @Test
    @DisplayName("F4.2: Parse valid JSON from LLM into structured TikTokScript")
    void testExtractValidJson() throws Exception {
        String llmOutput = """
            {
              "topic": "Family",
              "targetBand": "Band 7+",
              "hook": "Welcome to IELTS Master Band 7+ vocabulary on Family!",
              "vocabularyItems": [
                {
                  "word": "Sibling",
                  "definition": "a brother or sister",
                  "collocation": "sibling rivalry",
                  "exampleSentence": "Siblings can have a profound impact."
                }
              ],
              "callToAction": "Write 2 sentences in comments!"
            }
            """;

        when(mockLlmClient.generate(anyString(), anyString())).thenReturn(llmOutput);

        LlmScriptExtractor extractor = new LlmScriptExtractor(mockLlmClient);
        TikTokScript script = extractor.extract("Sample transcript");

        assertThat(script.topic()).isEqualTo("Family");
        assertThat(script.totalScenes()).isEqualTo(3); // Hook, 1 Word, CTA
        assertThat(script.vocabularyScenes().get(0).vocabularyItem().ipa()).isEqualTo("/ˈsɪb.lɪŋ/");
    }

    @Test
    @DisplayName("F4.4: Incomplete JSON missing vocabularyItems throws ScriptExtractionException")
    void testIncompleteJsonThrows() throws Exception {
        when(mockLlmClient.generate(anyString(), anyString())).thenReturn("{\"hook\": \"Hello\"}");

        LlmScriptExtractor extractor = new LlmScriptExtractor(mockLlmClient);
        assertThatThrownBy(() -> extractor.extract("Sample transcript"))
            .isInstanceOf(ScriptExtractionException.class)
            .hasMessageContaining("missing 'vocabularyItems'");
    }
}
