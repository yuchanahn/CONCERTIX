# CATALOG-001 실행 기록

## 목표

CATALOG-001.md 계약의 관리자 공연·회차 API 및 공개 조회를 구현한다. 독립 리뷰와 실제 HTTP 통합 테스트까지 완료 기준으로 사용한다.

## 모델 배정 및 판단

- Anscombe / chatgpt-web/medium: 공연 스캐폴딩을 작성했으나 회차 저장, 수정·삭제·상세, 페이지, 새 테스트를 구현하지 않고 제출했다. 기존 4테스트 통과는 새 기능의 증거가 아니므로 Supervisor가 반려했다.
- Banach / chatgpt-web/high: API 일부를 추가했으나 회차·검증·새 테스트가 빠진 상태로 제출해 반려했다.
- 사용자 지시로 Astra 워커는 이후 전면 금지. 예고했던 Astra 구현 워커는 생성하지 않았다.
- Hume / gpt-5.5: 남은 계약과 HTTP 테스트 구현 완료. 실제 XML에서 8개(신규 공연4+기존4) 성공 확인. 새 테스트 첫 실행 실패 후 워커가 자체 수정하여 성공했다.
- Hubble / gpt-5.5 high, 새 컨텍스트: sessions:[null]가 NPE/500을 일으키는 검증 누락을 발견.
- Hume / gpt-5.5: 원소 NotNull 추가 및 POST/PUT 400, 실패 PUT의 기존 데이터 보존 회귀 테스트 추가. 총9개 성공.
- Wegener / gpt-5.5 medium, 새 컨텍스트: 중요 결함 없음. 수정 검증과 XML9개 성공 확인.
- 이 사례는 medium의 이번 작업 수용 기준 미충족을 보여준다. 한 사례만으로 모델 일반 성능이나 비용 효율을 단정하지 않는다.

## 검증 계획

- 기존 V1/인증 코드/TestInfrastructure 보존 및 변경 범위 확인.
- Flyway V2 + JPA validate + 실제 HTTP CRUD/인가/입력 오류 통합 테스트.
- 공개 후 수정·삭제 방지와 회차 교체 정합성 독립 리뷰.
- 개발 DB는 V1 상태 유지, 테스트는 임시 DB에서 V2를 적용한다.
- 기준점: .harness/CATALOG-001-baseline. 실행 증거: .harness/CATALOG-001-evidence.

## 현재 상태

DONE. 전체 9테스트 통과, 새 컨텍스트 최종 독립 리뷰 통과, 개발 데이터 보존 및 변경 범위 확인 후 Supervisor 승인.

## 실행 증거와 한계

- `.harness/CATALOG-001-evidence/final-results` XML: 공연5+기존4=9개, 실패/오류/skip 0.
- 실제 HTTP: 관리자 CRUD/공개, 비인증401, USER403, 초안404, 공개 후409, 입력 오류400, 페이지와 회차 교체 검증.
- 개발 PostgreSQL members 1행의 digest `dfd95f6a344957f83e6f73dd2e773df1`, Redis DB0 1키의 digest `1a69b91fe7352ba3a4c0ea642ca14b126419c3ac` 유지(TTL 제외).
- 테스트 종료 후 기존 개발 컨테이너만 남음. 운영 소스 기준 변경은 concert 신규 코드와 SecurityConfig이며 기존 인증/V1/테스트 인프라는 보존.
- 공개/수정/삭제 경쟁을 잠금으로 제어하지만 실제 경쟁 부하 실험은 이번 테스트에 포함하지 않음. 동시성 성능 검증은 후속 단계.
- 관리자 UI, 운영 관리자 최초 발급, 이미지 업로드, 좌석 생성은 후속 범위. 가격/토큰 비용은 수집하지 않아 비용 절감률을 주장하지 않음.
