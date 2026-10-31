package com.tiktok.video.audio;

import com.tiktok.video.audio.tts.SyntheticWavGenerator;
import com.tiktok.video.audio.tts.VieneuTtsClient;
import com.tiktok.video.core.contract.AudioTimelineService;
import com.tiktok.video.core.contract.TtsClient;
import com.tiktok.video.core.contract.TtsRequest;
import com.tiktok.video.core.exception.TtsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class AudioServiceApplication {

    private static final Logger log = LoggerFactory.getLogger(AudioServiceApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(AudioServiceApplication.class, args);
    }

    @Bean
    public TtsClient ttsClient(
            @Value("${tiktok.tts.base-url:http://localhost:8000}") String baseUrl,
            @Value("${tiktok.tts.mode:auto}") String mode) {
        if ("synthetic".equalsIgnoreCase(mode)) {
            log.info("Configured TtsClient with SyntheticWavGenerator (mode=synthetic)");
            return new SyntheticWavGenerator();
        }
        VieneuTtsClient liveClient = new VieneuTtsClient(baseUrl);
        SyntheticWavGenerator fallback = new SyntheticWavGenerator();
        log.info("Configured TtsClient with VieneuTtsClient (baseUrl={}) and SyntheticWavGenerator fallback", baseUrl);
        return new TtsClient() {
            @Override
            public byte[] synthesize(TtsRequest request) throws TtsException {
                try {
                    if (liveClient.isAvailable()) {
                        return liveClient.synthesize(request);
                    }
                } catch (Exception e) {
                    log.warn("Live TTS synthesis failed ({}: {}), using fallback synthetic generator",
                            e.getClass().getSimpleName(), e.getMessage());
                }
                return fallback.synthesize(request);
            }
        };
    }

    @Bean
    public AudioTimelineService audioTimelineService(TtsClient ttsClient) {
        return new AudioTimelineServiceImpl(ttsClient);
    }
}
