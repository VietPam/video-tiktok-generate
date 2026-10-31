package com.tiktok.video.visual.renderer;

import com.tiktok.video.core.contract.VisualCardRenderer;
import com.tiktok.video.core.exception.VisualRenderException;
import com.tiktok.video.core.model.Scene;
import com.tiktok.video.core.model.TikTokScript;
import com.tiktok.video.core.model.VocabularyItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders high-resolution transparent PNG visual cards (1080x1920) using Java2D Graphics2D
 * for vocabulary words, IPA phonetic notation, definitions, and collocations.
 */
public class Graphics2DVisualCardRenderer implements VisualCardRenderer {

    private static final Logger log = LoggerFactory.getLogger(Graphics2DVisualCardRenderer.class);

    public static final int CANVAS_WIDTH = 1080;
    public static final int CANVAS_HEIGHT = 1920;

    @Override
    public Map<String, Path> renderCards(TikTokScript script, Path outputDir) throws VisualRenderException {
        if (script == null) throw new IllegalArgumentException("script cannot be null");
        if (outputDir == null) throw new IllegalArgumentException("outputDir cannot be null");

        try {
            Files.createDirectories(outputDir);
        } catch (IOException e) {
            throw new VisualRenderException("Failed to create output directory: " + outputDir, e);
        }

        Map<String, Path> cardMap = new HashMap<>();

        for (Scene scene : script.scenes()) {
            BufferedImage cardImage = null;
            if (scene.isVocabulary() && scene.vocabularyItem() != null) {
                cardImage = createCardImage(script.topic(), scene.vocabularyItem());
            } else if (scene.isHook()) {
                cardImage = createHookCardImage(script.topic(), scene);
            } else if (scene.isCallToAction()) {
                cardImage = createCtaCardImage(script.topic(), scene);
            }

            if (cardImage != null) {
                String fileName = "card_" + scene.id() + ".png";
                Path cardPath = outputDir.resolve(fileName);
                try {
                    ImageIO.write(cardImage, "PNG", cardPath.toFile());
                    cardMap.put(scene.id(), cardPath.toAbsolutePath());
                    log.info("Rendered visual card for scene {} at: {}", scene.id(), cardPath.toAbsolutePath());
                } catch (IOException e) {
                    throw new VisualRenderException("Failed to write card PNG for scene: " + scene.id(), e);
                }
            }
        }

        return cardMap;
    }

