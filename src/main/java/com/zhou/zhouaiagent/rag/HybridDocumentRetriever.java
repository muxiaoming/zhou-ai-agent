package com.zhou.zhouaiagent.rag;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 向量 + 关键词混合检索器
 *
 * 设计原因：
 * - 向量检索擅长语义相似度匹配，但对精确关键词匹配不够敏感
 * - 关键词检索（ILIKE）可精确命中术语，弥补向量检索的盲区
 * - 两者合并去重后覆盖面更广，提升检索召回率
 *
 * 关键词检索方案：
 * 使用 PostgreSQL 原生 ILIKE 查询 content 和 metadata 列，
 * 无需额外引入 Elasticsearch，保持最简技术栈。
 * 对于当前知识库规模（数百条文档片段）完全够用。
 */
@Slf4j
public class HybridDocumentRetriever implements DocumentRetriever {

    private static final String KEYWORD_SEARCH_SQL = """
            SELECT id, content, metadata
            FROM public.vector_store
            WHERE content ILIKE ANY(?)
               OR metadata::text ILIKE ANY(?)
            LIMIT ?
            """;

    private final VectorStore vectorStore;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final double similarityThreshold;
    private final int topK;

    private HybridDocumentRetriever(Builder builder) {
        this.vectorStore = builder.vectorStore;
        this.jdbcTemplate = builder.jdbcTemplate;
        this.objectMapper = new ObjectMapper();
        this.similarityThreshold = builder.similarityThreshold;
        this.topK = builder.topK;
    }

    @Override
    public List<Document> retrieve(Query query) {
        String queryText = query.text();
        log.debug("Hybrid retrieval for query: {}", queryText);

        List<Document> vectorResults = vectorSearch(queryText);
        List<Document> keywordResults = keywordSearch(queryText);

        List<Document> merged = mergeDocuments(vectorResults, keywordResults);
        log.info("Hybrid retrieval: vector={}, keyword={}, merged={}",
                vectorResults.size(), keywordResults.size(), merged.size());
        return merged;
    }

    private List<Document> vectorSearch(String queryText) {
        SearchRequest searchRequest = SearchRequest.builder()
                .query(queryText)
                .topK(this.topK)
                .similarityThreshold(this.similarityThreshold)
                .build();
        return vectorStore.similaritySearch(searchRequest);
    }

    private List<Document> keywordSearch(String queryText) {
        List<String> patterns = extractPatterns(queryText);
        if (patterns.isEmpty()) {
            return List.of();
        }

        String[] patternArray = patterns.toArray(new String[0]);
        try {
            return jdbcTemplate.query(KEYWORD_SEARCH_SQL,
                    this::mapRow,
                    patternArray,
                    patternArray,
                    this.topK);
        } catch (Exception e) {
            log.warn("Keyword search failed, falling back to vector-only results", e);
            return List.of();
        }
    }

    private Document mapRow(java.sql.ResultSet rs, int rowNum) {
        try {
            String id = rs.getString("id");
            String content = rs.getString("content");
            String metadataJson = rs.getString("metadata");
            Map<String, Object> metadata = Map.of();
            if (metadataJson != null && !metadataJson.isBlank()) {
                metadata = objectMapper.readValue(metadataJson,
                        new TypeReference<Map<String, Object>>() {});
            }
            return new Document(id, content, metadata);
        } catch (Exception e) {
            log.warn("Failed to map row at index {}: {}", rowNum, e.getMessage());
            return null;
        }
    }

    private List<String> extractPatterns(String queryText) {
        String[] terms = queryText.split("[\\s,，。？?！!、；;：:\"\"''（）()\\[\\]【】]+");
        List<String> patterns = new ArrayList<>();
        for (String term : terms) {
            String trimmed = term.trim();
            if (trimmed.length() >= 2) {
                patterns.add("%" + trimmed + "%");
            }
        }
        return patterns;
    }

    private List<Document> mergeDocuments(List<Document> vectorResults,
                                          List<Document> keywordResults) {
        LinkedHashMap<String, Document> merged = new LinkedHashMap<>();
        for (Document doc : vectorResults) {
            if (doc != null) {
                merged.putIfAbsent(doc.getId(), doc);
            }
        }
        for (Document doc : keywordResults) {
            if (doc != null) {
                merged.putIfAbsent(doc.getId(), doc);
            }
        }
        return new ArrayList<>(merged.values());
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private VectorStore vectorStore;
        private JdbcTemplate jdbcTemplate;
        private double similarityThreshold = 0.5;
        private int topK = 3;

        public Builder vectorStore(VectorStore vectorStore) {
            this.vectorStore = vectorStore;
            return this;
        }

        public Builder jdbcTemplate(JdbcTemplate jdbcTemplate) {
            this.jdbcTemplate = jdbcTemplate;
            return this;
        }

        public Builder similarityThreshold(double similarityThreshold) {
            this.similarityThreshold = similarityThreshold;
            return this;
        }

        public Builder topK(int topK) {
            this.topK = topK;
            return this;
        }

        public HybridDocumentRetriever build() {
            if (vectorStore == null) {
                throw new IllegalArgumentException("vectorStore is required");
            }
            if (jdbcTemplate == null) {
                throw new IllegalArgumentException("jdbcTemplate is required");
            }
            return new HybridDocumentRetriever(this);
        }
    }
}
