package org.mate.mate10.config;

import org.mate.mate10.service.Impl.LuceneIndexer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LuceneConfig {
    //应用关闭时释放 writer目录锁
    @Bean(destroyMethod = "close")
    public LuceneIndexer luceneIndexer(RagProperties ragProperties) throws Exception {
        return new LuceneIndexer(ragProperties.getLuceneDir());
    }
}