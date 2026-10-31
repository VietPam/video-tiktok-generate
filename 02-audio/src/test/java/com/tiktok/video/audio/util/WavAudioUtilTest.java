package com.tiktok.video.audio.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WavAudioUtilTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("F8: Build canonical 48kHz mono 16-bit PCM WAV header and verify fields")
    void testBuildWavHeaderValid() {
        byte[] pcmData = new byte[96000]; // 1 second of audio
        byte[] wav = WavAudioUtil.buildWav(pcmData);

        assertThat(wav.length).isEqualTo(44 + 96000);

        ByteBuffer buf = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN);

        // RIFF header
        byte[] riff = new byte[4];
        buf.get(riff);
        assertThat(new String(riff)).isEqualTo("RIFF");
        assertThat(buf.getInt()).isEqualTo(36 + 96000); // ChunkSize

        byte[] wave = new byte[4];
        buf.get(wave);
        assertThat(new String(wave)).isEqualTo("WAVE");

        // fmt subchunk
        byte[] fmt = new byte[4];
        buf.get(fmt);
        assertThat(new String(fmt)).isEqualTo("fmt ");
        assertThat(buf.getInt()).isEqualTo(16); // SubChunk1Size
        assertThat(buf.getShort()).isEqualTo((short) 1); // AudioFormat PCM
        assertThat(buf.getShort()).isEqualTo((short) 1); // Channels = 1
        assertThat(buf.getInt()).isEqualTo(48000); // SampleRate
        assertThat(buf.getInt()).isEqualTo(96000); // ByteRate
        assertThat(buf.getShort()).isEqualTo((short) 2); // BlockAlign
        assertThat(buf.getShort()).isEqualTo((short) 16); // BitsPerSample

        // data subchunk
        byte[] data = new byte[4];
        buf.get(data);
        assertThat(new String(data)).isEqualTo("data");
        assertThat(buf.getInt()).isEqualTo(96000); // SubChunk2Size
    }

    @Test
    @DisplayName("F8: Patch in-place 0xFFFFFFFF streaming WAV header sizes")
    void testPatchWavHeaderInPlace() {
        byte[] pcm = new byte[48000]; // 0.5s audio
        byte[] wav = WavAudioUtil.buildWav(pcm);

        // Simulate streaming 0xFFFFFFFF header from VieNeu-TTS
        ByteBuffer buf = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(4, 0xFFFFFFFF);
        buf.putInt(40, 0xFFFFFFFF);

        assertThat(buf.getInt(4)).isEqualTo(-1);
        assertThat(buf.getInt(40)).isEqualTo(-1);

        byte[] patched = WavAudioUtil.patchWavHeaderInPlace(wav);
        assertThat(patched).isSameAs(wav);

        ByteBuffer patchedBuf = ByteBuffer.wrap(patched).order(ByteOrder.LITTLE_ENDIAN);
        assertThat(patchedBuf.getInt(4)).isEqualTo(wav.length - 8);
        assertThat(patchedBuf.getInt(40)).isEqualTo(wav.length - 44);
    }

    @Test
    @DisplayName("F8: Patch WAV file on disk via RandomAccessFile")
    void testPatchWavFile() throws IOException {
        byte[] pcm = new byte[9600]; // 0.1s audio
        byte[] wav = WavAudioUtil.buildWav(pcm);

        ByteBuffer buf = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(4, 0xFFFFFFFF);
        buf.putInt(40, 0xFFFFFFFF);

        Path testFile = tempDir.resolve("test_streaming.wav");
        Files.write(testFile, wav);

        WavAudioUtil.patchWavFile(testFile);

        byte[] readBack = Files.readAllBytes(testFile);
        ByteBuffer readBuf = ByteBuffer.wrap(readBack).order(ByteOrder.LITTLE_ENDIAN);
        assertThat(readBuf.getInt(4)).isEqualTo(readBack.length - 8);
        assertThat(readBuf.getInt(40)).isEqualTo(readBack.length - 44);
    }

    @Test
    @DisplayName("F8: Duration calculations from PCM bytes and WAV bytes")
    void testDurationCalculations() {
        assertThat(WavAudioUtil.calculateDurationSeconds(96000)).isEqualTo(1.0);
        assertThat(WavAudioUtil.calculateDurationSeconds(48000)).isEqualTo(0.5);
        assertThat(WavAudioUtil.calculateDurationSeconds(0)).isEqualTo(0.0);

        byte[] pcm = new byte[96000];
        byte[] wav = WavAudioUtil.buildWav(pcm);
        assertThat(WavAudioUtil.calculateWavDurationSeconds(wav)).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Extract raw PCM data from WAV byte array")
    void testExtractPcm() {
        byte[] originalPcm = new byte[]{1, 2, 3, 4, 5, 6, 7, 8};
        byte[] wav = WavAudioUtil.buildWav(originalPcm);

        byte[] extracted = WavAudioUtil.extractPcm(wav);
        assertThat(extracted).isEqualTo(originalPcm);
    }

    @Test
    @DisplayName("Generate silence PCM and WAV with frame alignment")
    void testGenerateSilence() {
        byte[] silencePcm = WavAudioUtil.generateSilencePcm(0.25);
        // 0.25 * 96000 = 24000 bytes
        assertThat(silencePcm.length).isEqualTo(24000);
        assertThat(silencePcm).containsOnly((byte) 0);

        byte[] silenceWav = WavAudioUtil.generateSilenceWav(0.25);
        assertThat(silenceWav.length).isEqualTo(44 + 24000);
    }

    @Test
    @DisplayName("Concatenate multiple PCM byte arrays")
    void testConcatenatePcm() {
        byte[] seg1 = new byte[]{1, 2};
        byte[] seg2 = new byte[]{3, 4, 5};
        byte[] seg3 = new byte[]{6};

        byte[] combined = WavAudioUtil.concatenatePcm(List.of(seg1, seg2, seg3));
        assertThat(combined).containsExactly(1, 2, 3, 4, 5, 6);
    }

    @Test
    @DisplayName("Invalid input validations throw IllegalArgumentException")
    void testInvalidInputs() {
        assertThatThrownBy(() -> WavAudioUtil.patchWavHeaderInPlace(null))
            .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> WavAudioUtil.patchWavHeaderInPlace(new byte[20]))
            .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> WavAudioUtil.patchWavHeaderInPlace(new byte[44]))
            .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> WavAudioUtil.calculateDurationSeconds(-1))
            .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> WavAudioUtil.generateSilencePcm(-0.1))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Extract PCM excludes trailing metadata chunks (e.g. LIST INFO)")
    void testExtractPcmExcludesTrailingMetadataChunks() {
        byte[] originalPcm = new byte[]{1, 2, 3, 4, 5, 6, 7, 8};
        byte[] standardWav = WavAudioUtil.buildWav(originalPcm);

        // Append a mock LIST chunk (12 bytes)
        byte[] trailingChunk = "LIST\0\0\0\4INFO".getBytes();
        byte[] wavWithTrailing = new byte[standardWav.length + trailingChunk.length];
        System.arraycopy(standardWav, 0, wavWithTrailing, 0, standardWav.length);
        System.arraycopy(trailingChunk, 0, wavWithTrailing, standardWav.length, trailingChunk.length);

        // Update RIFF size
        ByteBuffer.wrap(wavWithTrailing).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(4, wavWithTrailing.length - 8);

        byte[] extracted = WavAudioUtil.extractPcm(wavWithTrailing);
        assertThat(extracted).isEqualTo(originalPcm);
        assertThat(extracted.length).isEqualTo(8);
    }

    @Test
    @DisplayName("Patch WAV file with non-standard header containing JUNK chunk before data chunk")
    void testPatchWavFileWithNonStandardHeader() throws IOException {
        byte[] pcm = new byte[9600];
        Arrays.fill(pcm, (byte) 42);

        // Build WAV with 28-byte JUNK chunk before data chunk
        // 12 (RIFF) + 24 (fmt) + 28 (JUNK) + 8 (data header) + 9600 = 9672 bytes total
        ByteBuffer buf = ByteBuffer.allocate(72 + pcm.length).order(ByteOrder.LITTLE_ENDIAN);
        buf.put("RIFF".getBytes());
        buf.putInt(0xFFFFFFFF); // streaming RIFF size
        buf.put("WAVE".getBytes());

        // fmt chunk (16 bytes payload)
        buf.put("fmt ".getBytes());
        buf.putInt(16);
        buf.putShort((short) 1);
        buf.putShort((short) 1);
        buf.putInt(48000);
        buf.putInt(96000);
        buf.putShort((short) 2);
        buf.putShort((short) 16);

        // JUNK chunk (20 bytes payload + 8 header = 28 bytes)
        buf.put("JUNK".getBytes());
        buf.putInt(20);
        buf.put(new byte[20]);

        // data chunk (streaming 0xFFFFFFFF)
        int dataChunkOffset = buf.position(); // offset 64
        assertThat(dataChunkOffset).isEqualTo(64);
        buf.put("data".getBytes());
        buf.putInt(0xFFFFFFFF);
        buf.put(pcm);

        Path testFile = tempDir.resolve("non_standard_streaming.wav");
        Files.write(testFile, buf.array());

        // Patch via RandomAccessFile
        WavAudioUtil.patchWavFile(testFile);

        byte[] readBack = Files.readAllBytes(testFile);
        ByteBuffer readBuf = ByteBuffer.wrap(readBack).order(ByteOrder.LITTLE_ENDIAN);

        // RIFF size must be fileSize - 8
        assertThat(readBuf.getInt(4)).isEqualTo(readBack.length - 8);
        // data size at dataChunkOffset + 4 (offset 68) must be exactly 9600
        assertThat(readBuf.getInt(68)).isEqualTo(9600);
        // Old hardcoded offset 40 (inside JUNK chunk) must remain unaltered
        assertThat(readBuf.getInt(40)).isEqualTo(20); // JUNK chunk size intact!
    }

    @Test
    @DisplayName("Patch in-place WAV header with non-standard JUNK chunk")
    void testPatchWavHeaderInPlaceWithNonStandardHeader() {
        byte[] pcm = new byte[4800];
        ByteBuffer buf = ByteBuffer.allocate(72 + pcm.length).order(ByteOrder.LITTLE_ENDIAN);
        buf.put("RIFF".getBytes()).putInt(0xFFFFFFFF).put("WAVE".getBytes());
        buf.put("fmt ".getBytes()).putInt(16).putShort((short) 1).putShort((short) 1)
           .putInt(48000).putInt(96000).putShort((short) 2).putShort((short) 16);
        buf.put("JUNK".getBytes()).putInt(20).put(new byte[20]);
        buf.put("data".getBytes()).putInt(0xFFFFFFFF).put(pcm);

        byte[] wavBytes = buf.array();
        WavAudioUtil.patchWavHeaderInPlace(wavBytes);

        ByteBuffer patchedBuf = ByteBuffer.wrap(wavBytes).order(ByteOrder.LITTLE_ENDIAN);
        assertThat(patchedBuf.getInt(4)).isEqualTo(wavBytes.length - 8);
        assertThat(patchedBuf.getInt(68)).isEqualTo(4800);
    }

    @Test
    @DisplayName("Calculate WAV duration correctly when trailing metadata chunk exists")
    void testCalculateWavDurationWithTrailingMetadata() throws IOException {
        byte[] pcm = new byte[96000]; // 1.0 second
        byte[] standardWav = WavAudioUtil.buildWav(pcm);

        // Add 5000 bytes trailing metadata
        byte[] trailing = new byte[5000];
        byte[] wavWithTrailing = new byte[standardWav.length + trailing.length];
        System.arraycopy(standardWav, 0, wavWithTrailing, 0, standardWav.length);
        System.arraycopy(trailing, 0, wavWithTrailing, standardWav.length, trailing.length);

        // Byte array duration must ignore trailing chunk
        assertThat(WavAudioUtil.calculateWavDurationSeconds(wavWithTrailing)).isEqualTo(1.0);

        // File duration must ignore trailing chunk
        Path testFile = tempDir.resolve("with_trailing.wav");
        Files.write(testFile, wavWithTrailing);
        assertThat(WavAudioUtil.calculateWavDurationSeconds(testFile)).isEqualTo(1.0);
    }
}
