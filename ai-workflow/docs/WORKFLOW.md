# 开发流程（轻量版）

> 原则：能扫描的不手写，不能扫描的结构化；快照只记增量，活文档记现状；验收标准实现前冻结；影响面分级门禁。
> 文档常数级：全局活文档 `ai-workflow/DESIGN.md` + `ai-workflow/STATUS.md`，每需求 `ai-workflow/records/REQ-x.y.md` + `ai-workflow/acceptance/REQ-x.y.md`。

## 0. 工具裁剪（禁止全套叠加）

| 规模 | 使用 |
|---|---|
| 小改动（<半天） | 直接改 + 测试；需要留痕时补一行 REQ 摘要 |
| 单需求（1-3 天） | REQ（变更控制单）+ acceptance + TDD |
| 新能力/跨模块（3 天+） | + spec-kit（0→1）或 OpenSpec（存量变更）+ Superpowers 执行纪律 |
| 架构级 | + 更新 DESIGN 与活图 |

- 规格真源：`specs/`（spec-kit）或 `openspec/`；REQ 只引用，不复制实现要求。
- 计划真源：plan/tasks；REQ 只留一行执行摘要 + commit，不写详细过程。
- Superpowers 是 AI 内部纪律，不产生项目文档。

## 1. 需求输入

- 若 `ai-workflow/requirements/INDEX.md` 存在且用户提到 `REQ-x.y` → 按索引读取外部需求卡（Vault 期卡）。
- 口述需求 → 按 `ai-workflow/templates/REQ.md` 起草需求与验收，用户确认。
- 两种来源都汇合到 `ai-workflow/records/REQ-x.y.md` + `ai-workflow/acceptance/REQ-x.y.md`。

## 2. 影响面与门禁（实现前）

1. 读 `ai-workflow/STATUS.md`（当前接口/表/配置/变更矩阵）+ `ai-workflow/DESIGN.md` + 依赖需求的 REQ。
2. 写 REQ §2 边界（做/不做/兼容级别）与 §4 对外契约变更。模块、接口、表清单不手写——`scripts/status.ps1` 会从代码和 Flyway 扫描出来，手抄一份就是制造第二份会过期的真源。
3. **职责能力归属检查（§2.6）**：判断能力是横切治理（认证/限流/预算/计价/计量/日志/追踪/幂等）还是业务能力；横切治理必须放统一入口/切面、接口无关，不得进 core 或具体业务 Service；在 REQ §2 声明归属。
4. **资源与故障模式检查（§2.7）**：涉及连接/线程/子进程/缓存时，在 REQ §8 声明资源、四条故障路径与指标归属（业务计数→Redis/DB；本地资源→每实例指标）。
5. 分级门禁：
   - `additive`（纯新增）→ 自动通过，继续；
   - `extension` / `breaking`（修改已有接口/表/行为）→ **停下，向用户说明影响面并获得确认**。
6. 同时冻结 `ai-workflow/acceptance/REQ-x.y.md` §1 验收标准（实现前写定，之后不改）。

## 3. 实现

- 规格按需（spec-kit/OpenSpec/手写）。
- TDD：失败测试 → 最小实现 → 通过 → 重构。
- 遵守 `ai-workflow/docs/STANDARDS.md` + `ai-workflow/docs/PROJECT.md`；技术栈缺失 → 停下问用户。
- 外部依赖不确定时先查文档/源码，不猜 API。

## 4. 收尾

0. 流程/链路说明使用 Mermaid（简单流程图/时序图）或 draw.io（复杂跨系统图），禁止 ASCII 箭头文本——见 `docs/STANDARDS.md §3.7`。
1. 更新 REQ：§3 实现方式（含 Mermaid 链路图）、§4 契约变更、§5 关键判断、§6 执行摘要（每会话 1-2 行 + commit）、§7 复盘（3 行）、§8 资源与多实例检查。
2. 补 acceptance §2-4（三分钟看懂 / 面试讲法 3 句 / 已知限制），全文不超过 30 行。不再写自测题、参考答案、亲手改口子——实测没人回看，只是把文档写长。
3. 若架构、运行时链路或跨需求决策变化 → 更新 `ai-workflow/DESIGN.md` 与 `ai-workflow/acceptance/assets/design.drawio|svg`。
4. 若新增或修改表 → 新增/修改 `db/migration/Vn__*.sql`（Flyway）并更新 `ai-workflow/docs/DATA-MODEL.md`（表/用途/关键关系/状态/引入需求）。
5. 运行 `ai-workflow/scripts/status.ps1` 更新 `ai-workflow/STATUS.md`（需求/接口/数据表速查/配置/变更矩阵/下一步）。
5. 代码 + REQ + acceptance（+DESIGN/STATUS）一起提交。

