# Research: Model Configuration & Multi-Deployment Routing

## 1. 负载均衡算法

- **选择**：NGINX 平滑加权轮询（Smooth Weighted Round-Robin, SWRR）。
- **理由**：确定性、无需随机数，连续请求按权重交错命中；长期比例精确，验收可用固定序列/固定次数断言。
- **算法**：每次选择时，对每个部署执行 `currentWeight += effectiveWeight`，选出 `currentWeight` 最大者，再让其 `currentWeight -= totalWeight`。平票时取列表中靠前者。
- **备选方案**：
  - 随机加权：长期比例正确，但短期可能连续命中同一部署，测试需统计容差。
  - 权重扩展槽（`[A,A,A,B]` 取模）：实现更简单，但会产生连续同部署，不满足「交错命中」。
  - 计数器取模：仅在等权时正确，无法表达权重。

示例（权重 3:1）：

```text
初始      A=0  B=0
请求 1    A=3  B=1  -> A（A 变为 -1）
请求 2    A=2  B=2  -> A（A 变为 -2）  平票取靠前
请求 3    A=1  B=3  -> B（B 变为 -1）
请求 4    A=4  B=0  -> A（A 变为 0）
```

## 2. 轮询状态与并发

- **选择**：`ConcurrentHashMap<String, RoundState>` 按别名缓存；每个 `RoundState` 用 `synchronized` 保护 `currentWeight` 数组。
- **理由**：部署数很小，锁竞争可忽略；避免为每个部署引入原子数组的复杂度。
- **状态键**：`别名 + "#" + 部署数`，避免同一别名在配置变化（仅重启）或测试中复用不同规模列表时数组越界。
- **单部署**：直接返回唯一部署，不维护状态。
- **边界**：`currentWeight` 使用 `long`，避免 `tpm` 大数值长期累加溢出。

## 3. 权重解析与配置校验

- **规则**：`effectiveWeight = rpm`（若存在）→ 否则 `tpm`（若存在）→ 否则 1；值 ≤ 0 按 1 处理并 `WARN`。
- **同源校验**：同一别名下，所有部署必须全部配置 `rpm`、全部配置 `tpm`，或全部未配置；其他组合在启动时抛 `IllegalStateException`，阻止应用启动。
- **校验位置**：`GatewayProperties` 的 `@PostConstruct`。配置类由 `@ConfigurationPropertiesScan` 注册，属性绑定完成后执行，能保证失败早于请求处理。
- **理由**：配置错误不进入运行时，避免请求期出现难以定位的偏斜。
- **备选**：请求时校验——启动成功但错误延迟暴露，不采用；JSR-303 自定义校验器——对跨列表一致性校验表达复杂，收益低。

## 4. `/models` 契约

- **选择**：`GET /models` 与 `GET /v1/models` 返回 OpenAI 兼容结构：

```json
{
  "object": "list",
  "data": [
    {"id": "chat", "object": "model", "created": 0, "owned_by": "ai-hub"}
  ]
}
```

- **理由**：兼容 OpenAI SDK 的模型列表消费方式；`created` 用 0 表示无时间语义，避免每次调用变化。
- **数据来源**：`GatewayProperties.getModelList()` 的 key 集合，保持 YAML 顺序（`LinkedHashMap`）。
- **不泄露**：响应只含别名与固定元数据；不含上游模型名、`api-base`、`api-key`、`rpm`、`tpm`。
- **鉴权**：沿用现有 `AuthFilter`；配置了 `master-key` 时，`/models` 同样需要 Bearer。

## 5. 路由可观测性

- **选择**：每次转发前记录 `INFO` 日志：别名、上游模型名、`api-base`；不记录 `api-key`。
- **理由**：满足“连续调用可观察轮询”；`api-base` 用于区分同一上游模型名的不同部署。
- **注意**：若同一别名下两个部署的 `api-base` 与 `model` 完全相同，日志无法区分；配置本身也无区分意义，视为运维配置问题。

## 6. 测试策略

| 测试 | 手段 | 覆盖 |
|---|---|---|
| `GatewayPropertiesTest` | `ApplicationContextRunner` | `rpm`/`tpm` 绑定；混用/部分配置导致启动失败 |
| `SmoothWeightedSelectorTest` | 直接调用 | 1:1、3:1、单部署、缺失权重、无效权重、交错序列 |
| `ChatCompletionServiceTest` | Mockito | 按 selector 选择结果转发；别名回写不变 |
| `ModelServiceTest` | 直接调用 | 只含别名、顺序稳定 |
| `ModelControllerTest` | `@WebMvcTest` | `/models`、`/v1/models` 状态码与 JSON |
| `ArchitectureTest` | ArchUnit | 包循环不变 |

## 7. 与 LiteLLM 的差异（本期）

| 项 | LiteLLM | 本期 Java 版 |
|---|---|---|
| 多部署路由 | 支持，默认 simple-shuffle | 支持，确定性平滑加权轮询 |
| 权重来源 | `rpm` / `tpm` / `weight` | `rpm` 优先，`tpm` 回退，默认 1 |
| 权重混用 | 允许 | 禁止，启动失败 |
| 模型目录 | `GET /models` | `GET /models` + `/v1/models`，仅别名 |
| fallback | 支持 | 本期不做（1.6） |
| 限流/预算 | 支持 | 本期不做（1.4） |
| 流式 | 支持 | 本期仍拒绝（1.7） |
