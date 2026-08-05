#!/usr/bin/env python3
"""Runiverse 백엔드 구조 규칙 검사.

문서를 읽어야 판단되는 것(스펙 ↔ 구현 정합성)은 다루지 않는다.
소스만 보고 기계적으로 판정 가능한 것만 검사한다.

사용법:
    python3 .claude/skills/spec-check/scripts/check_conventions.py [저장소_루트]

출력은 사람이 읽는 리포트. 위반이 있어도 종료 코드는 0이다
(게이트가 아니라 조사 도구이므로, 호출한 쪽이 결과를 해석한다).
"""

import re
import sys
from pathlib import Path

SRC = "running-service/src/main/java/com/runiverse/running_service"
BASE_PKG = "com.runiverse.running_service"

# 레이어별 금지 import (접두사 매칭). lombok은 전 레이어 허용.
FORBIDDEN = {
    "domain": [
        ("org.springframework", "스프링 의존"),
        ("jakarta.persistence", "JPA 의존"),
        ("jakarta.validation", "Bean Validation 의존"),
        ("jakarta.transaction", "트랜잭션 API 의존"),
        ("org.hibernate", "하이버네이트 의존"),
        ("com.fasterxml", "잭슨 의존"),
        (f"{BASE_PKG}.application", "바깥 레이어 참조"),
        (f"{BASE_PKG}.infrastructure", "바깥 레이어 참조"),
        (f"{BASE_PKG}.presentation", "바깥 레이어 참조"),
    ],
    "application": [
        ("jakarta.persistence", "JPA 의존 — 영속성은 어댑터 책임"),
        ("org.hibernate", "하이버네이트 의존"),
        (f"{BASE_PKG}.infrastructure", "바깥 레이어 참조"),
        (f"{BASE_PKG}.presentation", "바깥 레이어 참조"),
    ],
    "infrastructure": [
        (f"{BASE_PKG}.presentation", "presentation 참조 — 순환 의존"),
    ],
    "presentation": [
        (f"{BASE_PKG}.infrastructure", "infrastructure 참조 — port/in만 통해야 함"),
    ],
}

# port/out 이름에 쓰는 동사 접두사 (architecture.md의 '동사로 시작' 규칙)
PORT_VERBS = (
    "Check", "Load", "Save", "Delete", "Generate", "Exchange",
    "Parse", "Block", "Exists", "Update", "Find", "Send", "Publish",
)

# 단위 접미사가 필요한 물리량 키워드 → 허용 접미사
UNIT_KEYWORDS = {
    "distance": ("Meters",),
    "pace": ("SecondsPerKm",),
    "weight": ("Kg",),
    "height": ("Cm",),
    "altitude": ("Meters",),
    "elevation": ("Meters",),
    "speed": ("MetersPerSecond",),
    "cadence": ("Spm",),
    "calorie": ("Kcal",),
    "duration": ("Seconds",),
    "heading": ("Degrees",),
    "accuracy": ("Meters",),
}

IMPORT_RE = re.compile(r"^\s*import\s+(?:static\s+)?([\w.]+)", re.M)
# 인터페이스 본문의 메서드 선언 (기본 구현 default 메서드 제외)
IFACE_METHOD_RE = re.compile(r"^\s*(?!default\b|static\b)[\w<>\[\],.?\s]+?\s+(\w+)\s*\(", re.M)


def strip_annotations(text: str) -> str:
    """`@Foo(...)` 를 제거한다. 어노테이션 인자 안의 쉼표·괄호·문자열 때문에
    정규식으로는 안정적으로 못 지운다 — 괄호 깊이를 세며 훑는다."""
    out, i, n = [], 0, len(text)
    while i < n:
        if text[i] != "@":
            out.append(text[i])
            i += 1
            continue
        i += 1
        while i < n and (text[i].isalnum() or text[i] in "_."):
            i += 1
        j = i
        while j < n and text[j].isspace():
            j += 1
        if j < n and text[j] == "(":
            i = skip_balanced(text, j)
    return "".join(out)


def skip_balanced(text: str, start: int) -> int:
    """`text[start]`의 여는 괄호에 대응하는 닫는 괄호 다음 위치를 반환한다."""
    depth, i, n = 0, start, len(text)
    while i < n:
        c = text[i]
        if c in "\"'":
            quote, i = c, i + 1
            while i < n and text[i] != quote:
                i += 2 if text[i] == "\\" else 1
        elif c == "(":
            depth += 1
        elif c == ")":
            depth -= 1
            if depth == 0:
                return i + 1
        i += 1
    return n


