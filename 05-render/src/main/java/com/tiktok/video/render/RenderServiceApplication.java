package com.tiktok.video.render;

import com.tiktok.video.core.contract.FallbackAssetGenerator;
import com.tiktok.video.core.contract.VideoComposer;
import com.tiktok.video.render.composer.FfmpegVideoComposer;
import com.tiktok.video.render.generator.FfmpegFallbackAssetGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class RenderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(RenderServiceApplication.class, args);
    }

    @Bean
    public VideoComposer videoComposer(@Value("${tiktok.render.ffmpeg-path:ffmpeg}") String ffmpegPath) {
        return new FfmpegVideoComposer(ffmpegPath);
    }

    @Bean
    public FallbackAssetGenerator fallbackAssetGenerator(@Value("${tiktok.render.ffmpeg-path:ffmpeg}") String ffmpegPath) {
        return new FfmpegFallbackAssetGenerator(ffmpegPath);
    }
}
