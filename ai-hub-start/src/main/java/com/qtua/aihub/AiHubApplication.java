package com.qtua.aihub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * ai-hub 启动入口。
 *
 * <p>位于根包，默认扫描 {@code com.qtua.aihub} 下全部模块（start/api/service/core/
 * manager/integration/common）。</p>
 */
@SpringBootApplication
@ConfigurationPropertiesScan("com.qtua.aihub")
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
