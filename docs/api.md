# API 예시

기본 주소는 `http://localhost:8080`입니다. Swagger UI는 `/swagger-ui.html`, OpenAPI JSON은 `/v3/api-docs`입니다. 아래 응답은 2026년 10월 11일 `docker compose up --build -d` 뒤 `scripts/smoke.sh`로 실제 받은 값입니다. 날짜와 시각은 실행 시점에 따라 다릅니다.

## 계정과목

```bash
curl -s http://localhost:8080/api/accounts | head -c 300
```

```json
[{"code":"101","name":"현금","type":"ASSET","normalSide":"DEBIT","active":true,"vatRelated":false}, ...]
```

## 초안 생성 (저장하지 않음)

```bash
curl -s -X POST http://localhost:8080/api/vouchers/draft \
  -H 'Content-Type: application/json' \
  -d '{"description":"사무용품 카드 구입","voucherDate":"2026-10-11","counterpartyName":"가상문구",
       "evidenceType":"CARD","vatType":"TAXABLE","supplyAmount":100000}'
```

```json
{"draft":{"lines":[
  {"lineNo":1,"accountCode":"830","accountName":"소모품비","side":"DEBIT","amount":100000,"memo":"소모품비"},
  {"lineNo":2,"accountCode":"135","accountName":"부가세대급금","side":"DEBIT","amount":10000,"memo":"매입세액"},
  {"lineNo":3,"accountCode":"253","accountName":"미지급금","side":"CREDIT","amount":110000,"memo":"결제"}]},
 "drafter":"fixture","note":"fixture supplies 적용. 실제 LLM 호출 아님",
 "validation":{"overall":"PASS","findings":[]}}
```

거래 설명이 어느 fixture 키워드와도 맞지 않으면 라인이 비고 R11 FAIL이 돌아옵니다.

## 전표 저장

```bash
curl -s -X POST http://localhost:8080/api/vouchers \
  -H 'Content-Type: application/json' \
  -d '{"description":"사무용품 카드 구입","voucherDate":"2026-10-11","counterpartyName":"가상문구",
       "evidenceType":"CARD","vatType":"TAXABLE","supplyAmount":100000,"drafter":"fixture","preparer":"김작성",
       "lines":[{"accountCode":"830","side":"DEBIT","amount":100000},
                {"accountCode":"135","side":"DEBIT","amount":10000},
                {"accountCode":"253","side":"CREDIT","amount":110000}]}'
```

HTTP 201. `status`는 판정에 따라 `DRAFT`, `REVIEW`, `REJECTED`입니다. `preparer`(작성자)는 필수이며 없으면 400입니다. `evidenceType`은 `TAX_INVOICE`(세금계산서), `INVOICE`(계산서), `CARD`, `CASH_RECEIPT`, `NONE`이고, `vatType`은 `TAXABLE`, `EXEMPT`, `ZERO_RATED`, `NA`입니다.

```json
{"id":1,"voucherDate":"2026-10-11","description":"사무용품 카드 구입","counterpartyName":"가상문구",
 "counterpartyBizNo":null,"evidenceType":"CARD","vatType":"TAXABLE","supplyAmount":100000,
 "status":"DRAFT","drafter":"fixture","lines":[...],"validation":{"overall":"PASS","findings":[]},
 "preparer":"김작성","approver":null,"approvalReason":null,"createdAt":"2026-10-11T16:14:42.637","approvedAt":null,
 "reversalOfId":null,"reversedById":null}
```

차대가 맞지 않는 전표를 보내면 201로 저장되지만 `status`가 `REJECTED`이고 사유가 붙습니다.

```json
{"id":2,"status":"REJECTED","validation":{"overall":"FAIL","findings":[
  {"ruleCode":"R01","severity":"FAIL","message":"차변 합계 110000원과 대변 합계 100000원이 다르다","basis":"복식부기 대차평균의 원리"}]}, ...}
```

## 조회

```bash
curl -s http://localhost:8080/api/vouchers/1
curl -s 'http://localhost:8080/api/vouchers?status=POSTED&month=2026-10'
```

## 승인과 전기

```bash
curl -s -X POST http://localhost:8080/api/vouchers/1/approve \
  -H 'Content-Type: application/json' -d '{"approver":"검토자"}'
```

```json
{"id":1,"status":"POSTED","approver":"검토자","approvalReason":null,"approvedAt":"2026-10-11T16:14:42.693", ...}
```

FAIL 전표를 승인하면 409이고 본문에 재검증 결과가 들어 있습니다.

```json
{"status":409,"error":"Conflict","message":"FAIL 판정 전표는 승인할 수 없다",
 "validation":{"overall":"FAIL","findings":[{"ruleCode":"R01", ...}]},"timestamp":"2026-10-11T16:14:42.744"}
```

REVIEW 전표는 `reason`을 넣어야 승인됩니다.

