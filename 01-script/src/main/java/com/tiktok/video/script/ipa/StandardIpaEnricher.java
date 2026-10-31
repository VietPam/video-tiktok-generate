package com.tiktok.video.script.ipa;

import com.tiktok.video.core.model.VocabularyItem;

import java.util.Map;
import java.util.regex.Pattern;

public class StandardIpaEnricher implements IpaEnricher {

    private static final Pattern CLEAN_WORD_PATTERN = Pattern.compile("[^a-zA-Z0-9-]");

    private static final Map<String, String> IPA_DICTIONARY = Map.of(
        "sibling", "/ˈsɪb.lɪŋ/",
        "relative", "/ˈrel.ə.tɪv/",
        "adolescence", "/ˌæd.əˈles.ns/",
        "adolescent", "/ˌæd.əˈles.nt/",
        "nurture", "/ˈnɜː.tʃər/",
        "nurturing", "/ˈnɜː.tʃər.ɪŋ/",
        "relationship", "/rɪˈleɪ.ʃn.ʃɪp/",
        "rivalry", "/ˈraɪ.vəl.ri/",
        "profound", "/prəˈfaʊnd/",
        "nuclear", "/ˈnjuː.kli.ər/"
    );

    @Override
    public String lookup(String word) {
        if (word == null || word.isBlank()) {
            return "";
        }
        String clean = CLEAN_WORD_PATTERN.matcher(word).replaceAll("").toLowerCase().trim();
        return IPA_DICTIONARY.getOrDefault(clean, "");
    }

    @Override
    public VocabularyItem enrich(VocabularyItem item) {
        if (item == null) {
            return null;
        }
        if (item.ipa() != null && !item.ipa().isBlank()) {
            return item;
        }
        String enrichedIpa = lookup(item.word());
        if (enrichedIpa.isBlank()) {
            return item;
        }
        return VocabularyItem.builder()
            .word(item.word())
            .ipa(enrichedIpa)
            .partOfSpeech(item.partOfSpeech())
            .definition(item.definition())
            .collocation(item.collocation())
            .collocationMeaning(item.collocationMeaning())
            .exampleSentence(item.exampleSentence())
            .build();
    }
}
