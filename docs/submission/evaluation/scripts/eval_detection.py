"""Score the running CV service (/v1/analyze) on the COCO subset. Usage: eval_detection.py subset.json images_dir url out.json
Set CV_API_TOKEN to call a deployment that requires a bearer token."""
import json, os, sys, time, statistics, urllib.request, uuid
subset, img_dir, url, out_path = sys.argv[1:5]
items = json.load(open(subset))

def post(path):
    boundary = uuid.uuid4().hex
    body = (f"--{boundary}\r\nContent-Disposition: form-data; name=\"file\"; filename=\"img.jpg\"\r\n"
            f"Content-Type: image/jpeg\r\n\r\n").encode() + open(path, "rb").read() + f"\r\n--{boundary}--\r\n".encode()
    headers = {"Content-Type": f"multipart/form-data; boundary={boundary}"}
    if os.environ.get("CV_API_TOKEN"):
        headers["Authorization"] = f"Bearer {os.environ['CV_API_TOKEN']}"
    req = urllib.request.Request(url, data=body, headers=headers)
    t = time.perf_counter()
    with urllib.request.urlopen(req, timeout=60) as r:
        data = json.load(r)
    return data, (time.perf_counter() - t) * 1000

def iou(a, b):
    ax2, ay2, bx2, by2 = a[0] + a[2], a[1] + a[3], b[0] + b[2], b[1] + b[3]
    iw, ih = max(0, min(ax2, bx2) - max(a[0], b[0])), max(0, min(ay2, by2) - max(a[1], b[1]))
    inter = iw * ih
    return inter / (a[2] * a[3] + b[2] * b[3] - inter) if inter else 0.0

classes = ["bed", "sofa", "chair", "table", "tv", "plant"]
stats = {c: {"tp": 0, "fp": 0, "fn": 0, "large_gt": 0, "large_tp": 0} for c in classes}
per_image, server_ms, wall_ms = [], [], []
for it in items:
    resp, wall = post(f"{img_dir}/{it['file']}")
    wall_ms.append(wall); server_ms.append(resp["processingTimeMs"])
    W, H = it["width"], it["height"]
    preds = [{"label": o["label"], "conf": o["confidence"],
              "bbox": [o["bbox"]["x"] * W, o["bbox"]["y"] * H, o["bbox"]["w"] * W, o["bbox"]["h"] * H]} for o in resp["objects"]]
    record = {"id": it["id"], "file": it["file"], "fn": [], "fp": [], "tp": []}
    for c in classes:
        gts = [g for g in it["objects"] if g["label"] == c]
        ps = sorted([p for p in preds if p["label"] == c], key=lambda p: -p["conf"])
        used = set()
        for p in ps:
            best, best_i = 0.0, None
            for i, g in enumerate(gts):
                if i in used: continue
                v = iou(p["bbox"], g["bbox"])
                if v > best: best, best_i = v, i
            if best >= 0.5 and not gts[best_i]["crowd"]:
                used.add(best_i); stats[c]["tp"] += 1; record["tp"].append([c, round(p["conf"], 2)])
                if gts[best_i]["area"] >= 0.02 * W * H: stats[c]["large_tp"] += 1
            elif best >= 0.5:  # matched a crowd region: neither TP nor FP
                used.add(best_i)
            else:
                stats[c]["fp"] += 1; record["fp"].append([c, round(p["conf"], 2)])
        for i, g in enumerate(gts):
            if g["crowd"]: continue
            if g["area"] >= 0.02 * W * H: stats[c]["large_gt"] += 1
            if i not in used:
                stats[c]["fn"] += 1; record["fn"].append([c, round(g["area"] / (W * H), 3)])
    per_image.append(record)

def ratio(a, b): return round(a / b, 3) if b else None
rows = []
tot = {k: sum(s[k] for s in stats.values()) for k in ["tp", "fp", "fn", "large_gt", "large_tp"]}
for c, s in list(stats.items()) + [("all", tot)]:
    rows.append({"class": c, "gt": s["tp"] + s["fn"], "tp": s["tp"], "fp": s["fp"], "fn": s["fn"],
                 "precision": ratio(s["tp"], s["tp"] + s["fp"]), "recall": ratio(s["tp"], s["tp"] + s["fn"]),
                 "large_recall": ratio(s["large_tp"], s["large_gt"]), "large_gt": s["large_gt"]})
q = lambda xs, p: round(statistics.quantiles(xs, n=100)[p - 1], 1)
result = {"images": len(items), "classes": rows,
          "latency_ms": {"server_p50": q(server_ms, 50), "server_p95": q(server_ms, 95), "wall_p50": q(wall_ms, 50), "wall_p95": q(wall_ms, 95),
                         "wall_max": round(max(wall_ms), 1)},
          "per_image": per_image}
json.dump(result, open(out_path, "w"), indent=1)
for r in rows: print(r)
print(result["latency_ms"])
