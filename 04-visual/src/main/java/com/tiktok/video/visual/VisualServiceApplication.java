package com.tiktok.video.visual;

import com.tiktok.video.core.contract.VisualCardRenderer;
import com.tiktok.video.visual.renderer.Graphics2DVisualCardRenderer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class VisualServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(VisualServiceApplication.class, args);
    }

    @Bean
    public VisualCardRenderer visualCardRenderer() {
        return new Graphics2DVisualCardRenderer();
    }
}
