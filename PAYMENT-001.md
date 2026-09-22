# PAYMENT-001: 가상 결제 및 예매 확정

## 목표

선점된 예매에 대해 가상 결제를 생성하고 성공/실패/취소 결과를 처리한다. 결제 성공 시에만 예매와 좌석을 최종 확정한다.

## 상태 전이

- Payment: `PENDING -> SUCCESS | FAILED | CANCELED`
- 성공: `Reservation.PENDING_PAYMENT -> CONFIRMED`, `Seat.HELD -> RESERVED`
- 실패: `Reservation.PENDING_PAYMENT -> CANCELED`, `Seat.HELD -> AVAILABLE`
- 결제/예매 취소: `Reservation -> CANCELED`, `Payment -> CANCELED`, `Seat -> AVAILABLE`
- 선점 시간이 지난 결제의 성공 처리는 `409 Conflict`로 거부한다.

## API

| Method | Path | 설명 |
|---|---|---|
| POST | `/api/payments` | 본인 예매의 결제 생성 |
| POST | `/api/payments/{paymentId}/complete` | `SUCCESS` 또는 `FAILED` 결과 처리 |
| POST | `/api/payments/{paymentId}/cancel` | 결제 취소 |
| GET | `/api/payments/{paymentId}` | 본인 결제 조회 |

같은 Reservation에 대한 결제 생성은 기존 Payment를 반환한다. 이미 처리된 Payment에 동일한 완료 결과를 다시 보내면 기존 결과를 반환하며, 반대 결과로 변경하려는 요청은 409로 거부한다.

## 초기 재시도 정책

결제가 `FAILED`가 되면 해당 Reservation은 종료되고 좌석을 반환한다. 결제를 다시 시도하려면 좌석을 새로 선점하여 새로운 Reservation을 생성한다.
