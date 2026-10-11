# 평가 결과와 재현 명령

모든 수치는 로컬(macOS, Apple Silicon, Temurin 21.0.11, Gradle 9.6.1 Wrapper, Docker Desktop 29.6.1, kubectl v1.36.3, kind v0.33.0)에서 실제로 실행해 얻은 값입니다. 10월 11일 값과 10월 12일 값을 날짜로 구분했습니다. 다시 실행하면 생성 시각과 전표 id가 달라질 수 있습니다.

## 1. 테스트 (2026-10-12)

```bash
./gradlew test
```

결과: 테스트 132개(llm-drafter 브랜치), 실패 0개. 테스트는 H2(PostgreSQL 모드)로 돌며 Docker가 필요 없습니다. 10월 11일에는 100개였고, 직무 분리와 역분개와 낙관적 락과 변이 평가와 지표와 증빙 일치 규칙과 시산표 누계를 더하면서 28개가 늘었습니다.

| 테스트 클래스 | 개수 | 내용 |
|---|---|---|
| validation.rules.R01 ~ R13 (13개 클래스) | 68 | 규칙별 통과, 실패, 경계값. R05와 R10의 역분개 제외, R12의 단계와 공백과 대소문자, R13의 증빙과 부가세 구분 조합, R09의 계산서 |
| validation.BusinessNumberValidatorTest | 13 | 사업자등록번호 체크섬, 형식, 하이픈 |
| validation.ValidationEngineTest | 5 | 코드 순 정렬(R01부터 R13), 사유 전부 수집, FAIL > REVIEW > PASS |
| draft.FixtureJournalDrafterTest | 7 | fixture 로딩, 금액 채우기, 결제 계정 선택, 매칭 실패 |
| draft.OpenAiJournalDrafterTest | 8 | 모의 서버 응답 파싱, JSON 파싱 실패, 서버 오류, 프롬프트 내용, 코드 펜스 추출, 되먹임 요청 |
| draft.DraftRevisionLoopTest | 2 | 가짜 작성기로 수정 루프 상한(1 + 2회)과 조기 종료 |
| draft.DrafterSelectionTest | 1 | 키가 있을 때 OpenAI 작성기 등록과 선택 |
| api.VoucherFlowIntegrationTest | 2 | 초안 → 저장 → 승인 → 시산표, fixture 없는 설명의 R11 FAIL |
| api.ApprovalRejectionIntegrationTest | 5 | FAIL 전표 승인 409, REVIEW 전표 사유 필수, 작성자 자기 승인 R12 409, 작성자 없는 전표 400, 404와 400 |
| api.ClosedPeriodIntegrationTest | 1 | 기간 마감 뒤 승인 409와 저장 REJECTED |
| api.ReversalIntegrationTest | 5 | 역분개 차대 반전과 즉시 전기와 시산표 상쇄, POSTED만 역분개, 역분개의 R12, 마감 달 역분개 R07, 요청 검증 |
| api.ConcurrentApprovalIntegrationTest | 3 | 같은 전표 동시 승인 2건 중 200과 409 각 1건, 원장 라인 3개 (3회 반복) |
| voucher.OptimisticLockTest | 1 | 트랜잭션 A가 version 0을 읽은 사이 B가 커밋하면 A는 OptimisticLockingFailureException |
| api.HealthAndDocsIntegrationTest | 3 | readiness와 liveness, 계정 30건, OpenAPI, Prometheus 카운터 |
| api.TrialBalanceScopeIntegrationTest | 1 | 1월과 2월 전기 뒤 2월의 month 범위와 ytd 범위 비교. 기초 잔액, 당월 발생액, 누계, 잘못된 scope 400 |
| golden.GoldenSetEvaluationTest | 3 | 아래 골든셋 평가 |
| golden.MutationEvaluationTest | 3 | 아래 변이 평가 |
| LedgerGuardApplicationTests | 1 | 컨텍스트 로딩, 기본 작성기 fixture, OpenAI 빈 없음 |

