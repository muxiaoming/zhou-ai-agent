package com.zhou.zhouaiagent.rag;

import com.zhou.zhouaiagent.rag.model.RetrievalEvaluation;
import com.zhou.zhouaiagent.rag.model.RouteDecision;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Agentic RAG 服务
 * LLM 作为代理自主决策的完整 RAG Pipeline
 *
 * 设计原因：
 * - 查询路由：由 LLM 自主判断是否需要检索，替代硬编码规则
 * - 混合检索：向量语义检索 + 关键词精确匹配，提升召回率
 * - 多轮反思：LLM 评估检索质量，决定是否继续检索或放弃
 * - 查询改写：通过 RewriteQueryTransformer 优化检索词
 * - 兜底处理：检索为空时让 LLM 直接回答
 *
 * 面试价值：展示对 Agentic RAG 全链路的理解，包括 LLM 驱动的路由、
 * 混合检索策略、反思循环机制、查询改写与兜底方案
 */
@Slf4j
@Service
public class AgenticRagService {

    private static final int MAX_RETRIEVAL_ROUNDS = 2;

    private final ChatClient chatClient;
    private final HybridDocumentRetriever hybridDocumentRetriever;
    private final QueryTransformer queryTransformer;

    public AgenticRagService(ChatModel chatModel,
                             VectorStore vectorStore,
                             JdbcTemplate jdbcTemplate) {
        this.chatClient = ChatClient.builder(chatModel).build();
        this.hybridDocumentRetriever = HybridDocumentRetriever.builder()
                .vectorStore(vectorStore)
                .jdbcTemplate(jdbcTemplate)
                .similarityThreshold(0.5)
                .topK(5)
                .build();
        this.queryTransformer = RewriteQueryTransformer.builder()
                .chatClientBuilder(ChatClient.builder(chatModel))
                .build();
    }

    /**
     * 执行 Agentic RAG 查询
     *
     * @param query  用户查询
     * @param chatId 会话 ID
     * @return 回答内容
     */
    public String query(String query, String chatId) {
        log.info("Agentic RAG query: {}", query);

        // 1. LLM 路由决策：判断是否需要检索
        RouteDecision routeDecision = decideRoute(query);
        log.info("Route decision: {}, reason: {}", routeDecision.action(), routeDecision.reason());

        if (routeDecision.action() == RouteDecision.RouteAction.DIRECT_ANSWER) {
            log.info("Direct answer, skipping retrieval");
            return chatClient.prompt().user(query).call().content();
        }

        // 2. 查询改写，优化检索词
        String currentQuery = rewriteQuery(query);
        log.info("Rewritten query: {}", currentQuery);

        // 3. 多轮反思检索循环
        List<Document> allDocuments = new ArrayList<>();
        for (int round = 0; round < MAX_RETRIEVAL_ROUNDS; round++) {
            log.info("Retrieval round {}/{}", round + 1, MAX_RETRIEVAL_ROUNDS);

            Query ragQuery = new Query(currentQuery);
            List<Document> roundDocs = hybridDocumentRetriever.retrieve(ragQuery);
            log.info("Round {} retrieved {} documents", round + 1, roundDocs.size());

            // 首轮检索为空，尝试改写查询重试
            if (roundDocs.isEmpty() && round == 0) {
                currentQuery = rewriteQuery(query);
                log.info("First round empty, rewritten query: {}", currentQuery);
                continue;
            }
            if (roundDocs.isEmpty()) {
                log.info("Second round also empty, giving up retrieval");
                break;
            }

            // 合并跨轮次文档
            allDocuments = mergeWithPrevious(allDocuments, roundDocs);

            // LLM 评估检索质量
            String context = buildContext(allDocuments);
            RetrievalEvaluation evaluation = evaluateRetrieval(query, context);
            log.info("Retrieval evaluation: {}, reason: {}", evaluation.action(), evaluation.reason());

            if (evaluation.action() == RetrievalEvaluation.RetrievalAction.SUFFICIENT) {
                log.info("Documents sufficient after round {}", round + 1);
                break;
            }
            if (evaluation.action() == RetrievalEvaluation.RetrievalAction.GIVE_UP) {
                log.info("Giving up retrieval after round {}", round + 1);
                break;
            }
            // RE_RETRIEVE：使用 LLM 生成的 refinedQuery 进行下一轮
            if (evaluation.refinedQuery() != null && !evaluation.refinedQuery().isBlank()) {
                currentQuery = evaluation.refinedQuery();
                log.info("Refined query for next round: {}", currentQuery);
            }
        }

        // 4. 生成最终回答
        if (allDocuments.isEmpty()) {
            log.info("No documents found, using fallback");
            return chatClient.prompt()
                    .user(query + "\n\n如果没有相关知识，请直接根据你的理解回答。")
                    .call()
                    .content();
        }

        String context = buildContext(allDocuments);
        log.info("Generating answer with {} documents", allDocuments.size());
        return chatClient.prompt()
                .system(context)
                .user(query)
                .call()
                .content();
    }

