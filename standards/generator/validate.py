# -*- coding: utf-8 -*-
"""校验规则卡 YAML：必填字段、id 唯一、枚举值。"""
import sys
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[1] / "rules"
REQUIRED = [
    "id", "title", "statement", "level", "category", "scope", "source",
    "rationale", "examples", "enforcement", "applicability", "priority",
]
LEVELS = {"must", "should", "may"}
ENFORCEMENT_TYPES = {"auto", "manual"}


def main() -> int:
    ids = set()
    total = 0
    layer_counts = {}
    for f in sorted(ROOT.glob("*.yaml")):
        data = yaml.safe_load(f.read_text(encoding="utf-8")) or {}
        rules = data.get("rules", [])
        layer = data.get("layer", f.stem)
        layer_counts[layer] = len(rules)
        for r in rules:
            missing = [k for k in REQUIRED if k not in r]
            assert not missing, f"{f.name}: {r.get('id')} missing {missing}"
            assert r["id"] not in ids, f"duplicate id {r['id']}"
            assert r["level"] in LEVELS, f"{r['id']} invalid level {r['level']}"
            etype = r["enforcement"].get("type")
            assert etype in ENFORCEMENT_TYPES, f"{r['id']} invalid enforcement.type {etype}"
            assert isinstance(r["scope"], list) and r["scope"], f"{r['id']} scope must be non-empty list"
            assert isinstance(r["applicability"], dict), f"{r['id']} applicability must be dict"
            ids.add(r["id"])
            total += 1
    print(f"OK: {total} rules")
    for layer, count in sorted(layer_counts.items()):
        print(f"  {layer}: {count}")
    assert total >= 40, f"expected at least 40 rules, got {total}"
    return 0


if __name__ == "__main__":
    sys.exit(main())
