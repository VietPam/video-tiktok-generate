package com.tiktok.video.script.ipa;

import com.tiktok.video.core.model.VocabularyItem;

public interface IpaEnricher {
    String lookup(String word);
    VocabularyItem enrich(VocabularyItem item);
}
