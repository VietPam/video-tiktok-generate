package com.tiktok.video.audio.util;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * High-performance pure-Java utility for WAV audio manipulation, duration calculation,
 * and header patching for streaming TTS engines.
 * Supports dynamic RIFF FourCC chunk traversal to locate the 'data' chunk header
 * and ensures trailing metadata chunks (e.g. LIST, INFO, id3) are not read as PCM audio.
 */
public final class WavAudioUtil {

    public static final int HEADER_SIZE = 44;
    public static final int WAV_HEADER_SIZE = 44;
    public static final int RIFF_SIZE_OFFSET = 4;
    public static final int DATA_SIZE_OFFSET = 40;

    public static final int DEFAULT_SAMPLE_RATE = 48000;
    public static final int DEFAULT_CHANNELS = 1; // mono
    public static final int DEFAULT_BITS_PER_SAMPLE = 16;
    public static final int BYTES_PER_SAMPLE = DEFAULT_BITS_PER_SAMPLE / 8; // 2
    public static final int DEFAULT_BYTES_PER_SAMPLE = BYTES_PER_SAMPLE;
    public static final int BYTES_PER_SECOND = DEFAULT_SAMPLE_RATE * DEFAULT_CHANNELS * BYTES_PER_SAMPLE; // 96000
    public static final int DEFAULT_BYTES_PER_SECOND = BYTES_PER_SECOND;

    private WavAudioUtil() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Patches the WAV header in-place in the provided byte array.
     * Fixes the streaming 0xFFFFFFFF placeholder sizes emitted by chunked TTS engines.
     * Bytes 4-7 are set to (fileSize - 8).
     * The data chunk size is set to (fileSize - (dataChunkOffset + 8)).
     *
     * @param wavBytes the WAV audio byte array to patch in-place
     * @return the same byte array with patched sizes
     * @throws IllegalArgumentException if wavBytes is null, shorter than 44 bytes, or lacks RIFF/WAVE signatures
     */
    public static byte[] patchWavHeaderInPlace(byte[] wavBytes) {
        Objects.requireNonNull(wavBytes, "wavBytes must not be null");
        if (wavBytes.length < HEADER_SIZE) {
            throw new IllegalArgumentException("WAV byte array must be at least 44 bytes, but was: " + wavBytes.length);
        }

        // Validate RIFF signature
        if (wavBytes[0] != 'R' || wavBytes[1] != 'I' || wavBytes[2] != 'F' || wavBytes[3] != 'F') {
            throw new IllegalArgumentException("Invalid WAV header: missing 'RIFF' signature");
        }
        // Validate WAVE signature
        if (wavBytes[8] != 'W' || wavBytes[9] != 'A' || wavBytes[10] != 'V' || wavBytes[11] != 'E') {
            throw new IllegalArgumentException("Invalid WAV header: missing 'WAVE' signature");
        }

        int fileSize = wavBytes.length;
        int riffSize = fileSize - 8;

        int dataChunkOffset = findDataChunkOffset(wavBytes);
        int dataSize = fileSize - (dataChunkOffset + 8);

        ByteBuffer buf = ByteBuffer.wrap(wavBytes).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(RIFF_SIZE_OFFSET, riffSize);
        buf.putInt(dataChunkOffset + 4, dataSize);

        return wavBytes;
    }

    /**
     * Alias for patchWavHeaderInPlace.
     */
    public static byte[] patchHeader(byte[] wavBytes) {
        return patchWavHeaderInPlace(wavBytes);
    }

