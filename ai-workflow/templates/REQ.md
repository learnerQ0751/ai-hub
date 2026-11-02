# REQ-XXX：<需求名>

- 日期：YYYY-MM-DD
- 状态：进行中 / 已完成 / 暂停
- 关联规格：`specs/NNN-<feature>/`（如有）
- 依赖需求：REQ-x.y（无则写“无”）
- 需求来源：外部需求卡章节（或“口述需求”）
- 提交：<sha>

## 1. 是什么

（人话 3-5 行：这个需求解决什么问题、给谁用）

## 2. 有什么用

- 业务价值：
- 对应参照行为（LiteLLM B-xx）：
- 面试叙事点：

## 3. 怎么实现的

- 分层落点：
- 核心类（≤3）：
- 请求链路（≤8 行）：

```text

```

## 4. 影响面

> 实现前填写；分级门禁依据：`additive` 自动通过，`extension` / `breaking` 必须用户确认后动手。

```yaml
capability:
  type: business            # business | cross-cutting（横切治理：认证/限流/预算/计价/计量/日志/追踪/幂等）
  entry_point: ""           # Filter / Interceptor / AOP / Service / Core
  interface_agnostic: false # 横切治理必须 true：新增接口自动复用，不允许复制
modules:
  add: []
  change: []
interfaces:
  add: []
  change: []
  remove: []
tables:
  add: []
  change: []
config: []
depends_on: []
compat: additive        # additive | extension | breaking
gate: auto              # auto | confirm
regression: []
```

## 5. 关键决策

| 决策 | 理由 | 日期 |
|---|---|---|
|  |  |  |

## 6. 执行摘要

> 每会话 1-2 行 + commit；详细过程留在对话、plan/tasks 与提交历史，不在此复制。

### YYYY-MM-DD

- 进展：
- 下一步：

## 7. 复盘

- 做对了什么：
- 踩了什么坑：
- 下次改进：

## 8. 多实例检查

- [ ] 是否有可变成员变量？是否线程安全、跨实例一致？
- [ ] 计数器/限流/预算/冷却是否在 Redis/数据库？
- [ ] 锁是否分布式？是否有超时、防死锁、幂等？
- [ ] 定时任务是否只执行一次？
- [ ] 本地缓存是否可容忍过期/不一致？
