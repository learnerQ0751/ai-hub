# Data Model: Chat Completions Gateway

## 1. 配置模型

### GatewayProperties

```java
@ConfigurationProperties(prefix = "gateway")
public class GatewayProperties {
    private String masterKey;
    private Map<String, List<Deployment>> modelList = new LinkedHashMap<>();
}
```

### Deployment

```java
public class Deployment {
    private String apiBase;
    private String apiKey;
    private String model;
}
```

约束：
- `modelList` 的 key = 用户侧别名。
- 本期取 `List` 的第一个元素；列表为空视为配置错误。
- `apiBase` 不含 `/chat/completions`，由客户端拼接。
- `apiKey` 不得出现在日志/响应中。

## 2. 请求模型（只读校验）

网关不把请求映射为完整 DTO，只解析以下字段：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `model` | string | 是 | 模型别名 |
| `messages` | array | 是 | 非空 |
| `stream` | boolean | 否 | 本期必须为 false/缺省 |

原始请求体以 `String` 保留并转发，避免重新序列化丢字段。

## 3. 响应模型

上游响应原样透传（`String`），必须包含：

| 字段 | 说明 |
|---|---|
| `id` | 上游响应 ID |
| `object` | `chat.completion` |
| `model` | 本期为请求别名（由上游返回；1.2 再处理别名改写） |
| `choices` | 至少一个 choice |
| `usage` | `prompt_tokens` / `completion_tokens` / `total_tokens` |

## 4. 错误模型

```json
{
  "error": {
    "message": "Invalid model name passed in model=no-such-model",
    "type": "invalid_request_error",
    "param": "model",
    "code": "400"
  }
}
```

| 场景 | HTTP | type | param |
|---|---|---|---|
| 未知模型 | 400 | invalid_request_error | model |
| 非法 JSON / messages 为空 | 400 | invalid_request_error | messages |
| stream=true（本期） | 400 | invalid_request_error | stream |
| 上游 401 | 401 | auth_error | null |
| 上游超时 | 504 | timeout_error | null |
| 上游其他失败 | 502 | upstream_error | null |

## 5. 追踪

- `request_id`：UUID，写入 MDC、响应头 `X-Request-Id`、日志。
- 本期不落库；需求 1.5 引入调用日志表。
