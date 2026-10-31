package com.tiktok.video.visual.renderer;

import com.tiktok.video.core.contract.VisualCardRenderer;
import com.tiktok.video.core.exception.VisualRenderException;
import com.tiktok.video.core.model.Scene;
import com.tiktok.video.core.model.SceneType;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Renders high-resolution transparent PNG visual cards (1080x1920) using Java2D Graphics2D.
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

        Map<String, Integer> vocabOrder = new HashMap<>();
        List<VocabularyItem> orderedVocabulary = new ArrayList<>();
        for (Scene scene : script.scenes()) {
            if (scene.sceneType() == SceneType.VOCABULARY && scene.vocabularyItem() != null) {
                String key = scene.vocabularyItem().word().toLowerCase(Locale.ROOT);
                if (!vocabOrder.containsKey(key)) {
                    vocabOrder.put(key, vocabOrder.size() + 1);
                    orderedVocabulary.add(scene.vocabularyItem());
                }
            }
        }

        Map<String, Path> cardMap = new HashMap<>();
        for (Scene scene : script.scenes()) {
            BufferedImage cardImage = switch (scene.sceneType()) {
                case HOOK -> createHookCardImage(script.topic(), orderedVocabulary);
                case VOCABULARY -> createCardImage(script.topic(), scene.vocabularyItem(), vocabOrder, false, false);
                case VOCABULARY_COLLOCATION -> createCardImage(script.topic(), scene.vocabularyItem(), vocabOrder, true, false);
                case VOCABULARY_EXAMPLE -> createCardImage(script.topic(), scene.vocabularyItem(), vocabOrder, false, true, scene.spokenText());
                case CALL_TO_ACTION -> createCtaCardImage(script.topic(), scene);
            };

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
        return createCardImage(topic, item, Map.of(), false, false);
    }

    private BufferedImage createCardImage(String topic,
                                          VocabularyItem item,
                                          Map<String, Integer> vocabOrder,
                                          boolean collocationSlide,
                                          boolean exampleSlide) {
        return createCardImage(topic, item, vocabOrder, collocationSlide, exampleSlide, null);
    }

    private BufferedImage createCardImage(String topic,
                                          VocabularyItem item,
                                          Map<String, Integer> vocabOrder,
                                          boolean collocationSlide,
                                          boolean exampleSlide,
                                          String spokenText) {
        if (item == null) {
            return null;
        }

        BufferedImage image = new BufferedImage(CANVAS_WIDTH, CANVAS_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();
        try {
            applyQuality(g2d);

            int index = getWordIndex(item, vocabOrder);
            int totalWords = Math.max(vocabOrder.size(), 2);
            String chipText = index + "/" + totalWords;
            String upperTopic = (topic == null || topic.isBlank()) ? "FAMILY" : topic.toUpperCase(Locale.ROOT);

            int badgeY = 238;
            drawCenteredPill(g2d,
                    "★ IELTS 7+ " + upperTopic + " - PART 1",
                    CANVAS_WIDTH / 2,
                    badgeY,
                    new Color(255, 243, 205),
                    new Color(255, 188, 73),
                    new Color(145, 88, 0),
                    27,
                    20,
                    9);

            int cardX = 80;
            int cardY = 320;
            int cardW = CANVAS_WIDTH - 160;
            int cardH = 980;
            int cardArc = 44;
            drawBaseCard(g2d, cardX, cardY, cardW, cardH, cardArc);

            drawWordChip(g2d, chipText, cardX + 36, cardY + 32);

            if (collocationSlide) {
                drawHeaderRow(g2d, cardX, cardY, cardW, chipText, item.word(), item.ipa());
                drawCollocationSlide(g2d, item, cardX, cardY, cardW);
            } else if (exampleSlide) {
                drawHeaderRow(g2d, cardX, cardY, cardW, chipText, item.word(), item.ipa());
                drawExampleSlide(g2d, item, spokenText, cardX, cardY, cardW, cardH);
            } else {
                drawWordSlide(g2d, item, cardX, cardY, cardW);
            }

            return image;
        } finally {
            g2d.dispose();
        }
    }

    public BufferedImage createHookCardImage(String topic, List<VocabularyItem> vocabularyItems) {
        BufferedImage image = new BufferedImage(CANVAS_WIDTH, CANVAS_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();

        try {
            applyQuality(g2d);

            int cardX = 90;
            int cardY = 240;
            int cardW = CANVAS_WIDTH - 180;
            int cardH = 990;
            drawBaseCard(g2d, cardX, cardY, cardW, cardH, 44);

            int y = cardY + 70;
            drawCenteredPill(g2d,
                    "★ IELTS BAND 7+",
                    CANVAS_WIDTH / 2,
                    y,
                    new Color(255, 243, 205),
                    new Color(255, 198, 84),
                    new Color(145, 88, 0),
                    30,
                    24,
                    10);

            y += 110;
            g2d.setColor(new Color(24, 44, 84));
            g2d.setFont(new Font("SansSerif", Font.BOLD, 74));
            String title = "TOPIC: " + ((topic == null || topic.isBlank()) ? "FAMILY" : topic.toUpperCase(Locale.ROOT));
            drawCentered(g2d, title, CANVAS_WIDTH / 2, y);

            y += 70;
            drawCenteredPill(g2d,
                    "PHẦN 1 / 3",
                    CANVAS_WIDTH / 2,
                    y,
                    new Color(236, 251, 240),
                    new Color(107, 160, 124),
                    new Color(46, 110, 70),
                    28,
                    22,
                    10);

            List<VocabularyItem> twoWords = vocabularyItems == null ? List.of() : vocabularyItems;
            String line1 = buildHookWordLine(twoWords, 0, "Sibling", "anh chị em ruột");
            String line2 = buildHookWordLine(twoWords, 1, "Relative", "họ hàng thân thích");

            int boxX = cardX + 76;
            int boxW = cardW - 152;
            y += 130;
            drawHookWordBox(g2d, boxX, y, boxW, line1);
            y += 160;
            drawHookWordBox(g2d, boxX, y, boxW, line2);

            g2d.setColor(new Color(26, 49, 95));
            g2d.setFont(new Font("SansSerif", Font.BOLD, 42));
            String caption = "Muốn đạt IELTS 7.0+? Nâng cấp ngay 2 từ vựng chủ đề Family cực đỉnh!";
            drawCenteredWrappedText(g2d, caption, 120, 1320, CANVAS_WIDTH - 240, 54);

        } finally {
            g2d.dispose();
        }

        return image;
    }

    public BufferedImage createCtaCardImage(String topic, Scene scene) {
        BufferedImage image = new BufferedImage(CANVAS_WIDTH, CANVAS_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();

        try {
            applyQuality(g2d);

            int cardX = 60;
            int cardY = 320;
            int cardW = CANVAS_WIDTH - 120;
            int cardH = 1100;
            drawBaseCard(g2d, cardX, cardY, cardW, cardH, 52);

            drawCenteredPill(g2d,
                    "★ NEXT LESSON COMING SOON",
                    CANVAS_WIDTH / 2,
                    cardY + 120,
                    new Color(238, 250, 246),
                    new Color(165, 225, 205),
                    new Color(15, 130, 95),
                    26,
                    20,
                    9);

            g2d.setColor(new Color(26, 39, 68));
            g2d.setFont(new Font("SansSerif", Font.BOLD, 54));
            drawCentered(g2d, "PART 2: ADOLESCENCE", CANVAS_WIDTH / 2, cardY + 250);

            g2d.setColor(new Color(70, 95, 135));
            g2d.setFont(new Font("SansSerif", Font.PLAIN, 36));
            String subNote = "Follow for daily IELTS vocabulary and scoring strategies!";
            drawCenteredWrappedText(g2d, subNote, cardX + 40, cardY + 335, cardW - 80, 44);

        } finally {
            g2d.dispose();
        }

        return image;
    }

    private void drawWordSlide(Graphics2D g2d, VocabularyItem item, int cardX, int cardY, int cardW) {
        int y = cardY + 170;
        g2d.setColor(new Color(24, 44, 84));
        g2d.setFont(new Font("SansSerif", Font.BOLD, 96));
        drawCentered(g2d, safe(item.word(), "Vocabulary"), CANVAS_WIDTH / 2, y);

        y += 48;
        String ipaLine = safe(item.ipa(), "") + (item.partOfSpeech() == null || item.partOfSpeech().isBlank() ? "" : "  •  " + item.partOfSpeech());
        drawCenteredPill(g2d,
                ipaLine.isBlank() ? "/.../" : ipaLine,
                CANVAS_WIDTH / 2,
                y,
                new Color(232, 242, 255),
                new Color(182, 208, 246),
                new Color(29, 87, 190),
                34,
                28,
                10);

        y += 120;
        g2d.setColor(new Color(24, 44, 84));
        g2d.setFont(new Font("SansSerif", Font.BOLD, 52));
        drawCenteredWrappedText(g2d, safe(item.definition(), "(no meaning)"), cardX + 70, y, cardW - 140, 58);

        int dividerY = y + 120;
        g2d.setColor(new Color(225, 233, 245));
        g2d.setStroke(new BasicStroke(2.0f));
        g2d.drawLine(cardX + 50, dividerY, cardX + cardW - 50, dividerY);

        g2d.setColor(new Color(86, 103, 130));
        g2d.setFont(new Font("SansSerif", Font.PLAIN, 36));
        drawLeftWrappedText(g2d,
                "Definition: " + safe(item.definition(), "No definition available."),
                cardX + 58,
                dividerY + 58,
                cardW - 116,
                46,
                4);
    }

    private void drawCollocationSlide(Graphics2D g2d, VocabularyItem item, int cardX, int cardY, int cardW) {
        int labelY = cardY + 215;
        g2d.setColor(new Color(52, 132, 88));
        g2d.setFont(new Font("SansSerif", Font.BOLD, 33));
        drawCentered(g2d, "COLLOCATION", CANVAS_WIDTH / 2, labelY);

        int boxX = cardX + 56;
        int boxY = cardY + 280;
        int boxW = cardW - 112;
        int boxH = 420;

        g2d.setColor(new Color(233, 250, 244));
        g2d.fill(new RoundRectangle2D.Double(boxX, boxY, boxW, boxH, 34, 34));
        g2d.setColor(new Color(166, 224, 200));
        g2d.setStroke(new BasicStroke(2.0f));
        g2d.draw(new RoundRectangle2D.Double(boxX, boxY, boxW, boxH, 34, 34));

        g2d.setColor(new Color(23, 111, 73));
        g2d.setFont(new Font("SansSerif", Font.BOLD, 68));
        drawCenteredWrappedText(g2d, safe(item.collocation(), "(no collocation)"), boxX + 50, boxY + 160, boxW - 100, 74);

        g2d.setColor(new Color(70, 90, 94));
        g2d.setFont(new Font("SansSerif", Font.PLAIN, 38));
        drawCenteredWrappedText(g2d,
                "→ " + safe(item.collocationMeaning(), "No collocation meaning."),
                boxX + 46,
                boxY + 310,
                boxW - 92,
                50);
    }

    private void drawExampleSlide(Graphics2D g2d,
                                  VocabularyItem item,
                                  String spokenText,
                                  int cardX,
                                  int cardY,
                                  int cardW,
                                  int cardH) {
        int labelY = cardY + 215;
        g2d.setColor(new Color(148, 102, 26));
        g2d.setFont(new Font("SansSerif", Font.BOLD, 33));
        drawCentered(g2d, "EXAMPLE", CANVAS_WIDTH / 2, labelY);

        String example = safe(item.exampleSentence(), spokenText);
        if (example != null && example.toLowerCase(Locale.ROOT).startsWith("example sentence")) {
            int idx = example.indexOf(':');
            if (idx >= 0 && idx < example.length() - 1) {
                example = example.substring(idx + 1).trim();
            }
        }

        g2d.setColor(new Color(24, 44, 84));
        g2d.setFont(new Font("SansSerif", Font.BOLD, 62));
        drawCenteredWrappedText(g2d, safe(example, "No example available."), cardX + 70, cardY + (cardH / 2), cardW - 140, 80);
    }

    private void drawHeaderRow(Graphics2D g2d, int cardX, int cardY, int cardW, String chipText, String word, String ipa) {
        drawWordChip(g2d, chipText, cardX + 36, cardY + 32);

        g2d.setColor(new Color(29, 82, 176));
        g2d.setFont(new Font("SansSerif", Font.BOLD, 34));
        String head = safe(word, "Word") + " " + safe(ipa, "");
        g2d.drawString(head.trim(), cardX + 220, cardY + 84);

        int dividerY = cardY + 120;
        g2d.setColor(new Color(225, 233, 245));
        g2d.setStroke(new BasicStroke(2.0f));
        g2d.drawLine(cardX + 40, dividerY, cardX + cardW - 40, dividerY);
    }

    private void drawWordChip(Graphics2D g2d, String text, int x, int y) {
        g2d.setFont(new Font("SansSerif", Font.BOLD, 32));
        FontMetrics fm = g2d.getFontMetrics();
        int w = fm.stringWidth(text) + 34;
        int h = fm.getHeight() + 14;

        g2d.setColor(new Color(227, 240, 255));
        g2d.fill(new RoundRectangle2D.Double(x, y, w, h, 24, 24));
        g2d.setColor(new Color(77, 120, 202));
        g2d.setStroke(new BasicStroke(1.5f));
        g2d.draw(new RoundRectangle2D.Double(x, y, w, h, 24, 24));

        g2d.setColor(new Color(31, 77, 160));
        g2d.drawString(text, x + 17, y + ((h - fm.getHeight()) / 2) + fm.getAscent());
    }

    private void drawHookWordBox(Graphics2D g2d, int x, int y, int w, String text) {
        int h = 120;
        g2d.setColor(new Color(236, 251, 244));
        g2d.fill(new RoundRectangle2D.Double(x, y, w, h, 28, 28));
        g2d.setColor(new Color(184, 220, 204));
        g2d.setStroke(new BasicStroke(2f));
        g2d.draw(new RoundRectangle2D.Double(x, y, w, h, 28, 28));

        int slash = text.indexOf('/');
        if (slash < 0) {
            g2d.setColor(new Color(16, 68, 145));
            g2d.setFont(new Font("SansSerif", Font.BOLD, 42));
            drawCentered(g2d, text, CANVAS_WIDTH / 2, y + 74);
            return;
        }

        String left = text.substring(0, slash).trim();
        String right = text.substring(slash + 1).trim();

        g2d.setColor(new Color(16, 68, 145));
        g2d.setFont(new Font("SansSerif", Font.BOLD, 42));
        g2d.drawString(left + " /", x + 34, y + 56);

        g2d.setColor(new Color(75, 86, 95));
        g2d.setFont(new Font("SansSerif", Font.PLAIN, 34));
        g2d.drawString(right, x + 34, y + 98);
    }

    private void drawBaseCard(Graphics2D g2d, int x, int y, int w, int h, int arc) {
        g2d.setColor(new Color(60, 80, 120, 22));
        g2d.fill(new RoundRectangle2D.Double(x + 2, y + 8, w, h, arc, arc));

        g2d.setColor(Color.WHITE);
        g2d.fill(new RoundRectangle2D.Double(x, y, w, h, arc, arc));

        g2d.setColor(new Color(225, 233, 245));
        g2d.setStroke(new BasicStroke(2.2f));
        g2d.draw(new RoundRectangle2D.Double(x, y, w, h, arc, arc));
    }

    private int getWordIndex(VocabularyItem item, Map<String, Integer> vocabOrder) {
        if (item == null || item.word() == null || item.word().isBlank()) {
            return 1;
        }
        Integer index = vocabOrder.get(item.word().toLowerCase(Locale.ROOT));
        return index == null ? 1 : index;
    }

    private String buildHookWordLine(List<VocabularyItem> items, int idx, String fallbackWord, String fallbackMeaning) {
        if (items == null || items.size() <= idx || items.get(idx) == null) {
            return (idx + 1) + " " + fallbackWord + " / " + fallbackMeaning;
        }
        VocabularyItem item = items.get(idx);
        String meaning = safe(item.definition(), fallbackMeaning);
        return (idx + 1) + " " + safe(item.word(), fallbackWord) + " / " + meaning;
    }

    private String safe(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value.trim();
    }

    private void drawCenteredPill(Graphics2D g2d,
                                  String text,
                                  int centerX,
                                  int topY,
                                  Color bgColor,
                                  Color borderColor,
                                  Color textColor,
                                  int fontSize,
                                  int padX,
                                  int padY) {
        g2d.setFont(new Font("SansSerif", Font.BOLD, fontSize));
        FontMetrics fm = g2d.getFontMetrics();
        int w = fm.stringWidth(text) + (padX * 2);
        int h = fm.getHeight() + (padY * 2);
        int x = centerX - (w / 2);

        g2d.setColor(bgColor);
        g2d.fill(new RoundRectangle2D.Double(x, topY, w, h, 26, 26));
        g2d.setColor(borderColor);
        g2d.setStroke(new BasicStroke(2f));
        g2d.draw(new RoundRectangle2D.Double(x, topY, w, h, 26, 26));

        g2d.setColor(textColor);
        g2d.drawString(text, x + padX, topY + padY + fm.getAscent());
    }

    private void drawCentered(Graphics2D g2d, String text, int centerX, int baselineY) {
        FontMetrics fm = g2d.getFontMetrics();
        g2d.drawString(text, centerX - (fm.stringWidth(text) / 2), baselineY);
    }

    private int drawCenteredWrappedText(Graphics2D g, String text, int x, int y, int maxWidth, int lineHeight) {
        FontMetrics fm = g.getFontMetrics();
        String[] words = text.split("\\s+");
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

    private void drawLeftWrappedText(Graphics2D g, String text, int x, int y, int maxWidth, int lineHeight, int maxLines) {
        FontMetrics fm = g.getFontMetrics();
        String[] words = text.split("\\s+");
        StringBuilder line = new StringBuilder();
        int currentY = y;
        int lines = 0;

        for (String word : words) {
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (fm.stringWidth(candidate) <= maxWidth) {
                line.setLength(0);
                line.append(candidate);
            } else {
                g.drawString(line.toString(), x, currentY);
                currentY += lineHeight;
                lines++;
                if (lines >= maxLines) {
                    return;
                }
                line.setLength(0);
                line.append(word);
            }
        }

        if (!line.isEmpty() && lines < maxLines) {
            g.drawString(line.toString(), x, currentY);
        }
    }

    private void applyQuality(Graphics2D g2d) {
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    }
}
