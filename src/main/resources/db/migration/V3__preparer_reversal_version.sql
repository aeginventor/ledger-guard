-- 전표 작성자(직무 분리 R12), 역분개 상호 참조, 낙관적 락 버전.
-- H2(MODE=PostgreSQL)와 PostgreSQL 양쪽에서 동작하는 표준 SQL만 사용한다.

ALTER TABLE voucher ADD COLUMN preparer VARCHAR(100);
ALTER TABLE voucher ADD COLUMN reversal_of_id BIGINT;
ALTER TABLE voucher ADD COLUMN reversed_by_id BIGINT;
ALTER TABLE voucher ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE voucher ADD CONSTRAINT fk_voucher_reversal_of FOREIGN KEY (reversal_of_id) REFERENCES voucher (id);
ALTER TABLE voucher ADD CONSTRAINT fk_voucher_reversed_by FOREIGN KEY (reversed_by_id) REFERENCES voucher (id);

CREATE INDEX idx_voucher_reversal_of ON voucher (reversal_of_id);
