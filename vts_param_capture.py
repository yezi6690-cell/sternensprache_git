"""
VTube Studio parameter capture — WebSocket.
Uses Live2DParameterListRequest (one request = list + current values).
"""
import asyncio
import json
import os
import websockets

WS_URL = "ws://127.0.0.1:8001"
TOKEN_FILE = os.path.join(os.path.dirname(__file__), "vts_token.json")
OUTPUT = os.path.join(os.path.dirname(__file__), "xingyue_vts_param_capture.json")
PLUGIN_NAME = "XingyueParamCapture"
PLUGIN_DEV = "MindIsle"

rid = 0
def next_rid():
    global rid; rid += 1; return str(rid)

def send_msg(msg_type, data=None):
    msg = json.dumps({
        "apiName": "VTubeStudioPublicAPI",
        "apiVersion": "1.0",
        "requestID": next_rid(),
        "messageType": msg_type,
        "data": data or {}
    })
    print(f"  -> {msg_type}")
    return msg

async def recv_msg(ws, timeout):
    raw = await asyncio.wait_for(ws.recv(), timeout=timeout)
    obj = json.loads(raw)
    mt = obj.get("messageType", "?")
    print(f"  <- {mt}")
    return obj


def load_token():
    if os.path.exists(TOKEN_FILE):
        try:
            with open(TOKEN_FILE) as f:
                return json.load(f).get("token")
        except:
            pass
    return None


def save_token(token):
    with open(TOKEN_FILE, "w") as f:
        json.dump({"token": token}, f)


async def main():
    print("=== VTube Studio WS param capture ===")
    print(f"connecting {WS_URL}")

    async with websockets.connect(WS_URL, ping_interval=30) as ws:
        print("connected")

        # 1) Test connection
        await ws.send(send_msg("APIStateRequest"))
        state = await recv_msg(ws, 10)
        print(f"  API active={state['data'].get('active')}, v={state['data'].get('vTubeStudioVersion')}")

        # 2) Auth token
        token = load_token()
        if token:
            print("  using saved token from vts_token.json")
        else:
            print("  requesting new token")
            print("  waiting for VTube Studio authorization...")
            print("  please click Allow in VTube Studio (Plugins → Permissions)")
            await ws.send(send_msg("AuthenticationTokenRequest", {
                "pluginName": PLUGIN_NAME,
                "pluginDeveloper": PLUGIN_DEV
            }))
            try:
                token_resp = await recv_msg(ws, 120)
                token = token_resp['data']['authenticationToken']
                save_token(token)
                print(f"  token saved: {token[:8]}...")
            except asyncio.TimeoutError:
                print("  ERROR: No response after 120s.")
                print("  Go to VTube Studio → Plugins → Permission and allow XingyueParamCapture.")
                return

        # 3) Authenticate
        await ws.send(send_msg("AuthenticationRequest", {
            "pluginName": PLUGIN_NAME,
            "pluginDeveloper": PLUGIN_DEV,
            "authenticationToken": token
        }))
        auth_resp = await recv_msg(ws, 10)
        if not auth_resp['data'].get('authenticated'):
            print(f"  auth failed, clearing token")
            if os.path.exists(TOKEN_FILE):
                os.remove(TOKEN_FILE)
            return
        print("  authenticated")

        # 4) Capture — Live2DParameterListRequest gives list + values in one call
        print("\n  Perform: look left/right, sway body, toggle accessories/expressions")
        print("  capturing 30s...")
        samples = []
        start = asyncio.get_event_loop().time()
        while asyncio.get_event_loop().time() - start < 30:
            await ws.send(send_msg("Live2DParameterListRequest"))
            resp = await recv_msg(ws, 5)
            params = resp.get('data', {}).get('parameters', [])
            if params:
                sample = {"t": round(asyncio.get_event_loop().time() - start, 2)}
                for p in params:
                    sample[p['name']] = round(p.get('value', 0), 4)
                samples.append(sample)
            await asyncio.sleep(0.1)

        print(f"\n  captured {len(samples)} samples")

        # 5) Analyze
        all_keys = set()
        for s in samples:
            all_keys.update(k for k in s if k != 't')
        stats = {}
        for key in sorted(all_keys):
            vals = [s[key] for s in samples if key in s]
            if not vals:
                continue
            mn, mx = min(vals), max(vals)
            avg = sum(vals) / len(vals)
            rng = mx - mn
            stats[key] = {"min": mn, "max": mx, "avg": round(avg, 4), "range": round(rng, 4)}

        top = sorted(
            [(k, v) for k, v in stats.items() if v['range'] > 0.01],
            key=lambda x: -x[1]['range']
        )

        result = {
            "model": state['data'].get('modelName', '?'),
            "samples": len(samples),
            "parameters": stats,
            "top_by_range": top
        }
        with open(OUTPUT, "w", encoding="utf-8") as f:
            json.dump(result, f, indent=2, ensure_ascii=False)
        print(f"  saved {OUTPUT}")

        print("\n=== top changed parameters ===")
        for name, s in top[:30]:
            rng = s['range']
            tag = " ← HIGH" if rng > 0.3 else (" ← MED" if rng > 0.1 else "")
            print(f"  {name:30s}  range={rng:.4f}  avg={s['avg']:.3f}{tag}")


asyncio.run(main())
