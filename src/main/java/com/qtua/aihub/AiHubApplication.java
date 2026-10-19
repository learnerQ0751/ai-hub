package com.qtua.aihub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AiHubApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiHubApplication.class, args);
    }
}
