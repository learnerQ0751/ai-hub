# 规范库使用说明

## 结构

```
standards/
├── rules/
│   ├── L0-personal.yaml     # 个人底线（10 条）
│   ├── L1-java.yaml         # 通用 Java/Spring（25 条）
│   └── L2-project7.yaml     # 项目 7 专属（10 条）
├── generator/
│   ├── validate.py          # 校验 schema / id 唯一 / 枚举
│   └── generate.py          # 生成 constitution / CLAUDE.md / Cursor rules / index
└── index.md                 # 生成的人读索引
```

## 规则卡 schema

```yaml
- id: rule-java-null-001
  title: 空值判断统一使用 ObjectUtils/StringUtils
  statement: 禁止 == null / != null 判空；统一用 ObjectUtils.isEmpty() / StringUtils.isBlank()
  level: must              # must | should | may
  category: null-safety
  scope: [java, spring]
  source:
    doc: data-submission-standards.md
    section: "1. 空值判断"
  rationale: 统一判空语义
  examples:
    good: |
      if (ObjectUtils.isEmpty(x)) { ... }
    bad: |
      if (x == null) { ... }
  enforcement:
    type: auto             # auto | manual
    tool: checkstyle       # checkstyle | pmd | archunit | custom | review | jacoco
  applicability:
    project7: true
    data-submission: true
  priority: P1
```

## 新增 / 修改规则

1. 编辑 `standards/rules/*.yaml`。
2. 运行 `python standards/generator/validate.py`。
3. 运行 `python standards/generator/generate.py`。
4. 运行 `mvn -Pquality verify`。
5. 在提交信息中说明规则变更。

## 分层与适用性

- `applicability.project7: false` 的规则（如 Drip 技术栈专属）不会进入本项目的生成物，只作工作参考。
- 规则冲突按：安全红线 > 项目级 > 通用级；强制 > 推荐 > 参考；新版 > 旧版；更具体 > 更宽泛。

## 硬门禁

- Checkstyle：`config/checkstyle.xml`
- SpotBugs：`config/spotbugs-exclude.xml`
- ArchUnit：`src/test/java/com/qtua/aihub/ArchitectureTest.java`
- JaCoCo：`pom.xml` 的 `quality` profile
- PMD 规则集：`config/pmd-ruleset.xml`（MVP 暂未接入 Maven profile，待兼容性验证）

## 来源

- 监管报送规范 17 节（`data-submission-standards.md`）
- 阿里巴巴 Java 开发手册（黄山版，最新）
- 数据库研发规范
- 金融科技开发技术规范（技术红线）
- 项目 7 需求卡与 LiteLLM 行为基线
