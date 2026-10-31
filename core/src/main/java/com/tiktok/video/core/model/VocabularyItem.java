package com.tiktok.video.core.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

/**
 * Domain model representing an IELTS vocabulary item with phonetic, grammatical,
 * and contextual details.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record VocabularyItem(
    @JsonProperty("word") String word,
    @JsonProperty("ipa") String ipa,
    @JsonProperty("partOfSpeech") String partOfSpeech,
    @JsonProperty("definition") String definition,
    @JsonProperty("collocation") String collocation,
    @JsonProperty("collocationMeaning") String collocationMeaning,
    @JsonProperty("exampleSentence") String exampleSentence,
    @JsonProperty("vietnameseMeaning") String vietnameseMeaning
) {
    public VocabularyItem {
        Objects.requireNonNull(word, "word cannot be null");
        if (word.isBlank()) {
            throw new IllegalArgumentException("word cannot be blank");
        }
        word = word.trim();
        ipa = ipa == null ? "" : ipa.trim();
        partOfSpeech = partOfSpeech == null ? "" : partOfSpeech.trim();
        definition = definition == null ? "" : definition.trim();
        collocation = collocation == null ? "" : collocation.trim();
        collocationMeaning = collocationMeaning == null ? "" : collocationMeaning.trim();
        exampleSentence = exampleSentence == null ? "" : exampleSentence.trim();
        vietnameseMeaning = vietnameseMeaning == null ? "" : vietnameseMeaning.trim();
    }

    public VocabularyItem(String word, String ipa, String partOfSpeech, String definition, String collocation, String collocationMeaning, String exampleSentence) {
        this(word, ipa, partOfSpeech, definition, collocation, collocationMeaning, exampleSentence, "");
    }

    /**
     * Convenient display title: e.g. "Sibling /ˈsɪb.lɪŋ/"
     */
    @JsonIgnore
    public String displayTitle() {
        if (ipa.isEmpty()) {
            return word;
        }
        return word + " " + ipa;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String word;
        private String ipa = "";
        private String partOfSpeech = "";
        private String definition = "";
        private String collocation = "";
        private String collocationMeaning = "";
        private String exampleSentence = "";
        private String vietnameseMeaning = "";

        public Builder word(String word) {
            this.word = word;
            return this;
        }

        public Builder ipa(String ipa) {
            this.ipa = ipa;
            return this;
        }

        public Builder partOfSpeech(String partOfSpeech) {
            this.partOfSpeech = partOfSpeech;
            return this;
        }

        public Builder definition(String definition) {
            this.definition = definition;
            return this;
        }

        public Builder collocation(String collocation) {
            this.collocation = collocation;
            return this;
        }

        public Builder collocationMeaning(String collocationMeaning) {
            this.collocationMeaning = collocationMeaning;
            return this;
        }

        public Builder exampleSentence(String exampleSentence) {
            this.exampleSentence = exampleSentence;
            return this;
        }

        public Builder vietnameseMeaning(String vietnameseMeaning) {
            this.vietnameseMeaning = vietnameseMeaning;
            return this;
        }

        public VocabularyItem build() {
            return new VocabularyItem(word, ipa, partOfSpeech, definition, collocation, collocationMeaning, exampleSentence, vietnameseMeaning);
        }
    }
}
