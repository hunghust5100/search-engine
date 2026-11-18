package com.matrix.search.nlp.analyzer;

import com.matrix.search.nlp.service.VietnameseNlpServiceImpl;
import lombok.RequiredArgsConstructor;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.StopFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.springframework.stereotype.Component;

/**
 * Shared Lucene {@link Analyzer} bean used consistently during indexing and searching.
 *
 * <p>Pipeline: {@link VietnameseTokenizer} (VnCoreNLP {@code wseg}) -> {@link LowerCaseFilter}
 * -> {@link StopFilter} -> {@link VietnameseTokenFilter} (unaccented & syllable expansion).
 */
@Component
@RequiredArgsConstructor
public class VietnameseAnalyzer extends Analyzer {

    private final VietnameseNlpServiceImpl nlpService;

    @Override
    protected TokenStreamComponents createComponents(String fieldName) {
        Tokenizer source = new VietnameseTokenizer(nlpService);
        TokenStream result = new LowerCaseFilter(source);
        result = new StopFilter(result, nlpService.getLuceneStopwords());
        result = new VietnameseTokenFilter(result, nlpService);
        return new TokenStreamComponents(source, result);
    }
}
