# AGENTS.md

AI 코딩 도구와 사람이 이 저장소에서 작업할 때 먼저 읽는 지도다. 100줄 안에 둔다. 상세는 링크한 문서에 있다.

## 무엇인가

AI가 쓴 분개 초안을 사람이 승인하기 전에 규칙 13개로 판정하는 전표 검증 API. 해석은 AI, 판정과 계산은 규칙 코드. 승인된 전표만 원장에 전기되고, 틀린 전기는 역분개로 상쇄한다. [README](README.md)

## 지도

- `validation/rules/` R01~R13. 규칙 하나가 클래스 하나. `ValidationEngine`이 코드 순으로 전부 돌려 사유를 모은다. 규칙은 `ValidationContext`만 보고 DB를 모른다
- `draft/` `JournalDrafter` 인터페이스. 기본은 키워드 매칭 `FixtureJournalDrafter`. `OpenAiJournalDrafter`는 키가 있을 때만 켜진다. 실제 호출 실측은 `llm-drafter` 브랜치
- `voucher/` 전표 저장, 승인(재검증, 낙관적 락), 역분개. `period/` 기간 마감. `ledger/` 원장. `report/` 시산표(month, ytd)
- `db/migration/` Flyway. H2와 PostgreSQL 양쪽에서 도는 SQL만 쓴다(JSONB, 배열, INCLUDE 금지)
- `test/resources/golden/` 정답표 3개. `golden/MutationEvaluationTest` 변이 연산자. 수치를 바꾸면 docs/evaluation.md도 바꾼다
- `docs/` rules, decisions, evaluation, legal-basis, architecture, api. 새 설계 결정은 decisions.md에 "사용자 확인 필요: 예"로 적는다

## 규칙

- 테스트 없이 규칙을 바꾸지 않는다. 규칙을 더하면 단위 테스트, 골든셋 결함 전표, 변이 연산자, rules.md, README 표를 함께 더한다
- 문서의 수치는 실측값만 쓴다. 재실행하지 않은 수치를 적지 않는다. 안 한 것은 안 했다고 적는다
- 커밋 제목은 한글 한 줄, 본문 없음, 타입 접두어 없음. AI 도구 이름과 공동 작성자 표기를 넣지 않는다. `git -c commit.gpgsign=false commit`
- 글쓰기: 두괄식, 짧은 문장, 가운뎃점 대신 "와, 과, 그리고". 상투어를 쓰지 않는다
- 비밀값을 커밋하지 않는다. `.env.example`과 `k8s/secret.example.yaml`만 둔다
- 실제 기업의 사업자등록번호, 실명, 실제 회계 데이터를 쓰지 않는다. 테스트 번호는 체크섬으로 만든 가상 번호다
- 세법 조문은 확신이 없으면 "확인 필요"를 붙이고 작성자가 닫는다. 조회 메모는 docs/legal-basis.md

## 자주 쓰는 명령

```bash
./gradlew test                                   # H2, Docker 없이 128개 이상
docker compose up --build -d && bash scripts/smoke.sh && docker compose down -v
kubectl apply --dry-run=client --validate=false -f k8s/
python3 experiments/llm_eval.py --base http://localhost:8080   # llm-drafter 브랜치, 작성기 openai일 때
```

## 하지 말 것

- 실제 LLM API 키를 코드나 설정 파일에 넣기
- 골든셋을 통과시키려고 규칙을 느슨하게 고치기. 못 잡는 결함은 semantic_misses.json과 변이 평가에 "통과"로 고정한다
- 수치를 외우거나 추정해서 적기
