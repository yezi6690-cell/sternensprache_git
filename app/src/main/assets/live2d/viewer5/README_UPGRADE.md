# MindIsle viewer5 upgrade notes

`viewer5` is intentionally separate from the existing `viewer` directory.

- `viewer` keeps the current Cubism 4 / pixi-live2d-display chain for old
  models such as whitecat.
- `viewer5` is the Cubism 5 route for moc3 ver 5 models such as 星月水母.

The current first stage only verifies that WebViewAssetLoader can fetch the
星月水母 safe model resources:

- `../xingyue_shuimu_safe/xingyue_shuimu_safe.model3.json`
- `../xingyue_shuimu_safe/xingyue_shuimu.moc3`
- `../xingyue_shuimu_safe/textures/texture_00.png`
- `../xingyue_shuimu_safe/textures/texture_01.png`

Do not replace the old viewer files. Add the official Cubism 5 Web SDK runtime
to `viewer5/libs` and implement the loader in `mindisle-cubism5-adapter.js`.
