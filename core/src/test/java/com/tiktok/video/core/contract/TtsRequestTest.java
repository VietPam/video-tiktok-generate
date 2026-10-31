package com.tiktok.video.core.contract;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TtsRequestTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("Should apply default parameters when using factory method")
    void shouldApplyDefaultParameters() {
        TtsRequest req = TtsRequest.of("Hello IELTS");

        assertThat(req.text()).isEqualTo("Hello IELTS");
        assertThat(req.model()).isEqualTo(TtsRequest.DEFAULT_MODEL);
        assertThat(req.voice()).isEqualTo(TtsRequest.DEFAULT_VOICE);
        assertThat(req.responseFormat()).isEqualTo(TtsRequest.DEFAULT_FORMAT);
        assertThat(req.speed()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when text is null or blank")
    void shouldRejectInvalidText() {
        assertThatThrownBy(() -> TtsRequest.of(null))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> TtsRequest.of("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blank");
    }

    @Test
    @DisplayName("Should serialize text field to OpenAI 'input' JSON property")
    void shouldSerializeToOpenAiFormat() throws Exception {
        TtsRequest req = TtsRequest.of("Vocabulary lesson", "Hải Đăng");
        String json = objectMapper.writeValueAsString(req);

        assertThat(json).contains("\"input\":\"Vocabulary lesson\"");
        assertThat(json).contains("\"voice\":\"Hải Đăng\"");
        assertThat(json).contains("\"model\":\"" + TtsRequest.DEFAULT_MODEL + "\"");
    }

    @Test
    @DisplayName("Should deserialize OpenAI format JSON into TtsRequest")
    void shouldDeserializeFromOpenAiJson() throws Exception {
        String json = """
            {
              "input": "Adolescence definition",
              "model": "custom-model",
              "voice": "Adam",
              "response_format": "wav",
              "speed": 1.1
            }
            """;
        TtsRequest req = objectMapper.readValue(json, TtsRequest.class);

        assertThat(req.text()).isEqualTo("Adolescence definition");
        assertThat(req.model()).isEqualTo("custom-model");
        assertThat(req.voice()).isEqualTo("Adam");
        assertThat(req.responseFormat()).isEqualTo("wav");
        assertThat(req.speed()).isEqualTo(1.1);
    }
}
