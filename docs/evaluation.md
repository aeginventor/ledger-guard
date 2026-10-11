# 골든셋 평가 결과와 재현 명령

모든 수치는 2026년 10월 11일 로컬(macOS, Apple Silicon, Temurin 21.0.11, Gradle 9.6.1 Wrapper, Docker Desktop 29.6.1, kubectl v1.36.3)에서 실제로 실행해 얻은 값입니다. 다시 실행하면 생성 시각과 전표 id가 달라질 수 있습니다.

## 1. 테스트

```bash
./gradlew test
```

결과: 테스트 100개, 실패 0개. 테스트는 H2(PostgreSQL 모드)로 돌며 Docker가 필요 없습니다.

| 테스트 클래스 | 개수 | 내용 |
|---|---|---|
| validation.rules.R01 ~ R11 (11개 클래스) | 56 | 규칙별 통과, 실패, 경계값 |
| validation.BusinessNumberValidatorTest | 13 | 사업자등록번호 체크섬, 형식, 하이픈 |
| validation.ValidationEngineTest | 5 | 코드 순 정렬, 사유 전부 수집, FAIL > REVIEW > PASS |
| draft.FixtureJournalDrafterTest | 7 | fixture 로딩, 금액 채우기, 결제 계정 선택, 매칭 실패 |
| draft.OpenAiJournalDrafterTest | 6 | 모의 서버 응답 파싱, JSON 파싱 실패, 서버 오류, 프롬프트 내용 |
| draft.DrafterSelectionTest | 1 | 키가 있을 때 OpenAI 작성기 등록과 선택 |
| api.VoucherFlowIntegrationTest | 2 | 초안 → 저장 → 승인 → 시산표, fixture 없는 설명의 R11 FAIL |
| api.ApprovalRejectionIntegrationTest | 3 | FAIL 전표 승인 409, REVIEW 전표 사유 필수, 404와 400 |
| api.ClosedPeriodIntegrationTest | 1 | 기간 마감 뒤 승인 409와 저장 REJECTED |
| api.HealthAndDocsIntegrationTest | 2 | readiness와 liveness, 계정 30건, OpenAPI |
| golden.GoldenSetEvaluationTest | 3 | 아래 골든셋 평가 |
| LedgerGuardApplicationTests | 1 | 컨텍스트 로딩, 기본 작성기 fixture, OpenAI 빈 없음 |

## 2. 골든셋

```bash
./gradlew test --tests 'io.github.aeginventor.ledgerguard.golden.GoldenSetEvaluationTest'
cat build/reports/golden-summary.md
```

골든셋은 `src/test/resources/golden/`의 JSON 3개입니다. 계정과목은 Flyway 시드를 그대로 쓰고, 오늘 날짜(2026-10-11), 마감 기간(2026-08), 기존 전표는 각 파일의 `setup`으로 고정합니다. 사업자등록번호는 체크섬 알고리즘으로 만든 가상 번호입니다.

| 세트 | 건수 | 결과 |
|---|---|---|
| valid_vouchers.json | 27 | PASS 27건, 거짓 양성 0건 |
| faulty_vouchers.json | 26 | expectedCodes 전부 검출 26건, 검출 코드가 기대와 정확히 일치 26건 |
| semantic_misses.json | 5 | 규칙이 못 잡아 PASS 5건 (알려진 한계) |

결함 전표 26건이 R01부터 R11까지 모든 코드를 최소 한 번씩 기대합니다. 테스트는 이 커버리지도 확인합니다.

### valid_vouchers.json (27건, 전부 PASS)

과세 매입(현금, 카드, 세금계산서 외상), 과세 매출(세금계산서 외상, 현금영수증 소매), 면세 매입과 매출, 영세율 수출, 급여와 원천징수 예수금, 임차료, 비품 구입, 기업업무추진비 2만원 증빙 없음과 5만원 카드, 통신비, 차량 주유, 기장료, 자본금, 채권 회수와 채무 지급, 복리후생비, 공급가액 999,999원과 12,345원의 원 미만 버림, 단기차입금, 이자비용, 매출원가 대체, 여비교통비, 미수금 회수, 초안 단계의 올바른 3라인 초안.

