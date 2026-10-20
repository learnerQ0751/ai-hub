# REQ-1.2：模型配置 + 多部署路由

- 日期：2026-10-20
- 状态：进行中
- 关联规格：`specs/002-model-router/`（待 spec-kit 生成）
- 参考：`05-项目7-第1期-LLM网关核心.md` 需求 1.2；`06-LiteLLM行为基线.md`

## 需求

- 背景：项目 7 第 1 期 LLM 网关核心的第二个需求，在需求 1.1 单模型转发之上增加模型配置与多部署路由。
- 范围：
  1. 配置结构对齐 LiteLLM `model_list`：模型别名 → 部署列表，每个部署含 `api-base` / `api-key` / `model`。
  2. 同名多部署负载均衡：实现 `simple-shuffle`（加权轮询，支持 `rpm` / `tpm` 权重），预留策略接口供后续 least-busy / usage-based / latency-based 扩展。
  3. `GET /models` 返回可用模型别名，不暴露上游真实模型名与部署参数。
  4. 单部署行为保持需求 1.1 不变；多部署连续调用可观察到轮询。
- 不做什么：虚拟 Key、限流预算、日志计量、fallback、SSE（后续需求）。

## 验收标准

- [ ] 配置两个别名（如 `chat` / `reasoner`）+ 一个别名双部署；连续调用能观察到轮询。
- [ ] `GET /models` 只返回别名，不暴露上游真实模型名、`api-base`、`api-key`。
- [ ] 新增/修改别名与部署只改 YAML，不改代码。
- [ ] 单部署路径与需求 1.1 行为一致（响应 `model` 回写别名、`usage` 不丢）。
- [ ] 单元测试覆盖：配置绑定、权重轮询、未知模型、`/models` 输出。
- [ ] `mvn -Pquality verify` 通过。

## 决策

| 决策 | 理由 | 日期 |
|---|---|---|
| 配置结构对齐 LiteLLM `model_list` | 便于与参照物逐项对照，降低理解成本 | 2026-10-20 |
| 首期实现 `simple-shuffle` + 策略接口 | 满足多部署基础能力，同时为后续策略留扩展点 | 2026-10-20 |
| `/models` 只暴露别名 | 不泄露上游供应商与密钥配置 | 2026-10-20 |

## 执行日志

### 2026-10-20

- 创建分支 `feat/req-1.2-model-router`。
- 创建本需求记录，待 spec-kit 产出 spec/plan/tasks。
- 下一步：在 Cursor 的 Claude Code 中执行 `/speckit-specify`，然后 clarify/plan/tasks/implement。

## 复盘

- 做对了什么：
- 踩了什么坑：
- 下次改进：

## 归因（可选）

- 提交：
- AI/人工行数：
- prompt 可追溯：

## 建议的 spec-kit 输入

```text
实现模型配置与多部署路由：
1. 配置结构对齐 LiteLLM model_list：模型别名 → 部署列表（api-base / api-key / model）。
2. 同名多部署 simple-shuffle（加权轮询，支持 rpm/tpm 权重）+ 预留策略接口。
3. GET /models 只返回别名，不暴露上游真实模型名与部署参数。
4. 验收：chat / reasoner 两个别名 + chat 双部署，连续调用能观察到轮询；/models 正确；单部署行为与需求 1.1 一致。
```
