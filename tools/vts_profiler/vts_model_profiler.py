"""
VTube Studio model parameter profiler.
Phased capture → analysis → profile.json generation.
Usage:
  python tools/vts_profiler/vts_model_profiler.py ^
    --model-id xingyue_shuimu ^
    --model-dir app/src/main/assets/live2d/xingyue_shuimu_safe ^
    --out app/src/main/assets/live2d/xingyue_shuimu_safe/xingyue_shuimu_profile.json
"""
import asyncio, json, os, sys, argparse, time
import websockets

HERE = os.path.dirname(os.path.abspath(__file__))
WS_URL = "ws://127.0.0.1:8001"
TOKEN_FILE = os.path.join(HERE, "vts_token.json")
CAPTURES_DIR = os.path.join(HERE, "captures")
PLUGIN_NAME = "XingyueParamCapture"
PLUGIN_DEV = "MindIsle"

PHASES = [
    ("idle",        "保持静止，不要动模型"),
    ("head",        "左右看、上下看、左右晃头"),
    ("body",        "身体左右摆动，上半身左右晃"),
    ("hair",        "轻轻左右晃头让头发自然摆动"),
    ("cloth",       "轻轻左右晃身体让衣服裙摆飘动"),
    ("accessory",   "切换饰品开关（水母/兽耳/发夹等）"),
    ("extra",       "水母/触须/特殊部件摆动（如果有的话）"),
    ("expression",  "切换表情（脸红/生气/星星眼等）"),
]

rid = 0
def next_rid():
    global rid; rid += 1; return str(rid)

def build(msg_type, data=None):
    return json.dumps({"apiName": "VTubeStudioPublicAPI", "apiVersion": "1.0",
        "requestID": next_rid(), "messageType": msg_type, "data": data or {}})

def load_token():
    if os.path.exists(TOKEN_FILE):
        try:
            with open(TOKEN_FILE) as f: return json.load(f).get("token")
        except: pass
    return None

def save_token(t):
    with open(TOKEN_FILE, "w") as f: json.dump({"token": t}, f)

async def recv_msg(ws, timeout):
    raw = await asyncio.wait_for(ws.recv(), timeout=timeout)
    return json.loads(raw)

def read_local_file(path):
    if os.path.exists(path):
        with open(path, encoding="utf-8") as f: return json.load(f)
    return None

# ── analysis ──
def param_range(samples, key):
    vals = [s['parameters'].get(key, 0) for s in samples if key in s['parameters']]
    if not vals: return None
    return {"min": min(vals), "max": max(vals), "range": max(vals) - min(vals), "avg": sum(vals)/len(vals)}

def parse_vts_mapping(vtube):
    """Parse VTube Studio .vtube.json and return {live2dParamId: vtsRole}.
    Roles: headX, headY, eyeX, eyeY, bodyX, bodyY, expression, skip.
    """
    result = {}
    if not vtube: return result
    params = vtube.get("ParameterSettings", [])
    VTS_ROLE = {
        "FaceAngleX": "headX", "FaceAngleZ": "headX",
        "FaceAngleY": "headY",
        "EyeLeftX": "eyeX", "EyeRightX": "eyeX", "EyeX": "eyeX",
        "EyeLeftY": "eyeY", "EyeRightY": "eyeY", "EyeY": "eyeY",
        "BodyAngleX": "bodyX", "BodyAngleZ": "bodyX",
        "BodyAngleY": "bodyY",
        "BrowLeftY": "expression", "BrowRightY": "expression",
        "MouthSmile": "expression", "MouthOpen": "expression",
        "MouthX": "expression", "MouthShrug": "expression",
        "CheekPuff": "expression", "TongueOut": "expression",
        "JawOpen": "expression", "MouthFunnel": "expression",
        "MouthPucker": "expression", "MouthPressLipOpen": "expression",
    }
    for p in params:
        inp = p.get("Input", "")
        live2d_id = p.get("OutputLive2D", "")  # actual Live2D parameter ID
        if not live2d_id:
            continue  # skip entries without OutputLive2D
        role = VTS_ROLE.get(inp, "skip")
        if role != "skip":
            result[live2d_id] = role
    return result

