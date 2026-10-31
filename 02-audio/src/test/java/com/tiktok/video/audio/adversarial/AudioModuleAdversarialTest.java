package com.tiktok.video.audio.adversarial;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tiktok.video.audio.AudioTimelineServiceImpl;
import com.tiktok.video.audio.controller.AudioController;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class AudioModuleAdversarialTest {

    @TempDir
    Path tempDir;

    @Mock
    private TtsClient mockTtsClient;

    private AudioTimelineService audioTimelineService;
    private AudioController audioController;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        objectMapper = new ObjectMapper();
        audioTimelineService = new AudioTimelineServiceImpl(mockTtsClient);
        audioController = new AudioController(mockTtsClient, audioTimelineService, tempDir.toString());
    }

    // =========================================================================
    // 1. AudioController & AudioTimelineService: Concurrency Stress Testing
    // =========================================================================

    @Test
    @DisplayName("AudioController: 50 concurrent synthesize requests succeed without race conditions")
    void testConcurrentSynthesizeRequests() throws Exception {
        byte[] sampleWav = WavAudioUtil.buildWav(new byte[1920]); // 20ms
        when(mockTtsClient.synthesize(any(TtsRequest.class))).thenReturn(sampleWav);

        int concurrency = 50;
        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<ResponseEntity<byte[]>>> futures = new ArrayList<>();

        for (int i = 0; i < concurrency; i++) {
            final int index = i;
            futures.add(executor.submit(() -> {
                startLatch.await();
                TtsRequest req = TtsRequest.of("Concurrent audio phrase number " + index);
                return audioController.synthesize(req);
            }));
        }

        startLatch.countDown();
        executor.shutdown();
        assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        for (Future<ResponseEntity<byte[]>> f : futures) {
            ResponseEntity<byte[]> resp = f.get();
            assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
            assertThat(resp.getBody()).isEqualTo(sampleWav);
        }
    }

    // =========================================================================
    // 2. AudioController: Boundary Values & Zero/Empty Audio Byte Arrays
    // =========================================================================

    @Test
    @DisplayName("AudioController: Null TtsRequest throws IllegalArgumentException")
    void testControllerNullTtsRequest() {
        assertThatThrownBy(() -> audioController.synthesize(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TtsRequest cannot be null");
    }

    @Test
    @DisplayName("AudioController: Null TikTokScript throws IllegalArgumentException")
    void testControllerNullScript() {
        assertThatThrownBy(() -> audioController.generateTimeline(null, tempDir.toString()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TikTokScript cannot be null");
    }

    @Test
    @DisplayName("AudioController: Empty audio byte array from TTS returned cleanly on synthesize")
    void testControllerSynthesizeEmptyBytes() throws Exception {
        when(mockTtsClient.synthesize(any(TtsRequest.class))).thenReturn(new byte[0]);

        var resp = audioController.synthesize(TtsRequest.of("Test empty"));
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().length).isEqualTo(0);
    }

    @Test
    @DisplayName("AudioTimelineService: Empty scenes script throws AudioProcessingException")
    void testTimelineEmptyScenes() {
        TikTokScript emptyScript = new TikTokScript("Family", "Band 7+", List.of());
        assertThatThrownBy(() -> audioController.generateTimeline(emptyScript, tempDir.toString()))
                .isInstanceOf(AudioProcessingException.class)
                .hasMessageContaining("contains no scenes");
    }

    @Test
    @DisplayName("AudioTimelineService: Zero-byte audio from TTS during timeline generation throws AudioProcessingException")
    void testTimelineZeroByteAudioFromTts() throws Exception {
        when(mockTtsClient.synthesize(any(TtsRequest.class))).thenReturn(new byte[0]);

        TikTokScript script = new TikTokScript("Family", "Band 7+", List.of(
                Scene.hook("scene_00", 0, "Welcome spoken text", "Welcome")
        ));

        assertThatThrownBy(() -> audioController.generateTimeline(script, tempDir.toString()))
                .isInstanceOf(AudioProcessingException.class)
                .hasMessageContaining("returned empty audio");
    }

    @Test
    @DisplayName("AudioTimelineService: Zero-length PCM from 44-byte WAV header throws AudioProcessingException")
    void testTimelineZeroLengthPcmFromWav() throws Exception {
        // Standard 44-byte WAV header with 0 PCM payload
        byte[] emptyWav = WavAudioUtil.buildWav(new byte[0]);
        when(mockTtsClient.synthesize(any(TtsRequest.class))).thenReturn(emptyWav);

        TikTokScript script = new TikTokScript("Family", "Band 7+", List.of(
                Scene.hook("scene_00", 0, "Welcome spoken text", "Welcome")
        ));

        assertThatThrownBy(() -> audioController.generateTimeline(script, tempDir.toString()))
                .isInstanceOf(AudioProcessingException.class)
                .hasMessageContaining("Extracted PCM data is empty");
    }

    // =========================================================================
    // 3. WavAudioUtil: Zero-Length Data Chunks
    // =========================================================================

    @Test
    @DisplayName("WavAudioUtil: Standard 44-byte WAV with zero-length data chunk traversal and patching")
    void testStandardWavZeroLengthDataChunk() throws IOException {
        byte[] emptyWav = WavAudioUtil.buildWav(new byte[0]);
        assertThat(emptyWav.length).isEqualTo(44);

        // 1. findDataChunkOffset
        int offset = WavAudioUtil.findDataChunkOffset(emptyWav);
        assertThat(offset).isEqualTo(36);

        // 2. extractPcm
        byte[] pcm = WavAudioUtil.extractPcm(emptyWav);
        assertThat(pcm).isEmpty();

        // 3. calculateWavDurationSeconds
        double dur = WavAudioUtil.calculateWavDurationSeconds(emptyWav);
        assertThat(dur).isEqualTo(0.0);

        // 4. patchWavHeaderInPlace
        ByteBuffer buf = ByteBuffer.wrap(emptyWav).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(4, 0xFFFFFFFF);
        buf.putInt(40, 0xFFFFFFFF);
        WavAudioUtil.patchWavHeaderInPlace(emptyWav);
        assertThat(buf.getInt(4)).isEqualTo(36); // 44 - 8
        assertThat(buf.getInt(40)).isEqualTo(0);  // 44 - 44

        // 5. patchWavFile on disk
        Path diskFile = tempDir.resolve("zero_data.wav");
        Files.write(diskFile, emptyWav);
        WavAudioUtil.patchWavFile(diskFile);
        byte[] readBack = Files.readAllBytes(diskFile);
        ByteBuffer diskBuf = ByteBuffer.wrap(readBack).order(ByteOrder.LITTLE_ENDIAN);
        assertThat(diskBuf.getInt(4)).isEqualTo(36);
        assertThat(diskBuf.getInt(40)).isEqualTo(0);
        assertThat(WavAudioUtil.calculateWavDurationSeconds(diskFile)).isEqualTo(0.0);
    }

    @Test
    @DisplayName("WavAudioUtil: Non-standard WAV with JUNK chunk and zero-length data chunk")
    void testNonStandardWavWithJunkAndZeroDataChunk() throws IOException {
        // 12 (RIFF) + 24 (fmt) + 28 (JUNK) + 8 (data header) + 0 PCM = 72 bytes total
        ByteBuffer buf = ByteBuffer.allocate(72).order(ByteOrder.LITTLE_ENDIAN);
        buf.put("RIFF".getBytes()).putInt(0xFFFFFFFF).put("WAVE".getBytes());
        buf.put("fmt ".getBytes()).putInt(16).putShort((short) 1).putShort((short) 1)
                .putInt(48000).putInt(96000).putShort((short) 2).putShort((short) 16);
        buf.put("JUNK".getBytes()).putInt(20).put(new byte[20]);
        buf.put("data".getBytes()).putInt(0xFFFFFFFF); // zero data

        byte[] nonStandardWav = buf.array();

        // 1. findDataChunkOffset
        int offset = WavAudioUtil.findDataChunkOffset(nonStandardWav);
        assertThat(offset).isEqualTo(64);

        // 2. patchWavHeaderInPlace
        WavAudioUtil.patchWavHeaderInPlace(nonStandardWav);
        ByteBuffer patchedBuf = ByteBuffer.wrap(nonStandardWav).order(ByteOrder.LITTLE_ENDIAN);
        assertThat(patchedBuf.getInt(4)).isEqualTo(72 - 8);
        assertThat(patchedBuf.getInt(68)).isEqualTo(0); // dataSize = 72 - 72 = 0
        assertThat(patchedBuf.getInt(40)).isEqualTo(20); // JUNK size preserved!

        // 3. extractPcm
        byte[] pcm = WavAudioUtil.extractPcm(nonStandardWav);
        assertThat(pcm).isEmpty();

        // 4. calculateWavDurationSeconds
        double dur = WavAudioUtil.calculateWavDurationSeconds(nonStandardWav);
        assertThat(dur).isEqualTo(0.0);

        // 5. patchWavFile on disk
        Path diskFile = tempDir.resolve("non_standard_zero.wav");
        Files.write(diskFile, nonStandardWav);
        WavAudioUtil.patchWavFile(diskFile);
        assertThat(WavAudioUtil.calculateWavDurationSeconds(diskFile)).isEqualTo(0.0);
    }

    // =========================================================================
    // 4. WavAudioUtil: Missing fmt Chunks
    // =========================================================================

    @Test
    @DisplayName("WavAudioUtil: Missing fmt chunk (RIFF, WAVE, JUNK, data) traverses and extracts correctly")
    void testMissingFmtChunkTraversal() {
        byte[] rawPcm = new byte[]{10, 20, 30, 40, 50, 60, 70, 80};
        // 12 (RIFF) + 16 (JUNK: 8 header + 8 payload) + 8 (data header) + 8 (pcm) = 44 bytes
        ByteBuffer buf = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
        buf.put("RIFF".getBytes()).putInt(36).put("WAVE".getBytes());
        buf.put("JUNK".getBytes()).putInt(8).put(new byte[8]);
        buf.put("data".getBytes()).putInt(rawPcm.length).put(rawPcm);

        byte[] wavNoFmt = buf.array();

        // 1. findDataChunkOffset successfully locates 'data' at offset 28
        int dataOffset = WavAudioUtil.findDataChunkOffset(wavNoFmt);
        assertThat(dataOffset).isEqualTo(28);

        // 2. extractPcm successfully extracts the 8 bytes of PCM data
        byte[] extracted = WavAudioUtil.extractPcm(wavNoFmt);
        assertThat(extracted).isEqualTo(rawPcm);

        // 3. patchWavHeaderInPlace operates on the data chunk properly
        buf.putInt(4, 0xFFFFFFFF);
        buf.putInt(dataOffset + 4, 0xFFFFFFFF);
        WavAudioUtil.patchWavHeaderInPlace(wavNoFmt);
        assertThat(buf.getInt(4)).isEqualTo(36);
        assertThat(buf.getInt(dataOffset + 4)).isEqualTo(8);
    }

    // =========================================================================
    // 5. WavAudioUtil: Trailing Bytes Handling
    // =========================================================================

    @Test
    @DisplayName("WavAudioUtil: Trailing bytes (e.g. 10,000 metadata bytes) are completely excluded from PCM")
    void testTrailingBytesExclusion() throws IOException {
        byte[] pcmData = new byte[9600]; // 0.1s audio = 4800 samples
        Arrays.fill(pcmData, (byte) 77);

        byte[] standardWav = WavAudioUtil.buildWav(pcmData);
        byte[] trailingMetadata = new byte[10_000];
        Arrays.fill(trailingMetadata, (byte) 0xEE);

        byte[] wavWithTrailing = new byte[standardWav.length + trailingMetadata.length];
        System.arraycopy(standardWav, 0, wavWithTrailing, 0, standardWav.length);
        System.arraycopy(trailingMetadata, 0, wavWithTrailing, standardWav.length, trailingMetadata.length);

        // Update RIFF size to reflect total file size
        ByteBuffer.wrap(wavWithTrailing).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(4, wavWithTrailing.length - 8);

        // 1. extractPcm MUST return strictly 9600 bytes and contain none of trailing 0xEE
        byte[] extracted = WavAudioUtil.extractPcm(wavWithTrailing);
        assertThat(extracted.length).isEqualTo(9600);
        assertThat(extracted).isEqualTo(pcmData);

        // 2. calculateWavDurationSeconds MUST be strictly 0.1s, NOT inflated by trailing 10,000 bytes
        double durArray = WavAudioUtil.calculateWavDurationSeconds(wavWithTrailing);
        assertThat(durArray).isEqualTo(0.1);

        // 3. Disk file duration calculation
        Path diskFile = tempDir.resolve("trailing_meta.wav");
        Files.write(diskFile, wavWithTrailing);
        double durFile = WavAudioUtil.calculateWavDurationSeconds(diskFile);
        assertThat(durFile).isEqualTo(0.1);
    }

    @Test
    @DisplayName("WavAudioUtil: Truncated WAV header (<44 bytes) or missing data chunk throws IllegalArgumentException")
    void testTruncatedWavValidation() {
        assertThatThrownBy(() -> WavAudioUtil.findDataChunkOffset(new byte[30]))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least 44 bytes");

        // 44 bytes containing RIFF and WAVE but no 'data' chunk
        byte[] noDataChunk = new byte[44];
        System.arraycopy("RIFF".getBytes(), 0, noDataChunk, 0, 4);
        System.arraycopy("WAVE".getBytes(), 0, noDataChunk, 8, 4);
        Arrays.fill(noDataChunk, 12, 44, (byte) 'X');

        assertThatThrownBy(() -> WavAudioUtil.findDataChunkOffset(noDataChunk))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing 'data' chunk");
    }
}
