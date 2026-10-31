package com.tiktok.video.core.contract;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

/**
 * Request payload for text-to-speech synthesis adhering to OpenAI speech API specification.
 *
 * @param text the text to synthesize; maps to 'input' in OpenAI JSON payload
 * @param model the TTS model name (e.g., 'vieneu-v3-turbo')
 * @param voice the voice name (e.g., 'Hải Đăng', 'Adam', 'Mai Anh')
 * @param responseFormat the output audio format (e.g., 'wav')
 * @param speed the speaking speed multiplier (default 1.0)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TtsRequest(
    @JsonProperty("input") String text,
    @JsonProperty("model") String model,
    @JsonProperty("voice") String voice,
    @JsonProperty("response_format") String responseFormat,
    @JsonProperty("speed") Double speed
) {
    public static final String DEFAULT_MODEL = "vieneu-v3-turbo";
    public static final String DEFAULT_VOICE = "Hải Đăng";
    public static final String DEFAULT_FORMAT = "wav";
    public static final double DEFAULT_SPEED = 1.0;

    @JsonCreator
    public TtsRequest {
        Objects.requireNonNull(text, "text must not be null");
        if (text.isBlank()) {
            throw new IllegalArgumentException("text must not be blank");
        }
        if (model == null || model.isBlank()) {
            model = DEFAULT_MODEL;
        }
        if (voice == null || voice.isBlank()) {
            voice = DEFAULT_VOICE;
        }
        if (responseFormat == null || responseFormat.isBlank()) {
            responseFormat = DEFAULT_FORMAT;
        }
        if (speed == null || speed <= 0.0) {
            speed = DEFAULT_SPEED;
        }
    }

    public static TtsRequest of(String text) {
        return new TtsRequest(text, DEFAULT_MODEL, DEFAULT_VOICE, DEFAULT_FORMAT, DEFAULT_SPEED);
    }

    public static TtsRequest of(String text, String voice) {
        return new TtsRequest(text, DEFAULT_MODEL, voice, DEFAULT_FORMAT, DEFAULT_SPEED);
    }
}
