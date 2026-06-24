import zlib, os, hashlib

GIT_DIR = r"E:\sternensprache\.git"
WS = r"E:\sternensprache"
FILES = [
    "app/src/main/AndroidManifest.xml",
    "app/src/main/res/layout/activity_companion.xml",
    "app/src/main/java/com/mindisle/app/activity/CompanionActivity.kt",
    "app/src/main/res/drawable/bg_floating_input.xml",
]

def read_object(hash_hex):
    path = os.path.join(GIT_DIR, "objects", hash_hex[:2], hash_hex[2:])
    if not os.path.exists(path):
        return None
    with open(path, "rb") as f:
        return zlib.decompress(f.read())

def parse_tree(data):
    null_idx = data.find(b'\x00')
    pos = null_idx + 1
    entries = []
    while pos < len(data):
        space_idx = data.find(b' ', pos)
        mode = data[pos:space_idx].decode()
        null_idx = data.find(b'\x00', space_idx + 1)
        name = data[space_idx + 1:null_idx].decode()
        hash_bytes = data[null_idx + 1:null_idx + 21]
        entries.append((mode, name, hash_bytes))
        pos = null_idx + 21
    return entries

def traverse_tree(tree_hash, prefix=""):
    results = {}
    data = read_object(tree_hash)
    if data is None:
        return results
    for mode, name, hash_bytes in parse_tree(data):
        hash_hex = hash_bytes.hex()
        path = f"{prefix}{name}"
        if mode == "40000":
            results.update(traverse_tree(hash_hex, path + "/"))
        else:
            results[path] = hash_hex
    return results

def get_commit_tree(commit_hash):
    data = read_object(commit_hash)
    if data is None:
        return None
    null_idx = data.find(b'\x00')
    body = data[null_idx + 1:]
    for line in body.split(b'\n'):
        if line.startswith(b'tree '):
            return line[5:].decode().strip()
    return None

def get_blob_content(blob_hash):
    data = read_object(blob_hash)
    if data is None:
        return None
    null_idx = data.find(b'\x00')
    return data[null_idx + 1:]

def read_current_file(path):
    full = os.path.join(WS, path)
    if not os.path.exists(full):
        return None
    with open(full, "rb") as f:
        return f.read()

# Get v0.12 tree
tag_hash = "1c244d7c093c3d31d14c43819090e7dbc69c9a32"
tree = get_commit_tree(tag_hash)
if tree is None:
    print("ERROR: cannot read v0.12 commit")
    exit(1)
v12_files = traverse_tree(tree)

for f in FILES:
    v12_blob = v12_files.get(f)
    v12_content = get_blob_content(v12_blob) if v12_blob else None
    cur_content = read_current_file(f)

    if v12_content is None and cur_content is None:
        print(f"[{f}]  both missing")
    elif v12_content is None:
        print(f"[{f}]  NEW — not in v0.12 ({len(cur_content)} bytes)")
    elif cur_content is None:
        print(f"[{f}]  DELETED — was in v0.12 ({len(v12_content)} bytes)")
    elif v12_content == cur_content:
        print(f"[{f}]  SAME — no difference")
    else:
        print(f"[{f}]  DIFFERENT — v0.12={len(v12_content)}B, now={len(cur_content)}B")
