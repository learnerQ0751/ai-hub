package com.qtua.aihub.start.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jackson 2 ObjectMapper 配置。
 *
 * <p>Spring Boot 4 默认自动配置的是 Jackson 3（tools.jackson），而 LangChain4j
 * 生态与现有网关代码使用 Jackson 2（com.fasterxml）。这里显式提供一个 Jackson 2
 * ObjectMapper，供鉴权错误体、请求/响应改写等内部 JSON 处理使用。</p>
 */
@Configuration
public class JacksonConfig {

    /**
     * 提供网关内部使用的 Jackson 2 ObjectMapper。
     *
     * @return Jackson 2 ObjectMapper
     */
    @Bean
    public ObjectMapper gatewayObjectMapper() {
        return new ObjectMapper();
    }
}