def split_params(params: str):
    """최상위 쉼표로만 나눈다 (제네릭·괄호 안 쉼표는 무시)."""
    parts, depth, buf = [], 0, []
    for c in params:
        if c in "<(":
            depth += 1
        elif c in ">)":
            depth -= 1
        if c == "," and depth == 0:
            parts.append("".join(buf))
            buf = []
        else:
            buf.append(c)
    if "".join(buf).strip():
        parts.append("".join(buf))
    return parts


def record_fields(text: str):
    """record 헤더의 필드명 목록."""
    m = re.search(r"\brecord\s+\w+\s*(?=\()", text)
    if not m:
        return []
    open_paren = text.index("(", m.end() - 1)
    params = text[open_paren + 1 : skip_balanced(text, open_paren) - 1]
    fields = []
    for part in split_params(strip_annotations(params)):
        tokens = part.split()
        if len(tokens) >= 2:
            fields.append(tokens[-1])
    return fields


def java_files(root: Path):
    return sorted((root / SRC).rglob("*.java"))


def layer_of(path: Path, root: Path) -> str:
    rel = path.relative_to(root / SRC).as_posix()
    return rel.split("/")[0] if "/" in rel else ""


def rel(path: Path, root: Path) -> str:
    return path.relative_to(root).as_posix()


def check_layers(root: Path):
    """의존 방향 위반. 안쪽 레이어가 바깥을 참조하면 클린 아키텍처가 깨진다."""
    hits = []
    for f in java_files(root):
        layer = layer_of(f, root)
        rules = FORBIDDEN.get(layer)
        if not rules:
            continue
        text = f.read_text(encoding="utf-8")
        for imported in IMPORT_RE.findall(text):
            for prefix, why in rules:
                if imported.startswith(prefix):
                    hits.append((rel(f, root), imported, why))
                    break
    return hits


def parse_enum_constants(path: Path):
    """enum 상수명만 뽑는다 (CONSTANT("code", "msg") 형태)."""
    if not path.exists():
        return []
    text = path.read_text(encoding="utf-8")
    body = text.split("{", 1)[-1]
    names = []
    for line in body.splitlines():
        line = line.strip()
        if line.startswith("//") or line.startswith("*") or line.startswith("/*"):
            continue
        m = re.match(r"([A-Z][A-Z0-9_]*)\s*\(", line)
        if m:
            names.append(m.group(1))
    return names


def check_error_exposure(root: Path):
    """ErrorCode ↔ toStatus ↔ EXPOSED_CODES 3자 대조.

    EXPOSED_CODES 누락은 컴파일러가 못 잡고 런타임에 500으로 둔갑한다.
    """
    base = root / SRC
    codes = parse_enum_constants(base / "application/common/exception/ErrorCode.java")

    handler = base / "presentation/common/exception/GlobalExceptionHandler.java"
    policy = base / "presentation/common/exception/ErrorExposurePolicy.java"
    handler_text = handler.read_text(encoding="utf-8") if handler.exists() else ""
    policy_text = policy.read_text(encoding="utf-8") if policy.exists() else ""

    # toStatus 스위치 본문만 잘라낸다
    m = re.search(r"toStatus\s*\([^)]*\)\s*\{(.*?)\n\s*\}", handler_text, re.S)
    switch_body = m.group(1) if m else ""

    exposed = set(re.findall(r"ErrorCode\.(\w+)\.getCode\(\)", policy_text))

    rows = []
    for code in codes:
        in_switch = re.search(rf"\b{code}\b", switch_body) is not None
        rows.append((code, in_switch, code in exposed))
    return rows


def check_dead_exceptions(root: Path):
    """어디서도 생성되지 않는 예외 클래스. 리팩토링 잔재일 가능성이 높다."""
    files = java_files(root)
    corpus = {f: f.read_text(encoding="utf-8") for f in files}
    dead = []
    for f in files:
        if not f.name.endswith("Exception.java"):
            continue
        name = f.stem
        # 추상 기반 클래스는 직접 생성되지 않는 게 정상이다
        if re.search(rf"\babstract\s+class\s+{re.escape(name)}\b", corpus[f]):
            continue
        used = any(
            other is not f and (f"new {name}(" in text or f"{name}::new" in text)
            for other, text in corpus.items()
        )
        if not used:
            dead.append(rel(f, root))
    return dead


