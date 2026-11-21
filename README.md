# HUST Search Engine

Hệ thống tìm kiếm toàn văn (Full-text Search Engine) thu thập dữ liệu từ `hust.edu.vn` và các trang web con (`*.hust.edu.vn`), trích xuất và chuẩn hóa tài liệu, tách từ tiếng Việt bằng **VnCoreNLP**, đánh chỉ mục và truy vấn bằng **Apache Lucene**.

## Công nghệ sử dụng

- **Java 25** & **Spring Boot 4.1.2** (Maven)
- **Apache Lucene 10.6.0** (`lucene-core`, `lucene-analysis-common`, `lucene-queryparser`, `lucene-highlighter`)
- **VnCoreNLP 1.2** (tách từ tiếng Việt `wseg`)
- **Jsoup 1.24.1** (crawl & parse HTML)
- **H2 Database** + **Spring Data JPA** (lưu metadata & nội dung tài liệu)
- **SpringDoc OpenAPI** (Swagger UI)

## Cấu trúc thư mục

```text
SearchEngine/
├── be/
│   ├── data/                   # Dữ liệu runtime (được ignore bởi git)
│   │   ├── db/                 # File H2 database
│   │   ├── lucene-index/       # Thư mục lưu inverted index của Lucene
│   │   └── raw-docs/           # Snapshot tài liệu thô (nếu cần)
│   ├── models/
│   │   └── vncorenlp/          # VnCoreNLP-1.2.jar và thư mục models/
│   └── search/                 # Project Spring Boot chính
│       └── src/main/java/com/matrix/search/
│           ├── config/         # LuceneConfig, VnNlpConfig, CrawlerConfig, CorsConfig, OpenApiConfig
│           ├── common/         # LuceneFields, tiện ích và xử lý lỗi dùng chung
│           ├── crawler/        # Thu thập dữ liệu web (*.hust.edu.vn)
│           ├── document/       # Làm sạch nội dung & lưu trữ WebDocument
│           ├── nlp/            # Xử lý tiếng Việt (VnCoreNLP) & VietnameseAnalyzer
│           ├── indexer/        # Đánh chỉ mục Lucene & reindex
│           └── search/         # API tìm kiếm, xếp hạng BM25 & highlight snippet
└── fe/                         # Giao diện HTML + CSS đơn giản
```

## Hướng dẫn chạy Backend

```bash
cd be/search
mvn clean install
mvn spring-boot:run
```

- **Swagger UI**: `http://localhost:8080/swagger-ui.html`
- **H2 Console**: `http://localhost:8080/h2-console` (JDBC URL: `jdbc:h2:file:../data/db/hust_search`, user: `sa`, password để trống)

## Crawler module

The crawler collects HTML pages from `hust.edu.vn` and its subdomains (`*.hust.edu.vn`) with a configurable pool of parallel worker threads.

**URL filtering** — only `http(s)` URLs whose host is the allowed domain or one of its subdomains enter the crawl frontier. URLs are canonicalized (lowercase scheme/host, no fragment, no default port, no trailing slash, tracking parameters stripped) so equivalent URLs deduplicate to a single entry. Non-HTML resources (PDF, images, archives, videos, executables) are skipped.

**robots.txt** — `robots.txt` is fetched and parsed per origin and cached. `Allow`/`Disallow` rules use longest-prefix matching (an `Allow` of equal length wins), `Crawl-delay` is honored, and a missing or unreachable `robots.txt` permits crawling. An HTTP 401/403 response disallows the whole origin.

**Politeness** — every host is fetched at most once per `delay-ms` window (or the robots `Crawl-delay`, whichever is larger), serialized across all worker threads.

### Crawler REST API

| Method | Path                 | Description                                                        |
|--------|----------------------|--------------------------------------------------------------------|
| `POST` | `/api/crawler/start` | Start a new asynchronous crawl (optional JSON body, see below)      |
| `POST` | `/api/crawler/stop`  | Stop the currently running crawl (graceful, returns 409 if idle)   |
| `GET`  | `/api/crawler/status`| Live snapshot of the running crawl, or of the most recent run       |

Example request body for `POST /api/crawler/start` (every field is optional and falls back to `app.search.crawler` configuration):

```json
{
  "seedUrls": ["https://hust.edu.vn"],
  "maxPages": 500,
  "maxDepth": 4,
  "threads": 8
}
```

The status response reports the run `state` (`IDLE`, `RUNNING`, `STOPPING`, `COMPLETED`, `STOPPED`, `FAILED`) along with counters (`pagesCrawled`, `pagesFailed`, `urlsDiscovered`, `urlsQueued`, `urlsSkippedRobots`) and the effective run parameters.

### Crawler configuration

| Key                          | Default                | Meaning                                              |
|------------------------------|------------------------|------------------------------------------------------|
| `seed-urls`                  | `https://hust.edu.vn`  | Entry points for a crawl                             |
| `allowed-domain-suffix`      | `hust.edu.vn`          | Only this domain and its subdomains are crawled      |
| `max-pages`                  | `500`                  | Page budget per run                                  |
| `max-depth`                  | `4`                    | Maximum link-follow depth (0 = seed pages only)     |
| `threads`                    | `8`                    | Worker threads per run                              |
| `respect-robots-txt`         | `true`                 | Honor robots.txt rules                               |
| `delay-ms`                   | `300`                  | Minimum interval between two fetches of one host     |
| `timeout-ms`                 | `10000`                | HTTP fetch timeout                                   |
| `user-agent`                 | `HustSearchBot/1.0`    | User agent sent with every request                   |

