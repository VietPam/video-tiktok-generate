package com.tiktok.video.audio;

import com.tiktok.video.audio.util.WavAudioUtil;
import com.tiktok.video.core.contract.AudioTimelineService;
import com.tiktok.video.core.contract.TtsClient;
import com.tiktok.video.core.contract.TtsRequest;
import com.tiktok.video.core.exception.AudioProcessingException;
import com.tiktok.video.core.exception.TtsException;
import com.tiktok.video.core.model.AudioTimelineManifest;
import com.tiktok.video.core.model.Scene;
import com.tiktok.video.core.model.SceneAudioTimeline;
import com.tiktok.video.core.model.TikTokScript;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Production implementation of AudioTimelineService coordinating segment-by-segment TTS synthesis,
 * exact duration calculation, inter-scene pause buffering, master audio stitching, and timeline manifest generation.
 */
public class AudioTimelineServiceImpl implements AudioTimelineService {

    private static final Logger log = LoggerFactory.getLogger(AudioTimelineServiceImpl.class);

    public static final double DEFAULT_PAUSE_SECONDS = 0.25; // 250ms inter-scene pause
    public static final int DEFAULT_SAMPLE_RATE = 48000;
    public static final int DEFAULT_CHANNELS = 1;
    public static final int DEFAULT_BITS_PER_SAMPLE = 16;
    public static final int BYTES_PER_SAMPLE = DEFAULT_BITS_PER_SAMPLE / 8; // 2
    public static final int BYTES_PER_SECOND = DEFAULT_SAMPLE_RATE * DEFAULT_CHANNELS * BYTES_PER_SAMPLE; // 96000

    private final TtsClient ttsClient;
    private final String defaultVoice;
    private final String defaultModel;
    private final double pauseDurationSeconds;
    private final int sampleRate;

    public AudioTimelineServiceImpl(TtsClient ttsClient) {
        this(ttsClient, TtsRequest.DEFAULT_VOICE, TtsRequest.DEFAULT_MODEL, DEFAULT_PAUSE_SECONDS, DEFAULT_SAMPLE_RATE);
    }

    public AudioTimelineServiceImpl(TtsClient ttsClient, String defaultVoice, double pauseDurationSeconds) {
        this(ttsClient, defaultVoice, TtsRequest.DEFAULT_MODEL, pauseDurationSeconds, DEFAULT_SAMPLE_RATE);
    }

    public AudioTimelineServiceImpl(TtsClient ttsClient, String defaultVoice, String defaultModel, double pauseDurationSeconds, int sampleRate) {
        this.ttsClient = Objects.requireNonNull(ttsClient, "ttsClient must not be null");
        this.defaultVoice = (defaultVoice == null || defaultVoice.isBlank()) ? TtsRequest.DEFAULT_VOICE : defaultVoice.trim();
        this.defaultModel = (defaultModel == null || defaultModel.isBlank()) ? TtsRequest.DEFAULT_MODEL : defaultModel.trim();
        if (pauseDurationSeconds < 0.0) {
            throw new IllegalArgumentException("pauseDurationSeconds cannot be negative: " + pauseDurationSeconds);
        }
        this.pauseDurationSeconds = pauseDurationSeconds;
        if (sampleRate <= 0) {
            throw new IllegalArgumentException("sampleRate must be positive: " + sampleRate);
        }
        this.sampleRate = sampleRate;
    }

