package com.qtua.aihub.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ConfigurationProperties(prefix = "gateway")
public class GatewayProperties {

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

    public static class Deployment {

        private String apiBase;
        private String apiKey;
        private String model;

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
    }
}