### faulty_vouchers.json (26건)

| id | 결함 | 기대 코드 | 검출 코드 | 판정 |
|---|---|---|---|---|
| F01 | 면세 거래에 부가세 라인 | R04 | R04 | FAIL |
| F02 | 부가세 반올림 (버림 아님) | R04 | R04 | FAIL |
| F03 | 차대 불일치 | R01 | R01 | FAIL |
| F04 | 계정과목표에 없는 계정코드 | R02 | R02 | FAIL |
| F05 | 사용 중지된 계정 150 제품 | R02 | R02 | FAIL |
| F06 | 세금계산서인데 사업자번호 체크섬 오류 | R06 | R06 | FAIL |
| F07 | 세금계산서인데 사업자번호 없음 | R06 | R06 | FAIL |
| F08 | 과세 세금계산서인데 세액 라인 미분리 | R04, R06 | R04, R06 | FAIL |
| F09 | 마감된 기간의 일자 | R07 | R07 | FAIL |
| F10 | 미래 일자 | R08 | R08 | FAIL |
| F11 | 기업업무추진비 5만원 현금 증빙 없음 | R09 | R09 | REVIEW |
| F12 | 같은 일자 거래처 총액의 전표가 이미 있음 | R10 | R10 | REVIEW |
| F13 | 라인 1개짜리 초안 | R01, R11 | R01, R11 | FAIL |
| F14 | 금액 0 | R03 | R03 | FAIL |
| F15 | 음수 금액 | R03 | R03 | FAIL |
| F16 | 소수 금액 | R03 | R03 | FAIL |
| F17 | 매입 부가세를 부가세예수금으로 | R05 | R05 | FAIL |
| F18 | 매출 부가세를 부가세대급금 차변으로 | R05 | R05 | FAIL |
| F19 | 영세율 매출에 부가세 라인 | R04 | R04 | FAIL |
| F20 | 급여(부가세 대상 아님)에 부가세 라인 | R04 | R04 | FAIL |
| F21 | 과세 매입인데 부가세 라인 없음 | R04 | R04 | FAIL |
| F22 | 초안 JSON 파싱 실패 | R11 | R11 | FAIL |
| F23 | 초안 라인에 필수 필드 누락 | R01, R02, R03, R11 | R01, R02, R03, R11 | FAIL |
| F24 | 미래 일자와 차대 불일치와 없는 계정 | R01, R02, R08 | R01, R02, R08 | FAIL |
| F25 | 세금계산서 사업자번호 9자리 | R06 | R06 | FAIL |
| F26 | 면세 매입에 부가세예수금 차변 | R04, R05 | R04, R05 | FAIL |

## 3. 규칙이 못 잡는 결함

차변과 대변이 맞고, 계정코드도 유효하고, 부가세 금액과 계정도 맞지만 계정 선택이 거래의 실질과 다른 전표입니다. 현재 규칙 11개는 전표의 형식과 금액만 보기 때문에 모두 PASS로 통과시킵니다. `semantic_misses.json`에 5건을 두고 `GoldenSetEvaluationTest.semanticMissesStillPassUnderCurrentRules`가 PASS를 고정합니다. 이 테스트가 통과한다는 것은 한계가 그대로라는 뜻입니다. 의미를 보는 규칙을 추가하면 이 테스트를 고쳐야 합니다.

