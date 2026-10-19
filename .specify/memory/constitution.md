# ai-hub Constitution

> 本文件由 `standards/generator/generate.py` 从规则库生成；修订规则后重新生成。
> 规则唯一真源：`standards/rules/*.yaml`；人读索引：`standards/index.md`。

## Core Principles

### I. Security First (NON-NEGOTIABLE)

- 所有 API Key、数据库密码、令牌等敏感凭据只能通过环境变量或本地未提交配置文件注入； 禁止硬编码、禁止提交到 Git、禁止打印到日志或异常信息。
- 禁止在日志中打印 API Key、密码、完整令牌、身份证号、银行卡号等敏感字段； 必要时脱敏（如只保留后四位）。
- 所有 SQL 必须使用参数化绑定（#{} / ? / JPA 参数），禁止字符串拼接用户输入。
- 虚拟 Key 明文只在创建时返回一次；数据库只存哈希，禁止存明文； Auth 校验使用哈希比对。
- 网关启动时必须校验 master key 已配置；缺失则拒绝启动并输出明确错误。

### II. Test-First & Verification (NON-NEGOTIABLE)

- 新增或修改 service 层方法时，必须同步新增单测，覆盖成功场景与关键异常场景 （参数校验、重复校验、状态校验等）。
- 每次提交前必须运行 mvn -Pquality verify（或项目等价命令）， 门禁失败不得提交。
- 单测必须覆盖方法的成功场景与关键异常场景（参数校验、重复校验、状态校验）； 测试命名表达意图。

### III. Layered Boundaries

- Controller → Service → Repository/DAL；禁止跨层调用、禁止反向依赖； 包依赖必须无环。
- 参数非空/格式/存在性/唯一性校验与 DTO→BO 转换统一放 service 层； core/repository 层只做领域逻辑。
- @Transactional 只放在事务边界；rollbackFor 必须指定 Throwable； 非事务方法不得标注。

### IV. OpenAI Compatibility & Observability

- /chat/completions 与 /v1/chat/completions 返回 OpenAI ChatCompletion 结构； 上游响应的 id/object/model/choices/usage 等字段必须原样保留，禁止因序列化丢字段。
- 错误响应统一为 {"error":{"message","type","param","code"}}； HTTP 状态码与 LiteLLM 基线一致（400/401/403/429/502/504）。
- GET /models 只返回配置中的模型别名，不暴露上游真实模型名与 api_base/api_key。
- 统一使用 SLF4J 占位符日志；禁止 System.out/System.err、禁止字符串拼接日志。

### V. Simplicity & Maintainability

- 单个方法（含注释和空行）不超过 70 行；超出须拆分为子方法。
- 类名 UpperCamelCase；方法/变量 lowerCamelCase；常量 UPPER_SNAKE_CASE； 包名小写；禁止拼音与无意义缩写。
- 状态值使用枚举；其他常量定义在类级别 private static final；代码中禁止硬编码字符串/数字。

## Additional Constraints

- 技术栈：Java 21 / Spring Boot 3.3.11 / Maven；PostgreSQL（一期可先内存）。
- 配置与密钥：外部化配置；密钥只放环境变量或 `application-local.yml`（gitignored）；无 master key 拒绝启动。
- 质量门禁：`mvn -Pquality verify` 必须通过（Checkstyle / SpotBugs / ArchUnit / JaCoCo）。
- 响应透传：上游 JSON 原样透传，不得因序列化丢字段。
- 计价：显式价格表，按 1M tokens 换算；spend 可从日志复算。

## Development Workflow

- 0→1（新模块/新仓库）：spec-kit（constitution → specify → clarify → plan → tasks → analyze → implement）。
- 1→100（存量迭代）：OpenSpec（explore → propose → apply → archive）+ Superpowers 技能。
- 每个任务：先写失败测试 → 最小实现 → 测试通过 → 硬门禁 → 人工验收。
- 每次提交：git-ai 记录行级归因；提交信息不含模型署名。

## Governance

- 本宪法优先于其他开发实践；规则冲突按「安全红线 > 项目级 > 通用级；强制 > 推荐 > 参考；新版 > 旧版」裁决。
- 修订流程：修改 `standards/rules/*.yaml` → 运行校验与生成器 → 更新本文件 → 在任务决策记录中留痕。
- 复杂度必须被证明必要；不能自动检查的规则进入人工审查清单。

**Version**: 1.0.0 | **Ratified**: 2026-10-19 | **Last Amended**: 2026-10-19
