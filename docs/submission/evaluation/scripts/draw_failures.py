"""Draw ground truth (green) and predictions (red) for the worst images. Usage: draw_failures.py subset results images url outdir n"""
import json, sys, urllib.request, uuid
from PIL import Image, ImageDraw
subset, results, img_dir, url, out_dir, n = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4], sys.argv[5], int(sys.argv[6])
items = {i["id"]: i for i in json.load(open(subset))}
per = json.load(open(results))["per_image"]
worst = sorted(per, key=lambda r: -(len(r["fn"]) + len(r["fp"])))[:n]
for r in worst:
    it = items[r["id"]]; path = f"{img_dir}/{it['file']}"
    boundary = uuid.uuid4().hex
    body = (f"--{boundary}\r\nContent-Disposition: form-data; name=\"file\"; filename=\"i.jpg\"\r\nContent-Type: image/jpeg\r\n\r\n").encode() + open(path, "rb").read() + f"\r\n--{boundary}--\r\n".encode()
    resp = json.load(urllib.request.urlopen(urllib.request.Request(url, data=body, headers={"Content-Type": f"multipart/form-data; boundary={boundary}"})))
    img = Image.open(path).convert("RGB"); d = ImageDraw.Draw(img); W, H = img.size
    for g in it["objects"]:
        x, y, w, h = g["bbox"]; d.rectangle([x, y, x + w, y + h], outline=(0, 200, 0), width=3); d.text((x + 3, y + 3), "GT " + g["label"], fill=(0, 200, 0))
    for o in resp["objects"]:
        b = o["bbox"]; x, y, w, h = b["x"] * W, b["y"] * H, b["w"] * W, b["h"] * H
        d.rectangle([x, y, x + w, y + h], outline=(230, 30, 30), width=2); d.text((x + 3, y + h - 12), f"{o['label']} {o['confidence']:.2f}", fill=(230, 30, 30))
    name = f"{out_dir}/coco-{r['id']}.jpg"; img.save(name, quality=88)
    print(name, "missed:", r["fn"], "false:", r["fp"])
