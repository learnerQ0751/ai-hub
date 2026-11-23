# REQ-1.2 验收：模型配置 + 多部署路由

- 状态：已验收（2026-10-30，理解默认通过）
- 验收人：
- 需求记录：`../records/REQ-1.2.md`
- 需求来源：Vault `05-项目7-第1期-LLM网关核心.md` §REQ-1.2

## 1. 需求验收标准

- [x] 配置两个别名（`chat` / `reasoner`）+ 一个别名双部署；60 次调用两个部署都被选中，3:1 权重命中明显更多（`GatewayRoutingIntegrationTest`）
- [x] `GET /models` 只返回别名，不暴露上游真实模型名、`api-base`、`api-key`
- [x] 新增/修改别名与部署只改 YAML，不改代码
- [x] 单部署路径与 REQ-1.1 行为一致（响应 `model` 回写别名、`usage` 不丢）
- [x] 单元测试覆盖：配置绑定、权重轮询、未知模型、`/models` 输出
- [x] `mvn -Pquality verify` 通过（Checkstyle 0 / SpotBugs / ArchUnit / JaCoCo ≥70%）

**验收命令 / 示例**：

```bash
curl http://localhost:18080/v1/models -H "Authorization: Bearer sk-local-master-key"
```

## 2. 从哪看起（固定 10 分钟）

1. 本文 §1（2 分钟）
2. `records/REQ-1.2.md` §1-3 人向说明（3 分钟）
3. 本文 §3 设计图（本次增量）+ 核心文件 `WeightedRandomSelector` / `ChatCompletionCore`（5 分钟）

## 3. 设计图（drawio 导出 SVG）

![ai-hub 架构与运行链路](assets/design.svg)

> 本次增量：聊天链路中的路由段（Core 选部署）+ 模型目录链路（ModelController → ModelService）：加权随机与别名隔离。 全局设计说明见 `../DESIGN.md`。

## 4. 实现要点（按需阅读）

### 4.1 三条关键实现要点

1. **无状态加权随机**：每次请求按 rpm/tpm 权重现算，不保存轮询历史——多实例天然安全。
2. **别名隔离**：对外只暴露别名；`/models` 不含上游模型名、地址、密钥、权重。
3. **配置驱动 + 启动校验**：新增别名只改 YAML；同一别名权重字段混用会在启动时失败。

### 4.2 面试 1 分钟讲法（骨架）

```text
模型配置按 LiteLLM model_list 设计：别名映射到多部署，按 rpm/tpm 权重做无状态加权随机，
多实例部署不需要共享状态；对外只暴露别名，不泄露供应商细节；权重字段强制同源，
配置错误启动即失败。
```

## 5. 回归影响

- REQ-1.1：单部署聊天链路行为保持一致（响应 `model` 回写、`usage` 透传）

## 6. 验收记录

- 验收日期：2026-10-30
- 结论：工程验收通过；理解验收默认通过（如需深挖看 §4）
- 遗留问题：无
