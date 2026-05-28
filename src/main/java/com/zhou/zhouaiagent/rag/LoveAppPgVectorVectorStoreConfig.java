package com.zhou.zhouaiagent.rag;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgDistanceType.COSINE_DISTANCE;
import static org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgIndexType.HNSW;

/**
 * https://docs.spring.io/spring-ai/reference/api/vectordbs/pgvector.html#_manual_configuration
 * 手动配置
 */
// 如有代码冲突可注释
@Slf4j
@Configuration
public class LoveAppPgVectorVectorStoreConfig {

    @Resource
    private LoveAppDocumentLoader loveAppDocumentLoader;

    /**
     * https://docs.spring.io/spring-ai/reference/api/vectordbs/pgvector.html#_auto_configuration
     * 自动配置只需要 引入自动配置依赖直接属性注入即可
     */
    //@Autowired
    private VectorStore vectorStore;

    @Resource
    private MyKeywordEnricher myKeywordEnricher;

    @Value("${rag.init-on-startup:true}")
    private boolean initOnStartup;

    private PgVectorStore pgVectorVectorStore;

    /**
     * 手动配置 需要自己显式配置
     * @param jdbcTemplate
     * @param dashScopeEmbeddingModel
     * @return
     */
    @Bean
    public PgVectorStore pgVectorVectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel dashScopeEmbeddingModel) {
        // PgVectorStore vectorStore = PgVectorStore.builder(jdbcTemplate, dashScopeEmbeddingModel)
        this.pgVectorVectorStore = PgVectorStore.builder(jdbcTemplate, dashScopeEmbeddingModel)
                .dimensions(1536)                    // Optional: defaults to model dimensions or 1536
                .distanceType(COSINE_DISTANCE)       // Optional: defaults to COSINE_DISTANCE
                .indexType(HNSW)                     // Optional: defaults to HNSW
                .initializeSchema(true)              // Optional: defaults to false
                .schemaName("public")                // Optional: defaults to "public"
                .vectorTableName("vector_store")     // Optional: defaults to "vector_store"
                .maxDocumentBatchSize(10000)         // Optional: defaults to 10000
                .build();
/*        // 加载一次就够了, 不然会重复
        // 加载文档
        List<Document> documents = loveAppDocumentLoader.loadMarkdowns();
        // 为文档补充元信息 (可能会有英文)
        // {"title": "如何提升自身魅力吸引潜在伴侣？", "category": "header_4", "filename": "恋爱常见问题和回答 - 单身篇.md", "excerpt_keywords": "personal grooming, confidence building, social skills, style development, holistic self-improvement"}
        // {"title": "线上交友有哪些注意事项能提高脱单成功率？", "category": "header_4", "filename": "恋爱常见问题和回答 - 单身篇.md", "excerpt_keywords": "线上交友,个人资料优化,聊天技巧,隐私保护,脱单指南"}
        List<Document> enrichDocuments = myKeywordEnricher.enrichDocuments(documents);
        vectorStore.add(enrichDocuments);
        return vectorStore;   */
        return this.pgVectorVectorStore;
    }

    /**
     * 应用启动完成后异步加载文档并增强元信息，避免阻塞 Bean 初始化和触发 API 限流
     */
    @EventListener(ApplicationReadyEvent.class)
    public void initVectorStore() {
        if (!initOnStartup) {
            log.info("rag.init-on-startup=false，跳过 PgVector 知识库初始化");
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                log.info("开始异步初始化 PgVector 知识库...");
                List<Document> documents = loveAppDocumentLoader.loadMarkdowns();
                List<Document> enrichDocuments = myKeywordEnricher.enrichDocuments(documents);
                pgVectorVectorStore.add(enrichDocuments);
                log.info("PgVector 知识库初始化完成，共索引 {} 个文档片段", enrichDocuments.size());
            } catch (Exception e) {
                log.error("PgVector 知识库初始化失败", e);
            }
        });
    }
}
