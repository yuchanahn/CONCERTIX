# QUEUE-001: Redis 기반 예매 대기열

## 목표

예매 오픈 직후 트래픽 급증 시 공연별 대기열을 활성화하고, 제한된 사용자에게만 일정 시간 동안 예매 진입 권한을 부여한다.

## 정책

- 관리자가 공연별 대기열을 활성화/비활성화한다.
- 대기 순서는 Redis Sorted Set으로 관리한다.
- 동시에 입장 가능한 사용자 수는 `app.queue.max-admitted`로 제한한다.
- 입장 토큰은 `app.queue.admission-ttl` 동안 유효하다.
- 대기열이 활성화된 공연의 예매 요청은 `X-Queue-Token`을 검증하며, 누락/만료/타 사용자 토큰은 거부한다.
- 대기열이 비활성화된 공연은 기존 예매 흐름을 유지한다.

## API

| Method | Path | 권한 | 설명 |
|---|---|---|---|
| POST | `/api/queues/{concertId}/join` | USER | 대기열 등록 및 현재 상태 조회 |
| GET | `/api/queues/{concertId}/me` | USER | 내 대기 순서/입장권 조회 |
| POST | `/api/admin/queues/{concertId}/activate` | ADMIN | 대기열 활성화 |
| POST | `/api/admin/queues/{concertId}/deactivate` | ADMIN | 대기열 비활성화 및 Redis 상태 제거 |

예매 진입 권한이 발급되면 응답의 `admissionToken`을 `POST /api/reservations`의 `X-Queue-Token` 헤더로 전달한다.
