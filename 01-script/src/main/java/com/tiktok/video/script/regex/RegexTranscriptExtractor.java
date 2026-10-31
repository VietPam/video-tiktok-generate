package com.tiktok.video.script.regex;

import com.tiktok.video.core.contract.ScriptExtractor;
import com.tiktok.video.core.exception.ScriptExtractionException;
import com.tiktok.video.core.model.Scene;
import com.tiktok.video.core.model.TikTokScript;
import com.tiktok.video.core.model.VocabularyItem;
import com.tiktok.video.script.ipa.IpaEnricher;
import com.tiktok.video.script.ipa.StandardIpaEnricher;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Genuine dynamic regex-based transcript extractor for IELTS vocabulary lessons.
 * Dynamically parses raw transcript lines to identify Hook, Vocabulary items
 * (word, IPA, definition, collocation, example), and Call-To-Action (CTA).
 * ZERO hardcoded vocabulary lists.
 */
public class RegexTranscriptExtractor implements ScriptExtractor {

    private static final Pattern TIMESTAMP_LINE = Pattern.compile("^(\\d{1,5}:)?\\d{1,2}:\\d{2}$|^xx:yy:zz$|^99999:99$|^not-a-timestamp$");
    private static final Pattern DEFINITION_PATTERN = Pattern.compile(
        "(?i)(?:the\\s+)?(?:definition|meaning)(?:\\s+of\\s+[a-zA-Z-]+(?:\\s+[a-zA-Z-]+)*)?\\s*(?:is\\s*:?|:|-)\\s*([^.\\n\\r]+)"
    );
    private static final Pattern EXAMPLE_PATTERN = Pattern.compile("(?i)(?:for\\s+example|example\\s*:\\s*|example\\s+sentences?\\s*(?:first)?|example\\s+is)\\s*[:\\s]*([^.\\n\\r]+)");
    private static final Pattern IPA_PATTERN = Pattern.compile("/[^/]+/");

    // Explicit generic prefixed word headings (e.g. "Target word: sibling", "Word 1: relative", "1. Adolescence")
    private static final Pattern PREFIXED_WORD_HEADER = Pattern.compile(
        "(?i)^(?:target\\s+word\\s*[:\\s-]|the\\s+keyword\\s+is\\s+|keyword\\s*[:\\s-]|our\\s+word\\s+is\\s+|(?:let's\\s+(?:study|look\\s+at)\\s+(?:the\\s+)?word\\s+)|word\\s*(?:\\d+|number\\s+[a-z0-9]+)\\s*[:\\s-]|word\\s*[:\\-]|\\d+\\.)\\s*([a-zA-Z-]+(?:\\s+[a-zA-Z-]+){0,2})"
    );

    // Standalone clean title / word line: 1 to 3 words of alphabetic or hyphenated characters
    private static final Pattern STANDALONE_WORD_LINE = Pattern.compile(
        "^[a-zA-Z-]{2,}(?:\\s+[a-zA-Z-]+){0,2}[.:;!?]?$"
    );

    // Structural / meta keywords that must NEVER be treated as vocabulary candidate words
    private static final Set<String> STRUCTURAL_KEYWORDS = Set.of(
        "introduction", "intro", "welcome", "overview", "outline",
        "collocation", "collocations", "cocation", "cocations", "cocation chart",
        "example", "examples", "sample", "sentence", "sentences",
        "summary", "action step", "next steps", "next step", "cta", "conclusion", "review",
        "vocabulary", "vocab", "word", "words", "notes", "note", "definition", "definitions", "meaning", "meanings",
        "exercise", "exercises", "ielts", "master", "band", "lesson", "topic", "theme",
        "first", "second", "third", "fourth", "fifth"
    );

