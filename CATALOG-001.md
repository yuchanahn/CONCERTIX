# CATALOG-001: 공연·회차 관리 API 계약

## 이번 작업 범위

관리자 공연·회차 CRUD와 공개 조회를 구현한다. UI, 이미지 파일 업로드, 좌석 생성, 예매·결제는 후속 작업이다. 이미지는 이번 단계에서 http/https URL로 등록하며 서버가 URL을 가져오지 않는다.

## 데이터와 정책

- Concert: UUID id, title(1..200), description(최대 5000), imageUrl(최대 2048, http/https), venue(1..200), holdSeconds(1..3600), maxTicketsPerUser(1..100), status(PREPARING/PUBLISHED), createdAt.
- Session: UUID id, concertId, startsAt, bookingOpensAt, bookingClosesAt(ISO8601 offset 포함), capacity(1..100000).
- 공연별 선점 시간 및 구매 상한은 모든 회차에 적용한다. 최대 구매 수는 각 회차 정원 이하여야 한다. 후속 예매에서는 구매 수를 사용자·회차 단위로 집계한다.
- 회차는 공연당 1..100개. bookingOpensAt < bookingClosesAt <= startsAt. 동일 공연의 startsAt 중복 금지. 시각은 Instant로 저장한다.
- 생성은 PREPARING. 수정은 PREPARING에서만 가능하며 회차 목록 전체를 교체한다(이 단계에서는 좌석/예약이 없음). 회차 ID가 변경될 수 있다.
- 공개 전환은 별도 publish API. PUBLISHED는 수정/삭제 금지(409). 이미 공개된 공연의 publish는 동일 결과로 성공한다.
- 삭제는 PREPARING만 가능하고 회차도 삭제한다. 이후 좌석·예매 도입 시 삭제/수정 정책을 확장한다.
- PUBLISHED는 공개 여부이며 예매 가능 여부가 아니다. 실제 예매 허용은 회차별 예매 기간과 대기열로 후속 단계에서 판단한다. 기존 요구사항의 OPEN/CLOSED/ENDED 예시는 예매 기간 상태로 별도 취급한다.

## HTTP 계약

| Method | Path | 권한 | 결과 |
|---|---|---|---|
| POST | /api/admin/concerts | ADMIN | 201, 상세 JSON |
| GET | /api/admin/concerts?page=0&size=20 | ADMIN | 모든 상태 목록 |
| GET | /api/admin/concerts/{id} | ADMIN | 상세 |
| PUT | /api/admin/concerts/{id} | ADMIN | 초안 전체 수정, 상세 |
| DELETE | /api/admin/concerts/{id} | ADMIN | 204 |
| POST | /api/admin/concerts/{id}/publish | ADMIN | 공개 전환, 상세 |
| GET | /api/concerts?page=0&size=20 | 공개 | PUBLISHED만 목록 |
| GET | /api/concerts/{id} | 공개 | PUBLISHED 상세, 초안은 404 |

생성/수정 JSON 필드: title, description, imageUrl, venue, holdSeconds, maxTicketsPerUser, sessions[{startsAt,bookingOpensAt,bookingClosesAt,capacity}].
상세 응답에는 모든 필드, id, status, createdAt, 회차 id를 포함한다. 목록은 content(회차를 제외한 공연 요약), page, size, totalElements, totalPages. size 1..100, page>=0, createdAt DESC/id ASC 안정 정렬. 회차는 startsAt ASC 정렬.

오류: 익명 관리자 접근 401, USER 관리자 접근 403, 검증 실패/잘못된 UUID·페이지 400, 미존재·숨겨진 공연 404, 공개 후 변경 409. 검증은 HTTP 경계와 DB 제약에서 수행한다.

## 수용 기준

- Flyway V2 migration + JPA validate 통과, 기존 V1 보존.
- 관리자 JWT를 통한 실제 HTTP 생성→조회→수정→공개, 공개 조회 및 공개 후 수정/삭제 차단.
- 초안 삭제, 초안 공개 조회 불가, USER/익명 인가 실패, 잘못된 기간·인원·구매상한·중복회차·입력 누락·페이지 제한 검증.
- 기존 4테스트 보존. 모든 통합 테스트는 TestInfrastructure로 개발 데이터 격리.
- 별도 새 컨텍스트 코드 리뷰와 수정 후 재검증.

## 요청 예시

POST /api/admin/concerts 및 PUT /api/admin/concerts/{id}에 관리자 Bearer JWT와 application/json을 사용한다.

```json
{
  "title": "Autumn Live",
  "description": "첫 공연",
  "imageUrl": "https://example.com/poster.jpg",
  "venue": "서울 공연장",
  "holdSeconds": 300,
  "maxTicketsPerUser": 4,
  "sessions": [
    {
      "startsAt": "2026-12-20T19:00:00+09:00",
      "bookingOpensAt": "2026-11-01T10:00:00+09:00",
      "bookingClosesAt": "2026-12-20T18:00:00+09:00",
      "capacity": 100
    }
  ]
}
```

관리자 계정 발급은 공개 회원가입과 분리한다. 현재 회원가입은 USER만 생성한다. 이번 API 테스트는 격리 DB에 ADMIN fixture를 만들어 JWT를 발급한다. 운영 관리자 최초 발급 절차와 관리자 UI는 후속 작업에서 마련한다.
