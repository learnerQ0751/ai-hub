# -*- coding: utf-8 -*-
"""规则卡生成器：YAML 真源 → CLAUDE.md / Cursor rules / spec-kit constitution / index。

用法：python standards/generator/generate.py
幂等：重复运行输出一致。
"""
from __future__ import annotations

import sys
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[2]
RULES_DIR = ROOT / "standards" / "rules"
INDEX_PATH = ROOT / "standards" / "index.md"
CLAUDE_PATH = ROOT / "CLAUDE.md"
CONSTITUTION_PATH = ROOT / ".specify" / "memory" / "constitution.md"
CURSOR_RULES_DIR = ROOT / ".cursor" / "rules"

GENERATED_START = "<!-- GENERATED:START -->"
GENERATED_END = "<!-- GENERATED:END -->"

CATEGORY_GROUPS = {
    "java": lambda r: r["category"] not in {"sql", "database", "testing", "api", "routing", "cost"},
    "sql": lambda r: r["category"] in {"sql", "database"},
    "test": lambda r: r["category"] == "testing",
    "api": lambda r: r["category"] in {"api", "routing", "cost"},
}

CURSOR_GLOBS = {
    "java": ["src/main/java/**/*.java"],
    "sql": ["src/main/resources/**/*.xml", "**/*.sql"],
    "test": ["src/test/java/**/*.java"],
    "api": ["src/main/java/com/qtua/aihub/gateway/**/*.java", "specs/**/*.md"],
}


def one_line(text: str) -> str:
    return " ".join(str(text).split())


def load_rules() -> list[dict]:
    rules: list[dict] = []
    for f in sorted(RULES_DIR.glob("*.yaml")):
        data = yaml.safe_load(f.read_text(encoding="utf-8")) or {}
        layer = data.get("layer", f.stem)
        for rule in data.get("rules", []):
            rule["_layer"] = layer
            rules.append(rule)
    rules.sort(key=lambda r: r["id"])
    return rules


def applicable(rule: dict) -> bool:
    return bool(rule.get("applicability", {}).get("project7"))


def generate_index(rules: list[dict]) -> None:
    lines = [
        "# 规范规则库索引（自动生成）",
        "",
        "> 由 `standards/generator/generate.py` 生成；唯一真源为 `standards/rules/*.yaml`，请勿手改本文件。",
        "",
        "## 统计",
        "",
        "| 层 | 数量 |",
        "|---|---|",
    ]
    for layer in ["L0", "L1", "L2"]:
        lines.append(f"| {layer} | {sum(1 for r in rules if r['_layer'] == layer)} |")
    lines.append(f"| **合计** | **{len(rules)}** |")
    lines.append("")

    for layer in ["L0", "L1", "L2"]:
        layer_rules = [r for r in rules if r["_layer"] == layer]
        if not layer_rules:
            continue
        lines += [f"## {layer}", ""]
        for r in layer_rules:
            p7 = "是" if applicable(r) else "否"
            lines += [
                f"### {r['id']} · {r['title']}",
                "",
                f"- **级别**：{r['level']}",
                f"- **类别**：{r['category']}",
                f"- **执行**：{r['enforcement']['type']} / {r['enforcement']['tool']}",
                f"- **项目 7 适用**：{p7}",
                f"- **规则**：{one_line(r['statement'])}",
                "",
            ]
    INDEX_PATH.write_text("\n".join(lines).rstrip() + "\n", encoding="utf-8")


