# Git Convention

## 커밋 메시지

```text
<이모지> <Type>: <간단한 설명>

(선택) 본문 — 추가 설명이 필요한 경우에만 작성
```

> 예: `📍 Feat: 카카오 로그인 기능 추가`

- 하나의 커밋에는 **하나의 논리적인 변경 사항만** 담는다.

### 커밋 타입

| 타입 | 설명 |
|:-----|:-----|
| 📍 Feat | 새로운 기능 추가 |
| 🔨 Fix | 버그 수정, 기존 기능(UI/UX 포함)의 문제 해결 |
| 📝 Docs | 문서 추가·수정 |
| 🎨 Style | 코드 포맷·들여쓰기·공백 등 스타일 수정 (기능 변경 없음) |
| 🤖 Refactor | 기능 변경 없이 코드 구조 개선 |
| ✅ Test | 테스트 코드 추가·수정 |
| 🚚 Chore | 빌드·설정·라이브러리·개발 환경 변경 |
| ✂️ Remove | 파일 또는 사용하지 않는 코드 삭제 |
| 🔧 Rename | 파일·폴더 이름 변경 또는 이동 |

## 브랜치 규칙

```text
<type>/<description>
```

> 예: `feat/oauth-login`, `fix/token-refresh`

- **소문자**만 사용하고, 여러 단어는 **kebab-case(`-`)** 로 잇는다.
- `<type>`은 커밋 타입의 소문자 표기를 쓴다 (`feat`, `fix`, `docs`, `refactor`, …).

## PR

- 기본 브랜치는 `dev`이며, `.github/pull_request_template.md` 양식을 채워 작성한다.