    /**
     * Dynamically finds the byte offset of the 'data' FourCC chunk in a WAV byte array.
     * Employs RIFF chunk-hopping traversal with fallback linear scanning.
     *
     * @param wavBytes the WAV byte array
     * @return 0-indexed byte offset of the 'data' FourCC
     * @throws IllegalArgumentException if 'data' chunk cannot be found
     */
    public static int findDataChunkOffset(byte[] wavBytes) {
        Objects.requireNonNull(wavBytes, "wavBytes must not be null");
        if (wavBytes.length < HEADER_SIZE) {
            throw new IllegalArgumentException("WAV byte array must be at least 44 bytes, but was: " + wavBytes.length);
        }

        // Fast path: standard 44-byte WAV header where 'data' is at offset 36
        if (wavBytes[36] == 'd' && wavBytes[37] == 'a' && wavBytes[38] == 't' && wavBytes[39] == 'a') {
            return 36;
        }

        // RIFF chunk-hopping traversal starting at offset 12
        int offset = 12;
        while (offset + 8 <= wavBytes.length) {
            if (wavBytes[offset] == 'd' && wavBytes[offset + 1] == 'a'
                    && wavBytes[offset + 2] == 't' && wavBytes[offset + 3] == 'a') {
                return offset;
            }
            int chunkSize = (wavBytes[offset + 4] & 0xFF)
                    | ((wavBytes[offset + 5] & 0xFF) << 8)
                    | ((wavBytes[offset + 6] & 0xFF) << 16)
                    | ((wavBytes[offset + 7] & 0xFF) << 24);

            if (chunkSize < 0) {
                // Negative size (e.g. streaming 0xFFFFFFFF) - break to linear scan
                break;
            }
            long next = (long) offset + 8 + chunkSize + (chunkSize & 1);
            if (next > wavBytes.length - 8 || next <= offset + 8) {
                break;
            }
            offset = (int) next;
        }

        // Fallback: search for 'data' FourCC across header/bytes
        for (int i = 12; i <= wavBytes.length - 8; i++) {
            if (wavBytes[i] == 'd' && wavBytes[i + 1] == 'a'
                    && wavBytes[i + 2] == 't' && wavBytes[i + 3] == 'a') {
                return i;
            }
        }
        throw new IllegalArgumentException("Invalid WAV header: missing 'data' chunk");
    }

    /**
     * Dynamically finds the byte offset of the 'data' FourCC chunk in a WAV file using RandomAccessFile.
     * Employs RIFF chunk-hopping traversal with fallback scanning.
     *
     * @param raf the open RandomAccessFile
     * @return 0-indexed byte offset of the 'data' FourCC
     * @throws IOException if an I/O error occurs
     * @throws IllegalArgumentException if 'data' chunk cannot be found
     */
    public static int findDataChunkOffset(RandomAccessFile raf) throws IOException {
        Objects.requireNonNull(raf, "raf must not be null");
        long length = raf.length();
        if (length < HEADER_SIZE) {
            throw new IllegalArgumentException("File size is less than 44 bytes: " + length);
        }

        // Fast path: standard 44-byte WAV header where 'data' is at offset 36
        byte[] fourCc = new byte[4];
        raf.seek(36);
        raf.readFully(fourCc);
        if (fourCc[0] == 'd' && fourCc[1] == 'a' && fourCc[2] == 't' && fourCc[3] == 'a') {
            return 36;
        }

        // RIFF chunk-hopping traversal starting at offset 12
        long offset = 12;
        byte[] sizeBuf = new byte[4];
        while (offset + 8 <= length) {
            raf.seek(offset);
            raf.readFully(fourCc);
            if (fourCc[0] == 'd' && fourCc[1] == 'a' && fourCc[2] == 't' && fourCc[3] == 'a') {
                return (int) offset;
            }
            raf.readFully(sizeBuf);
            int chunkSize = (sizeBuf[0] & 0xFF)
                    | ((sizeBuf[1] & 0xFF) << 8)
                    | ((sizeBuf[2] & 0xFF) << 16)
                    | ((sizeBuf[3] & 0xFF) << 24);

            if (chunkSize < 0) {
                // Negative size (e.g. streaming 0xFFFFFFFF) - break to scan
                break;
            }
            long next = offset + 8 + chunkSize + (chunkSize & 1);
            if (next > length - 8 || next <= offset + 8) {
                break;
            }
            offset = next;
        }

        // Fallback: scan for 'data' FourCC across header/bytes
        int scanLimit = (int) Math.min(length, 65536L);
        byte[] scanBuffer = new byte[scanLimit];
        raf.seek(0);
        raf.readFully(scanBuffer);
        for (int i = 12; i <= scanLimit - 8; i++) {
            if (scanBuffer[i] == 'd' && scanBuffer[i + 1] == 'a'
                    && scanBuffer[i + 2] == 't' && scanBuffer[i + 3] == 'a') {
                return i;
            }
        }

        if (length > scanLimit) {
            long currentPos = scanLimit - 3;
            byte[] block = new byte[4096];
            while (currentPos < length - 4) {
                raf.seek(currentPos);
                int bytesRead = raf.read(block);
                if (bytesRead < 4) break;
                for (int i = 0; i <= bytesRead - 4; i++) {
                    if (block[i] == 'd' && block[i + 1] == 'a'
                            && block[i + 2] == 't' && block[i + 3] == 'a') {
                        return (int) (currentPos + i);
                    }
                }
                currentPos += (bytesRead - 3);
            }
        }

        throw new IllegalArgumentException("Invalid WAV file: missing 'data' chunk");
    }