    /**
     * LLM 路由决策：判断用户问题是否需要从知识库检索
     */
    private RouteDecision decideRoute(String query) {
        try {
            return chatClient.prompt()
                    .user("""
                            你是一个查询路由器。根据用户问题判断是否需要从知识库检索。
                            知识库主题：恋爱心理、感情问题、婚姻关系、单身社交、沟通技巧。
                            \
                            用户问题：%s
                            \
                            判断规则：
                            - RETRIEVE：问题涉及上述主题，或需要专业参考资料回答
                            - DIRECT_ANSWER：简单问候、闲聊，或与上述主题完全无关（如编程、数学计算等）
                            \
                            请直接返回 JSON，格式：{"action":"RETRIEVE","reason":"原因"}
                            或 {"action":"DIRECT_ANSWER","reason":"原因"}
                            """.formatted(query))
                    .call()
                    .entity(RouteDecision.class);
        } catch (Exception e) {
            log.warn("Route decision failed, defaulting to RETRIEVE", e);
            return new RouteDecision(RouteDecision.RouteAction.RETRIEVE, "路由决策异常，默认检索");
        }
    }

    /**
     * 查询改写：通过 RewriteQueryTransformer 优化检索词
     */
    private String rewriteQuery(String originalQuery) {
        try {
            Query query = new Query(originalQuery);
            Query transformed = queryTransformer.transform(query);
            return transformed.text();
        } catch (Exception e) {
            log.warn("Query rewrite failed, using original query", e);
            return originalQuery;
        }
    }

    /**
     * LLM 检索质量评估：判断文档是否足以回答问题
     */
    private RetrievalEvaluation evaluateRetrieval(String query, String context) {
        try {
            return chatClient.prompt()
                    .user("""
                            你是一个检索质量评估器。判断以下检索结果是否足以回答用户问题。
                            \
                            用户问题：%s
                            \
                            检索到的文档片段：
                            %s
                            \
                            请评估并返回 JSON：
                            - {"action":"SUFFICIENT","reason":"原因"} 信息足够回答
                            - {"action":"RE_RETRIEVE","reason":"原因","refinedQuery":"改进后的检索词"} 需要换个角度检索
                            - {"action":"GIVE_UP","reason":"原因"} 文档与问题无关
                            """.formatted(query, context))
                    .call()
                    .entity(RetrievalEvaluation.class);
        } catch (Exception e) {
            log.warn("Retrieval evaluation failed, defaulting to SUFFICIENT", e);
            return new RetrievalEvaluation(
                    RetrievalEvaluation.RetrievalAction.SUFFICIENT,
                    "评估异常，默认认为信息足够",
                    null);
        }
    }

    /**
     * 构建检索上下文：将文档列表构建为 system prompt
     */
    private String buildContext(List<Document> documents) {
        StringBuilder context = new StringBuilder();
        context.append("以下是相关的参考资料：\n\n");
        for (int i = 0; i < documents.size(); i++) {
            Document doc = documents.get(i);
            String text = doc.getText();
            if (text != null && text.length() > 500) {
                text = text.substring(0, 500) + "...";
            }
            context.append(i + 1).append(". ").append(text).append("\n\n");
        }
        context.append("请基于以上参考资料回答用户问题。如果资料不足以回答，请直接根据你的理解回答。");
        return context.toString();
    }

    /**
     * 跨轮次文档合并：按 ID 去重，保留已有文档
     */
    private List<Document> mergeWithPrevious(List<Document> existing,
                                             List<Document> newDocs) {
        LinkedHashMap<String, Document> merged = new LinkedHashMap<>();
        for (Document doc : existing) {
            merged.putIfAbsent(doc.getId(), doc);
        }
        for (Document doc : newDocs) {
            merged.putIfAbsent(doc.getId(), doc);
        }
        return new ArrayList<>(merged.values());
    }
}