## 2. 골든셋 (2026-10-12)

```bash
./gradlew test --tests 'io.github.aeginventor.ledgerguard.golden.GoldenSetEvaluationTest'
cat build/reports/golden-summary.md
```

골든셋은 `src/test/resources/golden/`의 JSON 3개입니다. 계정과목은 Flyway 시드를 그대로 쓰고, 오늘 날짜(2026-10-11), 마감 기간(2026-08), 기존 전표는 각 파일의 `setup`으로 고정합니다. 사업자등록번호는 체크섬 알고리즘으로 만든 가상 번호입니다.

| 세트 | 건수 | 결과 |
|---|---|---|
| valid_vouchers.json | 27 | PASS 27건, 거짓 양성 0건 |
| faulty_vouchers.json | 29 | expectedCodes 전부 검출 29건, 검출 코드가 기대와 정확히 일치 29건 |
| semantic_misses.json | 5 | 규칙이 못 잡아 PASS 5건 (알려진 한계) |

결함 전표 29건이 R01부터 R13까지 모든 코드를 최소 한 번씩 기대합니다. 테스트는 이 커버리지도 확인합니다. 10월 11일의 26건에 F27(작성자가 자기 전표를 승인, 승인 단계, R12), F28(면세 매입에 세금계산서, R13), F29(과세 매입에 계산서, R13)를 더했습니다.

### valid_vouchers.json (27건, 전부 PASS)

과세 매입(현금, 카드, 세금계산서 외상), 과세 매출(세금계산서 외상, 현금영수증 소매), 면세 매입과 매출, 영세율 수출, 급여와 원천징수 예수금, 임차료, 비품 구입, 기업업무추진비 2만원 증빙 없음과 5만원 카드, 통신비, 차량 주유, 기장료, 자본금, 채권 회수와 채무 지급, 복리후생비, 공급가액 999,999원과 12,345원의 원 미만 버림, 단기차입금, 이자비용, 매출원가 대체, 여비교통비, 미수금 회수, 초안 단계의 올바른 3라인 초안.

### faulty_vouchers.json (29건)

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
| F27 | 작성자가 자기 전표를 승인 (승인 단계) | R12 | R12 | FAIL |
| F28 | 면세 매입에 세금계산서 증빙 | R13 | R13 | REVIEW |
| F29 | 과세 매입에 계산서 증빙 | R13 | R13 | REVIEW |

### 규칙이 못 잡는 결함 (semantic_misses.json, 5건)

차변과 대변이 맞고, 계정코드도 유효하고, 부가세 금액과 계정도 맞지만 계정 선택이 거래의 실질과 다른 전표입니다. 현재 규칙은 전표의 형식과 금액만 보기 때문에 모두 PASS로 통과시킵니다. `GoldenSetEvaluationTest.semanticMissesStillPassUnderCurrentRules`가 PASS를 고정합니다. 이 테스트가 통과한다는 것은 한계가 그대로라는 뜻입니다.

| id | 거래 | 쓰인 계정 | 맞는 계정 | 판정 | 왜 못 잡나 |
|---|---|---|---|---|---|
| S01 | 1,800,000원 노트북 구입 | 830 소모품비 | 212 비품 | PASS | 자산과 비용의 금액 기준은 회사 정책이라 규칙으로 두지 않았다 |
| S02 | 상품 매출 입금 | 930 잡이익 | 401 매출 | PASS | 잡이익도 수익 계정이라 R05는 부가세예수금 대변을 정상으로 본다 |
| S03 | 직원 9월 급여 | 831 지급수수료 | 801 급여 | PASS | 근로자 급여와 외부 용역 수수료의 구분은 계약 관계를 알아야 한다 |
| S04 | 거래처 식사 | 811 복리후생비 | 813 기업업무추진비 | PASS | 참석자가 직원인지 거래처인지는 전표 데이터에 없다. 기업업무추진비는 매입세액 불공제라 부가세 라인도 달라진다 |
| S05 | 외상매입금 이체 | 253 미지급금 | 251 외상매입금 | PASS | 상품 매입 채무와 일반 미지급 채무의 구분은 원거래를 추적해야 한다 |

