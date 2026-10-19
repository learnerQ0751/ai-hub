# Research: Chat Completions Gateway

## 1. HTTP 客户端选型

- **选择**：Spring 6.1 `RestClient`（同步、简洁、Spring Boot 3.3 自带）。
- **理由**：需求 1.1 是非流式；RestClient 比 WebClient 简单，后续流式需求 1.7 再引入 WebClient。
- **实现要点**：`RestClient.builder().baseUrl(apiBase).build()`；POST `/chat/completions`；Header `Authorization: Bearer <apiKey>`；body 直接传原始 JSON 字符串或 `JsonNode`。

## 2. JSON 原样透传

- **选择**：Controller 返回 `ResponseEntity<String>`，`Content-Type: application/json`。
- **理由**：避免 DTO 映射丢字段（LiteLLM 基线包含 `usage.prompt_tokens_details` 等细分字段）。
- **校验**：用 Jackson `ObjectMapper.readTree` 解析请求以读取 `model` / `stream` / `messages`，但转发时使用原始字符串，避免重新序列化改变字段顺序/数值格式。

## 3. 错误体对齐

LiteLLM/OpenAI 错误体：

```json
{"error":{"message":"...","type":"invalid_request_error","param":"model","code":"400"}}
```

- 未知模型 → 400，`type=invalid_request_error`，`param=model`。
- 上游 401 → 401/502，`type=auth_error`（不泄露上游 key）。
- 超时 → 504，`type=timeout_error`。
- 非法 JSON → 400，`type=invalid_request_error`。

## 4. 配置结构

```yaml
gateway:
  master-key: ${GATEWAY_MASTER_KEY:}
  model-list:
    chat-default:
      - api-base: https://api.deepseek.com
        api-key: ${DEEPSEEK_API_KEY:}
        model: deepseek-chat
```

- 使用 `@ConfigurationProperties(prefix="gateway")` 绑定 `Map<String, List<Deployment>>`。
- 别名 = map key；部署 = list 元素。

## 5. 测试策略

- **Controller**：`@WebMvcTest` + `@MockBean`，验证路径、状态码、JSON 结构。
- **UpstreamClient**：MockWebServer 模拟上游，验证请求路径、Authorization、响应透传。
- **ArchUnit**：包循环检查（已有）。
- **集成**：启动应用 + curl（或 `@SpringBootTest` + MockWebServer）。

## 6. 代理

DeepSeek 直连超时；本地运行需：

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local \
  -Dspring-boot.run.jvmArguments="-Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=7897 -Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=7897"
```

## 7. 与 LiteLLM 的差异（本期）

| 项 | LiteLLM | 本期 Java 版 |
|---|---|---|
| 多部署负载均衡 | 支持 | 不支持（1.2） |
| 虚拟 Key | 支持 | 仅 master key 占位（1.3） |
| 限流/预算 | 支持 | 不支持（1.4） |
| fallback | 支持 | 不支持（1.6） |
| 流式 | 支持 | 明确拒绝（1.7） |
