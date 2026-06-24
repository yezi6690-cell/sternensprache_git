import zlib
import os
import struct

GIT_DIR = r"E:\sternensprache\.git"
TARGET_FILES = [
    "app/src/main/AndroidManifest.xml",
    "app/src/main/res/layout/activity_companion.xml",
    "app/src/main/java/com/mindisle/app/activity/CompanionActivity.kt",
]
TAG_HASH = "45195997d3d6b90d01d2d46cbd36d625f427f138"

def read_object(hash_hex):
    """Read and decompress a git object by its hex hash."""
    path = os.path.join(GIT_DIR, "objects", hash_hex[:2], hash_hex[2:])
    if not os.path.exists(path):
        return None
    with open(path, "rb") as f:
        compressed = f.read()
    return zlib.decompress(compressed)

def parse_commit(data):
    """Parse a git commit object, return {key: value}."""
    header, _, body = data.partition(b'\x00')
    # header is "commit <size>"
    lines = body.split(b'\n')
    info = {}
    for line in lines:
        if line == b'':
            break
        parts = line.split(b' ', 1)
        if len(parts) == 2:
            info[parts[0].decode()] = parts[1].decode()
        else:
            # multi-line (like mergetag)
            pass
    return info

def parse_tree(data):
    """Parse a git tree object, return list of (mode, name, hash_bytes)."""
    null_idx = data.find(b'\x00')
    # header is "tree <size>"
    pos = null_idx + 1
    entries = []
    while pos < len(data):
        # mode name\0<20-byte-hash>
        space_idx = data.find(b' ', pos)
        mode = data[pos:space_idx].decode()
        null_idx = data.find(b'\x00', space_idx + 1)
        name = data[space_idx + 1:null_idx].decode()
        hash_bytes = data[null_idx + 1:null_idx + 21]
        entries.append((mode, name, hash_bytes))
        pos = null_idx + 21
    return entries

def traverse_tree(tree_hash, prefix=""):
    """Traverse a tree recursively to find all blobs. Returns {path: blob_hash_hex}."""
    results = {}
    data = read_object(tree_hash)
    if data is None:
        return results
    entries = parse_tree(data)
    for mode, name, hash_bytes in entries:
        hash_hex = hash_bytes.hex()
        path = f"{prefix}{name}"
        if mode == "40000":  # tree (directory)
            results.update(traverse_tree(hash_hex, path + "/"))
        else:
            results[path] = hash_hex
    return results

def main():
    # Read the v0.11 commit
    commit_data = read_object(TAG_HASH)
    if commit_data is None:
        print(f"ERROR: Cannot read commit {TAG_HASH}")
        return
    commit_info = parse_commit(commit_data)
    tree_hash = commit_info.get("tree")
    if not tree_hash:
        print("ERROR: Cannot find tree hash in commit")
        return
    print(f"Tree hash: {tree_hash}")

    # Traverse tree to find all files
    all_files = traverse_tree(tree_hash)
    print(f"Found {len(all_files)} files in v0.11")

    # Restore target files
    for target in TARGET_FILES:
        if target not in all_files:
            print(f"WARNING: {target} not found in v0.11 tree")
            continue
        blob_hash = all_files[target]
        blob_data = read_object(blob_hash)
        if blob_data is None:
            print(f"ERROR: Cannot read blob {blob_hash} for {target}")
            continue
        # Remove the "blob <size>\0" header
        null_idx = blob_data.find(b'\x00')
        content = blob_data[null_idx + 1:]

        output_path = os.path.join(r"E:\sternensprache", target)
        os.makedirs(os.path.dirname(output_path), exist_ok=True)
        with open(output_path, "wb") as f:
            f.write(content)
        print(f"Restored: {target} ({len(content)} bytes)")

if __name__ == "__main__":
    main()
