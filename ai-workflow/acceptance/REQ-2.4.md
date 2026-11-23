# REQ-2.4 验收：Prompt 模板管理

- 状态：已验收（2026-11-03）
- 验收人：主人
- 需求记录：`../records/REQ-2.4.md`
- 需求来源：外部需求卡 REQ-2.4

## 1. 需求验收标准

> 实现前冻结，唯一勾选点；实现完成后只补 §2-4，不改本节标准。

- [x] 创建模板 v1 → 发布 → 查询返回 v1（默认最新发布版）
- [x] 创建 v2 → 发布 → 查询返回 v2；发布 v1（回滚）后查询返回 v1
- [x] 变量声明与模板 `{{var}}` 不一致 → 400（缺失/多余都拒绝）
- [x] 版本历史可查（v1、v2 均在）
- [x] 内容含疑似密钥或超长 → 400
- [x] Prompt 管理接口仅 master key；数据面 render/chat 为任意 Key（2.5）
- [x] `mvn -Pquality verify` 全绿

> 涉及对外错误/提示时，必须增加：`error.message` 为中文、`type`/`param`/`code`/HTTP 状态码保持不变（STANDARDS §3.6）。

**验收命令 / 示例**：

```bash
curl -X POST http://localhost:18080/v1/prompts \
  -H "Authorization: Bearer sk-local-master-key" -H "Content-Type: application/json" \
  -d '{"name":"greet","content":"User: Hello {{name}}","variables":["name"]}'
curl -X POST http://localhost:18080/v1/prompts/greet/publish \
  -H "Authorization: Bearer sk-local-master-key" -H "Content-Type: application/json" \
  -d '{"version":1}'
curl http://localhost:18080/v1/prompts/greet -H "Authorization: Bearer sk-local-master-key"
```

## 2. 从哪看起（固定 10 分钟）

1. 本文 §1（2 分钟）
2. `records/REQ-2.4.md` §1-3 人向说明（3 分钟）
3. `DESIGN.md` 架构图本次增量标注 + 核心文件 ≤3（5 分钟）

## 3. 实现要点（按需阅读）

### 3.1 三条关键实现要点

1. **状态机 + 发布事务**：版本只追加；发布在事务内 `SELECT ... FOR UPDATE` 锁同 name 行，先把旧 PUBLISHED 全归档，再把目标版本置 PUBLISHED——同一 name 永远只有一个发布版，发布旧版本即回滚。
2. **静态变量校验**：创建/追加时提取 `{{var}}` 集合与 variables 声明严格比对（缺失/多余都 400），把渲染期错误提前到配置期。
3. **安全**：内容 ≤10000 字符、拒绝 `sk-` 开头长串；渲染只做单次替换（替换值不递归解析），防注入。

### 3.2 面试 1 分钟讲法（骨架）

```text
Prompt 散在代码里无法版本化和回滚，所以做成集中管理：name + version。
版本只追加，状态机 DRAFT/PUBLISHED/ARCHIVED；发布用数据库行锁事务，
保证同一模板只有一个发布版；发布旧版本就是回滚。
配置期就校验变量声明与 {{var}} 占位符一致，运行时就不会出现缺变量；
内容还做长度和密钥泄露检查。
```

## 4. 回归影响

- 需重跑的已有验收项：AuthFilter 管理面规则（新增 /v1/prompts 排除项）；全量门禁覆盖
- 真实链路回归：Prompt CRUD/发布/回滚/列表全部正常

## 5. 验收记录

- 验收日期：2026-11-03
- 结论：理解验收默认通过（如需深挖看 §3）
- 遗留问题：无