def check_ports(root: Path):
    """포트 규칙: 단일 메서드 인터페이스, 동사로 시작."""
    multi, non_iface, odd_name = [], [], []
    for f in java_files(root):
        parts = f.relative_to(root / SRC).as_posix().split("/")
        if "port" not in parts:
            continue
        text = f.read_text(encoding="utf-8")
        kind = parts[parts.index("port") + 1] if parts.index("port") + 1 < len(parts) else ""

        if not re.search(r"\binterface\s+" + re.escape(f.stem) + r"\b", text):
            non_iface.append((rel(f, root), "인터페이스가 아님"))
            continue

        body = text.split("{", 1)[-1]
        # 주석 제거 후 메서드 선언 수를 센다
        body = re.sub(r"//.*?$|/\*.*?\*/", "", body, flags=re.S | re.M)
        methods = IFACE_METHOD_RE.findall(body)
        if len(methods) > 1:
            multi.append((rel(f, root), methods))

        if kind == "out" and not f.stem.startswith(PORT_VERBS):
            odd_name.append(rel(f, root))
    return multi, non_iface, odd_name


def check_unit_suffix(root: Path):
    """요청·응답 DTO 필드의 단위 접미사 (api-convention.md '예외 0' 규칙).

    휴리스틱이다 — 이름에 물리량 키워드가 있는데 접미사가 없는 필드를 모은다.
    """
    hits = []
    for f in java_files(root):
        p = f.relative_to(root / SRC).as_posix()
        if not (p.startswith("presentation/") and ("/request/" in p or "/response/" in p)):
            continue
        for field in record_fields(f.read_text(encoding="utf-8")):
            low = field.lower()
            for kw, suffixes in UNIT_KEYWORDS.items():
                if kw in low and not field.endswith(suffixes):
                    hits.append((rel(f, root), field, suffixes[0]))
                    break
    return hits


def main():
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
    if not (root / SRC).is_dir():
        print(f"소스 디렉터리를 찾을 수 없습니다: {root / SRC}")
        return 0

    total = 0

    print("## 1. 레이어 의존 방향")
    layers = check_layers(root)
    if layers:
        total += len(layers)
        for path, imported, why in layers:
            print(f"  - {path}\n      {imported}  ({why})")
    else:
        print("  위반 없음")

    print("\n## 2. 에러 코드 등록 (ErrorCode / toStatus / EXPOSED_CODES)")
    rows = check_error_exposure(root)
    missing = [(c, s, e) for c, s, e in rows if not (s and e)]
    if missing:
        total += len(missing)
        for code, in_switch, in_exposed in missing:
            flags = []
            if not in_switch:
                flags.append("toStatus 누락")
            if not in_exposed:
                flags.append("EXPOSED_CODES 없음 → 500으로 응답됨")
            print(f"  - {code}: {', '.join(flags)}")
        print("  ※ 의도적 비노출일 수 있다 — 보고 전 `git log -S <코드명>`으로 확인할 것")
    else:
        print(f"  {len(rows)}개 코드 모두 정상 등록")

    print("\n## 3. 사용되지 않는 예외 클래스")
    dead = check_dead_exceptions(root)
    if dead:
        total += len(dead)
        for path in dead:
            print(f"  - {path}")
    else:
        print("  없음")

    print("\n## 4. 포트 규칙")
    multi, non_iface, odd_name = check_ports(root)
    if multi or non_iface:
        total += len(multi) + len(non_iface)
    for path, methods in multi:
        print(f"  - {path}: 메서드 {len(methods)}개 — {', '.join(methods)} (단일 메서드로 분리)")
    for path, why in non_iface:
        print(f"  - {path}: {why}")
    for path in odd_name:
        print(f"  - (참고) {path}: 동사 접두사로 시작하지 않음")
    if not (multi or non_iface or odd_name):
        print("  위반 없음")

    print("\n## 5. DTO 단위 접미사 (휴리스틱 — 오탐 가능)")
    units = check_unit_suffix(root)
    if units:
        for path, field, expected in units:
            print(f"  - {path}: `{field}` → `...{expected}` 필요?")
    else:
        print("  의심 필드 없음")

    print(f"\n---\n확실한 위반 {total}건 (5번 항목은 별도 판단)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