```bash
curl -s -X POST http://localhost:8080/api/vouchers/3/approve \
  -H 'Content-Type: application/json' \
  -d '{"approver":"검토자","reason":"영수증 분실, 참석자 확인서로 대체"}'
```

작성자가 자기 전표를 승인하면 R12로 409입니다. 승인자 이름만 다르면 통과합니다.

```json
{"status":409,"error":"Conflict","message":"FAIL 판정 전표는 승인할 수 없다",
 "validation":{"overall":"FAIL","findings":[
  {"ruleCode":"R12","severity":"FAIL","message":"작성자 김작성이(가) 자기 전표를 승인할 수 없다","basis":"내부통제 직무 분리(전표 작성과 승인의 분리)"}]}}
```

같은 전표를 두 요청이 동시에 승인하면 먼저 커밋한 쪽만 200이고 늦은 쪽은 낙관적 락 충돌로 409입니다.

```json
{"status":409,"error":"Conflict","message":"다른 요청이 먼저 이 전표를 바꿨다. 다시 조회한 뒤 시도해야 한다","timestamp":"..."}
```

## 역분개

전기된(POSTED) 전표는 지우지 않고, 차대를 뒤집은 전표를 만들어 바로 전기해 상쇄합니다. 작성자, 승인자, 사유는 필수이고 작성자와 승인자는 달라야 합니다(R12). `voucherDate`가 없으면 오늘입니다.

```bash
curl -s -X POST http://localhost:8080/api/vouchers/1/reverse \
  -H 'Content-Type: application/json' \
  -d '{"preparer":"김작성","approver":"박승인","reason":"거래처 착오, 전표 취소"}'
```

HTTP 201. 응답은 역분개 전표이며 `reversalOfId`가 원전표를 가리킵니다. 원전표는 `status`가 `REVERSED`가 되고 `reversedById`가 채워집니다. 시산표에서는 두 전표가 계정별로 상쇄되어 잔액이 0이 됩니다.

```json
{"id":5,"description":"역분개: 사무용품 카드 구입","status":"POSTED","drafter":"manual","preparer":"김작성","approver":"박승인",
 "approvalReason":"거래처 착오, 전표 취소","reversalOfId":1,"reversedById":null,
 "lines":[{"lineNo":1,"accountCode":"830","side":"CREDIT","amount":100000},
          {"lineNo":2,"accountCode":"135","side":"CREDIT","amount":10000},
          {"lineNo":3,"accountCode":"253","side":"DEBIT","amount":110000}], ...}
```

POSTED가 아닌 전표, 이미 역분개된 전표, 마감된 달로의 역분개(R07), 작성자와 승인자가 같은 역분개(R12)는 모두 409이고 아무것도 저장되지 않습니다.

## 기간 마감

```bash
curl -s -X POST http://localhost:8080/api/periods/202609/close
curl -s http://localhost:8080/api/periods
```

```json
{"yyyymm":"202609","closed":true,"closedAt":"2026-10-11T16:20:01.120"}
```

`2026-09` 형식도 받습니다. 마감된 달의 전표는 저장 시 `REJECTED`, 승인 시 409가 됩니다.

## 시산표

```bash
curl -s 'http://localhost:8080/api/reports/trial-balance?month=2026-10'
```

```json
{"month":"2026-10","rows":[
  {"accountCode":"135","accountName":"부가세대급금","type":"ASSET","debitTotal":10000,"creditTotal":0,"balance":10000,"balanceSide":"DEBIT"},
  {"accountCode":"253","accountName":"미지급금","type":"LIABILITY","debitTotal":0,"creditTotal":110000,"balance":-110000,"balanceSide":"CREDIT"},
  {"accountCode":"830","accountName":"소모품비","type":"EXPENSE","debitTotal":100000,"creditTotal":0,"balance":100000,"balanceSide":"DEBIT"}],
 "totalDebit":110000,"totalCredit":110000,"balanced":true}
```

`balance`는 차변합에서 대변합을 뺀 값입니다. 양수이면 차변 잔액, 음수이면 대변 잔액입니다.

## 지표

```bash
curl -s http://localhost:8080/actuator/prometheus | grep ledger_guard
```

```
ledger_guard_verdicts_total{overall="pass",stage="save"} 1.0
ledger_guard_verdicts_total{overall="fail",stage="save"} 1.0
ledger_guard_findings_total{rule="R01",severity="fail",stage="save"} 1.0
```

`stage`는 draft, save, approve이고 `rule`은 R01부터 R13까지입니다.

## 오류 형식

```json
{"status":400,"error":"Bad Request","message":"요청 본문이 유효하지 않다","details":["voucherDate: must not be null"],"timestamp":"..."}
{"status":404,"error":"Not Found","message":"전표 999999을 찾을 수 없다","timestamp":"..."}
```
