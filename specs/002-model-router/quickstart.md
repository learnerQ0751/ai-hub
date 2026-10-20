# Quickstart: Model Configuration & Multi-Deployment Routing

## 前置条件

- Java 21、Maven Wrapper（`mvnw`）或本机 Maven
- 上游 DeepSeek 可用；本机代理 `127.0.0.1:7897`
- `src/main/resources/application-local.yml`（gitignored）中配置真实密钥

## 1. 配置双部署

在 `application-local.yml` 中：

```yaml
gateway:
  master-key: ${GATEWAY_MASTER_KEY:sk-local-master-key}
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

要点：

- `chat` 两个部署，权重 600:200（3:1）。
- `reasoner` 单部署。
- 同一别名下全部使用 `rpm`（或全部 `tpm`，或全部不配）；混用会启动失败。

## 2. 启动

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local \
  -Dspring-boot.run.jvmArguments="-Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=7897 -Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=7897"
```

## 3. 验证 `/models`

```bash
curl -s http://localhost:18080/models \
  -H "Authorization: Bearer sk-local-master-key"
```

期望：

```json
{"object":"list","data":[
  {"id":"chat","object":"model","created":0,"owned_by":"ai-hub"},
  {"id":"reasoner","object":"model","created":0,"owned_by":"ai-hub"}
]}
```

断言：响应中不出现 `deepseek-chat`、`deepseek-reasoner`、`api-base`、`api-key`、`rpm`、`tpm`。

`/v1/models` 返回相同结果。

## 4. 验证轮询

连续调用 8 次：

```bash
for i in $(seq 1 8); do
  curl -s http://localhost:18080/v1/chat/completions \
    -H "Authorization: Bearer sk-local-master-key" \
    -H "Content-Type: application/json" \
    -d '{"model":"chat","messages":[{"role":"user","content":"hi"}]}' > /dev/null
done
```

查看网关日志中形如：

```text
routed alias=chat upstreamModel=deepseek-chat apiBase=https://api.deepseek.com
```

期望两个部署交替出现，3:1 权重下约每 4 次请求中 3 次命中第一个部署。

## 5. 单部署回归

调用 `reasoner` 多次，日志每次都命中同一部署；响应 `model` 为 `reasoner`，`usage` 字段完整。

## 6. 配置校验失败演示

临时把 `chat` 第一个部署改为 `rpm: 600`，第二个改为 `tpm: 20000`，启动应用：

- 期望启动失败，错误信息明确指出别名 `chat` 权重字段混用。
- 恢复配置后重新启动。

## 7. 质量门禁

```bash
mvn -Pquality verify
```

要求：Checkstyle、SpotBugs、ArchUnit、JaCoCo（行覆盖 ≥70%）全部通过。

## 8. 文档对照

- 规格：`specs/002-model-router/spec.md`
- 设计：`specs/002-model-router/data-model.md`
- 契约：`specs/002-model-router/contracts/openapi.yaml`
