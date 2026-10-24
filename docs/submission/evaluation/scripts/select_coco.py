"""Select a deterministic indoor subset of COCO val2017 and write ground truth for Roomify's 6 classes."""
import json, sys
ann_path, out_path, limit = sys.argv[1], sys.argv[2], int(sys.argv[3])
data = json.load(open(ann_path))
names = {c["id"]: c["name"] for c in data["categories"]}
MAP = {"bed": "bed", "chair": "chair", "couch": "sofa", "dining table": "table", "tv": "tv", "potted plant": "plant"}
by_image = {}
for a in data["annotations"]:
    n = names[a["category_id"]]
    if n in MAP:
        by_image.setdefault(a["image_id"], []).append({"label": MAP[n], "bbox": a["bbox"], "crowd": a["iscrowd"], "area": a["area"]})
images = {i["id"]: i for i in data["images"]}
anchors = sorted(i for i, objs in by_image.items() if any(o["label"] in ("bed", "sofa") for o in objs))
chosen = anchors[:limit]
out = [{"id": i, "file": images[i]["file_name"], "width": images[i]["width"], "height": images[i]["height"],
        "url": images[i]["coco_url"], "objects": by_image[i]} for i in chosen]
json.dump(out, open(out_path, "w"), indent=1)
print(len(anchors), "candidate images;", len(out), "selected;", sum(len(o["objects"]) for o in out), "ground-truth objects")
