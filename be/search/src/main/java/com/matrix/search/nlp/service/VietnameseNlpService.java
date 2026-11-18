package com.matrix.search.nlp.service;

import java.util.List;
import java.util.Set;

/**
 * Contract for Vietnamese text normalization, word segmentation, accent removal, and stopword filtering.
 */
public interface VietnameseNlpService {

    /**
     * Segment Vietnamese words using underscore joining (e.g. "Đại_học Bách_khoa").
     */
    String segment(String text);

    /**
     * Segment text, lowercase, remove punctuation, and filter out Vietnamese stopwords.
     */
    List<String> tokenize(String text);

    /**
     * Remove Vietnamese diacritics/accents (including converting 'đ'/'Đ' to 'd'/'D').
     */
    String removeAccents(String text);

    /**
     * Return the loaded set of Vietnamese stopwords (lowercase).
     */
    Set<String> getStopwords();
}
