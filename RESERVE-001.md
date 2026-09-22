# RESERVE-001: 좌석 선점 및 예매 관리 API 계약

## 범위

공연 회차의 좌석을 선점(Hold)하고 예매(Reservation)를 생성·조회·취소하는 API를 제공한다.
동시 다중 요청에 대해 DB 비관적 락(PESSIMISTIC_WRITE)을 적용하여 동일 좌석의 중복 선점을 방지한다.
가상 결제 및 예매 확정은 후속 단위(PAYMENT-001)에서 다룬다.

## 데이터와 정책

1. **Reservation 엔티티**:
   - `id`: UUID (기본키)
   - `member_id`: UUID (외래키 -> members.id)
   - `session_id`: UUID (외래키 -> concert_sessions.id)
   - `seat_id`: UUID (외래키 -> seats.id)
   - `status`: `PENDING_PAYMENT`, `CONFIRMED`, `CANCELED`, `EXPIRED`
   - `price`: 결제 대상 금액 (좌석 가격 스냅샷)
   - `held_until`: Instant (선점 만료 일시, `Instant.now() + concert.holdSeconds`)
   - `created_at`, `updated_at`: Instant

2. **좌석 선점 및 예매 조건**:
   - 공연은 `PUBLISHED` 상태여야 한다. (아닐 시 409 Conflict)
   - 현재 시각은 회차의 예매 기간(`bookingOpensAt <= now <= bookingClosesAt`) 내여야 한다. (아닐 시 409 Conflict)
   - 요청 좌석은 해당 회차(`session_id`)에 속해야 한다. (불일치 또는 미존재 시 404 Not Found)
   - 좌석 상태는 `AVAILABLE`이거나, 기존 `HELD` 상태이나 선점 만료(`held_until < now`)된 상태여야 한다.
   - 이미 유효하게 `HELD` 중이거나, `RESERVED` 또는 `DISABLED`인 좌석은 409 Conflict로 거부한다.
   - 1인당 최대 예매 매수(`concert.maxTicketsPerUser`):
     - 사용자의 해당 공연 또는 회차에 대한 활성 예매(`PENDING_PAYMENT`, `CONFIRMED`) 개수가 `maxTicketsPerUser`를 초과할 수 없다. 초과 시 409 Conflict.
   - 동시성 제어:
     - 좌석 조회 시 `PESSIMISTIC_WRITE` (비관적 쓰기 락)을 획득하여 동일 좌석에 대한 동시 트랜잭션의 중복 선점을 원천 차단한다.
     - 선점 성공 시 좌석 상태를 `HELD`로 변경하고, `PENDING_PAYMENT` 상태의 Reservation을 생성한다.

3. **예매 조회 및 취소**:
   - `GET /api/reservations/me`: 현재 로그인한 사용자의 예매 목록을 반환한다 (최신 생성순 정렬).
   - `GET /api/reservations/{reservationId}`: 특정 예매 상세를 조회한다 (본인 예매가 아니면 403 Forbidden, 미존재 시 404).
   - `POST /api/reservations/{reservationId}/cancel`: 본인의 `PENDING_PAYMENT` 또는 `CONFIRMED` 예매를 취소한다.
     - 상태를 `CANCELED`로 변경하고, 좌석 상태를 `AVAILABLE`로 원복한다.
     - 이미 취소되었거나 만료된 경우 409 Conflict.

## HTTP 계약

| Method | Path | 권한 | 성공 응답 | 주요 에러 |
|---|---|---|---|---|
| POST | /api/reservations | USER | 201 Created, Reservation 상세 | 400, 401, 404, 409 |
| GET | /api/reservations/me | USER | 200 OK, Reservation 목록 | 401 |
| GET | /api/reservations/{reservationId} | USER | 200 OK, Reservation 상세 | 401, 403, 404 |
| POST | /api/reservations/{reservationId}/cancel | USER | 200 OK, Reservation 상세 | 401, 403, 404, 409 |

### 요청/응답 JSON 스펙

- **선점/예매 생성 요청 (`POST /api/reservations`)**:
```json
{
  "sessionId": "UUID",
  "seatId": "UUID"
}
```

- **예매 상세 응답**:
```json
{
  "id": "UUID",
  "sessionId": "UUID",
  "seatId": "UUID",
  "seatInfo": {
    "section": "A",
    "rowLabel": "1",
    "seatNumber": 10,
    "price": 120000
  },
  "status": "PENDING_PAYMENT",
  "price": 120000,
  "heldUntil": "2026-09-22T09:40:00Z",
  "createdAt": "2026-09-22T09:35:00Z"
}
```

## 수용 기준

1. **Flyway V4 마이그레이션**: `reservations` 테이블 생성 및 제약 조건 적용, JPA ddl validate 통과.
2. **선점 정상 흐름**: 일반 USER가 예매 기간 내 `AVAILABLE` 좌석 선점 시 201 응답, 좌석 상태 `HELD` 전환, 만료 시간 정상 계산.
3. **만료 좌석 재선점**: 선점 시간(`held_until`)이 지난 좌석에 대해 다른 사용자가 새로 선점 요청 시 성공.
4. **비정상/충돌 경로**:
   - 공개되지 않은 공연 / 예매 기간 외 요청 시 409.
   - 이미 선점된 좌석(`HELD`) 또는 판매 완료(`RESERVED`), 비활성(`DISABLED`) 좌석 선점 시 409.
   - 1인당 최대 예매 한도 초과 시 409.
   - 비로그인(401), 타인 예매 조회/취소 시도(403), 없는 좌석/예매(404).
5. **동시성 검증**:
   - `ReservationConcurrencyIntegrationTests`: 동일 좌석에 대해 20~50개 스레드가 동시 선점 요청 시 **단 1개 요청만 201 성공**하고 나머지는 모두 409 응답을 반환하여 데이터 정합성이 보장됨을 증명.
6. **기존 기능 회귀 없음**: 기존 12개 테스트 + 신규 예매/동시성 테스트 전원 통과 (`TestInfrastructure` 격리 환경 유지).