## 5. 验收（人向，AI 驱动）

1. 打开 `ai-workflow/acceptance/REQ-x.y.md`，读 §1 验收标准 + §2 三分钟看懂。
2. AI 用 2 分钟讲清"这个需求实现了什么、怎么实现的"。
3. **理解验收默认通过**；回填 §5 验收记录，状态改"已验收"。
4. 卡在"待验收"超过一个期的，视为流程缺陷：要么补验收，要么把该需求并入下一个期的总验收，不允许长期悬挂。

## 6. 完成定义（必须全部满足）

- [ ] REQ §1-4 完整（意图 / 边界 / 实现 / 契约变更）
- [ ] acceptance §1 验收标准实现前冻结，全部勾选；§2-4 完整且全文 ≤30 行
- [ ] `mvn -Pquality verify` 通过；真实验收场景已验证
- [ ] 模块结构与依赖方向符合 `ai-workflow/docs/PROJECT.md`（ArchUnit）
- [ ] 框架与 `ai-workflow/docs/PROJECT.md` 技术栈一致（无未授权替换/降级；持久层 MyBatis）
- [ ] 多实例检查完成（无单机业务状态/本地锁）
- [ ] 职责能力归属检查通过：横切治理在统一入口/切面且接口无关；core 未依赖治理组件；新增接口可自动复用（REQ §2 已声明归属）
- [ ] 资源生命周期检查通过：连接池显式配置、超时/异常释放、崩溃兜底、指标归属（STANDARDS §2.7，REQ §8 已声明）
- [ ] 编码硬规则检查通过：线程池用 `ThreadPoolExecutor` 显式参数（禁 `Executors` 工厂，rule-java-threadpool-001）、禁星号/未用导入、禁 System.out/printStackTrace、注释齐全（STANDARDS §3，Checkstyle 已加规则拦截）
- [ ] 类/方法/关键行注释齐全
- [ ] 流程/链路说明为 Mermaid 或 draw.io（无 ASCII 箭头文本块）
- [ ] 对外错误/提示文案语言符合 STANDARDS §3.6 与 PROJECT.md（中文 message；type/param/code 保持英文）
- [ ] `ai-workflow/STATUS.md` 已更新且变更矩阵正确
- [ ] 新增/修改表已登记 `ai-workflow/docs/DATA-MODEL.md`；STATUS 表速查与 DDL 一致
- [ ] 架构/链路/决策有变时 `ai-workflow/DESIGN.md` 与活图已更新
- [ ] 用户已了解"实现了什么、怎么实现的"（理解验收默认通过）；验收文档状态已更新

## 7. 一键提示词

```text
按 ai-workflow/docs/WORKFLOW.md 实现 REQ-x.y：
1. 读 requirements/INDEX.md → 外部需求卡
2. 对照 ai-workflow/STATUS.md 与 ai-workflow/DESIGN.md 写 REQ §2 边界；extension/breaking 先向我确认
3. 冻结 acceptance §1 验收标准
4. TDD 实现并跑质量门禁
5. 收尾：REQ §4-8 + acceptance §2-4 + 重建 STATUS（scripts/status.ps1）
6. 给我一页变更汇报，并用 2 分钟讲"实现了什么、怎么实现的"
```

## 8. Agent 适配

| Agent / IDE | 入口文件 | 说明 |
|---|---|---|
| JetBrains IDEA + Claude Code 插件 | `CLAUDE.md` | 插件从项目根目录读取 |
| Claude Code CLI | `CLAUDE.md` | 由 `install.ps1 -Agent claude` 生成/追加 |
| Cursor | `.cursor/rules/ai-workflow.mdc` | 由 `install.ps1 -Agent cursor` 生成 |
| 其他 | `AGENTS.md` | 由 `install.ps1 -Agent generic` 生成 |

## 9. 可选工具

| 工具 | 用途 | 没有时 |
|---|---|---|
| spec-kit | 0→1 规格驱动 | 手写 `spec.md/plan.md/tasks.md` |
| OpenSpec | 存量变更 | 在 REQ 记录写变更点 |
| git-ai | 行级 AI 归因 | 用 commit/PR 描述记录 |

## 10. 提交清单

- [ ] `mvn -Pquality verify` 通过
- [ ] 验收场景已验证
- [ ] REQ + acceptance 已更新
- [ ] 提交信息为 `<type>: <中文描述>`（type 保留英文；技术专有名词保留原文）
- [ ] `git status` 无密钥/本地配置
- [ ] 提交信息符合规范