def generate_claude_block(rules: list[dict]) -> None:
    block = [
        GENERATED_START,
        "> 本区块由 `standards/generator/generate.py` 生成，请勿手改。完整规则库见 `standards/index.md`。",
        "",
        "## 强制规则（项目 7 适用）",
        "",
    ]
    for layer, title in [("L0", "L0 个人底线"), ("L1", "L1 通用 Java/Spring"), ("L2", "L2 项目 7 专属")]:
        selected = [r for r in rules if r["_layer"] == layer and applicable(r) and r["level"] == "must"]
        if not selected:
            continue
        block += [f"### {title}", ""]
        for r in selected:
            block.append(f"- **{r['id']}** {one_line(r['statement'])}（{r['priority']}）")
        block.append("")
    block.append(GENERATED_END)

    current = CLAUDE_PATH.read_text(encoding="utf-8") if CLAUDE_PATH.exists() else "# ai-hub\n"
    if GENERATED_START in current and GENERATED_END in current:
        before = current.split(GENERATED_START)[0]
        after = current.split(GENERATED_END, 1)[1]
        new_text = before + "\n".join(block) + after
    else:
        new_text = current.rstrip() + "\n\n" + "\n".join(block) + "\n"
    CLAUDE_PATH.write_text(new_text, encoding="utf-8")


def generate_cursor_rules(rules: list[dict]) -> None:
    CURSOR_RULES_DIR.mkdir(parents=True, exist_ok=True)
    for group, predicate in CATEGORY_GROUPS.items():
        selected = [r for r in rules if applicable(r) and predicate(r)]
        lines = [
            "---",
            f"description: {group} 编码规则（项目 7 适用，自动生成）",
            "globs:",
        ]
        for g in CURSOR_GLOBS[group]:
            lines.append(f"  - {g}")
        lines += ["alwaysApply: false", "---", ""]
        lines.append(f"> 由 `standards/generator/generate.py` 从 `standards/rules/*.yaml` 生成，请勿手改。")
        lines.append("")
        for r in selected:
            lines += [
                f"## {r['id']} · {r['title']}",
                "",
                f"- 级别：{r['level']}",
                f"- 规则：{one_line(r['statement'])}",
                f"- 执行：{r['enforcement']['type']} / {r['enforcement']['tool']}",
                "",
            ]
        (CURSOR_RULES_DIR / f"{group}.mdc").write_text("\n".join(lines).rstrip() + "\n", encoding="utf-8")


def generate_constitution(rules: list[dict]) -> None:
    def pick(ids: list[str]) -> list[dict]:
        by_id = {r["id"]: r for r in rules}
        return [by_id[i] for i in ids if i in by_id]

    security = pick([
        "rule-l0-secret-001", "rule-l0-log-001", "rule-l0-sql-001",
        "rule-p7-keyhash-001", "rule-p7-master-001",
    ])
    testing = pick(["rule-l0-test-001", "rule-l0-gate-001", "rule-test-001"])
    layering = pick(["rule-java-layering-001", "rule-java-validate-001", "rule-java-tx-001"])
    api = pick(["rule-p7-openai-001", "rule-p7-error-001", "rule-p7-models-001", "rule-java-logging-001"])
    simplicity = pick(["rule-java-methodlen-001", "rule-java-naming-001", "rule-java-magic-001"])

    def bullets(items: list[dict]) -> str:
        return "\n".join(f"- {one_line(r['statement'])}" for r in items)

    text = f"""# ai-hub Constitution

> 本文件由 `standards/generator/generate.py` 从规则库生成；修订规则后重新生成。
> 规则唯一真源：`standards/rules/*.yaml`；人读索引：`standards/index.md`。

## Core Principles

### I. Security First (NON-NEGOTIABLE)

{bullets(security)}

### II. Test-First & Verification (NON-NEGOTIABLE)

{bullets(testing)}

### III. Layered Boundaries

{bullets(layering)}

### IV. OpenAI Compatibility & Observability

{bullets(api)}

### V. Simplicity & Maintainability

{bullets(simplicity)}

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
"""
    CONSTITUTION_PATH.write_text(text, encoding="utf-8")


def main() -> int:
    rules = load_rules()
    if not rules:
        print("ERROR: no rules found")
        return 1
    generate_index(rules)
    generate_claude_block(rules)
    generate_cursor_rules(rules)
    generate_constitution(rules)
    print(f"Generated from {len(rules)} rules:")
    for p in [INDEX_PATH, CLAUDE_PATH, CONSTITUTION_PATH]:
        print(f"  - {p.relative_to(ROOT)}")
    for p in sorted(CURSOR_RULES_DIR.glob("*.mdc")):
        print(f"  - {p.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