    @Override
    public AudioTimelineManifest generateTimeline(TikTokScript script, Path outputDir) throws AudioProcessingException {
        if (script == null) {
            throw new IllegalArgumentException("script must not be null");
        }
        if (outputDir == null) {
            throw new IllegalArgumentException("outputDir must not be null");
        }

        List<Scene> scenes = script.scenes();
        if (scenes == null || scenes.isEmpty()) {
            throw new AudioProcessingException("Script contains no scenes to synthesize");
        }

        try {
            Files.createDirectories(outputDir);
        } catch (IOException e) {
            throw new AudioProcessingException("Failed to create output directory: " + outputDir, e, outputDir);
        }

        int bytesPerSec = sampleRate * DEFAULT_CHANNELS * BYTES_PER_SAMPLE;
        int silenceBytes = (int) Math.round(pauseDurationSeconds * bytesPerSec);
        if (silenceBytes % 2 != 0) {
            silenceBytes++;
        }
        double actualPauseSeconds = (double) silenceBytes / bytesPerSec;
        byte[] silenceBuffer = new byte[silenceBytes]; // all zeros (silence)

        List<SceneAudioTimeline> timelines = new ArrayList<>(scenes.size());
        ByteArrayOutputStream masterPcmStream = new ByteArrayOutputStream();

        double currentTime = 0.0;
        log.info("Starting audio synthesis for {} scenes, pauseDuration={}s", scenes.size(), pauseDurationSeconds);

        for (int i = 0; i < scenes.size(); i++) {
            Scene scene = scenes.get(i);
            String spokenText = scene.spokenText();
            if (spokenText == null || spokenText.isBlank()) {
                throw new AudioProcessingException("Scene " + scene.id() + " has blank spokenText");
            }

            Path sceneWavPath = outputDir.resolve(sanitizeFileName(scene.id()) + ".wav");

            // 1. Synthesize audio via TTS client
            byte[] rawAudio;
            try {
                TtsRequest request = new TtsRequest(spokenText, defaultModel, defaultVoice, "wav", 1.0);
                rawAudio = ttsClient.synthesize(request);
            } catch (TtsException e) {
                throw new AudioProcessingException("TTS synthesis failed for scene " + scene.id() + ": " + e.getMessage(), e, sceneWavPath);
            } catch (Exception e) {
                throw new AudioProcessingException("Unexpected error during TTS synthesis for scene " + scene.id() + ": " + e.getMessage(), e, sceneWavPath);
            }

            if (rawAudio == null || rawAudio.length == 0) {
                throw new AudioProcessingException("TTS client returned empty audio for scene: " + scene.id(), sceneWavPath);
            }

            // 2. Extract PCM bytes and calculate exact duration
            byte[] pcmBytes = WavAudioUtil.extractPcm(rawAudio);
            if (pcmBytes.length == 0) {
                throw new AudioProcessingException("Extracted PCM data is empty for scene: " + scene.id(), sceneWavPath);
            }

            double durationSeconds = (double) pcmBytes.length / bytesPerSec;
            double startSeconds = currentTime;
            double endSeconds = startSeconds + durationSeconds;

            // 3. Write individual scene WAV (patched with valid RIFF header)
            byte[] patchedSceneWav = WavAudioUtil.buildWav(pcmBytes, sampleRate, DEFAULT_CHANNELS, DEFAULT_BITS_PER_SAMPLE);
            try {
                Files.write(sceneWavPath, patchedSceneWav);
            } catch (IOException e) {
                throw new AudioProcessingException("Failed to write scene audio file: " + sceneWavPath, e, sceneWavPath);
            }

            // 4. Create SceneAudioTimeline
            SceneAudioTimeline timeline = new SceneAudioTimeline(scene.id(), sceneWavPath, startSeconds, endSeconds, durationSeconds);
            timelines.add(timeline);

            // 5. Append PCM to master stream
            try {
                masterPcmStream.write(pcmBytes);
            } catch (IOException e) {
                throw new AudioProcessingException("Failed to append PCM to master stream for scene: " + scene.id(), e, sceneWavPath);
            }

            // 6. If not the last scene, insert inter-scene pause
            if (i < scenes.size() - 1 && silenceBytes > 0) {
                try {
                    masterPcmStream.write(silenceBuffer);
                } catch (IOException e) {
                    throw new AudioProcessingException("Failed to write silence pause after scene: " + scene.id(), e);
                }
                currentTime = endSeconds + actualPauseSeconds;
            } else {
                currentTime = endSeconds;
            }

            log.debug("Scene [{}] synthesized: start={:.3f}s, end={:.3f}s, dur={:.3f}s", 
                scene.id(), startSeconds, endSeconds, durationSeconds);
        }

        double totalDurationSeconds = currentTime;

        // 7. Write master voiceover WAV
        Path masterAudioPath = outputDir.resolve("master_voiceover.wav");
        byte[] masterPcmBytes = masterPcmStream.toByteArray();
        byte[] masterWav = WavAudioUtil.buildWav(masterPcmBytes, sampleRate, DEFAULT_CHANNELS, DEFAULT_BITS_PER_SAMPLE);
        try {
            Files.write(masterAudioPath, masterWav);
        } catch (IOException e) {
            throw new AudioProcessingException("Failed to write master voiceover audio file: " + masterAudioPath, e, masterAudioPath);
        }

        log.info("Audio timeline complete: totalDuration={:.3f}s, masterWav={}", totalDurationSeconds, masterAudioPath);
        return new AudioTimelineManifest(masterAudioPath, totalDurationSeconds, timelines, sampleRate);
    }

    private String sanitizeFileName(String id) {
        if (id == null || id.isBlank()) return "scene";
        return id.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