    /**
     * Dynamically finds the byte offset of the 'data' FourCC chunk in a WAV file path.
     */
    public static int findDataChunkOffset(Path wavFilePath) throws IOException {
        Objects.requireNonNull(wavFilePath, "wavFilePath must not be null");
        try (RandomAccessFile raf = new RandomAccessFile(wavFilePath.toFile(), "r")) {
            return findDataChunkOffset(raf);
        }
    }

    /**
     * Patches an existing WAV file on disk directly using RandomAccessFile.
     * Dynamically locates the 'data' chunk FourCC offset rather than assuming offset 40.
     * Fixes the streaming 0xFFFFFFFF placeholder sizes emitted by chunked TTS engines.
     *
     * @param wavFilePath path to the WAV file on disk
     * @throws IOException if an I/O error occurs
     * @throws IllegalArgumentException if file does not exist, is invalid, or missing data chunk
     */
    public static void patchWavFile(Path wavFilePath) throws IOException {
        Objects.requireNonNull(wavFilePath, "wavFilePath must not be null");
        if (!Files.exists(wavFilePath)) {
            throw new IllegalArgumentException("File does not exist: " + wavFilePath);
        }

        try (RandomAccessFile raf = new RandomAccessFile(wavFilePath.toFile(), "rw")) {
            long length = raf.length();
            if (length < HEADER_SIZE) {
                throw new IllegalArgumentException("File size is less than 44 bytes: " + length);
            }
            if (length > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("File size exceeds 2GB maximum WAV limit: " + length);
            }

            byte[] signature = new byte[12];
            raf.readFully(signature);
            if (signature[0] != 'R' || signature[1] != 'I' || signature[2] != 'F' || signature[3] != 'F'
                    || signature[8] != 'W' || signature[9] != 'A' || signature[10] != 'V' || signature[11] != 'E') {
                throw new IllegalArgumentException("File is not a valid RIFF/WAVE file: " + wavFilePath);
            }

            // Dynamically locate 'data' chunk offset
            int dataChunkOffset = findDataChunkOffset(raf);

            int riffSize = (int) (length - 8);
            int dataSize = (int) (length - (dataChunkOffset + 8));

            byte[] leRiff = new byte[4];
            ByteBuffer.wrap(leRiff).order(ByteOrder.LITTLE_ENDIAN).putInt(riffSize);
            raf.seek(RIFF_SIZE_OFFSET);
            raf.write(leRiff);

            byte[] leData = new byte[4];
            ByteBuffer.wrap(leData).order(ByteOrder.LITTLE_ENDIAN).putInt(dataSize);
            raf.seek(dataChunkOffset + 4);
            raf.write(leData);
        }
    }

    /**
     * Calculates duration in seconds from raw PCM byte length and audio format parameters.
     */
    public static double calculateDurationSeconds(long pcmBytes, int sampleRate, int channels, int bitsPerSample) {
        if (pcmBytes < 0) {
            throw new IllegalArgumentException("pcmBytes cannot be negative: " + pcmBytes);
        }
        if (sampleRate <= 0 || channels <= 0 || bitsPerSample <= 0) {
            throw new IllegalArgumentException("Audio format parameters must be positive");
        }
        long bytesPerSec = (long) sampleRate * channels * (bitsPerSample / 8);
        return (double) pcmBytes / bytesPerSec;
    }

    /**
     * Calculates duration in seconds for default 48kHz mono 16-bit PCM (96,000 bytes/sec).
     */
    public static double calculateDurationSeconds(long pcmBytes) {
        return calculateDurationSeconds(pcmBytes, DEFAULT_SAMPLE_RATE, DEFAULT_CHANNELS, DEFAULT_BITS_PER_SAMPLE);
    }

    public static double calculateDurationSeconds(int pcmBytes) {
        return calculateDurationSeconds((long) pcmBytes, DEFAULT_SAMPLE_RATE, DEFAULT_CHANNELS, DEFAULT_BITS_PER_SAMPLE);
    }

