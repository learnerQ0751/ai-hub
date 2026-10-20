package com.qtua.aihub.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.ObjectUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 网关模型配置。
 *
 * <p>结构对齐 LiteLLM 的 model_list：模型别名 → 部署列表。
 * 同一别名下可以有多个部署，由 core 层按权重选择。</p>
 */
@ConfigurationProperties(prefix = "gateway")
public class GatewayProperties {

    private static final Logger LOG = LoggerFactory.getLogger(GatewayProperties.class);
    private static final int MIN_VALID_WEIGHT = 1;

    /** 本地 master key 占位；为空时本地开发不校验鉴权。 */
    private String masterKey;
    /** 模型别名 → 部署列表；保持 YAML 顺序。 */
    private Map<String, List<Deployment>> modelList = new LinkedHashMap<>();

    public String getMasterKey() {
        return masterKey;
    }

    public void setMasterKey(String masterKey) {
        this.masterKey = masterKey;
    }

    public Map<String, List<Deployment>> getModelList() {
        return modelList;
    }

    public void setModelList(Map<String, List<Deployment>> modelList) {
        this.modelList = modelList;
    }

    /**
     * 启动时校验权重配置，尽早暴露配置错误。
     *
     * <p>规则：同一别名下不能混用 rpm/tpm；同一种权重必须对所有部署都配置；
     * 非正数权重回退为 1，并记录警告。</p>
     */
    @PostConstruct
    void validateDeployments() {
        modelList.forEach(this::validateAliasWeights);
    }

    /**
     * 校验单个别名下的权重组合。
     *
     * @param alias       模型别名
     * @param deployments 部署列表
     */
    private void validateAliasWeights(String alias, List<Deployment> deployments) {
        if (ObjectUtils.isEmpty(deployments)) {
            return;
        }
        long rpmCount = deployments.stream().filter(deployment -> hasValue(deployment.getRpm())).count();
        long tpmCount = deployments.stream().filter(deployment -> hasValue(deployment.getTpm())).count();
        if (rpmCount > 0 && tpmCount > 0) {
            throw new IllegalStateException("Model alias '" + alias
                    + "' must not mix 'rpm' and 'tpm' weights");
        }
        validateWeightSource(alias, "rpm", rpmCount, deployments.size());
        validateWeightSource(alias, "tpm", tpmCount, deployments.size());
        deployments.forEach(deployment -> warnInvalidWeights(alias, deployment));
    }

    /**
     * 同一别名下同一种权重必须全量配置，避免部分部署使用默认权重造成比例失真。
     *
     * @param alias 模型别名
     * @param field rpm 或 tpm
     * @param count 已配置该权重的部署数
     * @param total 部署总数
     */
    private void validateWeightSource(String alias, String field, long count, int total) {
        if (count > 0 && count < total) {
            throw new IllegalStateException("Model alias '" + alias
                    + "' must configure '" + field + "' for every deployment");
        }
    }

    /**
     * 记录非法权重（≤0）的警告；实际选择时回退为 1。
     *
     * @param alias      模型别名
     * @param deployment 部署
     */
    private void warnInvalidWeights(String alias, Deployment deployment) {
        warnInvalidWeight(alias, "rpm", deployment.getRpm());
        warnInvalidWeight(alias, "tpm", deployment.getTpm());
    }

    private void warnInvalidWeight(String alias, String field, Integer value) {
        if (hasValue(value) && value < MIN_VALID_WEIGHT) {
            LOG.warn("Model alias '{}' has invalid {} value {}; using default weight 1",
                    alias, field, value);
        }
    }

    private boolean hasValue(Integer value) {
        return !ObjectUtils.isEmpty(value);
    }

    /**
     * 单个上游部署。
     */
    public static class Deployment {

        /** 上游 OpenAI 兼容地址，不含 /chat/completions。 */
        private String apiBase;
        /** 上游 API Key；不得写入日志或返回给客户端。 */
        private String apiKey;
        /** 上游真实模型名（不是用户别名）。 */
        private String model;
        /** 权重来源之一；与 tpm 互斥。 */
        private Integer rpm;
        /** 权重来源之一；与 rpm 互斥。 */
        private Integer tpm;

        public String getApiBase() {
            return apiBase;
        }

        public void setApiBase(String apiBase) {
            this.apiBase = apiBase;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public Integer getRpm() {
            return rpm;
        }

        public void setRpm(Integer rpm) {
            this.rpm = rpm;
        }

        public Integer getTpm() {
            return tpm;
        }

        public void setTpm(Integer tpm) {
            this.tpm = tpm;
        }
    }
}
