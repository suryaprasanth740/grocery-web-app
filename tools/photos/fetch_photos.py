#!/usr/bin/env python3
"""
Collects freely licensed product photos from Wikimedia Commons for the demo shops.

Runs in GitHub Actions (see .github/workflows/photos.yml), because the photos are
downloaded once and then stored inside the app, so they never depend on another site.

photos.json has a "mode":
  "candidates"  For each product, find up to 5 photos and draw them on review sheets
                (review/sheet-N.jpg) so a person can pick the best one by number.
  "final"       Download the chosen file for each product, crop it square, shrink it,
                and save it to src/main/resources/static/img/products/<slug>.jpg,
                plus credits.json with the author and licence of every photo.

Only photos whose licence allows reuse are accepted (CC0, public domain, CC BY, CC BY-SA).
"""
import html
import io
import json
import re
import sys
import time
import urllib.parse
import urllib.request
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont, ImageOps

ROOT = Path(__file__).resolve().parents[2]
CONFIG = Path(__file__).with_name("photos.json")
OUT_DIR = ROOT / "src/main/resources/static/img/products"
REVIEW_DIR = ROOT / "review"
API = "https://commons.wikimedia.org/w/api.php"
UA = "NammaBasketPhotoFetcher/1.0 (https://github.com/suryaprasanth740/grocery-web-app; portfolio project)"
OK_LICENCES = ("cc0", "public domain", "pd", "cc by")
SIZE = 480


def get(url, tries=4):
    for attempt in range(tries):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": UA})
            with urllib.request.urlopen(req, timeout=60) as r:
                return r.read()
        except Exception as e:  # network hiccup or rate limit: wait and retry
            if attempt == tries - 1:
                raise
            time.sleep(3 * (attempt + 1))


def api(params):
    params = {**params, "format": "json", "formatversion": "2"}
    return json.loads(get(API + "?" + urllib.parse.urlencode(params)))


def clean(text):
    text = re.sub(r"<[^>]+>", "", text or "")
    return re.sub(r"\s+", " ", html.unescape(text)).strip()


def info_for(pages, width):
    """Keeps only usable photos and returns their details."""
    found = []
    for p in pages:
        ii = (p.get("imageinfo") or [None])[0]
        if not ii or ii.get("mime") not in ("image/jpeg", "image/png", "image/tiff", "image/webp"):
            continue
        if min(ii.get("width", 0), ii.get("height", 0)) < 400:
            continue
        meta = ii.get("extmetadata", {})
        licence = clean(meta.get("LicenseShortName", {}).get("value", ""))
        if not licence.lower().startswith(OK_LICENCES):
            continue
        found.append({
            "title": p["title"],
            "thumb": ii.get("thumburl") or ii["url"],
            "page": ii.get("descriptionurl", ""),
            "licence": licence,
            "licenceUrl": meta.get("LicenseUrl", {}).get("value", ""),
            "author": clean(meta.get("Artist", {}).get("value", "")) or "Unknown",
            "index": p.get("index", 0),
        })
    return sorted(found, key=lambda f: f["index"])


IIPROP = "url|extmetadata|mime|size"


def by_titles(titles, width):
    if not titles:
        return []
    data = api({"action": "query", "titles": "|".join(titles), "prop": "imageinfo",
                "iiprop": IIPROP, "iiurlwidth": width})
    pages = data.get("query", {}).get("pages", [])
    order = {t: i for i, t in enumerate(titles)}
    for p in pages:
        p["index"] = order.get(p["title"], 99)
    return info_for(pages, width)


def search(query, width, limit=15):
    data = api({"action": "query", "generator": "search", "gsrsearch": query + " filetype:bitmap",
                "gsrnamespace": 6, "gsrlimit": limit, "prop": "imageinfo",
                "iiprop": IIPROP, "iiurlwidth": width})
    return info_for(data.get("query", {}).get("pages", []), width)


