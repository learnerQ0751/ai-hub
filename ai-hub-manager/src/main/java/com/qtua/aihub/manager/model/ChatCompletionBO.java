package com.qtua.aihub.manager.model;

import com.qtua.aihub.common.catalog.ModelDeployment;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 聊天补全业务对象。
 *
 * <p>service 层解析并校验请求后构造，core 层只消费 BO，不直接处理原始 HTTP 报文。
 * 虚拟 Key 的白名单与 master 标识随 BO 下传，由 core 做授权判断；
 * requestId / keyHash 用于调用日志与消费累计。</p>
 */
public class ChatCompletionBO {

    private final String alias;
    private final List<ModelDeployment> deployments;
    private final String requestBody;
    private final List<String> allowedModels;
    private final boolean masterKey;
    private final String requestId;
    private final String keyHash;

    /**
     * 构造业务对象。
     *
     * @param alias         模型别名
     * @param deployments   可用部署；可能为空
     * @param requestBody   原始请求 JSON
     * @param allowedModels 虚拟 Key 的模型白名单；空表示全部
     * @param masterKey     是否 master key 调用
     * @param requestId     请求追踪 ID；空时生成兜底 ID
     * @param keyHash       虚拟 Key 的 SHA-256；master 调用为 null
     */
    public ChatCompletionBO(String alias, List<ModelDeployment> deployments, String requestBody,
                            List<String> allowedModels, boolean masterKey,
                            String requestId, String keyHash) {
        this.alias = alias;
        this.deployments = Objects.isNull(deployments) ? List.of() : List.copyOf(deployments);
        this.requestBody = requestBody;
        this.allowedModels = Objects.isNull(allowedModels) ? List.of() : List.copyOf(allowedModels);
        this.masterKey = masterKey;
        this.requestId = Objects.isNull(requestId) || requestId.isBlank()
                ? UUID.randomUUID().toString() : requestId;
        this.keyHash = keyHash;
    }

    public String getAlias() {
        return alias;
    }

    public List<ModelDeployment> getDeployments() {
        return List.copyOf(deployments);
    }

    public String getRequestBody() {
        return requestBody;
    }

    public List<String> getAllowedModels() {
        return List.copyOf(allowedModels);
    }

    public boolean isMasterKey() {
        return masterKey;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getKeyHash() {
        return keyHash;
    }
}
