package com.tiktok.video.subtitle;

import com.tiktok.video.core.contract.SubtitleGenerator;
import com.tiktok.video.subtitle.generator.AssSubtitleGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class SubtitleServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SubtitleServiceApplication.class, args);
    }

    @Bean
    public SubtitleGenerator subtitleGenerator(
            @Value("${tiktok.subtitle.font-name:Arial}") String fontName,
            @Value("${tiktok.subtitle.font-size:68}") int fontSize) {
        return new AssSubtitleGenerator(fontName, fontSize);
    }
}