def square(img, size, focus=(0.5, 0.5)):
    img = ImageOps.exif_transpose(img).convert("RGB")
    return ImageOps.fit(img, (size, size), Image.LANCZOS, centering=focus)


def font(size):
    for f in ("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",):
        try:
            return ImageFont.truetype(f, size)
        except OSError:
            pass
    return ImageFont.load_default()


def candidates(items):
    REVIEW_DIR.mkdir(exist_ok=True)
    tile, per_sheet, label_w = 200, 8, 230
    found_all = {}
    for item in items:
        prefer = ["File:" + f if not f.startswith("File:") else f for f in item.get("prefer", [])]
        seen, picks = set(), []
        found = by_titles(prefer, tile)
        for q in item.get("queries") or [item["query"]]:
            found += search(q, tile, limit=10)
        for c in found:
            if c["title"] not in seen:
                seen.add(c["title"])
                picks.append(c)
            if len(picks) == item.get("max", 5):
                break
        found_all[item["slug"]] = picks
        print(f"{item['slug']}: {len(picks)} candidates", flush=True)

    slugs = [i["slug"] for i in items]
    names = {i["slug"]: i["name"] for i in items}
    f_big, f_small = font(22), font(16)
    for s in range(0, len(slugs), per_sheet):
        chunk = slugs[s:s + per_sheet]
        cols = max(len(found_all[s]) for s in chunk) or 1
        sheet = Image.new("RGB", (label_w + cols * (tile + 10), len(chunk) * (tile + 14)), "white")
        d = ImageDraw.Draw(sheet)
        for row, slug in enumerate(chunk):
            y = row * (tile + 14)
            d.text((8, y + 8), names[slug], fill="black", font=f_big)
            d.text((8, y + 40), slug, fill="#666", font=f_small)
            for col, c in enumerate(found_all[slug]):
                try:
                    im = square(Image.open(io.BytesIO(get(c["thumb"]))), tile)
                except Exception as e:
                    print("  could not load", c["title"], e)
                    continue
                x = label_w + col * (tile + 10)
                sheet.paste(im, (x, y))
                d.rectangle((x, y, x + 34, y + 30), fill="black")
                d.text((x + 9, y + 3), str(col), fill="yellow", font=f_big)
        sheet.save(REVIEW_DIR / f"sheet-{s // per_sheet + 1:02d}.jpg", quality=80)
    (REVIEW_DIR / "candidates.json").write_text(json.dumps(found_all, indent=1, ensure_ascii=False))


def final(items):
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    credits_path = OUT_DIR / "credits.json"
    credits = json.loads(credits_path.read_text()) if credits_path.exists() else {}
    for item in items:
        title = item.get("file")
        if not title:
            print(f"{item['slug']}: no file chosen, skipped")
            continue
        title = title if title.startswith("File:") else "File:" + title
        found = by_titles([title], 800)
        if not found:
            print(f"{item['slug']}: {title} not usable (missing or licence not allowed)", file=sys.stderr)
            continue
        c = found[0]
        img = square(Image.open(io.BytesIO(get(c["thumb"]))), SIZE, tuple(item.get("focus", (0.5, 0.5))))
        img.save(OUT_DIR / f"{item['slug']}.jpg", quality=78, optimize=True, progressive=True)
        credits[item["slug"]] = {"product": item["name"], "title": c["title"].removeprefix("File:"),
                                 "author": c["author"], "licence": c["licence"],
                                 "licenceUrl": c["licenceUrl"], "source": c["page"]}
        print(f"{item['slug']}: saved from {c['title']} ({c['licence']})", flush=True)
    credits_path.write_text(json.dumps(dict(sorted(credits.items())), indent=1, ensure_ascii=False) + "\n")


if __name__ == "__main__":
    cfg = json.loads(CONFIG.read_text())
    only = set(cfg.get("only") or [])
    items = [i for i in cfg["items"] if not only or i["slug"] in only]
    (candidates if cfg["mode"] == "candidates" else final)(items)
