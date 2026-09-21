package com.aneto.registo_horas_service.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CacheResilienceConfig implements CachingConfigurer {

    private static final Logger logger = LoggerFactory.getLogger(CacheResilienceConfig.class);

    @Override
    public CacheErrorHandler errorHandler() {
        return new CustomCacheErrorHandler();
    }

    /**
     * Implementação customizada para garantir que a App não pare
     * se o Redis falhar em produção.
     */
    private static class CustomCacheErrorHandler implements CacheErrorHandler {

        @Override
        public void handleCacheGetError(RuntimeException e, Cache cache, Object key) {
            logger.error("Erro ao buscar no Redis (Cache: {}): {}. Redirecionando para a Base de Dados.",
                    cache.getName(), e.getMessage());
        }

        @Override
        public void handleCachePutError(RuntimeException e, Cache cache, Object key, Object value) {
            logger.error("Erro ao gravar no Redis (Cache: {}): {}.", cache.getName(), e.getMessage());
        }

        @Override
        public void handleCacheEvictError(RuntimeException e, Cache cache, Object key) {
            logger.error("Erro ao limpar cache (Evict) no Redis: {}.", e.getMessage());
        }

        @Override
        public void handleCacheClearError(RuntimeException e, Cache cache) {
            logger.error("Erro ao limpar todo o cache (Clear) no Redis: {}.", e.getMessage());
        }
    }
}