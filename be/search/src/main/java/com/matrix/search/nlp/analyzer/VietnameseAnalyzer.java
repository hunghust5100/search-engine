package com.matrix.search.nlp.analyzer;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.springframework.stereotype.Component;

/**
 * Shared Lucene {@link Analyzer} bean used consistently during indexing and searching.
 *
 * <p>Currently provides a baseline {@link StandardTokenizer} + {@link LowerCaseFilter} pipeline
 * so indexer and search modules can compile and run immediately; the NLP implementation will
 * replace this with VnCoreNLP word segmentation and Vietnamese stopword filtering.
 */
@Component
public class VietnameseAnalyzer extends Analyzer {

    @Override
    protected TokenStreamComponents createComponents(String fieldName) {
        Tokenizer source = new StandardTokenizer();
        TokenStream filter = new LowerCaseFilter(source);
        return new TokenStreamComponents(source, filter);
    }
}
