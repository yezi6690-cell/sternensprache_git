"""
Cubism 5 model integrity checker.
Usage: python check_cubism5_model.py --model-dir <path>
"""
import argparse, json, os, re, sys

REQUIRED = ["model3.json", "moc3"]
RECOMMENDED = ["physics3.json", "cdi3.json", "texture_00.png"]
OPTIONAL = ["pose3.json", "motion3.json", "exp3.json", "profile.json", "vtube.json", "items_pinned_to_model.json"]

def find_ext(dirpath, ext):
    """Find a file with given extension. Returns (rel_path, full_path) or (None, None)."""
    if os.path.isdir(dirpath):
        for f in os.listdir(dirpath):
            fp = os.path.join(dirpath, f)
            if os.path.isfile(fp) and f.lower().endswith(ext):
                return f, fp
    return None, None

def has(path):
    return os.path.exists(path)

def warn_path(name, path):
    issues = []
    if re.search(r'[一-鿿]', path):
        issues.append("contains Chinese characters")
    if ' ' in path:
        issues.append("contains spaces")
    if re.search(r'[^a-zA-Z0-9_\-\.\/\\]', path):
        issues.append("contains special characters")
    return issues

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--model-dir", required=True)
    parser.add_argument("--model-json", default=None,
        help="Explicit model3.json filename (e.g. xingyue_shuimu_safe.model3.json)")
    parser.add_argument("--out", default=None)
    args = parser.parse_args()
    d = args.model_dir

    report = {"dir": d, "status": "ok", "issues": [], "files": {}}
    dir_name = os.path.basename(os.path.normpath(d))

    # Collect all .model3.json files
    all_m3 = sorted([f for f in os.listdir(d) if f.lower().endswith('.model3.json')])
    report["modelJsonCandidates"] = all_m3

    m3name = None
    m3path = None

    if args.model_json:
        # Explicit path from user
        m3name = args.model_json
        m3path = os.path.join(d, m3name)
        if not has(m3path):
            report["status"] = "fail"
            report["issues"].append(f"--model-json specified but file not found: {m3name}")
            m3name = None
    elif len(all_m3) == 0:
        report["status"] = "fail"
        report["issues"].append("model3.json not found")
    elif len(all_m3) == 1:
        m3name = all_m3[0]
        m3path = os.path.join(d, m3name)
    else:
        # Priority: model.model3.json > <dirname>.model3.json
        preferred = "model.model3.json"
        if preferred in all_m3:
            m3name = preferred
            m3path = os.path.join(d, m3name)
        else:
            dir_match = f"{dir_name}.model3.json"
            if dir_match in all_m3:
                m3name = dir_match
                m3path = os.path.join(d, m3name)
            else:
                report["status"] = "fail"
                report["issues"].append(
                    "Multiple model3.json files found. "
                    "Use --model-json to specify: " + ", ".join(all_m3)
                )

    if m3name:
        report["selectedModelJson"] = m3name
        report["files"]["model3.json"] = m3name
        w = warn_path("model3.json", m3name)
        for iw in w: report["issues"].append(f"model3.json name: {iw} (warning)")

    # Find moc3
    mocname, mocpath = find_ext(d, ".moc3")
    if not mocname:
        report["status"] = "fail"
        report["issues"].append("moc3 not found")
    else:
        report["files"]["moc3"] = mocname
        w = warn_path("moc3", mocname)
        for iw in w: report["issues"].append(f"moc3 name: {iw} (warning)")

    # Model3.json internal references
    if m3name and m3path and has(m3path):
        try:
            with open(m3path, encoding="utf-8") as f:
                cfg = json.load(f)
            refs = cfg.get("FileReferences", {})
            for key in ["Moc", "Physics", "Pose", "DisplayInfo"]:
                val = refs.get(key)
                if val:
                    full = os.path.join(d, val)
                    ok = has(full)
                    report["files"][f"ref:{key}"] = {"path": val, "exists": ok}
                    if not ok:
                        report["issues"].append(f"model3.json references missing {key}: {val}")
            for i, tex in enumerate(refs.get("Textures", [])):
                full = os.path.join(d, tex)
                ok = has(full)
                report["files"][f"ref:Texture[{i}]"] = {"path": tex, "exists": ok}
                if not ok:
                    report["issues"].append(f"model3.json references missing Texture[{i}]: {tex}")
        except Exception as e:
            report["issues"].append(f"Failed to parse model3.json: {e}")

    # Optional files
    for ext in ["physics3.json", "pose3.json", "cdi3.json"]:
        name, path = find_ext(d, f".{ext}")
        report["files"][ext] = name if name else "missing"

    # Textures
    tex_files = []
    for f in os.listdir(d):
        fp = os.path.join(d, f)
        if os.path.isdir(fp):
            for tf in os.listdir(fp):
                if tf.lower().endswith(".png"):
                    tex_files.append(f"{f}/{tf}")

    # Profile
    profiles = [f for f in os.listdir(d) if f.endswith("_profile.json") or f == "profile.json"]
    report["files"]["profile"] = profiles if profiles else "missing"

    # Motion3 / Exp3 counts
    motions = [f for f in os.listdir(d) if f.lower().endswith(".motion3.json")]
    exps = [f for f in os.listdir(d) if f.lower().endswith(".exp3.json")]
    report["files"]["motion_count"] = len(motions)
    report["files"]["expression_count"] = len(exps)

    # Overall status
    issues = report.get("issues", [])
    if isinstance(issues, dict):
        issue_iter = list(issues.values())
    elif isinstance(issues, list):
        issue_iter = issues
    else:
        issue_iter = [issues]
    has_missing = any("missing" in str(i).lower() for i in issue_iter)
    has_error = any("error" in str(i).lower() for i in issue_iter)
    if report["status"] == "ok":
        report["status"] = "ready" if not has_missing and not has_error else "needs_fix"

    out_path = args.out or os.path.join(d, "import_report.json")
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(report, f, indent=2, ensure_ascii=False)

    print(f"Status: {report['status']}")
    for i in report["issues"]:
        print(f"  {i}")
    for k, v in report["files"].items():
        if isinstance(v, dict):
            print(f"  {k}: {v.get('path', '?')} -> {'OK' if v.get('exists') else 'MISSING'}")
        else:
            print(f"  {k}: {v}")
    print(f"Report: {out_path}")

if __name__ == "__main__":
    main()
