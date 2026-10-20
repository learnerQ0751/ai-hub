# ai-hub Constitution

> 权威规范在仓库内：
> - `ai-workflow/STANDARDS.md`（通用规范）
> - `ai-workflow/PROJECT.md`（项目专属规则）
> 本文件只保留 spec-kit 需要的最小原则摘要；规范变更以 `ai-workflow/` 为准。

## Core Principles

### I. Security First (NON-NEGOTIABLE)

- 密钥只放环境变量或 gitignored 本地配置；禁止入库/打印。
- SQL 参数化；错误响应不泄露上游密钥/内部堆栈。

### II. Test-First & Verification (NON-NEGOTIABLE)

- 新增/修改 service 方法必须有单测，覆盖成功 + 关键异常。
- 先写失败测试，再写最小实现；提交前跑 `mvn -Pquality verify`。

### III. Layered Boundaries

- Controller → Service → Repository/DAL；禁止跨层/反向依赖/包循环。
- 参数校验与 DTO 转换放 service 层；事务只在边界。

### IV. OpenAI Compatibility & Observability

- 对外 OpenAI 兼容；错误体 `{"error":{"message","type","param","code"}}`。
- 上游 JSON 原样透传，字段不丢；请求 ID 写入响应头与日志。

### V. Simplicity & Maintainability

- 方法 ≤70 行；禁止魔法值；类/方法/常量命名规范。
- 不使用不必要的抽象；小改动直接做，大改动走规格。

## Additional Constraints

- 技术栈：Java 21 / Spring Boot 3.3.11 / Maven；默认端口 18080。
- 质量门禁：`mvn -Pquality verify`（Checkstyle/SpotBugs/ArchUnit/JaCoCo）。
- 密钥：`application-local.yml`（gitignored）或环境变量。
- 需求记录：`ai-workflow/records/REQ-*.md`。

## Development Workflow

- 需求记录 → 规格（可选 spec-kit/OpenSpec）→ TDD 实现 → 门禁/验收 → 更新记录并提交。
- 工具可选：没有 spec-kit/OpenSpec/git-ai 时按 `ai-workflow/WORKFLOW.md` 手动执行。

## Governance

- 规范冲突：安全 > 项目 > 通用；MUST > SHOULD > MAY。
- 修订规范直接改 `ai-workflow/STANDARDS.md` / `PROJECT.md`，无需重新生成。

**Version**: 2.0.0 | **Ratified**: 2026-10-19 | **Last Amended**: 2026-10-20
