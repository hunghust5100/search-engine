package com.matrix.search.nlp.analyzer;

import com.matrix.search.nlp.service.VietnameseNlpServiceImpl;
import com.matrix.search.nlp.service.VietnameseNlpServiceImpl.SegmentedToken;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;

import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Custom Lucene {@link Tokenizer} that delegates Vietnamese word segmentation to VnCoreNLP
 * while preserving source character offsets for snippet highlighting.
 */
public final class VietnameseTokenizer extends Tokenizer {

    private final VietnameseNlpServiceImpl nlpService;
    private final CharTermAttribute termAttr = addAttribute(CharTermAttribute.class);
    private final OffsetAttribute offsetAttr = addAttribute(OffsetAttribute.class);
    private final PositionIncrementAttribute posIncrAttr = addAttribute(PositionIncrementAttribute.class);

    private Iterator<SegmentedToken> tokenIterator = Collections.emptyIterator();
    private int rawTextLength = 0;

    /**
     * Create a tokenizer backed by the shared {@link VietnameseNlpServiceImpl}.
     */
    public VietnameseTokenizer(VietnameseNlpServiceImpl nlpService) {
        this.nlpService = nlpService;
    }

    @Override
    public void reset() throws IOException {
        super.reset();
        String rawText = readAll(this.input);
        this.rawTextLength = rawText.length();
        List<SegmentedToken> tokens = nlpService.segmentWithOffsets(rawText);
        this.tokenIterator = tokens.iterator();
    }

    @Override
    public boolean incrementToken() {
        if (!tokenIterator.hasNext()) {
            return false;
        }
        clearAttributes();
        SegmentedToken token = tokenIterator.next();
        termAttr.append(token.term());
        offsetAttr.setOffset(correctOffset(token.startOffset()), correctOffset(token.endOffset()));
        posIncrAttr.setPositionIncrement(1);
        return true;
    }

    @Override
    public void end() throws IOException {
        super.end();
        int finalOffset = correctOffset(rawTextLength);
        offsetAttr.setOffset(finalOffset, finalOffset);
    }

    private String readAll(java.io.Reader reader) throws IOException {
        StringBuilder sb = new StringBuilder();
        char[] buffer = new char[4096];
        int read;
        while ((read = reader.read(buffer)) != -1) {
            sb.append(buffer, 0, read);
        }
        return sb.toString();
    }
}
