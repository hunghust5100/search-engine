package com.matrix.search.indexer;

import com.matrix.search.common.LuceneFields;
import com.matrix.search.document.entity.WebDocument;
import com.matrix.search.document.service.DocumentService;
import com.matrix.search.indexer.service.IndexerService;
import com.matrix.search.indexer.service.IndexerServiceImpl;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class IndexerServiceImplTest {

    private static final Instant CRAWLED_AT = Instant.parse("2026-11-20T12:00:00Z");

    @TempDir
    Path indexPath;

    private Directory directory;
    private StandardAnalyzer analyzer;
    private DocumentService documentService;
    private IndexerService indexer;

    @BeforeEach
    void setUp() throws IOException {
        directory = FSDirectory.open(indexPath);
        analyzer = new StandardAnalyzer();
        documentService = mock(DocumentService.class);
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("documentService", documentService);
        indexer = new IndexerServiceImpl(directory, analyzer, beans.getBeanProvider(DocumentService.class));
    }

    @AfterEach
    void tearDown() throws IOException {
        analyzer.close();
        directory.close();
    }

    @Test
    void indexesBatchAndStoresFieldsForSearch() throws IOException {
        WebDocument first = page(1, "Thông báo");
        first.setTitle("Tuyển sinh");
        first.setDescription("Hướng dẫn đăng ký");
        WebDocument second = page(2, null);
        doAnswer(invocation -> {
            try (DirectoryReader reader = DirectoryReader.open(directory)) {
                assertEquals(2, reader.numDocs());
            }
            return null;
        }).when(documentService).markIndexed(List.of(1L, 2L));

        indexer.indexBatch(List.of(first, second));

        try (DirectoryReader reader = DirectoryReader.open(directory)) {
            IndexSearcher searcher = new IndexSearcher(reader);
            Document stored = findOne(searcher, LuceneFields.CONTENT, "thông");
            assertEquals("1", stored.get(LuceneFields.ID));
            assertEquals(first.getUrl(), stored.get(LuceneFields.URL));
            assertEquals(first.getSubdomain(), stored.get(LuceneFields.SUBDOMAIN));
            assertEquals(first.getTitle(), stored.get(LuceneFields.TITLE));
            assertEquals(first.getDescription(), stored.get(LuceneFields.DESCRIPTION));
            assertEquals(first.getCleanContent(), stored.get(LuceneFields.CONTENT));
            assertEquals(CRAWLED_AT, Instant.parse(stored.get(LuceneFields.CRAWLED_AT)));
            assertEquals(1, searcher.count(term(LuceneFields.TITLE, "tuyển")));
            assertEquals(1, searcher.count(term(LuceneFields.DESCRIPTION, "hướng")));
            assertEquals(2, searcher.count(term(LuceneFields.SUBDOMAIN, "hust.edu.vn")));

            Document empty = findOne(searcher, LuceneFields.ID, "2");
            assertEquals("", empty.get(LuceneFields.TITLE));
            assertEquals("", empty.get(LuceneFields.DESCRIPTION));
            assertEquals("", empty.get(LuceneFields.CONTENT));
        }
        verify(documentService).markIndexed(List.of(1L, 2L));
    }

    @Test
    void updatesAnExistingIdWithoutDuplicatingIt() throws IOException {
        indexer.indexDocument(page(1, "Cũ"));
        indexer.indexDocument(page(1, "Mới"));

        try (DirectoryReader reader = DirectoryReader.open(directory)) {
            IndexSearcher searcher = new IndexSearcher(reader);
            assertEquals(1, reader.numDocs());
            assertEquals(0, searcher.count(term(LuceneFields.CONTENT, "cũ")));
            assertEquals("Mới", findOne(searcher, LuceneFields.ID, "1").get(LuceneFields.CONTENT));
        }
    }

    @Test
    void rebuildsFromDatabaseAndRemovesStaleDocuments() throws IOException {
        indexer.indexDocument(page(1, "Cũ"));
        WebDocument current = page(2, "Mới");
        current.setIndexed(true);
        when(documentService.findAllDocuments()).thenReturn(List.of(current, page(3, "Tuyển sinh")));

        assertEquals(2, indexer.reindexAll());

        try (DirectoryReader reader = DirectoryReader.open(directory)) {
            IndexSearcher searcher = new IndexSearcher(reader);
            assertEquals(2, reader.numDocs());
            assertEquals(0, searcher.count(term(LuceneFields.ID, "1")));
            assertEquals("Mới", findOne(searcher, LuceneFields.ID, "2").get(LuceneFields.CONTENT));
        }
        verify(documentService).markIndexed(List.of(2L, 3L));
    }

    @Test
    void clearsTheIndexWhenDatabaseIsEmpty() throws IOException {
        indexer.indexDocument(page(1, "Cũ"));
        when(documentService.findAllDocuments()).thenReturn(List.of());

        assertEquals(0, indexer.reindexAll());

        try (DirectoryReader reader = DirectoryReader.open(directory)) {
            assertEquals(0, reader.numDocs());
        }
    }

    @Test
    void preservesLastCommitWhenBatchOrRebuildFails() throws IOException {
        indexer.indexDocument(page(1, "Cũ"));
        clearInvocations(documentService);
        List<WebDocument> invalidBatch = List.of(page(2, "Mới"), new WebDocument());

        assertThrows(IllegalArgumentException.class, () -> indexer.indexBatch(invalidBatch));
        verifyNoInteractions(documentService);
        when(documentService.findAllDocuments()).thenReturn(invalidBatch);
        assertThrows(IllegalArgumentException.class, indexer::reindexAll);
        verify(documentService).findAllDocuments();
        verifyNoMoreInteractions(documentService);

        try (DirectoryReader reader = DirectoryReader.open(directory)) {
            IndexSearcher searcher = new IndexSearcher(reader);
            assertEquals(1, reader.numDocs());
            assertEquals("Cũ", findOne(searcher, LuceneFields.ID, "1").get(LuceneFields.CONTENT));
        }
    }

    private static WebDocument page(long id, String content) {
        return WebDocument.builder()
                .id(id)
                .url("https://hust.edu.vn/" + id)
                .subdomain("hust.edu.vn")
                .cleanContent(content)
                .crawledAt(CRAWLED_AT)
                .build();
    }

    private static Document findOne(IndexSearcher searcher, String field, String value) throws IOException {
        var hits = searcher.search(term(field, value), 2);
        assertEquals(1, hits.totalHits.value());
        return searcher.storedFields().document(hits.scoreDocs[0].doc);
    }

    private static TermQuery term(String field, String value) {
        return new TermQuery(new Term(field, value));
    }
}