| id | 거래 | 쓰인 계정 | 맞는 계정 | 판정 | 왜 못 잡나 |
|---|---|---|---|---|---|
| S01 | 1,800,000원 노트북 구입 | 830 소모품비 | 212 비품 | PASS | 자산과 비용의 금액 기준은 회사 정책이라 규칙으로 두지 않았다 |
| S02 | 상품 매출 입금 | 930 잡이익 | 401 매출 | PASS | 잡이익도 수익 계정이라 R05는 부가세예수금 대변을 정상으로 본다 |
| S03 | 직원 9월 급여 | 831 지급수수료 | 801 급여 | PASS | 근로자 급여와 외부 용역 수수료의 구분은 계약 관계를 알아야 한다 |
| S04 | 거래처 식사 | 811 복리후생비 | 813 기업업무추진비 | PASS | 참석자가 직원인지 거래처인지는 전표 데이터에 없다. 기업업무추진비는 매입세액 불공제라 부가세 라인도 달라진다 |
| S05 | 외상매입금 이체 | 253 미지급금 | 251 외상매입금 | PASS | 상품 매입 채무와 일반 미지급 채무의 구분은 원거래를 추적해야 한다 |

이 결함을 잡으려면 거래 설명과 계정의 의미를 대조하는 별도 단계(예: 설명 키워드와 계정 유형의 대응표, 또는 LLM에 계정 선택 근거를 묻고 사람이 검토)가 필요합니다. 이번 작업에서는 만들지 않았습니다.

## 4. Docker compose 실측

```bash
docker compose up --build -d
docker compose ps
bash scripts/smoke.sh
docker compose down -v
```

결과(2026-10-11 16:14 KST):

- `docker compose ps`: `ledger-guard-db-1` Up (healthy), `ledger-guard-app-1` Up (healthy)
- DB: PostgreSQL 16.15 (postgres:16-alpine). `flyway_schema_history`에 V1 schema, V2 seed accounts 2건 success. `account` 30건
- 이미지 `ledger-guard:local` 605MB (eclipse-temurin:21-jre 기반, curl 포함)
- smoke.sh 흐름
  1. `GET /actuator/health/readiness` → `{"status":"UP"}`
  2. `POST /api/vouchers/draft` 사무용품 카드 구입, 공급가액 100,000원 → `drafter: fixture`, 라인 3개(830 소모품비 차변 100,000, 135 부가세대급금 차변 10,000, 253 미지급금 대변 110,000), `validation.overall: PASS`
  3. `POST /api/vouchers` 초안 라인 그대로 → 201, id 1, `status: DRAFT`
  4. `POST /api/vouchers/1/approve` → 200, `status: POSTED`, `approvedAt` 기록
  5. 차대 불일치 전표 `POST /api/vouchers` → 201, id 2, `status: REJECTED`, R01 사유. `POST /api/vouchers/2/approve` → HTTP 409, 본문에 R01 재검증 결과
  6. `GET /api/reports/trial-balance?month=2026-10` → 135 차변 10,000, 253 대변 110,000, 830 차변 100,000, `totalDebit` 110,000, `totalCredit` 110,000, `balanced: true`
- `ledger_entry` 3건 (id 1 전표의 라인 3개). REJECTED 전표는 전기되지 않았다

## 5. Kubernetes 매니페스트

```bash
kubectl apply --dry-run=client --validate=false -f k8s/
```

결과: `deployment.apps/ledger-guard`, `service/ledger-guard`, `configmap/ledger-guard-config`, `service/ledger-guard-db`, `statefulset.apps/ledger-guard-db`, `secret/ledger-guard-secret` 6개가 `created (dry run)`. 연결된 클러스터가 없어 서버 측 검증과 실제 배포는 하지 않았습니다.

## 6. CI

`.github/workflows/ci.yml`은 push와 PR마다 `./gradlew test`(H2)와 `docker build`를 실행합니다. 첫 실행은 Linux의 나노초 시각이 DB를 거치며 잘려 승인 전후 응답이 달라지는 문제로 실패했고, 시각을 밀리초로 자르는 수정 뒤 통과했습니다. 실행 목록은 [GitHub Actions](https://github.com/aeginventor/ledger-guard/actions)에서 볼 수 있습니다.

## 7. 하지 않은 것

- 실제 OpenAI API 호출. `OpenAiJournalDrafter`는 모의 서버 테스트로만 확인했다
- 로컬 Homebrew PostgreSQL 17에 대한 `./gradlew bootRun`. compose의 PostgreSQL 16으로만 확인했다
- 실제 K8s 클러스터 배포
- 부하와 성능 측정
