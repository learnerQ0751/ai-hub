# Data Model: Model Configuration & Multi-Deployment Routing

## 1. 配置模型

### GatewayProperties

```java
@ConfigurationProperties(prefix = "gateway")
public class GatewayProperties {
    private String masterKey;
    private Map<String, List<Deployment>> modelList = new LinkedHashMap<>();
}
```

新增 `@PostConstruct` 校验：别名内权重字段必须完全同源，否则启动失败。

### Deployment

```java
public static class Deployment {
    private String apiBase;
    private String apiKey;
    private String model;
    private Integer rpm;   // 可选；每分钟请求数
    private Integer tpm;   // 可选；每分钟 token 数
}
```

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `api-base` | string | 是 | 上游根地址，不含 `/chat/completions` |
| `api-key` | string | 是 | 上游密钥；禁止出现在日志/响应 |
| `model` | string | 是 | 上游真实模型名 |
| `rpm` | integer | 否 | 权重来源之一 |
| `tpm` | integer | 否 | 权重来源之一 |

约束：

- `modelList` 的 key = 对外别名，大小写敏感。
- 别名下部署列表为空：启动允许，请求时返回 400 `invalid_request_error`。
- 同一别名内：全部配置 `rpm`、全部配置 `tpm`、或全部不配置；其他组合启动失败。
- 权重值 ≤ 0：按 1 处理并记录 `WARN`。

## 2. 权重解析

```text
effectiveWeight(d) =
    d.rpm != null ? (d.rpm > 0 ? d.rpm : 1)
  : d.tpm != null ? (d.tpm > 0 ? d.tpm : 1)
  : 1
```

## 3. 运行时状态（仅进程内）

### RoundState

| 字段 | 类型 | 说明 |
|---|---|---|
| `currentWeight[]` | `long[]` | 每个部署的当前累计权重 |
| 锁 | `synchronized` | 保证单别名内选择串行 |

- 状态键：`alias + "#" + deployments.size()`。
- 单部署不建状态。
- 重启后清零；不跨实例共享。

## 4. 响应模型

### ModelListResponse（`GET /models`、`GET /v1/models`）

```json
{
  "object": "list",
  "data": [
    {"id": "chat", "object": "model", "created": 0, "owned_by": "ai-hub"}
  ]
}
```

| 字段 | 说明 |
|---|---|
| `object` | 固定 `list` |
| `data[].id` | 模型别名 |
| `data[].object` | 固定 `model` |
| `data[].created` | 固定 0 |
| `data[].owned_by` | 固定 `ai-hub` |

不可出现：上游 `model`、`api-base`、`api-key`、`rpm`、`tpm`。

### 聊天响应（不变）

沿用需求 1.1：上游 JSON 原样透传，`model` 回写请求别名，`usage` 保留。

## 5. 错误模型（增量）

| 场景 | HTTP | type | param |
|---|---|---|---|
| 别名下部署列表为空 | 400 | invalid_request_error | model |
| 未知别名 | 400 | invalid_request_error | model |
| 权重字段混用/部分配置 | — | 启动失败 | — |

## 6. 配置示例

```yaml
gateway:
  master-key: ${GATEWAY_MASTER_KEY:}
  model-list:
    chat:
      - api-base: https://api.deepseek.com
        api-key: ${DEEPSEEK_API_KEY:}
        model: deepseek-chat
        rpm: 600
      - api-base: https://api.deepseek.com
        api-key: ${DEEPSEEK_API_KEY_2:}
        model: deepseek-chat
        rpm: 200
    reasoner:
      - api-base: https://api.deepseek.com
        api-key: ${DEEPSEEK_API_KEY:}
        model: deepseek-reasoner
```
