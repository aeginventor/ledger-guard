-- 전표 5만 건, 원장 15만 건을 넣고 실행계획을 잰 뒤 추가한 인덱스(docs/evaluation.md 8절).
-- idx_voucher_date_counterparty: R10 중복 후보 조회(같은 일자, 거래처). Buffers 173에서 11로.
-- idx_ledger_entry_date_account_side_amount: 시산표 월 집계. amount까지 키에 넣어 index only scan이 되게 한다. Buffers 677에서 89로.
-- H2와 PostgreSQL 양쪽에서 동작하는 표준 SQL만 사용한다(INCLUDE 절은 H2가 모르므로 쓰지 않는다).

CREATE INDEX idx_voucher_date_counterparty ON voucher (voucher_date, counterparty_name);
CREATE INDEX idx_ledger_entry_date_account_side_amount ON ledger_entry (entry_date, account_code, side, amount);
