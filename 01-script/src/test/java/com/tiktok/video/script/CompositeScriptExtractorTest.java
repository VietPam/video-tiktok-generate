package com.tiktok.video.script;

import com.tiktok.video.core.contract.ScriptExtractor;
import com.tiktok.video.core.exception.ScriptExtractionException;
import com.tiktok.video.core.model.Scene;
import com.tiktok.video.core.model.TikTokScript;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CompositeScriptExtractorTest {

    @Mock
    private ScriptExtractor mockPrimaryExtractor;

    @Mock
    private ScriptExtractor mockFallbackExtractor;

    @Test
    @DisplayName("F4.5: Primary extractor succeeds -> returns primary result without fallback")
    void testPrimarySuccess() throws Exception {
        TikTokScript expected = new TikTokScript("Family", "Band 7+", List.of(
            Scene.hook("hook", 0, "Hello", "Hello")
        ));
        when(mockPrimaryExtractor.extract("input text")).thenReturn(expected);

        CompositeScriptExtractor composite = new CompositeScriptExtractor(mockPrimaryExtractor, mockFallbackExtractor, true);
        TikTokScript result = composite.extract("input text");

        assertThat(result).isSameAs(expected);
        verify(mockFallbackExtractor, never()).extract(anyString());
    }

    @Test
    @DisplayName("F4.5: Primary extractor throws exception -> gracefully falls back to regex")
    void testPrimaryFailsFallsBack() throws Exception {
        TikTokScript fallbackScript = new TikTokScript("Family", "Band 7+", List.of(
            Scene.hook("hook", 0, "Fallback", "Fallback")
        ));
        when(mockPrimaryExtractor.extract("input text")).thenThrow(new ScriptExtractionException("LLM network down"));
        when(mockFallbackExtractor.extract("input text")).thenReturn(fallbackScript);

        CompositeScriptExtractor composite = new CompositeScriptExtractor(mockPrimaryExtractor, mockFallbackExtractor, true);
        TikTokScript result = composite.extract("input text");

        assertThat(result).isSameAs(fallbackScript);
        verify(mockFallbackExtractor).extract("input text");
    }

    @Test
    @DisplayName("llmEnabled = false -> directly executes fallback extractor")
    void testLlmDisabledDirectlyCallsFallback() throws Exception {
        TikTokScript fallbackScript = new TikTokScript("Family", "Band 7+", List.of(
            Scene.hook("hook", 0, "Fallback", "Fallback")
        ));
        when(mockFallbackExtractor.extract("input text")).thenReturn(fallbackScript);

        CompositeScriptExtractor composite = new CompositeScriptExtractor(mockPrimaryExtractor, mockFallbackExtractor, false);
        TikTokScript result = composite.extract("input text");

        assertThat(result).isSameAs(fallbackScript);
        verify(mockPrimaryExtractor, never()).extract(anyString());
    }

    @Test
    @DisplayName("Null or blank input throws IllegalArgumentException")
    void testBlankInputThrows() {
        CompositeScriptExtractor composite = new CompositeScriptExtractor(mockPrimaryExtractor, mockFallbackExtractor, true);
        assertThatThrownBy(() -> composite.extract("   "))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
