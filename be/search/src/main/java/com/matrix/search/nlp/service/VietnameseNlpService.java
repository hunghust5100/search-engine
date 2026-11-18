package com.matrix.search.nlp.service;

import java.util.List;

/**
 * Contract for Vietnamese text normalization, word segmentation, and stopword filtering.
 */
public interface VietnameseNlpService {

    /**
     * Segment Vietnamese words using underscore joining (e.g. "đại_học bách_khoa").
     */
    String segment(String text);

    /**
     * Segment text and return filtered tokens ready for indexing or querying.
     */
    List<String> tokenize(String text);
}
