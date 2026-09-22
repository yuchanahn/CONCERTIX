# TEST-001: 테스트 데이터 격리와 첫 오케스트레이션 실행

## 목표와 변경 기준

기존 인증 테스트는 기본 application.properties로 개발 PostgreSQL/Redis에 연결한 후 회원과 토큰을 삭제했다. 기존 세 통합 테스트를 테스트 JVM 전용 PostgreSQL 17/Redis 7에 연결하도록 변경한다.

Git 저장소가 없어 `.harness/TEST-001-baseline`에 원본 소스·빌드·Compose 및 SHA256 manifest를 저장했다. 이 로컬 스냅샷은 Git 이력을 대체하는 장기 운영 방식은 아니다.

## 역할과 실제 진행

1. Supervisor: 작업 범위와 수용 기준, 스냅샷, 개발 데이터 해시 확보.
2. Lagrange (`chatgpt-web/light`): 공통 TestInfrastructure와 기존 테스트 상속, 의존성, 실행 문서 작성.
3. Supervisor: Boot BOM과 다른 의존성 버전 발견, 수정 요청.
4. Fermat (`chatgpt-web/light`): 의존성/컨테이너 lifecycle 수정. 이 과정에서 필요한 SpringBootTest 설정을 제거해 컴파일 실패.
5. Supervisor: 세 줄의 Spring 설정 통합 복원. 예외 수정으로 기록하고 독립 리뷰 대상에 포함.
6. Supervisor: 전체 테스트와 잘못된 DB_URL/REDIS_PORT 환경변수에서 재실행, 개발 데이터 해시 비교.
7. Harvey (`gpt-6-astra`, medium, 새 컨텍스트): Redis URL 우선순위로 인한 개발 Redis 우회 가능성을 P1으로 지적. 수정 채택.
8. Turing (`chatgpt-web/light`, 새 컨텍스트): Redis URL도 테스트 컨테이너 주소로 고정.
9. Supervisor: 잘못된 SPRING_DATA_REDIS_URL을 포함한 환경에서 전체 테스트 재실행 성공, 개발 데이터 해시 일치 확인.
10. Gauss (`gpt-6-astra`, medium, 새 컨텍스트): 최종 독립 리뷰에서 재현 가능한 중요 결함 없음. 기존 4테스트 유지 및 임시 PostgreSQL/Flyway 실행 확인.
11. Supervisor: 테스트/XML, 개발 데이터 해시, 컨테이너 정리와 리뷰 결과를 종합하여 DONE 승인.

두 light 워커는 최초 파일 수정에 성공했으나 send_input 후속 요청에서 실행 도구가 없다고 보고했다. 원인은 확인되지 않았으며 새 워커로 재할당했다. 이 현상을 모든 워커의 일반적인 제약으로 단정하지 않는다.

## 검증 증거

- 최초 컴파일 실패 후 수정하여 전체 4테스트 성공, 실패/오류/skip 0.
- 잘못된 DB_URL, DB_USERNAME, DB_PASSWORD, REDIS_HOST, REDIS_PORT를 주입한 실행도 성공.
- 개발 PostgreSQL members 1행, digest `dfd95f6a344957f83e6f73dd2e773df1` 보존.
- 개발 Redis DB0 1키, digest `1a69b91fe7352ba3a4c0ea642ca14b126419c3ac` 보존(TTL 제외).
- 임시 컨테이너 정리 후 기존 개발 컨테이너 두 개만 남는 것 확인.
- 생산 소스 및 Compose는 스냅샷과 해시 일치.
- 로그/XML: `.harness/TEST-001-evidence/`. 로컬 생성 증거로 Git 제외.

## 미검증 및 한계

- 최종 수정 후 전체 4테스트 성공, 실패/오류/skip 0. 새 컨텍스트 최종 리뷰 통과. 검토 범위는 테스트 인프라이며 인증 구현 전체를 승인한 것은 아니다.
- 실제 IDE에서 클릭 실행, Docker 데몬 중지 상황은 별도 실행하지 않았다.
- 모델별 가격·토큰 비용은 수집되지 않았으므로 비용 절감률을 주장하지 않는다.
- 최초 테스트 통과 후에도 독립 리뷰에서 중요한 설정 결함이 발견됐다. 테스트 성공과 리뷰 완료를 별도로 관리한다.