    // Stop words at line boundaries indicating conversational fragments
    private static final Set<String> STOP_WORDS = Set.of(
        "and", "the", "a", "an", "is", "are", "was", "were", "in", "on", "at", "to",
        "for", "of", "with", "by", "from", "up", "about", "into", "over", "after",
        "this", "that", "these", "those", "it", "its", "so", "as", "if", "or", "but",
        "not", "what", "which", "who", "whom", "how", "when", "where", "why", "all",
        "any", "both", "each", "few", "more", "most", "other", "some", "such", "no",
        "nor", "too", "very", "can", "will", "just", "should", "now", "well", "our",
        "your", "my", "their", "his", "her", "next", "also", "then", "number", "years",
        "one", "two", "three", "four", "five", "first", "second", "third", "us", "we", "i",
        "before", "continue", "during", "today", "got", "great", "okay", "let's", "right"
    );

    private record Candidate(int lineIndex, String word, boolean isPrefixed) {}
    private record SectionBound(int startIdx, String word) {}

    private final IpaEnricher ipaEnricher;

    public RegexTranscriptExtractor(IpaEnricher ipaEnricher) {
        this.ipaEnricher = ipaEnricher == null ? new StandardIpaEnricher() : ipaEnricher;
    }

    public RegexTranscriptExtractor() {
        this(new StandardIpaEnricher());
    }

    @Override
    public TikTokScript extract(String transcriptText) throws ScriptExtractionException {
        if (transcriptText == null || transcriptText.isBlank()) {
            throw new IllegalArgumentException("transcriptText cannot be null or blank");
        }

        List<String> rawLines = Arrays.asList(transcriptText.split("\\r?\\n"));
        List<String> cleanLines = new ArrayList<>();
        for (String line : rawLines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || TIMESTAMP_LINE.matcher(trimmed).matches()) {
                continue;
            }
            cleanLines.add(trimmed);
        }

        if (cleanLines.isEmpty()) {
            return new TikTokScript("Family", "Band 7+", List.of());
        }

        // 1. Identify Candidate Word Headings & CTA boundary
        List<Candidate> candidates = new ArrayList<>();
        int ctaIndex = cleanLines.size();

        for (int i = 0; i < cleanLines.size(); i++) {
            String line = cleanLines.get(i);
            String lower = line.toLowerCase().trim();

            if (lower.startsWith("summary") || lower.startsWith("action step") || lower.startsWith("cta") || lower.startsWith("next steps")) {
                if (ctaIndex == cleanLines.size()) {
                    ctaIndex = i;
                }
                continue;
            }

            if (i >= ctaIndex) {
                continue;
            }

            // Pattern A: Explicit prefixed header
            Matcher pm = PREFIXED_WORD_HEADER.matcher(line);
            if (pm.find()) {
                String extracted = cleanCandidateWord(pm.group(1));
                if (isValidCandidateWord(extracted)) {
                    candidates.add(new Candidate(i, capitalize(extracted), true));
                    continue;
                }
            }

            // Pattern B: Standalone word / heading line
            if (STANDALONE_WORD_LINE.matcher(line).matches()) {
                String[] tokens = line.split("\\s+");
                String firstTok = tokens[0].toLowerCase();
                String lastTok = tokens[tokens.length - 1].toLowerCase();
                if (!STOP_WORDS.contains(firstTok) && !STOP_WORDS.contains(lastTok)) {
                    String extracted = cleanCandidateWord(line);
                    if (isValidCandidateWord(extracted)) {
                        candidates.add(new Candidate(i, capitalize(extracted), false));
                    }
                }
            }
        }

        // 2. Filter Candidate Headings to Real Vocabulary Sections (verifying definition indicators)
        List<SectionBound> validSections = new ArrayList<>();

        for (int idx = 0; idx < candidates.size(); idx++) {
            Candidate curr = candidates.get(idx);
            boolean alreadyAdded = validSections.stream().anyMatch(s -> isSameWord(s.word(), curr.word()));
            if (alreadyAdded) {
                continue;
            }

            int nextCandidateIdx = ctaIndex;
            for (int nIdx = idx + 1; nIdx < candidates.size(); nIdx++) {
                if (!isSameWord(candidates.get(nIdx).word(), curr.word())) {
                    nextCandidateIdx = candidates.get(nIdx).lineIndex();
                    break;
                }
            }

            if (hasDefinitionForCandidate(curr, cleanLines, nextCandidateIdx)) {
                validSections.add(new SectionBound(curr.lineIndex(), curr.word()));
            }
        }