## 3. 변이 평가 (2026-10-12)

```bash
./gradlew test --tests 'io.github.aeginventor.ledgerguard.golden.MutationEvaluationTest'
cat build/reports/mutation-summary.md
```

손으로 만든 결함 전표는 규칙과 같은 사람(AI 코딩 도구)이 결함의 종류와 위치를 골라 순환 검증이 됩니다. 변이 평가는 정상 전표 27건에 변이 연산자를 기계적으로 적용해 결함 전표를 만들므로 결함의 위치를 고르지 않습니다. 연산자 자체는 같은 AI가 설계했으므로 완전히 독립적인 검증은 아니고, 두 번째 측정입니다.

결과: 결함 변이 327건 중 327건 검출(연산자 15종), 의미 변이 27건 중 27건 통과(연산자 1종). 결함 변이는 하나라도 놓치면, 의미 변이는 하나라도 잡히면 테스트가 실패합니다.

| 연산자 | 설명 | 적용 | 검출 | 통과 |
|---|---|---|---|---|
| SIDE_FLIP | 1번 라인의 차대를 뒤집는다 | 27 | 27 | 0 |
| VAT_PLUS_ONE | 부가세 라인에 1원을 더하고 반대쪽 라인도 1원 올려 차대는 맞춘다(반올림 실수) | 14 | 14 | 0 |
| VAT_LINE_REMOVED | 과세 거래에서 부가세 라인을 빼고 반대쪽 라인에서 같은 금액을 뺀다 | 14 | 14 | 0 |
| VAT_ON_NON_TAXABLE | 면세, 영세율, 부가세 대상 아님 거래에 부가세대급금 차변을 붙이고 대변 라인을 같이 올린다 | 13 | 13 | 0 |
| INACTIVE_ACCOUNT | 부가세가 아닌 첫 라인의 계정을 사용 중지된 150 제품으로 바꾼다 | 27 | 27 | 0 |
| UNKNOWN_ACCOUNT | 부가세가 아닌 첫 라인의 계정을 계정과목표에 없는 999로 바꾼다 | 27 | 27 | 0 |
| ZERO_AMOUNT | 1번 라인 금액을 0으로 | 27 | 27 | 0 |
| NEGATIVE_AMOUNT | 1번 라인 금액의 부호를 뒤집는다 | 27 | 27 | 0 |
| DECIMAL_AMOUNT | 1번 라인 금액에 0.5원을 더한다 | 27 | 27 | 0 |
| FUTURE_DATE | 전표 일자를 오늘 다음 날로 | 27 | 27 | 0 |
| CLOSED_PERIOD_DATE | 전표 일자를 마감된 달의 15일로 | 27 | 27 | 0 |
| DUPLICATE_RESUBMIT | 같은 전표를 한 번 더 올린다(기존 전표로 자기 자신을 등록) | 26 | 26 | 0 |
| ENTERTAINMENT_EVIDENCE_DROPPED | 기업업무추진비 3만원 초과 전표의 증빙을 없앤다 | 1 | 1 | 0 |
| EVIDENCE_MISMATCH | 면세 거래의 증빙을 세금계산서로, 과세 거래의 증빙을 계산서로 바꾼다 | 16 | 16 | 0 |
| SELF_APPROVAL | 작성자가 자기 전표를 승인한다(승인 단계) | 27 | 27 | 0 |
| SAME_TYPE_ACCOUNT_SWAP | 부가세가 아닌 첫 라인의 계정을 같은 유형의 다른 활성 계정으로 바꾼다(의미 변이) | 27 | - | 27 |

