package com.matrix.search.indexer.mapper;

import com.matrix.search.common.LuceneFields;
import com.matrix.search.document.entity.WebDocument;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;

import java.util.Objects;

public final class LuceneDocumentMapper {

    private LuceneDocumentMapper() {
    }

    public static Document toLuceneDocument(WebDocument source) {
        if (source.getId() == null) {
            throw new IllegalArgumentException("WebDocument must have a database ID");
        }

        Document document = new Document();
        document.add(new StringField(LuceneFields.ID, source.getId().toString(), Store.YES));
        document.add(new StringField(LuceneFields.URL, source.getUrl(), Store.YES));
        document.add(new StringField(LuceneFields.SUBDOMAIN, source.getSubdomain(), Store.YES));
        document.add(new TextField(LuceneFields.TITLE, Objects.requireNonNullElse(source.getTitle(), ""), Store.YES));
        document.add(new TextField(LuceneFields.DESCRIPTION, Objects.requireNonNullElse(source.getDescription(), ""), Store.YES));
        document.add(new TextField(LuceneFields.CONTENT, Objects.requireNonNullElse(source.getCleanContent(), ""), Store.YES));
        document.add(new StoredField(LuceneFields.CRAWLED_AT, source.getCrawledAt().toString()));
        return document;
    }
}
