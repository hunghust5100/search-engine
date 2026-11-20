package com.matrix.search.indexer.service;

import com.matrix.search.common.LuceneFields;
import com.matrix.search.document.entity.WebDocument;
import com.matrix.search.document.service.DocumentService;
import com.matrix.search.indexer.mapper.LuceneDocumentMapper;
import lombok.RequiredArgsConstructor;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class IndexerServiceImpl implements IndexerService {

    private final Directory directory;
    private final Analyzer vietnameseAnalyzer;
    private final ObjectProvider<DocumentService> documentService;

    @Override
    public void indexDocument(WebDocument document) {
        indexBatch(List.of(document));
    }

    @Override
    public synchronized void indexBatch(List<WebDocument> documents) {
        if (!documents.isEmpty()) {
            writeIndex(documents, OpenMode.CREATE_OR_APPEND, documentService.getIfAvailable());
        }
    }

    @Override
    public synchronized int reindexAll() {
        DocumentService service = documentService.getIfAvailable();
        if (service == null) {
            throw new IllegalStateException("Reindexing requires a DocumentService implementation");
        }
        return writeIndex(service.findAllDocuments(), OpenMode.CREATE, service);
    }

    private int writeIndex(List<WebDocument> documents, OpenMode openMode, DocumentService service) {
        List<Long> indexedIds = new ArrayList<>(documents.size());
        // Closing a writer without a commit rolls back the batch, including a full rebuild.
        IndexWriterConfig config = new IndexWriterConfig(vietnameseAnalyzer)
                .setOpenMode(openMode)
                .setCommitOnClose(false);
        try (IndexWriter writer = new IndexWriter(directory, config)) {
            for (WebDocument source : documents) {
                Document document = LuceneDocumentMapper.toLuceneDocument(source);
                writer.updateDocument(new Term(LuceneFields.ID, document.get(LuceneFields.ID)), document);
                indexedIds.add(source.getId());
            }
            writer.commit();
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not write the Lucene index", ex);
        }

        if (service != null && !indexedIds.isEmpty()) {
            service.markIndexed(indexedIds);
        }
        return indexedIds.size();
    }
}
