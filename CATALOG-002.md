# CATALOG-002: 회차별 좌석 관리 API 계약

## 범위

공연 회차별 좌석을 생성하고 조회하며, 관리자가 좌석을 활성/비활성 처리할 수 있게 한다. 예매/선점/결제는 후속 작업이다.

## 데이터와 정책

- Seat: UUID id, sessionId, section(1..50), row(1..20), number(1..100000), price(0..1,000,000,000), status.
- 상태는 `AVAILABLE`, `DISABLED`, `HELD`, `RESERVED`를 저장할 수 있도록 스키마를 준비한다. 이번 단계의 관리자 변경은 `AVAILABLE`/`DISABLED`만 허용한다.
- 좌석은 회차에 귀속한다. `(session_id, section, row, number)`는 유일하다.
- 좌석 생성은 초안(`PREPARING`) 공연에서만 가능하다. 생성된 총 좌석 수는 회차 capacity를 넘을 수 없다.
- 한 요청은 같은 section/row에서 연속 좌석을 생성한다. `startNumber + count - 1 <= 100000`이어야 한다.
- 공개 좌석 조회는 `PUBLISHED` 공연에서만 허용한다. 비활성 좌석도 좌석 배치 표시를 위해 응답에 포함한다.
- 공연 공개 후 좌석 생성 및 활성/비활성 변경은 409로 거부한다.
- 좌석이 생성된 공연은 기존 전체 교체 PUT을 409로 거부한다. 회차 교체에 따른 좌석 연쇄 삭제를 방지하기 위한 초기 정책이며, 좌석 없는 초안 수정은 유지한다. 좌석을 보존하는 세부 수정 API는 후속 범위다.

## HTTP 계약

| Method | Path | 권한 | 결과 |
|---|---|---|---|
| POST | /api/admin/concerts/{concertId}/sessions/{sessionId}/seats | ADMIN | 201, 생성 좌석 배열 |
| GET | /api/admin/concerts/{concertId}/sessions/{sessionId}/seats | ADMIN | 전체 좌석 배열 |
| PATCH | /api/admin/concerts/{concertId}/sessions/{sessionId}/seats/{seatId} | ADMIN | 상태 변경 좌석 |
| GET | /api/concerts/{concertId}/sessions/{sessionId}/seats | 공개 | PUBLISHED 공연의 전체 좌석 배열 |

생성 JSON: `section`, `row`, `startNumber`, `count`, `price`.

상태 변경 JSON: `status` (`AVAILABLE` 또는 `DISABLED`).

좌석 목록은 section ASC, row ASC, number ASC로 정렬한다.

오류: 익명 관리자 접근 401, USER 관리자 접근 403, 입력/UUID 오류 400, 공연·회차·좌석 불일치 또는 미존재 404, capacity 초과·중복 좌석·공개 후 변경 409.

## 수용 기준

- Flyway V3 + JPA validate 통과.
- 관리자 JWT로 좌석 생성/조회/비활성/재활성 성공.
- 공개 전 좌석 조회 404, 공개 후 익명 조회 성공.
- capacity 초과, 중복 좌석, 잘못된 상태/입력 검증.
- 공개 후 좌석 생성/상태 변경 차단.
- 기존 테스트 보존 및 새 통합 테스트는 `TestInfrastructure`를 사용.
