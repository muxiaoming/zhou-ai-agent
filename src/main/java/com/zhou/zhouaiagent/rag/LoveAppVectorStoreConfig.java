package com.zhou.zhouaiagent.rag;

import jakarta.annotation.Resource;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 恋爱大师向量数据库配置（初始化基于内存的向量数据库 Bean）
 * (本地知识库 将加载的文件存储到 vectorStore中(可使用内存向量存储或者pgVector存储))
 */
@Configuration
public class LoveAppVectorStoreConfig {

    @Resource
    private LoveAppDocumentLoader loveAppDocumentLoader;

    @Bean
    public SimpleVectorStore simpleVectorStore(EmbeddingModel dashscopeEmbeddingModel) {
        SimpleVectorStore simpleVectorStore = SimpleVectorStore.builder(dashscopeEmbeddingModel).build();
        List<Document> documentList = loveAppDocumentLoader.loadMarkdowns();
        simpleVectorStore.add(documentList);
        return simpleVectorStore;
    }

    @Bean
    public QuestionAnswerAdvisor questionAnswerAdvisor(EmbeddingModel dashscopeEmbeddingModel) {
        return new QuestionAnswerAdvisor(simpleVectorStore(dashscopeEmbeddingModel));
    }
}
