package com.tiktok.video.audio.tts;

import com.tiktok.video.audio.util.WavAudioUtil;
import com.tiktok.video.core.contract.TtsClient;
import com.tiktok.video.core.contract.TtsRequest;
import com.tiktok.video.core.exception.TtsException;

import java.util.Objects;

/**
 * Deterministic synthetic WAV generator implementing TtsClient.
 * Generates valid 48kHz mono 16-bit PCM WAV bytes with silence or a gentle sine tone.
 * Ideal for offline CI runs where Docker is unavailable.
 */
public class SyntheticWavGenerator implements TtsClient {

    public enum ToneMode {
        SILENCE,
        SINE_TONE
    }

    private final ToneMode toneMode;
    private final double wordsPerSecond;
    private final double minDurationSeconds;
    private final int sampleRate;
    private final double toneFrequencyHz;

    public SyntheticWavGenerator() {
        this(ToneMode.SINE_TONE, 2.5, 1.0, 48000, 440.0);
    }

    public SyntheticWavGenerator(ToneMode toneMode) {
        this(toneMode, 2.5, 1.0, 48000, 440.0);
    }

    public SyntheticWavGenerator(ToneMode toneMode, double wordsPerSecond, double minDurationSeconds) {
        this(toneMode, wordsPerSecond, minDurationSeconds, 48000, 440.0);
    }

    public SyntheticWavGenerator(ToneMode toneMode, double wordsPerSecond, double minDurationSeconds, int sampleRate, double toneFrequencyHz) {
        this.toneMode = Objects.requireNonNull(toneMode, "toneMode must not be null");
        this.wordsPerSecond = wordsPerSecond > 0 ? wordsPerSecond : 2.5;
        this.minDurationSeconds = minDurationSeconds > 0 ? minDurationSeconds : 1.0;
        this.sampleRate = sampleRate > 0 ? sampleRate : 48000;
        this.toneFrequencyHz = toneFrequencyHz > 0 ? toneFrequencyHz : 440.0;
    }

    @Override
    public byte[] synthesize(TtsRequest request) throws TtsException {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        String text = request.text();
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("text must not be blank");
        }

        String[] words = text.trim().split("\\s+");
        double estimatedDuration = Math.max(minDurationSeconds, words.length / wordsPerSecond);

        int totalSamples = (int) Math.round(estimatedDuration * sampleRate);
        byte[] pcmData = new byte[totalSamples * 2]; // 16-bit mono = 2 bytes per sample

        if (toneMode == ToneMode.SINE_TONE) {
            short amplitude = 6000; // gentle volume to avoid clipping
            for (int i = 0; i < totalSamples; i++) {
                double angle = 2.0 * Math.PI * i * toneFrequencyHz / sampleRate;
                short sample = (short) Math.round(amplitude * Math.sin(angle));
                pcmData[2 * i] = (byte) (sample & 0xFF);
                pcmData[2 * i + 1] = (byte) ((sample >> 8) & 0xFF);
            }
        }

        return WavAudioUtil.buildWav(pcmData, sampleRate, 1, 16);
    }
}