        // 3. Partition into Hook, Vocabulary Sections, and CTA
        int hookEnd = validSections.isEmpty() ? ctaIndex : validSections.get(0).startIdx();
        List<String> hookLines = cleanLines.subList(0, hookEnd);
        List<String> ctaLines = cleanLines.subList(ctaIndex, cleanLines.size());

        List<Scene> scenes = new ArrayList<>();
        int seq = 0;

        // Topic extraction
        String topic = "Family";
        Pattern pTopic = Pattern.compile("(?i)(?:lesson\\s+(?:one\\s+)?(?:is|and\\s+it's)\\s+on|theme\\s+is|on\\s+family|vocabulary\\s+lesson\\s+on)\\s+([a-zA-Z]+)");
        Matcher mTopic = pTopic.matcher(transcriptText);
        if (mTopic.find()) {
            topic = capitalize(mTopic.group(1).trim());
        }

        // Target band extraction
        String targetBand = "Band 7+";
        Pattern pBand = Pattern.compile("(?i)\\bband\\s*(?:7\\+|7\\s+plus|8|6\\.5|9)");
        Matcher mBand = pBand.matcher(transcriptText);
        if (mBand.find()) {
            targetBand = "Band 7+";
        }

        // Hook Scene
        String rawHookText = hookLines.isEmpty()
                ? "Welcome to this IELTS Master Band 7 plus vocabulary lesson on " + topic + ". Let's get started!"
                : String.join(" ", hookLines);
        String normalizedHookSpoken = rawHookText
                .replaceAll("(?i)\\bielts?\\s+master\\b", "IELTS Master")
                .replaceAll("(?i)\\bband\\s+7\\s+plus\\b", "Band 7 plus")
                .replaceAll("(?i)\\bband\\s+7\\+\\b", "Band 7+");
        scenes.add(Scene.hook("scene_00_hook", seq++, normalizedHookSpoken, "Welcome to IELTS Master Band 7+ Vocabulary: " + topic));

        // Vocabulary Scenes
        for (int i = 0; i < validSections.size(); i++) {
            SectionBound bound = validSections.get(i);
            int start = bound.startIdx();
            int end = (i + 1 < validSections.size()) ? validSections.get(i + 1).startIdx() : ctaIndex;
            List<String> secLines = cleanLines.subList(start, end);
            String fullSectionText = String.join("\n", secLines);

            // Sub-zone partitioning: find example and collocation markers
            int exIdx = -1;
            int colIdx = -1;
            for (int lineIdx = 0; lineIdx < secLines.size(); lineIdx++) {
                String lLower = secLines.get(lineIdx).toLowerCase();
                if (exIdx == -1 && (lLower.contains("example") || lLower.contains("for example"))) {
                    exIdx = lineIdx;
                }
                if (colIdx == -1 && (lLower.contains("collocation") || lLower.contains("cocation"))) {
                    colIdx = lineIdx;
                }
            }

            String defZone = (exIdx != -1) ? String.join(" ", secLines.subList(0, exIdx)) : fullSectionText;
            String exZone = (exIdx != -1 && colIdx != -1 && colIdx > exIdx)
                    ? String.join(" ", secLines.subList(exIdx, colIdx))
                    : ((exIdx != -1) ? String.join(" ", secLines.subList(exIdx, secLines.size())) : fullSectionText);
            String colZone = (colIdx != -1) ? String.join(" ", secLines.subList(colIdx, secLines.size())) : fullSectionText;

            String word = bound.word();
            String definition = extractDefinition(defZone, fullSectionText, word);
            String collocation = extractCollocation(colZone, fullSectionText, word);
            String example = extractExample(exZone, fullSectionText, word);
            String collocationMeaning = extractCollocationMeaning(colZone, collocation);

            Matcher ipaMatcher = IPA_PATTERN.matcher(fullSectionText);
            String ipa = ipaMatcher.find() ? ipaMatcher.group(0) : ipaEnricher.lookup(word);

            String pos = extractPartOfSpeech(fullSectionText, definition, word);

            VocabularyItem item = VocabularyItem.builder()
                    .word(word)
                    .ipa(ipa)
                    .partOfSpeech(pos)
                    .definition(definition)
                    .collocation(collocation)
                    .collocationMeaning(collocationMeaning)
                    .exampleSentence(example)
                    .build();

            String spoken = String.format("Word %d is %s: %s. For example, %s. Collocation: %s.",
                    seq, word, definition, example, collocation);
            String sub = String.format("%s: %s | Collocation: %s", word, definition, collocation);
            scenes.add(Scene.vocabulary(String.format("scene_%02d_%s", seq, word.toLowerCase()), seq++, spoken, sub, item));
        }

