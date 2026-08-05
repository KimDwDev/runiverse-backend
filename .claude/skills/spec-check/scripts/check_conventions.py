#!/usr/bin/env python3
"""Runiverse 백엔드 구조 규칙 검사.

문서를 읽어야 판단되는 것(스펙 ↔ 구현 정합성)은 다루지 않는다.
소스만 보고 기계적으로 판정 가능한 것만 검사한다.

사용법:
    python3 .claude/skills/spec-check/scripts/check_conventions.py [저장소_루트] [소스_범위]

출력은 확정 위반·조사 필요·휴리스틱 의심으로 나눈 사람이 읽는 리포트다.
위반이 있어도 종료 코드는 0이다(게이트가 아니라 조사 도구이므로 호출한 쪽이 결과를 해석한다).
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


def java_files(root: Path, scope: Path = None):
    target = scope or root / SRC
    if target.is_file():
        return [target] if target.suffix == ".java" else []
    return sorted(target.rglob("*.java"))


def resolve_scope(root: Path, value: str):
    """선택 범위를 소스 루트 안의 파일이나 디렉터리로 해석한다."""
    if not value:
        return None

    source_root = (root / SRC).resolve()
    raw = Path(value)
    candidates = [raw] if raw.is_absolute() else [root / raw, source_root / raw]
    scope = next((candidate.resolve() for candidate in candidates if candidate.exists()), None)
    if scope is None:
        raise ValueError(f"검사 범위를 찾을 수 없습니다: {value}")
    try:
        scope.relative_to(source_root)
    except ValueError as e:
        raise ValueError(f"검사 범위는 소스 디렉터리 안이어야 합니다: {scope}") from e
    if not scope.is_dir() and scope.suffix != ".java":
        raise ValueError(f"검사 범위는 디렉터리나 Java 파일이어야 합니다: {scope}")
    return scope


def layer_of(path: Path, root: Path) -> str:
    rel = path.relative_to(root / SRC).as_posix()
    return rel.split("/")[0] if "/" in rel else ""


def rel(path: Path, root: Path) -> str:
    return path.relative_to(root).as_posix()


def check_layers(root: Path, scope: Path = None):
    """의존 방향 위반. 안쪽 레이어가 바깥을 참조하면 클린 아키텍처가 깨진다."""
    hits = []
    for f in java_files(root, scope):
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


def check_error_exposure(root: Path, scope: Path = None):
    """ErrorCode ↔ toStatus ↔ EXPOSED_CODES 3자 대조.

    EXPOSED_CODES 누락은 컴파일러가 못 잡고 런타임에 500으로 둔갑한다.
    """
    base = root / SRC
    codes = parse_enum_constants(base / "application/common/exception/ErrorCode.java")

    if scope is not None:
        scoped_files = java_files(root, scope)
        global_files = {
            (base / "application/common/exception/ErrorCode.java").resolve(),
            (base / "presentation/common/exception/GlobalExceptionHandler.java").resolve(),
            (base / "presentation/common/exception/ErrorExposurePolicy.java").resolve(),
        }
        if not any(f.resolve() in global_files for f in scoped_files):
            scoped_text = "\n".join(f.read_text(encoding="utf-8") for f in scoped_files)
            referenced = set(re.findall(r"\bErrorCode\.(\w+)\b", scoped_text))
            codes = [code for code in codes if code in referenced]

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


def check_dead_exceptions(root: Path, scope: Path = None):
    """어디서도 생성되지 않는 예외 클래스. 리팩토링 잔재일 가능성이 높다."""
    files = java_files(root)
    candidates = java_files(root, scope)
    corpus = {f: f.read_text(encoding="utf-8") for f in files}
    dead = []
    for f in candidates:
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


def check_ports(root: Path, scope: Path = None):
    """port/out 규칙: 정확히 한 메서드를 가진 인터페이스."""
    wrong_count, non_iface = [], []
    for f in java_files(root, scope):
        parts = f.relative_to(root / SRC).as_posix().split("/")
        if "port" not in parts:
            continue
        text = f.read_text(encoding="utf-8")
        kind = parts[parts.index("port") + 1] if parts.index("port") + 1 < len(parts) else ""
        if kind != "out":
            continue

        if not re.search(r"\binterface\s+" + re.escape(f.stem) + r"\b", text):
            non_iface.append((rel(f, root), "인터페이스가 아님"))
            continue

        body = text.split("{", 1)[-1]
        # 주석 제거 후 메서드 선언 수를 센다
        body = re.sub(r"//.*?$|/\*.*?\*/", "", body, flags=re.S | re.M)
        methods = IFACE_METHOD_RE.findall(body)
        if len(methods) != 1:
            wrong_count.append((rel(f, root), methods))
    return wrong_count, non_iface


def check_application_structure(root: Path, scope: Path = None):
    """Command/Handler/Result 세트와 Handler 트랜잭션 경계를 검사한다."""
    source_root = root / SRC
    feature_dirs = set()
    for f in java_files(root, scope):
        parts = f.relative_to(source_root).parts
        if len(parts) >= 4 and parts[0] == "application" and parts[2] == "command":
            feature_dirs.add(source_root.joinpath(*parts[:4]))

    missing_sets, handler_issues = [], []
    for feature_dir in sorted(feature_dirs):
        files = sorted(feature_dir.glob("*.java"))
        stems = [f.stem for f in files]
        missing = [suffix for suffix in ("Command", "Handler", "Result")
                   if not any(stem.endswith(suffix) for stem in stems)]
        if missing:
            missing_sets.append((rel(feature_dir, root), missing))

        for handler in (f for f in files if f.stem.endswith("Handler")):
            text = re.sub(
                r"//.*?$|/\*.*?\*/",
                "",
                handler.read_text(encoding="utf-8"),
                flags=re.S | re.M,
            )
            issues = []
            if "@Transactional" not in text:
                issues.append("@Transactional 누락")
            declaration = re.search(
                r"\bclass\s+\w+Handler\b[^\{]*\bimplements\b[^\{]*\b\w+Usecase\b",
                text,
                re.S,
            )
            if not declaration:
                issues.append("*Usecase 구현 누락")
            if issues:
                handler_issues.append((rel(handler, root), issues))
    return missing_sets, handler_issues


def check_outbound_names(root: Path, scope: Path = None):
    """port/out 구현체가 *Adapter로 끝나는지 검사한다."""
    hits = []
    for f in java_files(root, scope):
        if layer_of(f, root) != "infrastructure":
            continue
        text = re.sub(r"//.*?$|/\*.*?\*/", "", f.read_text(encoding="utf-8"), flags=re.S | re.M)
        declaration = re.search(r"\bclass\s+(\w+)\b[^\{]*\bimplements\b([^\{]+)\{", text, re.S)
        if not declaration:
            continue
        class_name, interfaces = declaration.groups()
        if re.search(r"\b\w+Port\b", interfaces) and not class_name.endswith("Adapter"):
            hits.append(rel(f, root))
    return hits


def check_unit_suffix(root: Path, scope: Path = None):
    """요청·응답 DTO 필드의 단위 접미사 (api-convention.md '예외 0' 규칙).

    휴리스틱이다 — 이름에 물리량 키워드가 있는데 접미사가 없는 필드를 모은다.
    """
    hits = []
    for f in java_files(root, scope):
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
    try:
        scope = resolve_scope(root, sys.argv[2] if len(sys.argv) > 2 else None)
    except ValueError as e:
        print(e)
        return 0
    if scope is not None and not java_files(root, scope):
        print(f"검사 범위에 Java 파일이 없습니다: {scope}")
        return 0

    confirmed = 0
    investigate = 0

    print("## 1. 확정 위반")
    print("\n### 레이어 의존 방향")
    layers = check_layers(root, scope)
    if layers:
        confirmed += len(layers)
        for path, imported, why in layers:
            print(f"  - {path}\n      {imported}  ({why})")
    else:
        print("  위반 없음")

    print("\n### 포트 구조")
    wrong_count, non_iface = check_ports(root, scope)
    if wrong_count or non_iface:
        confirmed += len(wrong_count) + len(non_iface)
    for path, methods in wrong_count:
        detail = ", ".join(methods) if methods else "메서드 없음"
        print(f"  - {path}: 메서드 {len(methods)}개 — {detail} (정확히 1개 필요)")
    for path, why in non_iface:
        print(f"  - {path}: {why}")
    if not (wrong_count or non_iface):
        print("  위반 없음")

    print("\n### 애플리케이션 구성·트랜잭션")
    missing_sets, handler_issues = check_application_structure(root, scope)
    if missing_sets or handler_issues:
        confirmed += len(missing_sets) + len(handler_issues)
        for path, missing in missing_sets:
            print(f"  - {path}: {', '.join(missing)} 누락")
        for path, issues in handler_issues:
            print(f"  - {path}: {', '.join(issues)}")
    else:
        print("  위반 없음")

    print("\n### 아웃바운드 구현체 네이밍")
    outbound_names = check_outbound_names(root, scope)
    if outbound_names:
        confirmed += len(outbound_names)
        for path in outbound_names:
            print(f"  - {path}: port/out 구현체는 *Adapter로 명명")
    else:
        print("  위반 없음")

    print("\n## 2. 조사 필요")
    print("\n### 에러 코드 등록 (ErrorCode / toStatus / EXPOSED_CODES)")
    rows = check_error_exposure(root, scope)
    missing = [(c, s, e) for c, s, e in rows if not (s and e)]
    if missing:
        investigate += len(missing)
        for code, in_switch, in_exposed in missing:
            flags = []
            if not in_switch:
                flags.append("toStatus 누락")
            if not in_exposed:
                flags.append("EXPOSED_CODES 없음 — 400 외 상태는 500으로 마스킹됨")
            print(f"  - {code}: {', '.join(flags)}")
        print("  ※ 의도적 비노출일 수 있다 — 보고 전 `git log -S <코드명>`으로 확인할 것")
    else:
        print(f"  {len(rows)}개 코드 모두 정상 등록" if rows else "  범위에서 참조한 application ErrorCode 없음")

    print("\n### 사용되지 않는 예외 클래스")
    dead = check_dead_exceptions(root, scope)
    if dead:
        investigate += len(dead)
        for path in dead:
            print(f"  - {path}")
    else:
        print("  없음")

    print("\n## 3. 휴리스틱 의심")
    print("\n### DTO 단위 접미사")
    units = check_unit_suffix(root, scope)
    if units:
        for path, field, expected in units:
            print(f"  - {path}: `{field}` → `...{expected}` 필요?")
    else:
        print("  의심 필드 없음")

    print(
        f"\n---\n확정 위반 {confirmed}건 / "
        f"조사 필요 {investigate}건 / 휴리스틱 의심 {len(units)}건"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