Crawled pages are handed to the document module via `DocumentService.saveOrUpdateFromCrawl(RawCrawledPage)` as soon as an implementation is registered.

## Document module

Module `document` chịu trách nhiệm làm sạch dữ liệu HTML thô thu được từ crawler, trích xuất siêu dữ liệu (`title`, `description`), chuẩn hóa văn bản, băm SHA-256 để chống trùng lặp dữ liệu, và quản lý lưu trữ / cập nhật `WebDocument` trong cơ sở dữ liệu H2.

### Các thành phần chính

1. **`HtmlCleaner` (Jsoup)**:
   - Phân tích cú pháp HTML bằng Jsoup.
   - Loại bỏ các thẻ không chứa nội dung chính hoặc gây nhiễu: `<script>`, `<style>`, `<noscript>`, `<iframe>`, `<header>`, `<footer>`, `<nav>`, `<aside>`, `<form>`, `<button>`, `<svg>`, `[role=navigation]`,...
   - Trích xuất `title`: ưu tiên thẻ `<title>`, fallback sang thẻ meta OpenGraph (`og:title`), Twitter card, hoặc heading `<h1>`.
   - Trích xuất `description`: ưu tiên thẻ `<meta name="description">`, `og:description`, fallback sang đoạn trích ngắn của nội dung sạch.
   - Trích xuất `cleanContent`: toàn bộ văn bản thuần (plain text) từ thẻ `<body>`, chuẩn hóa khoảng trắng thừa và giải mã HTML entities.

2. **`HashUtils` (SHA-256 Deduplication)**:
   - Tính mã băm SHA-256 (64 ký tự hex) từ `cleanContent`.
   - **Chống trùng lặp theo URL**:
     - Khi crawler gửi một URL đã có trong H2 DB: so sánh `contentHash` mới với `contentHash` đã lưu.
     - **Nếu hash trùng nhau**: Nội dung trang không đổi -> Chỉ cập nhật `crawledAt`, `updatedAt`, giữ nguyên trạng thái `indexed` (tránh việc Lucene phải index lại vô ích).
     - **Nếu hash khác nhau**: Nội dung trang đã được cập nhật -> Cập nhật `title`, `description`, `cleanContent`, `contentHash`, `crawledAt`, `updatedAt` và đặt `indexed = false` để Indexer quét và cập nhật lại vào Lucene index.
   - **Chống trùng lặp theo Content**:
     - Cung cấp phương thức `findFirstByContentHash(contentHash)` để phát hiện trùng lặp nội dung giữa các URL khác nhau.

3. **`WebDocumentRepository` & `DocumentServiceImpl` (H2 DB & Spring Data JPA)**:
   - Lưu trữ thực thể `WebDocument` vào H2 Database (`jdbc:h2:file:../data/db/hust_search`).
   - Hỗ trợ các thao tác CRUD, phân trang, đếm tài liệu đã/chưa index.
   - Cung cấp phương thức tối ưu `markIndexed(List<Long> documentIds)` cập nhật hàng loạt trạng thái `indexed = true` khi Lucene hoàn tất đánh chỉ mục.

### Document REST API

| Method | Path | Description |
|--------|------|-------------|
| `GET`  | `/api/documents` | Lấy danh sách WebDocument đã lưu trong H2 (phân trang: `page`, `size`) |
| `GET`  | `/api/documents/{id}` | Lấy thông tin chi tiết một WebDocument theo ID |
| `GET`  | `/api/documents/stats` | Thống kê số lượng tài liệu: tổng số, đã index, chưa index |
| `POST` | `/api/documents/clean` | Thử nghiệm làm sạch HTML và xem mã băm SHA-256 trực tiếp (không lưu DB) |

## Bộ Contract dùng chung (`feature/common`)

1. **Tài liệu Web (`crawler` → `document` → `indexer`)**:
   - `RawCrawledPage`: dữ liệu HTML thô crawler thu được.
   - `WebDocument` & `DocumentService`: entity tài liệu đã làm sạch, chống trùng và giao diện lưu/đọc từ H2.
2. **Tiếng Việt & Trường Lucene (`nlp` → `indexer` & `search`)**:
   - `LuceneFields`: hằng số tên trường Lucene (`id`, `url`, `subdomain`, `title`, `description`, `content`, `crawledAt`).
   - `VietnameseNlpService` & `VietnameseAnalyzer`: bean Lucene Analyzer dùng chung khi đánh index và tìm kiếm.
3. **Indexer & Search DTO (`indexer` ↔ `search`)**:
   - `IndexerService`: giao diện đánh index đơn lẻ, theo lô và `reindexAll()`.
   - `SearchRequest`, `SearchHitDto`, `SearchResponse`, `SearchService`: hợp đồng đầu vào/đầu ra cho API tìm kiếm.