    public BufferedImage createCardImage(String topic, VocabularyItem item) {
        BufferedImage image = new BufferedImage(CANVAS_WIDTH, CANVAS_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();

        try {
            // High quality antialiasing
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

            // 1. TOP ILLUSTRATION IMAGE (Y: 150 to 750, 960x600)
            int imgX = 60;
            int imgY = 150;
            int imgW = CANVAS_WIDTH - 120; // 960px
            int imgH = 600;
            int imgArc = 48;

            // Load illustration image if exists
            BufferedImage illustImg = loadIllustrationForWord(item.word());
            if (illustImg != null) {
                Shape oldClip = g2d.getClip();
                g2d.setClip(new RoundRectangle2D.Double(imgX, imgY, imgW, imgH, imgArc, imgArc));
                g2d.drawImage(illustImg, imgX, imgY, imgW, imgH, null);
                g2d.setClip(oldClip);
            } else {
                // Fallback soft pastel gradient box
                GradientPaint bgGrad = new GradientPaint(imgX, imgY, new Color(225, 238, 255), imgX, imgY + imgH, new Color(245, 250, 255));
                g2d.setPaint(bgGrad);
                g2d.fill(new RoundRectangle2D.Double(imgX, imgY, imgW, imgH, imgArc, imgArc));
            }

            // Image border with crisp pastel outline & shadow
            g2d.setStroke(new BasicStroke(4.0f));
            g2d.setColor(new Color(255, 255, 255, 220));
            g2d.draw(new RoundRectangle2D.Double(imgX, imgY, imgW, imgH, imgArc, imgArc));

            // Cute pastel badge on top left of illustration (auto-sized to text width, 20px padding)
            String badgeText = "★ IELTS 7+ " + (topic != null ? topic.toUpperCase() : "FAMILY");
            g2d.setFont(new Font("SansSerif", Font.BOLD, 26));
            FontMetrics fmBadge = g2d.getFontMetrics();
            int badgeTextW = fmBadge.stringWidth(badgeText);
            int badgePadH = 20; // horizontal padding (each side)
            int badgePadV = 10; // vertical padding (each side)
            int badgeW = badgeTextW + (badgePadH * 2);
            int badgeH = fmBadge.getHeight() + (badgePadV * 2);
            int badgeX = imgX + 20;
            int badgeY = imgY + 20;

            g2d.setColor(new Color(255, 243, 205)); // Pastel light yellow
            g2d.fill(new RoundRectangle2D.Double(badgeX, badgeY, badgeW, badgeH, 24, 24));
            g2d.setColor(new Color(255, 180, 0));
            g2d.setStroke(new BasicStroke(2.0f));
            g2d.draw(new RoundRectangle2D.Double(badgeX, badgeY, badgeW, badgeH, 24, 24));

            g2d.setColor(new Color(180, 100, 0));
            int textX = badgeX + badgePadH;
            int textY = badgeY + badgePadV + fmBadge.getAscent();
            g2d.drawString(badgeText, textX, textY);

            // 2. BOTTOM VOCABULARY CARD (Y: 780 to 1420, 960x640)
            int cardX = 60;
            int cardY = 780;
            int cardW = CANVAS_WIDTH - 120; // 960px
            int cardH = 640;
            int cardArc = 48;

            // Soft warm pastel shadow
            g2d.setColor(new Color(60, 80, 120, 25));
            g2d.fill(new RoundRectangle2D.Double(cardX + 2, cardY + 8, cardW, cardH, cardArc, cardArc));

            // Pure clean card background with soft border
            g2d.setColor(Color.WHITE);
            g2d.fill(new RoundRectangle2D.Double(cardX, cardY, cardW, cardH, cardArc, cardArc));

            // Card delicate border
            g2d.setStroke(new BasicStroke(2.5f));
            g2d.setColor(new Color(225, 233, 245));
            g2d.draw(new RoundRectangle2D.Double(cardX, cardY, cardW, cardH, cardArc, cardArc));

            // 2.1 Main Word
            g2d.setColor(new Color(26, 39, 68));
            g2d.setFont(new Font("SansSerif", Font.BOLD, 68));
            FontMetrics fmWord = g2d.getFontMetrics();
            String wordText = item.word();
            int wordX = cardX + (cardW - fmWord.stringWidth(wordText)) / 2;
            int wordY = cardY + 82;
            g2d.drawString(wordText, wordX, wordY);

            // 2.2 IPA & Part of Speech pill tag (Pastel Blue Pill)
            String phoneticLine = item.ipa();
            if (item.partOfSpeech() != null && !item.partOfSpeech().isBlank()) {
                phoneticLine += "  •  " + item.partOfSpeech();
            }
            g2d.setFont(new Font("SansSerif", Font.BOLD, 30));
            FontMetrics fmIpa = g2d.getFontMetrics();
            int ipaTextW = fmIpa.stringWidth(phoneticLine);
            int ipaPillW = ipaTextW + 48;
            int ipaPillH = 44;
            int ipaPillX = cardX + (cardW - ipaPillW) / 2;
            int ipaPillY = wordY + 16;

            g2d.setColor(new Color(235, 243, 255)); // Soft pastel blue background
            g2d.fill(new RoundRectangle2D.Double(ipaPillX, ipaPillY, ipaPillW, ipaPillH, 22, 22));
            g2d.setColor(new Color(20, 100, 240)); // Cute primary blue text
            int ipaTextX = ipaPillX + (ipaPillW - ipaTextW) / 2;
            int ipaTextY = ipaPillY + ((ipaPillH - fmIpa.getHeight()) / 2) + fmIpa.getAscent();
            g2d.drawString(phoneticLine, ipaTextX, ipaTextY);

            // 2.3 Delicate divider
            int divY = ipaPillY + ipaPillH + 20;
            g2d.setColor(new Color(240, 242, 248));
            g2d.setStroke(new BasicStroke(2.0f));
            g2d.drawLine(cardX + 45, divY, cardX + cardW - 45, divY);

            // 2.4 English Definition line (gap 24px below divider)
            g2d.setColor(new Color(65, 80, 105));
            g2d.setFont(new Font("SansSerif", Font.PLAIN, 34));
            FontMetrics fmDef = g2d.getFontMetrics();
            String defText = item.definition();
            int defStartY = divY + 36;
            int defLinesDrawn = 1;
            if (defText != null && !defText.isBlank()) {
                defLinesDrawn = drawCenteredWrappedText(g2d, "Definition: " + defText, cardX + 40, defStartY, cardW - 80, 42);
            }
            int defEndY = defStartY + (defLinesDrawn * 42);

            // 2.5 Collocation Highlight Box (symmetric padding, perfectly balanced gap)
            if (item.collocation() != null && !item.collocation().isBlank()) {
                int collocBoxY = defEndY + 20;
                int collocBoxW = cardW - 80;
                int collocBoxX = cardX + 40;

                // Inner content layout
                Font collocTitleFont = new Font("SansSerif", Font.BOLD, 36);
                g2d.setFont(collocTitleFont);
                FontMetrics fmCollocTitle = g2d.getFontMetrics();
                String collocTitle = "⚡ Collocation: " + item.collocation();

                Font collocMeaningFont = new Font("SansSerif", Font.PLAIN, 32);
                g2d.setFont(collocMeaningFont);
                FontMetrics fmCollocMeaning = g2d.getFontMetrics();
                String meaningText = (item.collocationMeaning() != null && !item.collocationMeaning().isBlank())
                        ? "→ " + item.collocationMeaning() : null;

                int titleH = fmCollocTitle.getHeight();
                int meaningH = (meaningText != null) ? fmCollocMeaning.getHeight() : 0;
                int rowGap = (meaningText != null) ? 14 : 0;
                int contentH = titleH + rowGap + meaningH;

                int padV = 22; // Symmetric vertical padding
                int padH = 32; // Symmetric horizontal padding
                int collocBoxH = contentH + (padV * 2);

                // Pastel Mint Container
                g2d.setColor(new Color(238, 250, 246));
                g2d.fill(new RoundRectangle2D.Double(collocBoxX, collocBoxY, collocBoxW, collocBoxH, 28, 28));
                g2d.setColor(new Color(165, 225, 205));
                g2d.setStroke(new BasicStroke(2.0f));
                g2d.draw(new RoundRectangle2D.Double(collocBoxX, collocBoxY, collocBoxW, collocBoxH, 28, 28));

                // Draw Collocation Title (Centered inside box for consistency)
                g2d.setColor(new Color(15, 130, 95));
                g2d.setFont(collocTitleFont);
                int titleX = collocBoxX + (collocBoxW - fmCollocTitle.stringWidth(collocTitle)) / 2;
                int titleY = collocBoxY + padV + fmCollocTitle.getAscent();
                g2d.drawString(collocTitle, titleX, titleY);

                // Draw Meaning subtitle (Centered inside box for consistency)
                if (meaningText != null) {
                    g2d.setColor(new Color(50, 85, 75));
                    g2d.setFont(collocMeaningFont);
                    int meanX = collocBoxX + (collocBoxW - fmCollocMeaning.stringWidth(meaningText)) / 2;
                    int meanY = titleY + rowGap + fmCollocMeaning.getAscent();
                    g2d.drawString(meaningText, meanX, meanY);
                }
            }

        } finally {
            g2d.dispose();
        }

        return image;
    }

    public BufferedImage createHookCardImage(String topic, Scene scene) {
        BufferedImage image = new BufferedImage(CANVAS_WIDTH, CANVAS_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();

        try {
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

            int cardX = 60;
            int cardY = 320;
            int cardW = CANVAS_WIDTH - 120; // 960px
            int cardH = 1100;
            int cardArc = 52;

            // Soft shadow
            g2d.setColor(new Color(60, 80, 120, 25));
            g2d.fill(new RoundRectangle2D.Double(cardX + 2, cardY + 10, cardW, cardH, cardArc, cardArc));

            // Clean white card
            g2d.setColor(Color.WHITE);
            g2d.fill(new RoundRectangle2D.Double(cardX, cardY, cardW, cardH, cardArc, cardArc));
            g2d.setStroke(new BasicStroke(2.5f));
            g2d.setColor(new Color(225, 233, 245));
            g2d.draw(new RoundRectangle2D.Double(cardX, cardY, cardW, cardH, cardArc, cardArc));

            // Illustration top half
            int imgX = cardX + 30;
            int imgY = cardY + 30;
            int imgW = cardW - 60;
            int imgH = 580;
            BufferedImage hookImg = loadIllustrationForWord("hook");
            if (hookImg != null) {
                Shape oldClip = g2d.getClip();
                g2d.setClip(new RoundRectangle2D.Double(imgX, imgY, imgW, imgH, 40, 40));
                g2d.drawImage(hookImg, imgX, imgY, imgW, imgH, null);
                g2d.setClip(oldClip);
            }

            // Top pill badge
            String badgeText = "★ IELTS BAND 7+ MASTERCLASS";
            g2d.setFont(new Font("SansSerif", Font.BOLD, 26));
            FontMetrics fmBadge = g2d.getFontMetrics();
            int bW = fmBadge.stringWidth(badgeText) + 40;
            int bH = 50;
            int bX = cardX + (cardW - bW) / 2;
            int bY = imgY + imgH + 40;
            g2d.setColor(new Color(255, 243, 205));
            g2d.fill(new RoundRectangle2D.Double(bX, bY, bW, bH, 25, 25));
            g2d.setColor(new Color(255, 180, 0));
            g2d.setStroke(new BasicStroke(2.0f));
            g2d.draw(new RoundRectangle2D.Double(bX, bY, bW, bH, 25, 25));
            g2d.setColor(new Color(180, 100, 0));
            g2d.drawString(badgeText, bX + 20, bY + 34);

            // Title headline
            g2d.setColor(new Color(26, 39, 68));
            g2d.setFont(new Font("SansSerif", Font.BOLD, 54));
            String headline = "TOPIC: " + (topic != null ? topic.toUpperCase() : "FAMILY");
            FontMetrics fmH = g2d.getFontMetrics();
            int hX = cardX + (cardW - fmH.stringWidth(headline)) / 2;
            int hY = bY + bH + 65;
            g2d.drawString(headline, hX, hY);

            // Subtitle note
            g2d.setColor(new Color(70, 95, 135));
            g2d.setFont(new Font("SansSerif", Font.PLAIN, 36));
            String subNote = "Master essential vocabulary with high-scoring collocations";
            drawCenteredWrappedText(g2d, subNote, cardX + 40, hY + 55, cardW - 80, 44);

        } finally {
            g2d.dispose();
        }
        return image;
    }

    public BufferedImage createCtaCardImage(String topic, Scene scene) {
        BufferedImage image = new BufferedImage(CANVAS_WIDTH, CANVAS_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();

        try {
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

            int cardX = 60;
            int cardY = 320;
            int cardW = CANVAS_WIDTH - 120; // 960px
            int cardH = 1100;
            int cardArc = 52;

            // Soft shadow
            g2d.setColor(new Color(60, 80, 120, 25));
            g2d.fill(new RoundRectangle2D.Double(cardX + 2, cardY + 10, cardW, cardH, cardArc, cardArc));

            // Clean white card
            g2d.setColor(Color.WHITE);
            g2d.fill(new RoundRectangle2D.Double(cardX, cardY, cardW, cardH, cardArc, cardArc));
            g2d.setStroke(new BasicStroke(2.5f));
            g2d.setColor(new Color(225, 233, 245));
            g2d.draw(new RoundRectangle2D.Double(cardX, cardY, cardW, cardH, cardArc, cardArc));

            // Illustration top half
            int imgX = cardX + 30;
            int imgY = cardY + 30;
            int imgW = cardW - 60;
            int imgH = 580;
            BufferedImage ctaImg = loadIllustrationForWord("cta");
            if (ctaImg != null) {
                Shape oldClip = g2d.getClip();
                g2d.setClip(new RoundRectangle2D.Double(imgX, imgY, imgW, imgH, 40, 40));
                g2d.drawImage(ctaImg, imgX, imgY, imgW, imgH, null);
                g2d.setClip(oldClip);
            }

            // Top pill badge
            String badgeText = "★ NEXT LESSON COMING SOON";
            g2d.setFont(new Font("SansSerif", Font.BOLD, 26));
            FontMetrics fmBadge = g2d.getFontMetrics();
            int bW = fmBadge.stringWidth(badgeText) + 40;
            int bH = 50;
            int bX = cardX + (cardW - bW) / 2;
            int bY = imgY + imgH + 40;
            g2d.setColor(new Color(238, 250, 246));
            g2d.fill(new RoundRectangle2D.Double(bX, bY, bW, bH, 25, 25));
            g2d.setColor(new Color(165, 225, 205));
            g2d.setStroke(new BasicStroke(2.0f));
            g2d.draw(new RoundRectangle2D.Double(bX, bY, bW, bH, 25, 25));
            g2d.setColor(new Color(15, 130, 95));
            g2d.drawString(badgeText, bX + 20, bY + 34);

            // Title headline
            g2d.setColor(new Color(26, 39, 68));
            g2d.setFont(new Font("SansSerif", Font.BOLD, 54));
            String headline = "PART 2: ADOLESCENCE";
            FontMetrics fmH = g2d.getFontMetrics();
            int hX = cardX + (cardW - fmH.stringWidth(headline)) / 2;
            int hY = bY + bH + 65;
            g2d.drawString(headline, hX, hY);

            // Subtitle note
            g2d.setColor(new Color(70, 95, 135));
            g2d.setFont(new Font("SansSerif", Font.PLAIN, 36));
            String subNote = "Follow for daily IELTS vocabulary and scoring strategies!";
            drawCenteredWrappedText(g2d, subNote, cardX + 40, hY + 55, cardW - 80, 44);

        } finally {
            g2d.dispose();
        }
        return image;
    }

    private BufferedImage loadIllustrationForWord(String word) {
        if (word == null) return null;
        String wLower = word.toLowerCase().trim();
        String fileName = switch (wLower) {
            case "sibling", "siblings" -> "sibling.jpg";
            case "relative", "relatives" -> "relative.jpg";
            case "adolescence", "adolescent" -> "hook.jpg";
            case "nurture", "nurturing" -> "cta.jpg";
            default -> "sibling.jpg";
        };

        // Try container path first, then local workspace path
        for (String base : List.of("/app/assets/images", "assets/images", "/data/assets/images", "06-media/assets/images")) {
            Path p = Paths.get(base).resolve(fileName);
            if (Files.exists(p)) {
                try {
                    return ImageIO.read(p.toFile());
                } catch (Exception ignored) {}
            }
        }
        return null;
    }

    private int drawCenteredWrappedText(Graphics2D g, String text, int x, int y, int maxWidth, int lineHeight) {
        FontMetrics fm = g.getFontMetrics();
        String[] words = text.split(" ");
        StringBuilder currentLine = new StringBuilder();
        int currentY = y;
        int linesCount = 0;

        for (String w : words) {
            String test = currentLine.length() == 0 ? w : currentLine + " " + w;
            if (fm.stringWidth(test) <= maxWidth) {
                currentLine.append(currentLine.length() == 0 ? "" : " ").append(w);
            } else {
                int lineX = x + (maxWidth - fm.stringWidth(currentLine.toString())) / 2;
                g.drawString(currentLine.toString(), lineX, currentY);
                currentLine = new StringBuilder(w);
                currentY += lineHeight;
                linesCount++;
            }
        }
        if (currentLine.length() > 0) {
            int lineX = x + (maxWidth - fm.stringWidth(currentLine.toString())) / 2;
            g.drawString(currentLine.toString(), lineX, currentY);
            linesCount++;
        }
        return linesCount;
    }
}
