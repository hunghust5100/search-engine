package com.matrix.search.document.cleaner;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HtmlCleanerTest {

    private HtmlCleaner htmlCleaner;

    @BeforeEach
    void setUp() {
        htmlCleaner = new HtmlCleaner();
    }

    @Test
    void cleansNullOrEmptyHtmlSafely() {
        CleanedDocumentData nullResult = htmlCleaner.clean(null, null);
        assertNotNull(nullResult);
        assertEquals("", nullResult.title());
        assertEquals("", nullResult.description());
        assertEquals("", nullResult.cleanContent());

        CleanedDocumentData emptyResult = htmlCleaner.clean("   ", "");
        assertNotNull(emptyResult);
        assertEquals("", emptyResult.title());
        assertEquals("", emptyResult.description());
        assertEquals("", emptyResult.cleanContent());
    }

    @Test
    void extractsTitleFromTitleTag() {
        String html = "<html><head><title>  Đại học Bách khoa Hà Nội  </title></head><body><p>Nội dung</p></body></html>";
        CleanedDocumentData result = htmlCleaner.clean(html, "https://hust.edu.vn");
        assertEquals("Đại học Bách khoa Hà Nội", result.title());
    }

    @Test
    void extractsTitleFromOgTitleWhenTitleTagMissing() {
        String html = "<html><head><meta property=\"og:title\" content=\"Cổng thông tin tuyển sinh HUST\" /></head><body></body></html>";
        CleanedDocumentData result = htmlCleaner.clean(html, "https://ts.hust.edu.vn");
        assertEquals("Cổng thông tin tuyển sinh HUST", result.title());
    }

    @Test
    void extractsTitleFromH1WhenHeadTitlesMissing() {
        String html = "<html><body><h1>Thông báo học bổng tài năng</h1><p>Nội dung học bổng...</p></body></html>";
        CleanedDocumentData result = htmlCleaner.clean(html, "https://ctt.hust.edu.vn");
        assertEquals("Thông báo học bổng tài năng", result.title());
    }

    @Test
    void extractsDescriptionFromMetaTag() {
        String html = """
                <html>
                <head>
                    <title>Trang chủ</title>
                    <meta name="description" content="Trường Đại học Bách khoa Hà Nội đào tạo kỹ thuật công nghệ hàng đầu." />
                </head>
                <body><p>Chào mừng bạn đến với HUST.</p></body>
                </html>
                """;
        CleanedDocumentData result = htmlCleaner.clean(html, "https://hust.edu.vn");
        assertEquals("Trường Đại học Bách khoa Hà Nội đào tạo kỹ thuật công nghệ hàng đầu.", result.description());
    }

    @Test
    void extractsDescriptionFromOgDescriptionWhenMetaMissing() {
        String html = """
                <html>
                <head>
                    <meta property="og:description" content="Mô tả qua thẻ OpenGraph" />
                </head>
                <body><p>Chi tiết bài viết</p></body>
                </html>
                """;
        CleanedDocumentData result = htmlCleaner.clean(html, "https://hust.edu.vn");
        assertEquals("Mô tả qua thẻ OpenGraph", result.description());
    }

    @Test
    void fallbacksDescriptionToContentPrefixWhenMetaTagsMissing() {
        String html = "<html><body><p>Đại học Bách khoa Hà Nội được thành lập năm 1956, là trung tâm đào tạo và nghiên cứu khoa học kỹ thuật hàng đầu của cả nước.</p></body></html>";
        CleanedDocumentData result = htmlCleaner.clean(html, "https://hust.edu.vn");
        assertTrue(result.description().startsWith("Đại học Bách khoa Hà Nội được thành lập"));
    }

    @Test
    void stripsUnwantedNoiseTagsFromCleanContent() {
        String html = """
                <html>
                <head>
                    <title>Tiêu đề bài viết</title>
                    <style>body { font-size: 14px; }</style>
                    <script>console.log("tracking code");</script>
                </head>
                <body>
                    <header>Header logo and top bar</header>
                    <nav><a href="/home">Home</a> | <a href="/about">About</a></nav>
                    <aside>Sidebar advertisements</aside>
                    <main>
                        <article>
                            <h2>Thông báo xét tuyển</h2>
                            <p>Thí sinh đăng ký trực tuyến trước ngày 15/07/2026.</p>
                        </article>
                    </main>
                    <form action="/search"><input type="text" name="q"><button>Search</button></form>
                    <footer>Bản quyền thuộc về Trường ĐH Bách khoa Hà Nội &copy; 2026</footer>
                </body>
                </html>
                """;

        CleanedDocumentData result = htmlCleaner.clean(html, "https://hust.edu.vn/tuyen-sinh");
        String content = result.cleanContent();

        // Should include main article content
        assertTrue(content.contains("Thông báo xét tuyển"));
        assertTrue(content.contains("Thí sinh đăng ký trực tuyến trước ngày 15/07/2026."));

        // Should NOT include scripts, styles, header, nav, aside, form, footer
        assertFalse(content.contains("console.log"));
        assertFalse(content.contains("font-size"));
        assertFalse(content.contains("Header logo"));
        assertFalse(content.contains("Sidebar advertisements"));
        assertFalse(content.contains("Bản quyền thuộc về Trường ĐH Bách khoa"));
    }

    @Test
    void normalizesWhitespaceAndEntities() {
        String html = "<body><p>Trường&nbsp;&nbsp;&nbsp;Đại   học \n\n\t Bách&amp;Khoa &quot;Hà Nội&quot;</p></body>";
        CleanedDocumentData result = htmlCleaner.clean(html, "https://hust.edu.vn");
        assertEquals("Trường Đại học Bách&Khoa \"Hà Nội\"", result.cleanContent());
    }
}
