package com.matrix.search.nlp.analyzer;

import com.matrix.search.nlp.service.VietnameseNlpService;
import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.Queue;
import java.util.Set;

/**
 * Custom Lucene {@link TokenFilter} that expands accented and compound Vietnamese tokens
 * into unaccented variants and constituent syllables at the same position ({@code posInc = 0}).
 */
public final class VietnameseTokenFilter extends TokenFilter {

    private final VietnameseNlpService nlpService;
    private final CharTermAttribute termAttr = addAttribute(CharTermAttribute.class);
    private final PositionIncrementAttribute posIncrAttr = addAttribute(PositionIncrementAttribute.class);

    private final Queue<String> pendingTokens = new ArrayDeque<>();
    private State savedState;

    /**
     * Create a filter wrapping an upstream {@link TokenStream}.
     */
    public VietnameseTokenFilter(TokenStream input, VietnameseNlpService nlpService) {
        super(input);
        this.nlpService = nlpService;
    }

    @Override
    public void reset() throws IOException {
        super.reset();
        pendingTokens.clear();
        savedState = null;
    }

    @Override
    public boolean incrementToken() throws IOException {
        if (!pendingTokens.isEmpty()) {
            restoreState(savedState);
            termAttr.setEmpty().append(pendingTokens.poll());
            posIncrAttr.setPositionIncrement(0);
            return true;
        }

        if (!input.incrementToken()) {
            return false;
        }

        String current = termAttr.toString();
        Set<String> variants = collectVariants(current);
        if (!variants.isEmpty()) {
            pendingTokens.addAll(variants);
            savedState = captureState();
        }
        return true;
    }

    private Set<String> collectVariants(String current) {
        Set<String> variants = new LinkedHashSet<>();

        String unaccented = nlpService.removeAccents(current);
        if (!unaccented.isEmpty() && !unaccented.equals(current)) {
            variants.add(unaccented);
        }

        if (current.indexOf('_') >= 0) {
            String[] syllables = current.split("_");
            Set<String> stopwords = nlpService.getStopwords();
            for (String syllable : syllables) {
                if (syllable.isEmpty() || stopwords.contains(syllable)) {
                    continue;
                }
                if (!syllable.equals(current)) {
                    variants.add(syllable);
                }
                String syllableUnaccented = nlpService.removeAccents(syllable);
                if (!syllableUnaccented.isEmpty() && !syllableUnaccented.equals(current)) {
                    variants.add(syllableUnaccented);
                }
            }
        }
        return variants;
    }
}
