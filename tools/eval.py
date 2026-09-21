#!/usr/bin/env python3
"""On-device evaluation: pushes test clips, replays them through the real pipeline (debug build only), scores the result.

  python3 tools/eval.py                      # all clips in eval/manifest.csv
  python3 tools/eval.py --lang te            # one language
  python3 tools/eval.py --only t1,t2,en1     # specific clips
  python3 tools/eval.py --selftest           # check the parser/scorer without a phone

Real-speech clips: put 16 kHz mono PCM16 WAVs in eval/clips/ and add rows to eval/manifest.csv (set=field for real speakers).
The replay is a debug-build feature: it feeds the WAVs into Whisper/Omnilingual/Gemma/rules exactly like the microphone would.
"""
import argparse, csv, html, json, os, re, subprocess, sys, time
from collections import defaultdict

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ADB = os.environ.get("ADB", os.path.expanduser("~/Library/Android/sdk/platform-tools/adb"))
PKG = "com.callguard"
EVAL_DIR = f"/sdcard/Android/data/{PKG}/files/eval"
RANK = {"LOW": 0, "MEDIUM": 1, "HIGH": 2}
TEST_LABELS = ["Test capture now (no call)", "अभी परीक्षण करें (बिना कॉल)", "ఇప్పుడే పరీక్షించండి (కాల్ లేకుండా)"]  # the button in each screen language

def adb(serial, *a):
    return subprocess.run([ADB] + (["-s", serial] if serial else []) + list(a), capture_output=True).stdout.decode("utf-8", "replace")

# ---------- pure logic (unit-tested by --selftest) ----------
def parse_replay(log_text):
    """`R|name|risk=HIGH|alerts=1|...|tx=...` lines from logcat -> {name: {risk, alerts, tx}}"""
    out = {}
    for m in re.finditer(r"Replay\s*:\s*R\|([^|]+)\|risk=(\w+)\|alerts=(\d+)\|(.*)", log_text):
        tx = re.search(r"\|tx=(.*)$", m.group(4))
        out[m.group(1)] = {"risk": m.group(2), "alerts": int(m.group(3)), "tx": tx.group(1).strip() if tx else ""}
    return out

def verdict(expect, got):
    if got == expect: return "OK"
    if RANK[got] > RANK[expect]: return "FALSE ALARM" if expect == "LOW" else "TOO HIGH"
    return "MISS"

def score(rows):
    """rows: [{file, lang, expect, got}] -> per-language and overall figures"""
    by = defaultdict(list)
    for r in rows: by[r["lang"]].append(r); by["ALL"].append(r)
    res = {}
    for lang, rs in by.items():
        n = len(rs); ok = sum(1 for r in rs if r["got"] == r["expect"])
        hi = [r for r in rs if r["expect"] == "HIGH"]; lo = [r for r in rs if r["expect"] == "LOW"]
        res[lang] = {
            "n": n, "exact": ok, "exact_pct": round(100 * ok / n) if n else 0,
            "high_recall_pct": round(100 * sum(1 for r in hi if r["got"] == "HIGH") / len(hi)) if hi else None,
            "false_alarms": sum(1 for r in lo if r["got"] != "LOW"), "low_n": len(lo),
            "misses": sum(1 for r in rs if r["expect"] != "LOW" and RANK[r["got"]] < RANK[r["expect"]]),
        }
    return res

def selftest():
    log = "09-21 10:00:00.1 1 2 I Replay  : R|t1|risk=HIGH|alerts=1|Gemma: ready|signals=a|tx=మీ otp\n09-21 10:00:20.1 1 2 I Replay  : R|en4|risk=MEDIUM|alerts=1|x|signals=|tx=hello\n09-21 I Replay : REPLAY done"
    got = parse_replay(log)
    assert got["t1"] == {"risk": "HIGH", "alerts": 1, "tx": "మీ otp"}, got
    assert got["en4"]["risk"] == "MEDIUM" and "REPLAY" not in got
    assert [verdict("HIGH", "HIGH"), verdict("LOW", "MEDIUM"), verdict("MEDIUM", "HIGH"), verdict("HIGH", "LOW")] == ["OK", "FALSE ALARM", "TOO HIGH", "MISS"]
    s = score([{"file": "a", "lang": "te", "expect": "HIGH", "got": "HIGH"}, {"file": "b", "lang": "te", "expect": "LOW", "got": "MEDIUM"}, {"file": "c", "lang": "en", "expect": "HIGH", "got": "LOW"}])
    assert s["ALL"]["n"] == 3 and s["ALL"]["exact"] == 1 and s["ALL"]["false_alarms"] == 1 and s["ALL"]["misses"] == 1 and s["te"]["high_recall_pct"] == 100 and s["en"]["high_recall_pct"] == 0, s
    print("selftest OK"); return 0

