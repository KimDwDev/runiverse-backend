---
description: PR 템플릿 양식으로 dev 대상 PR 생성
---

현재 브랜치의 작업으로 PR을 만들어줘.

1. 현재 브랜치가 `docs/git-convention.md`의 브랜치 규칙에 맞는지 확인한다 (`dev`에서 직접 PR 금지).
2. `git log dev..HEAD`와 `git diff dev...HEAD`로 이 브랜치의 전체 변경사항을 파악한다.
3. 푸시가 안 된 커밋이 있으면 push 여부를 확인받고 푸시한다.
4. `.github/pull_request_template.md` 양식을 채워 `gh pr create --base dev`로 PR을 생성한다:
   - **작업 내용 / 변경 사항(추가·수정·삭제)**: 커밋과 diff 기반으로 구체적으로 작성
   - **테스트**: 실제로 수행한 항목만 체크 (단위 테스트를 돌렸으면 결과 포함)
   - **리뷰 포인트**: UseCase 분리·Port 설계·예외 처리 등 이 변경에서 리뷰어가 봐야 할 부분을 명시
5. 생성된 PR URL을 알려준다.

$ARGUMENTS
