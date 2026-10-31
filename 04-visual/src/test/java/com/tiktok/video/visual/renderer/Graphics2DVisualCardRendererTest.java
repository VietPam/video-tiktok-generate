package com.tiktok.video.visual.renderer;

import com.tiktok.video.core.model.Scene;
import com.tiktok.video.core.model.TikTokScript;
import com.tiktok.video.core.model.VocabularyItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Graphics2DVisualCardRendererTest {

    private Graphics2DVisualCardRenderer renderer;

    @BeforeEach
    void setUp() {
        renderer = new Graphics2DVisualCardRenderer();
    }

    @Test
    void createCardImage_shouldProduceCorrectDimensions() {
        VocabularyItem item = new VocabularyItem("Sibling", "/ˈsɪb.lɪŋ/", "noun", "Anh chị em ruột", "sibling rivalry", "sự ganh đua giữa anh chị em", "I have two siblings.");
        BufferedImage img = renderer.createCardImage("Family", item);

        assertThat(img).isNotNull();
        assertThat(img.getWidth()).isEqualTo(1080);
        assertThat(img.getHeight()).isEqualTo(1920);
        assertThat(img.getType()).isEqualTo(BufferedImage.TYPE_INT_ARGB);
    }

    @Test
    void renderCards_shouldWritePngsToDisk(@TempDir Path tempDir) throws IOException {
        VocabularyItem item1 = new VocabularyItem("Sibling", "/ˈsɪb.lɪŋ/", "noun", "Anh chị em ruột", "sibling rivalry", "ganh đua anh em", "I have two siblings.");
        VocabularyItem item2 = new VocabularyItem("Relative", "/ˈrel.ə.tɪv/", "noun", "Họ hàng thân thích", "distant relative", "họ hàng xa", "Visit relatives often.");

        Scene s1 = Scene.hook("hook_1", 0, "Learn IELTS vocabulary", "Learn IELTS");
        Scene s2 = Scene.vocabulary("vocab_1", 1, "Word 1 is Sibling", "Sibling", item1);
        Scene s3 = Scene.vocabulary("vocab_2", 2, "Word 2 is Relative", "Relative", item2);

        TikTokScript script = new TikTokScript("Family", "Band 7+", List.of(s1, s2, s3));

        Map<String, Path> cards = renderer.renderCards(script, tempDir);

        assertThat(cards).hasSize(3);
        assertThat(cards).containsKey("hook_1");
        assertThat(cards).containsKey("vocab_1");
        assertThat(cards).containsKey("vocab_2");

        Path card1 = cards.get("vocab_1");
        assertThat(Files.exists(card1)).isTrue();
        assertThat(card1.toString()).endsWith(".png");

        BufferedImage readImg = ImageIO.read(card1.toFile());
        assertThat(readImg.getWidth()).isEqualTo(1080);
        assertThat(readImg.getHeight()).isEqualTo(1920);
    }

    @Test
    void renderCards_shouldThrowOnNullArguments(@TempDir Path tempDir) {
        assertThrows(IllegalArgumentException.class, () -> renderer.renderCards(null, tempDir));
    }
}
