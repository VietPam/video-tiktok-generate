package com.tiktok.video.audio;

import com.tiktok.video.audio.util.WavAudioUtil;
import com.tiktok.video.core.contract.TtsClient;
import com.tiktok.video.core.contract.TtsRequest;
import com.tiktok.video.core.exception.AudioProcessingException;
import com.tiktok.video.core.exception.TtsException;
import com.tiktok.video.core.model.AudioTimelineManifest;
import com.tiktok.video.core.model.Scene;
import com.tiktok.video.core.model.SceneAudioTimeline;
import com.tiktok.video.core.model.TikTokScript;
import com.tiktok.video.core.model.VocabularyItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AudioTimelineServiceImplTest {

    @TempDir
    Path tempDir;

    private static class MockTts implements TtsClient {
        private final double durationSeconds;
        private final boolean returnStreamingHeader;

        MockTts(double durationSeconds, boolean returnStreamingHeader) {
            this.durationSeconds = durationSeconds;
            this.returnStreamingHeader = returnStreamingHeader;
        }

        @Override
        public byte[] synthesize(TtsRequest request) throws TtsException {
            int pcmBytes = (int) Math.round(durationSeconds * WavAudioUtil.BYTES_PER_SECOND);
            if (pcmBytes % 2 != 0) pcmBytes++;
            byte[] pcm = new byte[pcmBytes];
            // Fill with arbitrary pseudo-audio samples
            for (int i = 0; i < pcm.length; i += 2) {
                pcm[i] = (byte) (i & 0xFF);
                pcm[i + 1] = (byte) ((i >> 8) & 0xFF);
            }
            byte[] wav = WavAudioUtil.buildWav(pcm);
            if (returnStreamingHeader) {
                // Mimic VieNeu-TTS streaming 0xFFFFFFFF header
                ByteBuffer buf = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN);
                buf.putInt(4, 0xFFFFFFFF);
                buf.putInt(40, 0xFFFFFFFF);
            }
            return wav;
        }
    }

    private TikTokScript createStandardScript() {
        Scene hook = Scene.hook("hook", 0, "Chào các bạn học IELTS", "Chào các bạn học IELTS");
        Scene word1 = Scene.vocabulary("vocab-sibling", 1, "Sibling nghĩa là anh chị em ruột", "Sibling",
                VocabularyItem.builder().word("Sibling").ipa("/ˈsɪb.lɪŋ/").definition("Brother or sister").build());
        Scene word2 = Scene.vocabulary("vocab-relative", 2, "Relative nghĩa là bà con họ hàng", "Relative",
                VocabularyItem.builder().word("Relative").ipa("/ˈrel.ə.tɪv/").definition("Family member").build());
        Scene cta = Scene.callToAction("cta", 3, "Hãy follow kênh để học thêm nhé", "Follow kênh nhé");
        return new TikTokScript("Family", "Band 7+", List.of(hook, word1, word2, cta));
    }

    @Test
    @DisplayName("F9.1 & F9.3: Synthesizes scenes and concatenates master audio with 250ms pause")
    void testStandardMultiSceneStitching() throws Exception {
        TtsClient tts = new MockTts(1.0, false);
        AudioTimelineServiceImpl service = new AudioTimelineServiceImpl(tts, "Hải Đăng", 0.25);

        TikTokScript script = createStandardScript();
        AudioTimelineManifest manifest = service.generateTimeline(script, tempDir);

        assertNotNull(manifest);
        assertEquals(4, manifest.sceneCount());
        assertTrue(Files.exists(manifest.masterAudioPath()));

        // 4 scenes * 1.0s + 3 pauses * 0.25s = 4.75s
        assertEquals(4.75, manifest.totalDurationSeconds(), 0.001);

        List<SceneAudioTimeline> timelines = manifest.sceneTimelines();
        // Scene 0: 0.0 -> 1.0
        assertEquals(0.0, timelines.get(0).startSeconds(), 0.001);
        assertEquals(1.0, timelines.get(0).endSeconds(), 0.001);
        assertEquals(1.0, timelines.get(0).durationSeconds(), 0.001);

        // Scene 1: 1.25 -> 2.25
        assertEquals(1.25, timelines.get(1).startSeconds(), 0.001);
        assertEquals(2.25, timelines.get(1).endSeconds(), 0.001);

        // Scene 2: 2.50 -> 3.50
        assertEquals(2.50, timelines.get(2).startSeconds(), 0.001);
        assertEquals(3.50, timelines.get(2).endSeconds(), 0.001);

        // Scene 3: 3.75 -> 4.75
        assertEquals(3.75, timelines.get(3).startSeconds(), 0.001);
        assertEquals(4.75, timelines.get(3).endSeconds(), 0.001);

        // Master WAV file length: 44 bytes header + (4.75 * 96000) bytes = 44 + 456000 = 456044
        long expectedFileLength = 44 + (long) (4.75 * 96000);
        assertEquals(expectedFileLength, Files.size(manifest.masterAudioPath()));
    }

    @Test
    @DisplayName("F9.2: Strict non-overlapping timestamps across all scenes")
    void testSequentialSceneMonotonicity() throws Exception {
        TtsClient tts = new MockTts(0.8, false);
        AudioTimelineServiceImpl service = new AudioTimelineServiceImpl(tts, "Hải Đăng", 0.30);

        TikTokScript script = createStandardScript();
        AudioTimelineManifest manifest = service.generateTimeline(script, tempDir);

        List<SceneAudioTimeline> list = manifest.sceneTimelines();
        for (int i = 0; i < list.size() - 1; i++) {
            assertTrue(list.get(i + 1).startSeconds() >= list.get(i).endSeconds());
            assertEquals(0.30, list.get(i + 1).startSeconds() - list.get(i).endSeconds(), 0.001);
        }
    }

    @Test
    @DisplayName("B4.3: Single scene timeline has duration equal to scene duration and 0 pauses")
    void testSingleSceneTimeline() throws Exception {
        TtsClient tts = new MockTts(2.5, false);
        AudioTimelineServiceImpl service = new AudioTimelineServiceImpl(tts);

        Scene hook = Scene.hook("hook", 0, "Single scene", "Single");
        TikTokScript script = new TikTokScript("Family", "Band 7+", List.of(hook));

        AudioTimelineManifest manifest = service.generateTimeline(script, tempDir);

        assertEquals(1, manifest.sceneCount());
        assertEquals(2.5, manifest.totalDurationSeconds(), 0.001);
        assertEquals(0.0, manifest.sceneTimelines().get(0).startSeconds(), 0.001);
        assertEquals(2.5, manifest.sceneTimelines().get(0).endSeconds(), 0.001);
    }

    @Test
    @DisplayName("B4.1: Zero ms pause produces continuous contiguous timestamps")
    void testZeroMsPause() throws Exception {
        TtsClient tts = new MockTts(1.5, false);
        AudioTimelineServiceImpl service = new AudioTimelineServiceImpl(tts, "Hải Đăng", 0.0);

        TikTokScript script = createStandardScript();
        AudioTimelineManifest manifest = service.generateTimeline(script, tempDir);

        List<SceneAudioTimeline> list = manifest.sceneTimelines();
        for (int i = 0; i < list.size() - 1; i++) {
            assertEquals(list.get(i).endSeconds(), list.get(i + 1).startSeconds(), 0.0001);
        }
        assertEquals(6.0, manifest.totalDurationSeconds(), 0.001); // 4 * 1.5 = 6.0
    }

    @Test
    @DisplayName("F8: Resilient handling of 0xFFFFFFFF streaming WAV header from TTS")
    void testStreamingHeaderHandling() throws Exception {
        TtsClient tts = new MockTts(1.2, true); // true = emit 0xFFFFFFFF header
        AudioTimelineServiceImpl service = new AudioTimelineServiceImpl(tts);

        TikTokScript script = createStandardScript();
        AudioTimelineManifest manifest = service.generateTimeline(script, tempDir);

        // Verify individual scene WAV has patched valid header (not 0xFFFFFFFF)
        Path sceneWav = manifest.sceneTimelines().get(0).audioFilePath();
        byte[] bytes = Files.readAllBytes(sceneWav);
        ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        int chunkSize = buf.getInt(4);
        int dataSize = buf.getInt(40);
        assertNotEquals(-1, chunkSize);
        assertNotEquals(-1, dataSize);
        assertEquals(bytes.length - 8, chunkSize);
        assertEquals(bytes.length - 44, dataSize);
    }

    @Test
    @DisplayName("Java Sound API verification on generated master WAV")
    void testJavaSoundApiReadability() throws Exception {
        TtsClient tts = new MockTts(1.0, false);
        AudioTimelineServiceImpl service = new AudioTimelineServiceImpl(tts, "Hải Đăng", 0.25);

        AudioTimelineManifest manifest = service.generateTimeline(createStandardScript(), tempDir);

        try (AudioInputStream ais = AudioSystem.getAudioInputStream(manifest.masterAudioPath().toFile())) {
            assertEquals(48000.0f, ais.getFormat().getSampleRate());
            assertEquals(1, ais.getFormat().getChannels());
            assertEquals(16, ais.getFormat().getSampleSizeInBits());
            long expectedFrames = (long) (4.75 * 48000);
            assertEquals(expectedFrames, ais.getFrameLength());
        }
    }

    @Test
    @DisplayName("Throws AudioProcessingException when TTS client throws TtsException")
    void testTtsFailureHandling() {
        TtsClient faultyTts = request -> {
            throw new TtsException("Connection refused", 503, "Service Unavailable");
        };
        AudioTimelineServiceImpl service = new AudioTimelineServiceImpl(faultyTts);

        assertThrows(AudioProcessingException.class, () -> 
            service.generateTimeline(createStandardScript(), tempDir));
    }

    @Test
    @DisplayName("Throws IllegalArgumentException for null script or null outputDir")
    void testNullArguments() {
        TtsClient tts = new MockTts(1.0, false);
        AudioTimelineServiceImpl service = new AudioTimelineServiceImpl(tts);

        assertThrows(IllegalArgumentException.class, () -> service.generateTimeline(null, tempDir));
        assertThrows(IllegalArgumentException.class, () -> service.generateTimeline(createStandardScript(), null));
    }

    @Test
    @DisplayName("Throws AudioProcessingException for empty scenes")
    void testEmptyScenes() {
        TtsClient tts = new MockTts(1.0, false);
        AudioTimelineServiceImpl service = new AudioTimelineServiceImpl(tts);
        TikTokScript emptyScript = new TikTokScript("Family", "Band 7+", List.of());

        assertThrows(AudioProcessingException.class, () -> service.generateTimeline(emptyScript, tempDir));
    }
}