의미 변이가 전부 통과한다는 것은, 노트북 구입(212 비품)을 101 현금으로 적거나 급여(801)를 811 복리후생비로 적어도 형식 규칙은 모른다는 뜻입니다. 변이별 원래 계정과 바뀐 계정은 `build/reports/mutation-summary.md`의 "의미 변이 상세" 표에 있습니다. 이것이 사람 승인 단계를 남긴 이유입니다.

## 4. Docker compose 실측

### 2026-10-11

```bash
docker compose up --build -d
docker compose ps
bash scripts/smoke.sh
docker compose down -v
```

- `docker compose ps`: `ledger-guard-db-1` Up (healthy), `ledger-guard-app-1` Up (healthy)
- DB: PostgreSQL 16.15 (postgres:16-alpine). `flyway_schema_history`에 V1 schema, V2 seed accounts 2건 success. `account` 30건
- smoke.sh 흐름: readiness UP, 초안 PASS, 저장 id 1 DRAFT, 승인 POSTED, 차대 불일치 전표 id 2 REJECTED와 승인 409, 시산표 총차변 110,000원 총대변 110,000원 balanced true
- `ledger_entry` 3건. REJECTED 전표는 전기되지 않았다

### 2026-10-12 (숫자 UID 이미지, V3 마이그레이션, 역분개와 직무 분리)

같은 명령으로 다시 띄운 뒤 smoke.sh에 이어 역분개와 자기 승인을 curl로 보냈습니다.

- `docker compose ps`: db healthy, app 기동 뒤 healthy. `flyway_schema_history`에 V1, V2, V3 세 건 success
- smoke.sh: 10월 11일과 같은 흐름이 같은 결과. 초안 PASS, id 1 DRAFT, 승인 POSTED, id 2 REJECTED와 승인 409, 시산표 110,000원 대 110,000원
- `POST /api/vouchers/1/reverse`(작성자 김작성, 승인자 박승인, 사유 있음) → 201, id 3 POSTED, 설명 "역분개: 사무용품 카드 구입", 차대 반전. 원전표 1은 REVERSED, reversedById 3
- 역분개 뒤 시산표: 총차변 220,000원, 총대변 220,000원, balanced true, 135와 253과 830의 잔액 모두 0
- `POST /api/vouchers/2/approve`를 작성자와 같은 이름(김작성)으로 → 409 (id 2는 R01로도 FAIL이므로 두 사유가 함께 나온다)
- `ledger_entry` 6건(id 1의 3줄과 역분개 id 3의 3줄). REJECTED 전표는 전기되지 않았다

## 5. 쿠버네티스 (2026-10-12, kind 실제 배포)

10월 11일에는 `kubectl apply --dry-run=client --validate=false -f k8s/`만 통과시켰습니다. 10월 12일에 kind v0.33.0(노드 이미지 kindest/node:v1.37.0)으로 로컬 클러스터를 만들어 실제로 배포했습니다. 세 번 돌렸고 앞의 두 번은 dry-run이 잡지 못한 문제를 드러냈습니다.

```bash
kind create cluster --name ledger-guard
docker build -t ledger-guard:0.2.0 .
kind load docker-image ledger-guard:0.2.0 --name ledger-guard
kubectl --context kind-ledger-guard apply -f k8s/secret.example.yaml   # 로컬 전용. 실제 값은 따로 만든다
kubectl --context kind-ledger-guard apply -f k8s/configmap.yaml -f k8s/postgres.yaml -f k8s/app-service.yaml -f k8s/app-deployment.yaml
kubectl --context kind-ledger-guard rollout status deployment/ledger-guard
kubectl --context kind-ledger-guard port-forward svc/ledger-guard 18080:80 &
BASE=http://localhost:18080 bash scripts/smoke.sh
kind delete cluster --name ledger-guard
```

