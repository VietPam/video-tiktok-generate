package com.tiktok.video.audio.tts;

import com.tiktok.video.audio.util.WavAudioUtil;
import com.tiktok.video.core.contract.TtsRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SyntheticWavGeneratorTest {

    @Test
    @DisplayName("F10: Synthesize valid WAV with SINE_TONE mode")
    void testSynthesizeSineTone() throws Exception {
        SyntheticWavGenerator generator = new SyntheticWavGenerator(SyntheticWavGenerator.ToneMode.SINE_TONE);
        TtsRequest request = new TtsRequest("Hello IELTS world", "model", "voice", "wav", 1.0);

        byte[] wav = generator.synthesize(request);
        assertThat(wav).isNotNull();
        assertThat(wav.length).isGreaterThan(44);

        double duration = WavAudioUtil.calculateWavDurationSeconds(wav);
        assertThat(duration).isGreaterThanOrEqualTo(1.0);

        byte[] pcm = WavAudioUtil.extractPcm(wav);
        assertThat(pcm).isNotEmpty();
        // SINE_TONE should contain non-zero samples
        boolean hasNonZero = false;
        for (byte b : pcm) {
            if (b != 0) {
                hasNonZero = true;
                break;
            }
        }
        assertThat(hasNonZero).isTrue();
    }

    @Test
    @DisplayName("F10: Synthesize valid WAV with SILENCE mode")
    void testSynthesizeSilence() throws Exception {
        SyntheticWavGenerator generator = new SyntheticWavGenerator(SyntheticWavGenerator.ToneMode.SILENCE);
        TtsRequest request = new TtsRequest("Hello IELTS world", "model", "voice", "wav", 1.0);

        byte[] wav = generator.synthesize(request);
        assertThat(wav).isNotNull();

        byte[] pcm = WavAudioUtil.extractPcm(wav);
        assertThat(pcm).containsOnly((byte) 0);
    }

    @Test
    @DisplayName("F10: Duration scales with word count")
    void testDurationScalesWithWordCount() throws Exception {
        SyntheticWavGenerator generator = new SyntheticWavGenerator(SyntheticWavGenerator.ToneMode.SILENCE, 2.0, 0.5);

        byte[] shortWav = generator.synthesize(new TtsRequest("Short phrase", "m", "v", "wav", 1.0));
        byte[] longWav = generator.synthesize(new TtsRequest("This is a much longer phrase with ten different words in it", "m", "v", "wav", 1.0));

        double shortDur = WavAudioUtil.calculateWavDurationSeconds(shortWav);
        double longDur = WavAudioUtil.calculateWavDurationSeconds(longWav);

        assertThat(longDur).isGreaterThan(shortDur);
    }

    @Test
    @DisplayName("Request validation throws IllegalArgumentException on null or blank text")
    void testRequestValidation() {
        SyntheticWavGenerator generator = new SyntheticWavGenerator();

        assertThatThrownBy(() -> generator.synthesize(null))
            .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> generator.synthesize(new TtsRequest("", "m", "v", "wav", 1.0)))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
