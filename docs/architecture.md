# 계층 구조와 요청 흐름

Spring Boot 3.5, Java 21, Spring Data JPA, Flyway, PostgreSQL 16(테스트는 H2 PostgreSQL 모드)입니다. 패키지는 기능 단위로 나눴고 규칙 엔진은 JPA에 의존하지 않습니다.

```
io.github.aeginventor.ledgerguard
├── account      계정과목 엔티티, 리포지토리, 조회 API
├── voucher      전표, 라인, 검증 사유 엔티티와 저장, 승인 서비스와 API
├── validation   VoucherData, ValidationContext, ValidationRule, ValidationEngine, ValidationMetrics, 사업자등록번호 체크섬
│   └── rules    R01 ~ R13 독립 규칙 클래스
├── draft        JournalDrafter 인터페이스, FixtureJournalDrafter, OpenAiJournalDrafter, DraftService
├── period       회계기간 마감
├── ledger       원장 엔티티와 집계 쿼리
├── report       시산표 서비스와 API
└── common       예외 처리, Clock, OpenAPI 설정
```

## 요청 흐름

```mermaid
sequenceDiagram
    participant C as 클라이언트
    participant VC as VoucherController
    participant DS as DraftService
    participant JD as JournalDrafter
    participant VE as ValidationEngine
    participant VS as VoucherService
    participant DB as PostgreSQL

    C->>VC: POST /api/vouchers/draft
    VC->>DS: draft(request)
    DS->>JD: draft(request, accounts)
    JD-->>DS: DraftResult(lines, problems)
    DS->>VE: evaluate(context, stage=DRAFT)
    VE-->>DS: ValidationResult
    DS-->>C: {draft, drafter, validation} (저장 없음)

    C->>VC: POST /api/vouchers (초안 라인 포함)
    VC->>VS: create(request)
    VS->>VE: evaluate(context, stage=SAVE)
    VE-->>VS: ValidationResult
    VS->>DB: voucher, voucher_line, validation_finding 저장
    VS-->>C: 201 {status: DRAFT | REVIEW | REJECTED, validation}

    C->>VC: POST /api/vouchers/{id}/approve
    VC->>VS: approve(id, approver, reason)
    VS->>VE: evaluate(stage=APPROVE, 자기 자신 제외, R12 포함)
    alt FAIL 또는 REVIEW인데 사유 없음
        VS-->>C: 409 (전표 변경 없음)
    else PASS 또는 REVIEW와 사유
        VS->>DB: status=POSTED, version+1, ledger_entry 생성
        VS-->>C: 200 {status: POSTED}
    end
    Note over VS,DB: 같은 전표를 동시에 승인하면 version 충돌로 늦은 쪽이 409

    C->>VC: POST /api/vouchers/{id}/reverse
    VC->>VS: reverse(id, preparer, approver, reason, date)
    VS->>VE: evaluate(역분개 전표, stage=SAVE, R05와 R10 제외)
    VS->>VE: evaluate(역분개 전표, stage=APPROVE)
    alt 어느 단계든 FAIL
        VS-->>C: 409 (아무것도 저장 안 함)
    else
        VS->>DB: 역분개 전표 POSTED, ledger_entry 생성, 원전표 REVERSED
        VS-->>C: 201 {status: POSTED, reversalOfId}
    end

    C->>VC: GET /api/reports/trial-balance?month=
    VC->>DB: ledger_entry를 계정과 차대로 집계
    DB-->>C: rows, totalDebit, totalCredit, balanced
```

## 규칙 엔진

`ValidationEngine`은 Spring이 모은 `ValidationRule` 빈을 코드 순으로 정렬해 모두 실행합니다. 규칙은 `ValidationContext`만 보고 판정하며, 컨텍스트의 외부 조회(계정과목, 기간 마감, 중복)는 함수로 주입됩니다. 그래서 규칙 단위 테스트는 DB 없이 돌고, 골든셋 평가는 Flyway 시드 계정과 JSON의 setup(오늘 날짜, 마감 기간, 기존 전표)으로 환경을 고정합니다.

`ValidationContextFactory`가 실제 서비스에서 리포지토리와 `Clock`을 감싸 컨텍스트를 만듭니다. 승인 단계에는 승인자 이름이 컨텍스트에 들어가 R12가 작성자와 비교합니다. 판정 결과는 `ValidationMetrics`가 Prometheus 카운터로 남깁니다.

## 테이블

```mermaid
erDiagram
    account {
        varchar code PK
        varchar name
        varchar type "ASSET LIABILITY EQUITY REVENUE EXPENSE"
        varchar normal_side "DEBIT CREDIT"
        boolean active
        boolean vat_related
    }
    voucher {
        bigint id PK
        date voucher_date
        varchar description
        varchar counterparty_name
        varchar counterparty_biz_no
        varchar evidence_type "TAX_INVOICE INVOICE CARD CASH_RECEIPT NONE"
        varchar vat_type "TAXABLE EXEMPT ZERO_RATED NA"
        bigint supply_amount
        varchar status "DRAFT REVIEW REJECTED APPROVED POSTED REVERSED"
        varchar drafter "FIXTURE OPENAI MANUAL"
        varchar validation_overall "PASS REVIEW FAIL"
        varchar preparer
        varchar approver
        varchar approval_reason
        timestamp created_at
        timestamp approved_at
        bigint reversal_of_id FK "역분개 전표이면 원전표"
        bigint reversed_by_id FK "역분개된 전표이면 역분개 전표"
        bigint version "낙관적 락"
    }
    voucher_line {
        bigint id PK
        bigint voucher_id FK
        int line_no
        varchar account_code
        varchar side
        bigint amount
        varchar memo
    }
    validation_finding {
        bigint id PK
        bigint voucher_id FK
        varchar rule_code
        varchar severity "FAIL REVIEW INFO"
        varchar message
        varchar basis
    }
    accounting_period {
        varchar yyyymm PK
        boolean closed
        timestamp closed_at
    }
    ledger_entry {
        bigint id PK
        bigint voucher_id FK
        int line_no
        varchar account_code FK
        varchar side
        bigint amount
        date entry_date
        timestamp posted_at
    }

    voucher ||--|{ voucher_line : "has"
    voucher |o--o| voucher : "reversal_of / reversed_by"
    voucher ||--o{ validation_finding : "has"
    voucher ||--o{ ledger_entry : "posted as"
    account ||--o{ ledger_entry : "aggregated into"
```

`voucher_line.account_code`는 일부러 외래키를 걸지 않았습니다. 없는 계정코드가 들어온 전표도 REJECTED로 저장해 사유를 남겨야 하기 때문입니다. `ledger_entry.account_code`는 승인된 전표만 들어가므로 외래키를 겁니다.

## 배포 구성

- `Dockerfile`: 1단계 `eclipse-temurin:21-jdk`에서 Gradle Wrapper로 bootJar 생성(테스트 제외), 2단계 `eclipse-temurin:21-jre`에 jar와 curl만 복사. 비루트 사용자로 실행
- `docker-compose.yml`: `postgres:16-alpine`과 앱. DB는 `pg_isready` healthcheck, 앱은 DB가 healthy가 된 뒤 시작하고 `/actuator/health/readiness`로 healthcheck
- `k8s/`: ConfigMap(접속 URL, 작성기, 시간대), Secret 예시, PostgreSQL StatefulSet과 headless Service, 앱 Deployment(readiness와 liveness 프로브, 리소스 요청과 제한, 비루트 UID 10001), ClusterIP Service. kind 클러스터 실측 결과는 [evaluation.md](evaluation.md)