    /**
     * Calculates duration in seconds from a complete WAV byte array.
     * Dynamically accounts for header size and reads only audio dataChunkSize if available.
     */
    public static double calculateWavDurationSeconds(byte[] wavBytes) {
        Objects.requireNonNull(wavBytes, "wavBytes must not be null");
        if (wavBytes.length < HEADER_SIZE) {
            throw new IllegalArgumentException("wavBytes length must be >= 44 bytes");
        }
        try {
            int dataOffset = findDataChunkOffset(wavBytes);
            int pcmStart = dataOffset + 8;
            if (pcmStart <= wavBytes.length) {
                int dataSize = (wavBytes[dataOffset + 4] & 0xFF)
                        | ((wavBytes[dataOffset + 5] & 0xFF) << 8)
                        | ((wavBytes[dataOffset + 6] & 0xFF) << 16)
                        | ((wavBytes[dataOffset + 7] & 0xFF) << 24);

                if (dataSize > 0 && (long) pcmStart + dataSize <= wavBytes.length) {
                    return calculateDurationSeconds(dataSize);
                }
                return calculateDurationSeconds(wavBytes.length - pcmStart);
            }
        } catch (Exception ignored) {
            // Fallback for non-standard / raw headers
        }
        return calculateDurationSeconds(wavBytes.length - HEADER_SIZE);
    }

    /**
     * Calculates duration in seconds from a WAV file on disk.
     * Dynamically accounts for header size and reads only audio dataChunkSize if available.
     */
    public static double calculateWavDurationSeconds(Path wavFile) throws IOException {
        Objects.requireNonNull(wavFile, "wavFile must not be null");
        long size = Files.size(wavFile);
        if (size < HEADER_SIZE) {
            throw new IllegalArgumentException("WAV file size must be >= 44 bytes");
        }
        try (RandomAccessFile raf = new RandomAccessFile(wavFile.toFile(), "r")) {
            int dataOffset = findDataChunkOffset(raf);
            int pcmStart = dataOffset + 8;
            if (pcmStart <= size) {
                raf.seek(dataOffset + 4);
                byte[] sizeBuf = new byte[4];
                raf.readFully(sizeBuf);
                int dataSize = (sizeBuf[0] & 0xFF)
                        | ((sizeBuf[1] & 0xFF) << 8)
                        | ((sizeBuf[2] & 0xFF) << 16)
                        | ((sizeBuf[3] & 0xFF) << 24);

                if (dataSize > 0 && (long) pcmStart + dataSize <= size) {
                    return calculateDurationSeconds(dataSize);
                }
                return calculateDurationSeconds(size - pcmStart);
            }
        } catch (Exception ignored) {
            // Fallback
        }
        return calculateDurationSeconds(size - HEADER_SIZE);
    }

    /**
     * Extracts raw PCM samples from a WAV byte array.
     * Dynamically finds the 'data' chunk identifier to accommodate standard and non-standard WAV headers.
     * Trailing metadata chunks (such as LIST, INFO, id3) are NOT read into raw PCM audio;
     * only the declared dataChunkSize bytes are extracted.
     * If the byte array does not have a RIFF header, returns the byte array as-is.
     *
     * @param wavBytes the input WAV bytes
     * @return raw PCM audio samples
     */
    public static byte[] extractPcm(byte[] wavBytes) {
        if (wavBytes == null || wavBytes.length == 0) {
            return new byte[0];
        }
        if (wavBytes.length >= 12
                && wavBytes[0] == 'R' && wavBytes[1] == 'I' && wavBytes[2] == 'F' && wavBytes[3] == 'F'
                && wavBytes[8] == 'W' && wavBytes[9] == 'A' && wavBytes[10] == 'V' && wavBytes[11] == 'E') {

            int dataOffset;
            try {
                dataOffset = findDataChunkOffset(wavBytes);
            } catch (IllegalArgumentException e) {
                // Fallback to standard 44 bytes if data chunk FourCC was not found
                if (wavBytes.length >= WAV_HEADER_SIZE) {
                    return Arrays.copyOfRange(wavBytes, WAV_HEADER_SIZE, wavBytes.length);
                }
                return new byte[0];
            }

            int pcmStart = dataOffset + 8;
            if (pcmStart > wavBytes.length) {
                return new byte[0];
            }

            int dataSize = (wavBytes[dataOffset + 4] & 0xFF)
                    | ((wavBytes[dataOffset + 5] & 0xFF) << 8)
                    | ((wavBytes[dataOffset + 6] & 0xFF) << 16)
                    | ((wavBytes[dataOffset + 7] & 0xFF) << 24);

            if (dataSize == 0) {
                return new byte[0];
            }

            // If dataSize is valid (not streaming 0xFFFFFFFF) and does not exceed array bounds,
            // extract ONLY the declared dataChunkSize bytes, excluding trailing chunks (LIST, INFO, id3, etc.)
            if (dataSize > 0) {
                long pcmEnd = (long) pcmStart + dataSize;
                if (pcmEnd <= wavBytes.length) {
                    return Arrays.copyOfRange(wavBytes, pcmStart, (int) pcmEnd);
                }
            }

            // Fallback for unpatched streaming 0xFFFFFFFF or truncated audio
            return Arrays.copyOfRange(wavBytes, pcmStart, wavBytes.length);
        }
        return wavBytes;
    }

