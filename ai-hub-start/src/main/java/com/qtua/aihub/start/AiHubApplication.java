package com.qtua.aihub.start;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * ai-hub 启动入口。
 *
 * <p>显式指定扫描根包，保证 start 之外的 api/service/core/integration/config/common 层
 * 都能被 Spring 加载。</p>
 */
@SpringBootApplication(scanBasePackages = "com.qtua.aihub")
@ConfigurationPropertiesScan(basePackages = "com.qtua.aihub")
public class AiHubApplication {

    /**
     * 启动 Spring Boot 应用。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(AiHubApplication.class, args);
    }
}