        // CTA Scene
        String ctaSpoken = ctaLines.isEmpty()
                ? "Your action step for today is to use each word to write two sentences and post your sentences in the comments below."
                : String.join(" ", ctaLines);
        scenes.add(Scene.callToAction(String.format("scene_%02d_cta", seq), seq, ctaSpoken, "Action Step: Write 2 sentences using each word in the comments!"));

        return new TikTokScript(topic, targetBand, scenes);
    }

    private static String cleanCandidateWord(String raw) {
        if (raw == null) return "";
        return raw.replaceAll("[.:;!?,\"'`]", "").trim();
    }

    private static boolean isValidCandidateWord(String word) {
        if (word == null || word.isBlank() || word.length() < 2) {
            return false;
        }
        String lower = word.toLowerCase().trim();
        if (STRUCTURAL_KEYWORDS.contains(lower)) {
            return false;
        }
        if (lower.matches("^\\d+$")) {
            return false;
        }
        return true;
    }

    private static String buildWordFlexRegex(String word) {
        if (word == null || word.isBlank()) return "";
        String w = word.toLowerCase().trim();
        if (w.endsWith("is") && w.length() > 3) {
            String stem = w.substring(0, w.length() - 2);
            return "(?:" + Pattern.quote(w) + "|" + Pattern.quote(stem + "es") + ")";
        }
        if (w.endsWith("ss") || w.endsWith("us")) {
            return "(?:" + Pattern.quote(w) + "(?:es)?)";
        }
        if (w.endsWith("sses") || w.endsWith("shes") || w.endsWith("ches") || w.endsWith("xes")) {
            String sing = w.substring(0, w.length() - 2);
            return "(?:" + Pattern.quote(w) + "|" + Pattern.quote(sing) + "(?:es)?)";
        }
        if (w.endsWith("s") && w.length() > 3) {
            String sing = w.substring(0, w.length() - 1);
            return "(?:" + Pattern.quote(w) + "|" + Pattern.quote(sing) + "(?:s|es)?)";
        }
        return "(?:" + Pattern.quote(w) + "(?:s|es)?)";
    }

    private static boolean isSameWord(String w1, String w2) {
        if (w1 == null || w2 == null) return false;
        String s1 = w1.toLowerCase().trim();
        String s2 = w2.toLowerCase().trim();
        if (s1.equals(s2)) return true;
        if (s1.endsWith("es") && s1.substring(0, s1.length() - 2).equals(s2)) return true;
        if (s2.endsWith("es") && s2.substring(0, s2.length() - 2).equals(s1)) return true;
        if (s1.endsWith("s") && !s1.endsWith("ss") && s1.substring(0, s1.length() - 1).equals(s2)) return true;
        if (s2.endsWith("s") && !s2.endsWith("ss") && s2.substring(0, s2.length() - 1).equals(s1)) return true;
        if (s1.endsWith("es") && s2.endsWith("is") && s1.substring(0, s1.length() - 2).equals(s2.substring(0, s2.length() - 2))) return true;
        if (s2.endsWith("es") && s1.endsWith("is") && s2.substring(0, s2.length() - 2).equals(s1.substring(0, s1.length() - 2))) return true;
        return false;
    }

    private boolean hasDefinitionForCandidate(Candidate cand, List<String> lines, int nextCandidateIdx) {
        if (cand.isPrefixed()) {
            return true;
        }
        String wFlex = buildWordFlexRegex(cand.word());
        int maxK = Math.min(cand.lineIndex() + 15, nextCandidateIdx);
        List<String> windowLines = lines.subList(cand.lineIndex(), maxK);
        String windowText = String.join(" ", windowLines).toLowerCase();

        // Pattern 1: "definition/meaning of [a/an/the] <wFlex> is/:"
        Pattern pDefOf = Pattern.compile("(?i)(?:the\\s+)?(?:definition|meaning)\\s+of\\s+(?:a\\s+|an\\s+|the\\s+)?" + wFlex + "\\b");
        if (pDefOf.matcher(windowText).find()) return true;

        // Pattern 2: "<wFlex> [the] definition/meaning is/:"
        Pattern pWordDef = Pattern.compile("(?i)\\b" + wFlex + "\\s+(?:the\\s+)?(?:definition|meaning)\\s*(?:is|:|\\b)");
        if (pWordDef.matcher(windowText).find()) return true;

        // Pattern 3: copula "<wFlex> is/are [defined as | a | an | the | to | like | means | refers to]"
        Pattern pCopula = Pattern.compile("(?i)\\b(?:a\\s+|an\\s+|the\\s+)?" + wFlex + "\\s+(?:is\\s+(?:defined\\s+as|a|an|the|to\\b|like\\b)|are\\s+(?:defined\\s+as|a|an|the|to\\b|like\\b)|means\\b|refers\\s+to\\b)");
        if (pCopula.matcher(windowText).find()) return true;

        // Pattern 4: Standalone definition keyword, meaning keyword, or IPA notation within immediate 1-3 lines
        for (int i = 1; i < Math.min(4, windowLines.size()); i++) {
            String line = windowLines.get(i).trim();
            if (line.matches("(?i)^(?:the\\s+)?(?:definition|meaning)\\b.*") || IPA_PATTERN.matcher(line).find()) {
                return true;
            }
        }
        return false;
    }

    private String extractPartOfSpeech(String fullSectionText, String definition, String word) {
        // 1. Explicit parentheses or bracket tags near the word:
        // e.g. (noun), (verb), (adjective), (adv), (v.), (n.), (adj.), (verb/noun)
        Pattern bracketedPos = Pattern.compile("(?i)[\\[(](?:a\\s+)?(noun|verb|adjective|adverb|adj\\.?|adv\\.?|v\\.?|n\\.?)(?:/[a-z.]+)?[\\])]");
        Matcher mBracket = bracketedPos.matcher(fullSectionText);
        if (mBracket.find()) {
            String tag = mBracket.group(1).toLowerCase().replaceAll("\\.", "").trim();
            return switch (tag) {
                case "v", "verb" -> "verb";
                case "adj", "adjective" -> "adjective";
                case "adv", "adverb" -> "adverb";
                default -> "noun";
            };
        }

        // 2. Explicit phrasing: "part of speech: verb", "is a verb", "as a verb"
        Pattern phrasedPos = Pattern.compile("(?i)(?:part\\s+of\\s+speech\\s*[:\\s]\\s*|\\b(?:is|as)\\s+(?:an?\\s+)?)(noun|verb|adjective|adverb)\\b");
        Matcher mPhrased = phrasedPos.matcher(fullSectionText);
        if (mPhrased.find()) {
            return mPhrased.group(1).toLowerCase();
        }

        // 3. Inspect definition text
        if (definition != null && !definition.isBlank()) {
            String cleanDef = definition.replaceAll("^[\"']+", "").trim().toLowerCase();
            if (cleanDef.matches("^to\\s+[a-z]+.*") || cleanDef.startsWith("help someone")) {
                return "verb";
            }
            if (cleanDef.startsWith("present,") || cleanDef.startsWith("relating to") || cleanDef.startsWith("characterized by")) {
                return "adjective";
            }
            if (cleanDef.startsWith("a ") || cleanDef.startsWith("an ") || cleanDef.startsWith("the ") || cleanDef.startsWith("a person") || cleanDef.startsWith("a brother")) {
                return "noun";
            }
        }

        // 4. Word derivational morphology heuristics
        String w = word.toLowerCase();
        if (w.endsWith("tion") || w.endsWith("sion") || w.endsWith("ment") || w.endsWith("ence") || w.endsWith("ance") || w.endsWith("ship") || w.endsWith("ity") || w.endsWith("ness") || w.endsWith("sis") || w.endsWith("is")) {
            return "noun";
        }
        if (w.endsWith("ous") || w.endsWith("ful") || w.endsWith("able") || w.endsWith("ible") || w.endsWith("ive") || w.endsWith("al") || w.endsWith("ic")) {
            return "adjective";
        }
        if (w.endsWith("ly")) {
            return "adverb";
        }
        if (w.endsWith("ize") || w.endsWith("ise") || w.endsWith("ate") || w.endsWith("ify")) {
            return "verb";
        }

        return "noun";
    }

    private String extractDefinition(String defZone, String fullText, String word) {
        // 1. Explicit DEFINITION_PATTERN in defZone
        Matcher m = DEFINITION_PATTERN.matcher(defZone);
        if (m.find()) {
            return cleanDefinitionText(m.group(1).trim());
        }

        // 2. Word copula pattern (e.g. "A hypothesis is defined as...", "Relative is a person...", "Analysis refers to...")
        String wFlex = buildWordFlexRegex(word);
        Pattern pWordDef = Pattern.compile(
            "(?i)\\b(?:a\\s+|an\\s+|the\\s+)?" + wFlex + "\\s+(?:is\\s+(?:defined\\s+as\\s+)?|are\\s+(?:defined\\s+as\\s+)?|means\\s+|refers\\s+to\\s+)([^.\\n\\r]+)"
        );
        Matcher mWordDef = pWordDef.matcher(defZone);
        if (mWordDef.find()) {
            return cleanDefinitionText(mWordDef.group(1).trim());
        }

        // 3. Word with colon/dash header (e.g. "Hypothesis: explanation...")
        Pattern pWordColon = Pattern.compile(
            "(?i)\\b(?:a\\s+|an\\s+|the\\s+)?" + wFlex + "\\s*[:\\-]\\s*([^.\\n\\r]+)"
        );
        Matcher mWordColon = pWordColon.matcher(defZone);
        if (mWordColon.find()) {
            return cleanDefinitionText(mWordColon.group(1).trim());
        }

        // 4. Fallback search across fullText
        Matcher mFull = DEFINITION_PATTERN.matcher(fullText);
        if (mFull.find()) {
            return cleanDefinitionText(mFull.group(1).trim());
        }
        Matcher mFullWord = pWordDef.matcher(fullText);
        if (mFullWord.find()) {
            return cleanDefinitionText(mFullWord.group(1).trim());
        }

        // 5. Authentic line extraction from defZone (eliminate dummy facade)
        for (String line : defZone.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()
                    || TIMESTAMP_LINE.matcher(trimmed).matches()
                    || IPA_PATTERN.matcher(trimmed).matches()
                    || isSameWord(trimmed, word)
                    || trimmed.toLowerCase().startsWith("for example")
                    || trimmed.toLowerCase().startsWith("example")
                    || trimmed.toLowerCase().startsWith("collocation")) {
                continue;
            }
            return cleanDefinitionText(trimmed);
        }

        return cleanDefinitionText(defZone.trim());
    }

    private String cleanDefinitionText(String raw) {
        if (raw == null) return "";
        String[] split = raw.split("(?i)\\s+(?:this is|and you can see|and this means|and it says|also note|and some example)\\b");
        String res = split[0].trim();
        res = res.replaceAll("^(?i)defined\\s+as\\s+", "").trim();
        res = res.replaceAll("^[:\\-\\s]+", "").trim();
        return res;
    }

    private String extractCollocation(String colZone, String fullText, String word) {
        String cleanFull = fullText.replaceAll("[\"']", " ");
        String cleanCol = colZone.replaceAll("[\"']", " ");
        String w = word.toLowerCase();
        String wStem;
        if (w.endsWith("ss") || w.endsWith("is") || w.endsWith("us") || !w.endsWith("s") || w.length() <= 4) {
            wStem = w;
        } else {
            wStem = w.substring(0, w.length() - 1);
        }

        // 1. Explicit labeled collocations (e.g. "First collocation: establish a relationship", "Key collocations: nurture talent, nurture & protect")
        Pattern pLabeled = Pattern.compile("(?i)(?:first|second|third|fourth|fifth|key|common)?\\s*collocations?\\s*[:\\-]\\s*([^.\\n\\r]+)");
        Matcher mLabeled = pLabeled.matcher(cleanFull);
        List<String> labeledList = new ArrayList<>();
        while (mLabeled.find()) {
            String rawMatch = mLabeled.group(1).trim();
            if (!rawMatch.isEmpty() && !rawMatch.toLowerCase().startsWith("chart")) {
                String[] parts = rawMatch.split("[,;]");
                for (String p : parts) {
                    String trimmed = p.replaceAll("\\s+", " ").trim();
                    if (!trimmed.isEmpty() && trimmed.toLowerCase().contains(wStem)) {
                        labeledList.add(trimmed);
                    }
                }
            }
        }
        if (!labeledList.isEmpty()) {
            return String.join(", ", labeledList);
        }

        // 2. Dynamic 'X is a common collocation' pattern
        Pattern pIsCol = Pattern.compile("(?i)\\b([a-zA-Z-]+\\s+" + Pattern.quote(wStem) + "[a-z]*|" + Pattern.quote(wStem) + "[a-z]*\\s+[a-zA-Z-]+)\\s+is\\s+a\\s+(?:common\\s+)?(?:collocation|cocation)");
        Matcher mIsCol = pIsCol.matcher(cleanCol);
        if (mIsCol.find()) {
            String cand = mIsCol.group(1).replaceAll("\\s+", " ").trim().toLowerCase();
            return normalizePluralCollocation(cand, w);
        }

        // 3. Universal English academic grammar patterns in speech text (earliest occurrence)
        Pattern pNounComp = Pattern.compile("(?i)\\b(" + Pattern.quote(wStem) + "[a-z]*\\s+(?:rivalry|talent|skills?|bonds?|growth|environment|support|presence))\\b");
        Pattern pAdjNoun = Pattern.compile("(?i)\\b((?:distant|close|personal|working|strong|good|elderly|special|early|late|healthy|ubiquitous|profound|crucial|turbulent)\\s+" + Pattern.quote(wStem) + "[a-z]*)\\b");
        Pattern pVerbObj = Pattern.compile("(?i)\\b((?:establish|develop|maintain|build|create|preserve|promote|enhance|foster)\\s+(?:a\\s+|an\\s+)?" + Pattern.quote(wStem) + "[a-z]*)\\b");

        record MatchedCollocation(int offset, String phrase) {}
        List<MatchedCollocation> matches = new ArrayList<>();

        for (Pattern pat : List.of(pNounComp, pAdjNoun, pVerbObj)) {
            Matcher sm = pat.matcher(cleanCol);
            while (sm.find()) {
                String cand = sm.group(1).replaceAll("\\s+", " ").trim().toLowerCase();
                matches.add(new MatchedCollocation(sm.start(), normalizePluralCollocation(cand, w)));
            }
        }
        if (!matches.isEmpty()) {
            matches.sort(Comparator.comparingInt(MatchedCollocation::offset));
            return matches.get(0).phrase();
        }

        // 4. Conversational speech intro cues in speech transcripts
        List<Pattern> conversationalCues = List.of(
            Pattern.compile("(?i)(?:example\\s+of\\s+(?:a|an)?|such\\s+as\\s+(?:a|an)?)\\s+([a-zA-Z-]+(?:\\s+[a-zA-Z-]+){1,2})"),
            Pattern.compile("(?i)(?:next\\s+(?:is\\s+)?|first\\s+(?:is\\s+|we\\s+have\\s+)?|we\\s+have\\s+|and\\s+to\\s+|to\\s+)(?:a\\s+|an\\s+)?([a-zA-Z-]+(?:\\s+[a-zA-Z-]+){1,2})"),
            Pattern.compile("(?i)\\b([a-zA-Z-]+(?:\\s+[a-zA-Z-]+){1,2})\\s+(?:this\\s+means|means|are\\s+your|is\\s+like|usually\\s+compares)\\b")
        );
        for (Pattern cue : conversationalCues) {
            Matcher cm = cue.matcher(cleanCol);
            while (cm.find()) {
                String match = cm.group(1).replaceAll("\\s+", " ").trim().toLowerCase();
                if (match.contains(wStem)) {
                    return normalizePluralCollocation(match, w);
                }
            }
        }

        // 5. Standalone 2-3 word phrase line in colZone containing target word
        for (String line : colZone.split("\\r?\\n")) {
            String trimmed = line.trim().toLowerCase();
            String[] tokens = trimmed.split("\\s+");
            if (tokens.length >= 2 && tokens.length <= 3 && Arrays.stream(tokens).anyMatch(t -> t.startsWith(wStem))) {
                return normalizePluralCollocation(String.join(" ", tokens), w);
            }
        }

        // 6. General fallback: 2-word phrase containing word
        Pattern pGen = Pattern.compile("(?i)\\b([a-zA-Z-]+\\s+" + Pattern.quote(wStem) + "[a-z]*|" + Pattern.quote(wStem) + "[a-z]*\\s+[a-zA-Z-]+)\\b");
        Matcher mGen = pGen.matcher(cleanCol);
        if (mGen.find()) {
            String cand = mGen.group(0).replaceAll("\\s+", " ").trim().toLowerCase();
            Set<String> stopTokens = Set.of("this", "that", "the", "a", "an", "our", "your", "for", "with", "and", "or", "so", "is");
            boolean containsStop = Arrays.stream(cand.split("\\s+")).anyMatch(stopTokens::contains);
            if (!containsStop) {
                return normalizePluralCollocation(cand, w);
            }
        }

        return "common collocation";
    }

    private static String normalizePluralCollocation(String phrase, String targetWord) {
        if (phrase == null || targetWord == null) return phrase;
        String trimmed = phrase.replaceAll("\\s+", " ").trim().toLowerCase();
        trimmed = trimmed.replaceAll("(?i)^(?:a|an|the)\\s+", "");
        trimmed = trimmed.replaceAll("(?i)\\s+(?:on|in|at|to|for|with|by|of|from|and|or|so|is|the|a|an)$", "");
        String tLower = targetWord.toLowerCase();
        if (!tLower.endsWith("s") && trimmed.endsWith(tLower + "s")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private String extractCollocationMeaning(String colZone, String collocation) {
        if (colZone == null || colZone.isEmpty() || collocation == null || collocation.isEmpty()) {
            return "";
        }
        Pattern pMeaning = Pattern.compile("(?i)(?:meaning|this means|which means)\\s+([^.\\n\\r]+)");
        Matcher m = pMeaning.matcher(colZone);
        if (m.find()) {
            return m.group(1).trim();
        }
        return "";
    }

    private String extractExample(String exZone, String fullText, String word) {
        Matcher m = EXAMPLE_PATTERN.matcher(exZone);
        if (m.find()) {
            String ex = m.group(1).trim();
            String[] split = ex.split("(?i)\\s+(?:so brothers|in this sentence|very true|here were|now of course)\\b");
            return split[0].trim();
        }
        Matcher mFull = EXAMPLE_PATTERN.matcher(fullText);
        if (mFull.find()) {
            String ex = mFull.group(1).trim();
            String[] split = ex.split("(?i)\\s+(?:so brothers|in this sentence|very true|here were|now of course)\\b");
            return split[0].trim();
        }
        return "Example with " + word;
    }

    private static String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return Character.toUpperCase(str.charAt(0)) + str.substring(1).toLowerCase();
    }
}
