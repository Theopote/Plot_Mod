import json
import os
import urllib.request

URL = "https://raw.githubusercontent.com/PrismarineJS/minecraft-data/master/data/pc/1.21.4/blocks.json"
OUT = os.path.join(
    os.path.dirname(__file__),
    "..",
    "src",
    "test",
    "resources",
    "pattern",
    "vanilla_block_ids.txt",
)

data = json.load(urllib.request.urlopen(URL, timeout=30))
ids = sorted(f"minecraft:{item['name']}" for item in data if item.get("name"))
os.makedirs(os.path.dirname(OUT), exist_ok=True)
with open(OUT, "w", encoding="utf-8") as file:
    file.write("# Source: PrismarineJS minecraft-data pc/1.21.4/blocks.json\n")
    for block_id in ids:
        file.write(block_id + "\n")
print(f"wrote {len(ids)} block ids to {OUT}")