def classify_param(name, phase_ranges, idle_range, local_info):
    ir = idle_range.get(name, {}).get('range', 0)
    best_phase, best_range = "unknown", 0
    for pn, pr in phase_ranges.items():
        ri = pr.get(name, {})
        eff = max(0, ri.get('range', 0) - ir)
        if eff > best_range: best_phase, best_range = pn, eff

    label = local_info.get('label', name)
    rec = {"label": label, "phase": best_phase, "effectiveRange": round(best_range, 2)}
    if best_range < 0.3: rec["recommended"] = "skip"
    elif best_phase in ("head",):    rec["recommendedRole"] = "headX"
    elif best_phase in ("body",):    rec["recommendedRole"] = "bodyX"
    elif best_phase in ("hair",):    rec["recommendedRole"] = "hairX"
    elif best_phase in ("cloth",):   rec["recommendedRole"] = "clothX"
    elif best_phase in ("accessory", "extra"): rec["recommendedRole"] = "accessoryX"
    else: rec["recommendedRole"] = "unknown"
    return rec

def suggest_gain(r, role):
    rng = r.get('range', 0)
    if rng < 0.1: return 0
    if role in ("headX",):           return round(min(rng * 0.3, 25), 1)
    if role in ("bodyX",):           return round(min(rng * 0.25, 15), 1)
    if role in ("hairX", "clothX"):  return round(min(rng * 0.2, 0.6), 2)
    return round(min(rng * 0.15, 1.0), 2)

CATEGORY_PATTERNS = {
    "hair": ["hair", "brow", "front", "back hair", "刘海", "碎发"],
    "cloth": ["dress", "skirt", "cloth", "sleeve", "裙", "衣服", "袖", "飘带"],
    "accessory": ["accessory", "item", "饰品", "耳", "发夹", "光环", "翅膀", "尾巴", "牙"],
    "extra": ["jelly", "水母", "触须", "tentacle"],
}

def infer_category(name, cdi_params):
    lower = name.lower()
    for cat, pats in CATEGORY_PATTERNS.items():
        for p in pats:
            if p.lower() in lower: return cat
    # Check CDI label
    for entry in cdi_params:
        if entry.get('Id') == name:
            label = entry.get('Name', '')
            for cat, pats in CATEGORY_PATTERNS.items():
                for p in pats:
                    if p in label: return cat
    return "body"

