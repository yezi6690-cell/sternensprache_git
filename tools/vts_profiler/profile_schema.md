# Model Profile Schema

Profile file: `<model_id>_profile.json`, placed alongside model files.

## Top-level
```json
{
  "modelId": "xingyue_shuimu",
  "generatedAt": "ISO timestamp",
  "source": { "vtsCapture": "...", "cdi3": true, "physics3": true, "vtube": true },
  "roles": { ... },
  "presets": { ... }
}
```

## Roles
Each role is an array of parameter entries:
```json
{
  "roleName": [
    { "id": "ParamName", "gain": 18.0 },
    { "id": "ParamHairX", "gain": 0.4, "inertia": 0.5 }
  ]
}
```

Standard roles:
- `eyeX`, `eyeY` — eye tracking
- `headX`, `headY` — head rotation
- `bodyX` — body sway
- `hairX`, `hairY` — hair movement
- `clothX`, `clothY` — clothing/skirt
- `accessoryX` — accessories
- `extraX` — special parts (jellyfish, tentacles)
- `disabled` — params to explicitly disable

## Presets
```json
{
  "presetName": {
    "scale": 1.0,
    "inertiaGain": 0.5,
    "hairInertiaGain": 0.8,
    "clothInertiaGain": 0.6,
    "jellyInertiaGain": 1.0
  }
}
```

## How viewer5 uses it
1. Load `<model_id>_profile.json` on model init
2. Map `roles.*` to `CubismLook` parameters with specified gains
3. Apply `presets.*` to inertia processor
4. If profile missing → use built-in defaults