    /**
     * Builds a standard 44-byte RIFF/WAVE header with exact chunk sizes and appends PCM data.
     */
    public static byte[] buildWav(byte[] pcmData, int sampleRate, int channels, int bitsPerSample) {
        if (pcmData == null) {
            pcmData = new byte[0];
        }
        int byteRate = sampleRate * channels * (bitsPerSample / 8);
        int blockAlign = channels * (bitsPerSample / 8);
        int subChunk2Size = pcmData.length;
        int chunkSize = 36 + subChunk2Size;

        ByteBuffer buffer = ByteBuffer.allocate(WAV_HEADER_SIZE + subChunk2Size)
                .order(ByteOrder.LITTLE_ENDIAN);

        // RIFF header
        buffer.put((byte) 'R').put((byte) 'I').put((byte) 'F').put((byte) 'F');
        buffer.putInt(chunkSize);
        buffer.put((byte) 'W').put((byte) 'A').put((byte) 'V').put((byte) 'E');

        // fmt subchunk
        buffer.put((byte) 'f').put((byte) 'm').put((byte) 't').put((byte) ' ');
        buffer.putInt(16); // SubChunk1Size (16 for PCM)
        buffer.putShort((short) 1); // AudioFormat (1 = Linear PCM)
        buffer.putShort((short) channels);
        buffer.putInt(sampleRate);
        buffer.putInt(byteRate);
        buffer.putShort((short) blockAlign);
        buffer.putShort((short) bitsPerSample);

        // data subchunk
        buffer.put((byte) 'd').put((byte) 'a').put((byte) 't').put((byte) 'a');
        buffer.putInt(subChunk2Size);
        buffer.put(pcmData);

        return buffer.array();
    }

    public static byte[] buildWav(byte[] pcmData) {
        return buildWav(pcmData, DEFAULT_SAMPLE_RATE, DEFAULT_CHANNELS, DEFAULT_BITS_PER_SAMPLE);
    }

    /**
     * Generates a silence byte buffer (all zeros) aligned to 2-byte frame boundaries.
     */
    public static byte[] generateSilence(double durationSeconds, int sampleRate, int channels, int bitsPerSample) {
        if (durationSeconds < 0.0) {
            throw new IllegalArgumentException("durationSeconds cannot be negative: " + durationSeconds);
        }
        int byteRate = sampleRate * channels * (bitsPerSample / 8);
        int bytes = (int) Math.round(durationSeconds * byteRate);
        int frameSize = channels * (bitsPerSample / 8);
        int rem = bytes % frameSize;
        if (rem != 0) {
            bytes += (frameSize - rem);
        }
        return new byte[Math.max(0, bytes)];
    }

    public static byte[] generateSilence(double durationSeconds) {
        return generateSilence(durationSeconds, DEFAULT_SAMPLE_RATE, DEFAULT_CHANNELS, DEFAULT_BITS_PER_SAMPLE);
    }

    public static byte[] generateSilencePcm(double durationSeconds) {
        return generateSilence(durationSeconds);
    }

    /**
     * Generates silence as a valid 48kHz mono 16-bit PCM WAV.
     */
    public static byte[] generateSilenceWav(double durationSeconds) {
        return buildWav(generateSilence(durationSeconds));
    }

    /**
     * Concatenates multiple raw PCM byte arrays.
     */
    public static byte[] concatenatePcm(List<byte[]> pcmSegments) {
        Objects.requireNonNull(pcmSegments, "pcmSegments must not be null");
        long totalLength = 0;
        for (byte[] seg : pcmSegments) {
            if (seg != null) {
                totalLength += seg.length;
            }
        }
        if (totalLength > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Total PCM length exceeds 2GB maximum array limit: " + totalLength);
        }
        byte[] result = new byte[(int) totalLength];
        int offset = 0;
        for (byte[] seg : pcmSegments) {
            if (seg != null && seg.length > 0) {
                System.arraycopy(seg, 0, result, offset, seg.length);
                offset += seg.length;
            }
        }
        return result;
    }
}
