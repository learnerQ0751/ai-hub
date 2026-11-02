package com.qtua.aihub.start.config;

import com.qtua.aihub.common.config.GatewayProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 启动安全校验：生产环境必须配置 master key。
 *
 * <p>本地开发允许空 master key（鉴权放行）并打 WARN；
 * prod / production profile 下为空则直接阻止启动，避免生产网关无鉴权运行。</p>
 */
@Component
public class MasterKeyStartupValidator {

    private static final Logger LOG = LoggerFactory.getLogger(MasterKeyStartupValidator.class);
    private static final Profiles PRODUCTION_PROFILES = Profiles.of("prod", "production");

    private final GatewayProperties properties;
    private final Environment environment;

    /**
     * 构造校验器。
     *
     * @param properties  网关配置
     * @param environment Spring 环境
     */
    public MasterKeyStartupValidator(GatewayProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    /**
     * 校验 master key 配置。
     *
     * @throws IllegalStateException 生产环境未配置 master key
     */
    @PostConstruct
    void validate() {
        if (StringUtils.hasText(properties.getMasterKey())) {
            return;
        }
        if (environment.acceptsProfiles(PRODUCTION_PROFILES)) {
            throw new IllegalStateException(
                    "gateway.master-key must be configured when running with prod/production profile");
        }
        LOG.warn("gateway.master-key is empty; authentication is disabled. "
                + "Use only for local development.");
    }
}
