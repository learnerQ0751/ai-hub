package com.qtua.aihub.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.ObjectUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ConfigurationProperties(prefix = "gateway")
public class GatewayProperties {

    private static final Logger LOG = LoggerFactory.getLogger(GatewayProperties.class);
    private static final int MIN_VALID_WEIGHT = 1;

    private String masterKey;
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

    @PostConstruct
    void validateDeployments() {
        modelList.forEach(this::validateAliasWeights);
    }

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

    private void validateWeightSource(String alias, String field, long count, int total) {
        if (count > 0 && count < total) {
            throw new IllegalStateException("Model alias '" + alias
                    + "' must configure '" + field + "' for every deployment");
        }
    }

    private void warnInvalidWeights(String alias, Deployment deployment) {
        warnInvalidWeight(alias, "rpm", deployment.getRpm());
        warnInvalidWeight(alias, "tpm", deployment.getTpm());
    }

    private void warnInvalidWeight(String alias, String field, Integer value) {
        if (hasValue(value) && value < MIN_VALID_WEIGHT) {
            LOG.warn("Model alias '{}' has invalid {} value {}; using default weight 1", alias, field, value);
        }
    }

    private boolean hasValue(Integer value) {
        return !ObjectUtils.isEmpty(value);
    }

    public static class Deployment {

        private String apiBase;
        private String apiKey;
        private String model;
        private Integer rpm;
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