| 실행 | 결과 | 원인과 조치 |
|---|---|---|
| 1차 | 앱 Pod가 `CreateContainerConfigError`로 4분 동안 뜨지 못함 | 매니페스트의 `runAsNonRoot: true`는 이미지의 `USER`가 이름(`app`)이면 비루트인지 검증하지 못한다. Dockerfile에서 UID와 GID를 10001로 고정하고 `USER 10001:10001`로 바꿨다. 매니페스트에도 `runAsUser`와 `runAsGroup`을 적었다 |
| 2차 | 앱이 뜨긴 했지만 재시작 2회 | 앱이 DB StatefulSet보다 먼저 떠서 Flyway가 연결에 실패해 종료됐다(`Unable to obtain connection from database`). compose의 `depends_on: condition: service_healthy`에 해당하는 것이 쿠버네티스에는 없으므로 DB 포트를 기다리는 초기화 컨테이너(busybox `nc -z ledger-guard-db 5432`)를 넣었다 |
| 3차 | 전부 통과 | 아래 수치 |

3차 실측값:

| 항목 | 값 |
|---|---|
| 클러스터 생성 | 24초 |
| DB StatefulSet Ready | 적용 뒤 15초 |
| 앱 Deployment Ready | DB Ready 뒤 23초. 초기화 컨테이너 Completed, 앱 컨테이너 재시작 0회, Spring Boot 기동 4.9초 |
| 실행 사용자 | `uid=10001(app) gid=10001(app)` |
| Flyway | V1, V2, V3 세 개 적용 |
| smoke | readiness UP, 초안 PASS, 저장 id 1 DRAFT, 승인 POSTED, 차대 불일치 id 2 REJECTED와 승인 409, 시산표 110,000원 대 110,000원 balanced true |
| 프로브 | readiness `/actuator/health/readiness` 20초 뒤 10초마다, liveness `/actuator/health/liveness` 40초 뒤 15초마다. 둘 다 UP |
| Prometheus | `/actuator/prometheus`에 `ledger_guard_verdicts_total`과 `ledger_guard_findings_total`이 단계별로 찍힘 |
| 롤링 업데이트 | 이미지 태그만 바꿔 `kubectl set image`. 21초. 새 Pod가 Ready가 된 뒤 옛 Pod가 Terminating으로 넘어갔다(replicas 1, 기본 maxSurge 25%) |
| 리소스 | requests cpu 250m memory 512Mi, limits cpu 1 memory 1Gi. metrics-server가 없어 실제 사용량은 재지 않았다 |

클러스터는 실측 뒤 삭제했고 kubectl 기본 컨텍스트는 원래대로 되돌렸습니다. 운영 클러스터에는 배포하지 않았습니다.

## 6. CI

`.github/workflows/ci.yml`은 push와 PR마다 `./gradlew test`(H2)와 `docker build`를 실행하고, 테스트 리포트와 골든셋 요약과 변이 요약을 업로드합니다. 10월 12일부터 main에 push될 때 이미지를 `ghcr.io/aeginventor/ledger-guard:latest`와 커밋 SHA 태그로 올립니다. GHCR 패키지의 공개 여부는 저장소 설정에서 작성자가 정합니다. 실행 목록은 [GitHub Actions](https://github.com/aeginventor/ledger-guard/actions)에 있습니다.

## 7. 하지 않은 것

- 실제 OpenAI API 호출. `OpenAiJournalDrafter`는 모의 서버 테스트로만 확인했다
- 로컬 Homebrew PostgreSQL 17에 대한 `./gradlew bootRun`. compose와 kind의 PostgreSQL 16으로만 확인했다
- 운영 클러스터 배포. kind는 노드 하나짜리 로컬 클러스터다
- 부하와 성능 측정. 동시성은 승인 2건 충돌까지만 확인했다
- 세법 조문의 최종 확인. 조회 메모는 [legal-basis.md](legal-basis.md)에 있고 "확인 필요" 표시는 작성자가 닫는다
