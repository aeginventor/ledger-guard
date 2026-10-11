#!/usr/bin/env python3
"""임베딩 유사도로 의미 결함을 잡을 수 있는지 실험한다 (Vector DB와 sLLM 수업의 적용).

거래 설명과 계정과목 이름을 같은 임베딩 모델(Ollama, 기본 bge-m3)로 벡터화해 코사인 유사도를 잰다.
Qdrant 같은 Vector DB를 띄우지 않고 30개 계정이라 메모리에서 계산한다. 운영에서는 Qdrant 컬렉션에 계정 벡터를 넣으면 같은 질의다.

측정 둘.
  1. 정상 전표 27건: 설명에 가장 가까운 계정이 실제로 쓰인 계정(부가세와 결제 계정이 아닌 첫 라인)인 비율(hit@1, hit@3)
  2. 의미 결함 5건: 맞는 계정이 쓰인 계정보다 설명에 더 가까운가. 가깝다면 "쓰인 계정이 상위 k 밖이면 REVIEW" 규칙의 재료가 된다

사용법: python3 experiments/semantic_embedding.py [--model bge-m3] [--ollama http://localhost:11434]
"""
import argparse
import json
import math
import pathlib
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parents[1]
GOLDEN = ROOT / "src/test/resources/golden"
SEED = ROOT / "src/main/resources/db/migration/V2__seed_accounts.sql"
VAT_ACCOUNTS = {"135", "255"}
SETTLEMENT = {"101", "103", "108", "120", "251", "253", "254", "260"}  # 결제와 채권 채무 계정은 설명이 아니라 결제 수단이 정한다


def embed(base, model, texts):
    req = urllib.request.Request(base + "/api/embed", data=json.dumps({"model": model, "input": texts}).encode(),
                                 headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=300) as resp:
        return json.loads(resp.read())["embeddings"]


def cosine(a, b):
    dot = sum(x * y for x, y in zip(a, b))
    na = math.sqrt(sum(x * x for x in a))
    nb = math.sqrt(sum(y * y for y in b))
    return dot / (na * nb) if na and nb else 0.0


def accounts():
    out = []
    for line in SEED.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line.startswith("('"):
            continue
        parts = [p.strip().strip("'") for p in line.strip("(),;").split(",")]
        code, name, typ, side, active = parts[0], parts[1], parts[2], parts[3], parts[4]
        if active.upper() == "TRUE":
            out.append((code, name, typ))
    return out


def subject_account(lines):
    for l in lines:
        if l["accountCode"] not in VAT_ACCOUNTS and l["accountCode"] not in SETTLEMENT:
            return l["accountCode"]
    return lines[0]["accountCode"]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--model", default="bge-m3")
    ap.add_argument("--ollama", default="http://localhost:11434")
    ap.add_argument("--out", default=None)
    args = ap.parse_args()

    accs = [a for a in accounts() if a[0] not in VAT_ACCOUNTS and a[0] not in SETTLEMENT]
    type_ko = {"ASSET": "자산", "LIABILITY": "부채", "EQUITY": "자본", "REVENUE": "수익", "EXPENSE": "비용"}
    acc_texts = [f"{name} ({type_ko[t]} 계정)" for _, name, t in accs]
    acc_vecs = embed(args.ollama, args.model, acc_texts)

    valid = json.loads((GOLDEN / "valid_vouchers.json").read_text(encoding="utf-8"))["cases"]
    semantic = json.loads((GOLDEN / "semantic_misses.json").read_text(encoding="utf-8"))["cases"]

    def rank(description):
        v = embed(args.ollama, args.model, [description])[0]
        scored = sorted(((cosine(v, av), code, name) for av, (code, name, _) in zip(acc_vecs, accs)), reverse=True)
        return scored

    lines_out = [f"# 임베딩 유사도 실험 ({args.model})", "",
                 f"계정 {len(accs)}개(부가세와 결제, 채권 채무 계정 제외)의 이름을 벡터화하고 거래 설명과의 코사인 유사도로 순위를 매겼다.", ""]
    hit1 = hit3 = 0
    rows = []
    for c in valid:
        v = c["voucher"]
        used = subject_account(v["lines"])
        if used in SETTLEMENT or used in VAT_ACCOUNTS:
            continue
        scored = rank(v["description"])
        codes = [s[1] for s in scored]
        pos = codes.index(used) + 1 if used in codes else None
        hit1 += pos == 1
        hit3 += pos is not None and pos <= 3
        rows.append((c["id"], v["description"], used, pos, ", ".join(f"{s[1]} {s[2]} {s[0]:.2f}" for s in scored[:3])))
    n = len(rows)
    lines_out += [f"## 정상 전표 {n}건 (결제 계정만 있는 전표 제외)", "",
                  f"- 쓰인 계정이 1위: {hit1}건 / {n}건", f"- 쓰인 계정이 3위 안: {hit3}건 / {n}건", "",
                  "| id | 설명 | 쓰인 계정 | 순위 | 상위 3 |", "|---|---|---|---|---|"]
    lines_out += [f"| {i} | {d} | {u} | {p} | {t} |" for i, d, u, p, t in rows]

    lines_out += ["", "## 의미 결함 5건", "", "| id | 설명 | 쓰인 계정 유사도 | 맞는 계정 유사도 | 맞는 쪽이 더 가까운가 | 쓰인 계정 순위 |", "|---|---|---|---|---|---|"]
    better = 0
    for c in semantic:
        scored = rank(c["voucher"]["description"])
        sim = {s[1]: s[0] for s in scored}
        codes = [s[1] for s in scored]
        w, r = c["wrongAccountCode"], c["correctAccountCode"]
        ws, rs = sim.get(w), sim.get(r)
        ok = ws is not None and rs is not None and rs > ws
        better += ok
        pos = codes.index(w) + 1 if w in codes else None
        lines_out.append(f"| {c['id']} | {c['voucher']['description']} | {w} {ws if ws is None else f'{ws:.2f}'} | {r} {rs if rs is None else f'{rs:.2f}'} | {'예' if ok else '아니오'} | {pos} |")
    lines_out += ["", f"맞는 계정이 쓰인 계정보다 설명에 가까운 건: {better}건 / {len(semantic)}건"]
    text = "\n".join(lines_out)
    print(text)
    if args.out:
        pathlib.Path(args.out).write_text(text + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