# ---------- device run ----------
def tap_test_button(serial):
    adb(serial, "shell", "uiautomator", "dump", "/sdcard/ui.xml")
    xml = adb(serial, "shell", "cat", "/sdcard/ui.xml")
    for m in re.finditer(r'text="([^"]+)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml):
        if html.unescape(m.group(1)).lower() in [t.lower() for t in TEST_LABELS]:
            x, y = (int(m.group(2)) + int(m.group(4))) // 2, (int(m.group(3)) + int(m.group(5))) // 2
            adb(serial, "shell", "input", "tap", str(x), str(y)); return True
    return False

def run(args):
    with open(os.path.join(ROOT, "eval", "manifest.csv"), newline="", encoding="utf-8") as f:
        rows = [r for r in csv.DictReader(f)]
    if args.lang: rows = [r for r in rows if r["lang"] == args.lang]
    if args.only: keep = set(args.only.split(",")); rows = [r for r in rows if r["file"] in keep]
    if not rows: print("no clips selected"); return 2
    if PKG not in adb(args.serial, "shell", "pm", "list", "packages", PKG): print("CallGuard debug build is not installed / no device"); return 2
    adb(args.serial, "shell", "svc", "power", "stayon", "true"); adb(args.serial, "shell", "input", "keyevent", "KEYCODE_WAKEUP")
    adb(args.serial, "shell", "mkdir", "-p", f"{EVAL_DIR}/wavs")
    for r in rows: adb(args.serial, "push", os.path.join(ROOT, "eval", "clips", r["file"] + ".wav"), f"{EVAL_DIR}/wavs/")
    open("/tmp/callguard_replay.txt", "w").write(" ".join(r["file"] for r in rows))
    adb(args.serial, "push", "/tmp/callguard_replay.txt", f"{EVAL_DIR}/replay.txt")
    adb(args.serial, "shell", "am", "force-stop", PKG); adb(args.serial, "logcat", "-c")
    adb(args.serial, "shell", "am", "start", "-n", f"{PKG}/.ui.MainActivity"); time.sleep(3)
    if not tap_test_button(args.serial): print("could not find the Test capture button (is a dialog open?)"); return 2
    deadline = time.time() + 60 + 24 * len(rows)
    while time.time() < deadline and "REPLAY done" not in adb(args.serial, "logcat", "-d", "-s", "Replay"): time.sleep(4)
    got = parse_replay(adb(args.serial, "logcat", "-d", "-s", "Replay"))
    adb(args.serial, "shell", "rm", "-rf", EVAL_DIR); adb(args.serial, "shell", "am", "force-stop", PKG)
    if not args.keep_awake: adb(args.serial, "shell", "svc", "power", "stayon", "false")
    results = []
    for r in rows:
        g = got.get(r["file"]); results.append({"file": r["file"], "lang": r["lang"], "set": r["set"], "expect": r["expect"], "got": g["risk"] if g else "LOW", "ran": bool(g), "alerts": g["alerts"] if g else 0, "verdict": verdict(r["expect"], g["risk"]) if g else "NOT RUN", "note": r["note"]})
    report(results, args)
    return 0 if all(x["verdict"] == "OK" for x in results) else 1

def report(results, args):
    print(f"\n{'clip':6} {'lang':9} {'expect':7} {'got':7} verdict")
    for r in results: print(f"{r['file']:6} {r['lang']:9} {r['expect']:7} {r['got']:7} {r['verdict']}{'' if r['ran'] else '  (did not run)'}")
    s = score([r for r in results if r["ran"]])
    print("\nlang       clips exact   HIGH-recall  false-alarms  misses")
    for lang, v in sorted(s.items(), key=lambda kv: (kv[0] == "ALL", kv[0])):
        print(f"{lang:9} {v['n']:6} {v['exact_pct']:4}%   {('%d%%' % v['high_recall_pct']) if v['high_recall_pct'] is not None else '-':>10}   {v['false_alarms']}/{v['low_n']:<11} {v['misses']}")
    not_run = [r["file"] for r in results if not r["ran"]]
    if not_run: print("did not run:", ", ".join(not_run))
    stamp = time.strftime("%Y%m%d-%H%M%S")
    out = os.path.join(ROOT, "eval", "reports", f"{stamp}.json")
    json.dump({"when": stamp, "results": results, "score": s}, open(out, "w"), indent=1, ensure_ascii=False)
    print("report:", os.path.relpath(out, ROOT))
    print("NOTE: clips marked set=dev were used to tune the rules, so this measures regressions, not generalisation. Use held-out real speech for that.")

if __name__ == "__main__":
    ap = argparse.ArgumentParser(); ap.add_argument("--serial"); ap.add_argument("--lang"); ap.add_argument("--only"); ap.add_argument("--keep-awake", action="store_true"); ap.add_argument("--selftest", action="store_true")
    a = ap.parse_args(); sys.exit(selftest() if a.selftest else run(a))
