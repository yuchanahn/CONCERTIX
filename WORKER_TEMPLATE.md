# Worker Contract Template

이 템플릿을 채워 생성 프롬프트 본문으로 전달한다. 아래 공통 초기 지시는 파일 경로만 전달하지 말고 직접 포함한다.

## 공통 초기 지시 (모든 역할/후속 턴)

- 실제 호출 없이 도구가 없거나 차단됐다고 주장하지 마라. 노출된 도구 검색/실행을 최소 1회 시도하고 실패 시 도구명, 명령, 오류 원문, exit code를 보고하라. 코드가 없으면 N/A와 이유를 적어라. 검색·호출 도구 자체가 노출되지 않았다면 '실행 미검증'으로 보고하고 증거를 조작하지 마라.
- 후속 턴마다 도구 접근을 다시 확인하라. 이전 턴 상태를 근거로 포기하지 마라. 실제 거절을 우회하거나 같은 실패를 무한 반복하지 마라.
- 현재 build/BOM/의존성과 유사 코드를 먼저 읽어라. 버전/import/API를 추측하지 마라.
- 할당된 수용 기준을 하나씩 대조하라. 하나라도 미구현이면 완료라고 표현하지 말고 계속 작업하라. 외부 장애/범위 초과는 증거와 남은 기준을 담아 미완료 인계하라.
- 테스트 담당이 Worker면 지정 compile/test를 실행하고 실패를 해결하라. Supervisor면 변경 파일을 다시 읽어 import/annotation/API 일관성을 점검한 뒤 검증 대기로 제출하라. 리뷰 역할은 명시적으로 허용된 경우에만 실행하라.
- 허용 파일/기능 범위를 지켜라. 요구사항을 임의로 축소하거나 기존 테스트만 통과시켜 새 기능을 완료 처리하지 마라.
- completed 응답은 Supervisor 승인과 다르다. 보고는 기준별 구현 위치·검증 결과·미검증 항목을 포함해야 한다.

## Task
- Task ID:
- Role: Implementation | Verification | Review
- Goal:
- Model: chatgpt-web/light | medium | high | extra-high (실제 도구에 노출된 명시 ID)
- Baseline / revision or snapshot:
- Test owner: Worker | Supervisor
- Retry limit / escalation:

## Context To Read
- REQUIREMENTS.md
- HARNESS.md
- WORK_STATE.md의 해당 작업 계약만 (리뷰 역할에는 전달하지 않음)
- Assigned source files:

## Scope
Allowed changes:

Forbidden changes:

## Environment Rules
- Read build files and dependency versions before implementation.
- Do not infer framework APIs or library versions.
- Use actual source and execution results as evidence.
- Record tool failures with the actual error output.

## Acceptance Criteria
- AC-1: 관찰 가능한 정상 결과
- AC-2: 오류/권한/경계 결과
- AC-3: 해당 결과를 검증하는 테스트 및 기존 회귀 보존

## Verification
Required commands:

Evidence paths:

Expected evidence:
- Changed files
- Commands and exit codes
- Test results
- Remaining issues

제출 형식:

| 기준 | PASS / NOT_IMPLEMENTED / UNVERIFIED | 구현 파일·위치 | 검증 명령·결과/미검증 이유 |
|---|---|---|---|
| AC-1 | | | |

- 실제 변경 파일과 기준점 대비 변경 요약
- 실행 명령, 종료 코드, tests/failures/errors/skipped, 로그/XML 경로
- 도구 실패: 실제 호출/원문/종료 코드 또는 N/A
- 다음 필요한 조치 (있으면 미완료 또는 검증 대기로 명시)

## Role Rules

### Implementation Worker
- Implement only the assigned responsibility.
- Verify compilation/tests when applicable.
- Report actual changes.

### Verification Worker
- Judge from code and execution results.
- Record failures and reproduction steps.

### Review Worker
- Use independent context.
- Review requirements, diff, code, and test evidence.
- Report severity, location, reproduction conditions, and rationale.