# ── main ──
async def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--model-id", required=True)
    parser.add_argument("--model-dir", required=True)
    parser.add_argument("--out", required=True)
    parser.add_argument("--safe-head-only", action="store_true",
        help="Only generate headX/headY/eyeX/eyeY/bodyX/disabled, skip hair/cloth/accessory")
    args = parser.parse_args()

    model_id = args.model_id
    model_dir = args.model_dir
    out_path = args.out
    os.makedirs(CAPTURES_DIR, exist_ok=True)

    # Read local model files (safe — skip if dir empty or files missing)
    cdi3 = vtube = physics3 = None
    try:
        if os.path.isdir(model_dir):
            cdi_files = [f for f in os.listdir(model_dir) if f.endswith('.cdi3.json')]
            if cdi_files: cdi3 = read_local_file(os.path.join(model_dir, cdi_files[0]))
            vtube_files = [f for f in os.listdir(model_dir) if f.endswith('.vtube.json')]
            if vtube_files: vtube = read_local_file(os.path.join(model_dir, vtube_files[0]))
            physics_files = [f for f in os.listdir(model_dir) if f.endswith('.physics3.json')]
            if physics_files: physics3 = read_local_file(os.path.join(model_dir, physics_files[0]))
    except Exception:
        pass

    cdi_params = cdi3.get('Parameters', []) if cdi3 else []
    param_labels = {p['Id']: p.get('Name', p['Id']) for p in cdi_params}
    physics_inputs = set()
    if physics3:
        for ps in physics3.get('PhysicsSettings', []):
            for inp in ps.get('Input', []):
                sid = inp.get('Source', {}).get('Id', '')
                if sid: physics_inputs.add(sid)

    # Connect
    print(f"connecting {WS_URL}")
    async with websockets.connect(WS_URL, ping_interval=10, ping_timeout=20, close_timeout=5) as ws:
        print("connected")
        await ws.send(build("APIStateRequest"))
        st = await recv_msg(ws, 10)
        vts_ver = st['data'].get('vTubeStudioVersion', '?')
        print(f"VTS {vts_ver}")

        token = load_token()
        if not token:
            await ws.send(build("AuthenticationTokenRequest", {"pluginName": PLUGIN_NAME, "pluginDeveloper": PLUGIN_DEV}))
            print("waiting for VTube Studio authorization... Click Allow in VTS (Plugins → Permissions)")
            try:
                tr = await recv_msg(ws, 120)
                token = tr['data']['authenticationToken']; save_token(token)
            except asyncio.TimeoutError:
                print("ERROR: No response after 120s"); return

        await ws.send(build("AuthenticationRequest", {"pluginName": PLUGIN_NAME, "pluginDeveloper": PLUGIN_DEV, "authenticationToken": token}))
        ar = await recv_msg(ws, 10)
        if not ar['data'].get('authenticated'):
            print("auth failed"); return
        print("authenticated")

        # Phased capture
        raw = {"modelId": model_id, "capturedAt": time.strftime("%Y-%m-%dT%H:%M:%S"), "vtsVersion": vts_ver, "phases": {}}
        for phase_name, instruction in PHASES:
            print(f"\n{'='*50}")
            print(f"Phase: {phase_name}")
            print(f"Action: {instruction}")
            await asyncio.to_thread(input, "Press Enter to START capture...")
            print("Capturing... Press Enter to STOP this phase.")

            samples = []
            stop_task = asyncio.create_task(asyncio.to_thread(input, ""))
            start = asyncio.get_event_loop().time()
            while not stop_task.done():
                try:
                    await ws.send(build("Live2DParameterListRequest"))
                    resp = await recv_msg(ws, 15)
                    params = resp.get('data', {}).get('parameters', [])
                    if params:
                        sample = {"time": round(asyncio.get_event_loop().time() - start, 2), "parameters": {}}
                        for p in params: sample['parameters'][p['name']] = round(p.get('value', 0), 4)
                        samples.append(sample)
                except (websockets.ConnectionClosedError, ConnectionAbortedError, OSError) as e:
                    print(f"  connection lost: {e}  reconnecting...")
                    try: await ws.close()
                    except: pass
                    await asyncio.sleep(1)
                    ws = await websockets.connect(WS_URL, ping_interval=10, ping_timeout=20, close_timeout=5)
                    await ws.send(build("AuthenticationRequest", {"pluginName": PLUGIN_NAME, "pluginDeveloper": PLUGIN_DEV, "authenticationToken": token}))
                    ar = await recv_msg(ws, 15)
                    if not ar['data'].get('authenticated'): raise Exception("reauth failed")
                    print("  reconnected")
                await asyncio.sleep(0.1)
            await stop_task
            raw['phases'][phase_name] = {"samples": samples}
            print(f"  Phase {phase_name} captured {len(samples)} samples.")
            if len(samples) < 5:
                print(f"  WARNING: only {len(samples)} samples captured, consider re-collecting this phase.")

        # Save raw
        raw_path = os.path.join(CAPTURES_DIR, f"{model_id}_raw_capture.json")
        with open(raw_path, "w", encoding="utf-8") as f: json.dump(raw, f, indent=2, ensure_ascii=False)
        print(f"\nsaved raw: {raw_path}")

        # ── Analysis ──
        # Skip phases with too few samples, and expression (not look-follow relevant)
        SKIP_ANALYSIS = {"expression"}
        phase_ranges: dict = {}
        idle_range: dict = {}
        for pn, pd in raw['phases'].items():
            if pn in SKIP_ANALYSIS: continue
            ss = pd['samples']
            if len(ss) < 10:
                print(f"  skipping {pn}: only {len(ss)} samples (need >= 10)")
                continue
            pr: dict = {}
            for s in ss:
                for k in s['parameters']:
                    if k not in pr: pr[k] = []
                    pr[k].append(s['parameters'][k])
            ranges = {k: {"min": min(v), "max": max(v), "range": max(v)-min(v), "avg": sum(v)/len(v)} for k, v in pr.items()}
            if pn == "idle": idle_range = ranges
            else: phase_ranges[pn] = ranges

        # ── Build profile ──
        profile = {"modelId": model_id, "generatedAt": time.strftime("%Y-%m-%dT%H:%M:%S"),
                   "source": {"vtsCapture": raw_path, "cdi3": bool(cdi3), "physics3": bool(physics3), "vtube": bool(vtube)},
                   "parameters": {}, "roles": {}, "presets": {"soft": {}, "vts": {}, "fullPhysics": {}}}
        assigned = set()  # params already assigned by vtube.json

        # Step 1: vtube.json mapping (highest priority)
        vts_map = parse_vts_mapping(vtube)
        if vts_map:
            print("\n  VTS mapping found, using as primary role source")
        for pid, role in vts_map.items():
            if role in ("skip", "expression"): continue
            rng_data = None
            for pr in phase_ranges.values():
                if pid in pr: rng_data = pr[pid]; break
            if not rng_data:
                rng_data = {"range": 10, "min": -10, "max": 10, "avg": 0}
            gain = suggest_gain(rng_data, role)
            if role in ("headX", "headY"): gain = max(gain, 8)
            entry = {"id": pid, "gain": gain}
            if role not in profile['roles']: profile['roles'][role] = []
            profile['roles'][role].append(entry)
            assigned.add(pid)

        # Step 2: phase capture for remaining roles (hair/cloth/accessory)
        SUPPLEMENTAL = {"hairX", "hairY", "clothX", "clothY", "accessoryX"}
        if not args.safe_head_only:
            all_names = set().union(*[set(r.keys()) for r in phase_ranges.values()])
            for name in sorted(all_names):
                if name in assigned: continue
                info = classify_param(name, phase_ranges, idle_range, {"label": param_labels.get(name, name)})
                profile['parameters'][name] = info
                if info.get('recommended') == 'skip': continue
                role = info.get('recommendedRole', 'unknown')
                # Only allow supplemental roles from phase capture
                if role not in SUPPLEMENTAL: continue
                rng_data = next((r[name] for r in phase_ranges.values() if name in r), None)
                if not rng_data: continue
                gain = min(suggest_gain(rng_data, role), 0.6)
                if gain <= 0: continue
                entry = {"id": name, "gain": round(gain, 2)}
                if role in ("hairX", "clothX", "accessoryX"):
                    entry["inertia"] = round(gain * 0.5, 2)
                if role not in profile['roles']: profile['roles'][role] = []
                profile['roles'][role].append(entry)
                assigned.add(name)
        else:
            print("  safe-head-only: skipping hair/cloth/accessory phase analysis")

        # Step 3: disabled params
        for pr in phase_ranges.values():
            for name in pr:
                if 'Y' in name.upper() and ('bodyangle' in name.lower() or 'angle' in name.lower()):
                    if name in assigned: continue
                    if "disabled" not in profile['roles']: profile['roles']['disabled'] = []
                    if not any(d['id'] == name for d in profile['roles']['disabled']):
                        profile['roles']['disabled'].append({"id": name, "reason": "Y-axis may cause tilt"})
                        assigned.add(name)

        # Validate: no VTS display names leaked as parameter IDs
        BANNED = {"Eye X", "Eye Y", "Face Left", "Face Up", "Face Lean",
                   "Body Rotation X", "Body Rotation Y", "Body Rotation Z",
                   "Eye Open", "Eye Smile", "Mouth", "Brow", "Cheek", "Tongue"}
        for role, entries in profile['roles'].items():
            for e in entries:
                if any(b.lower() in e['id'].lower() for b in BANNED):
                    print(f"  ERROR: role {role} has VTS display name as id: {e['id']}")
                    print(f"  This is not a Live2D parameter ID. Check vtube.json OutputLive2D field.")

        with open(out_path, "w", encoding="utf-8") as f: json.dump(profile, f, indent=2, ensure_ascii=False)
        print(f"saved profile: {out_path}")

        # Summary
        print("\n=== profile summary ===")
        role_order = ["eyeX", "eyeY", "headX", "headY", "bodyX", "bodyY",
                      "hairX", "hairY", "clothX", "clothY", "accessoryX", "disabled"]
        for role in role_order:
            entries = profile['roles'].get(role, [])
            if entries:
                names = [e['id'] for e in entries]
                print(f"  {role}: {len(entries)}  {names[:10]}{'...' if len(names) > 10 else ''}")
        total = sum(len(v) for v in profile['roles'].values())
        print(f"  total: {total} params")

asyncio.run(main())
