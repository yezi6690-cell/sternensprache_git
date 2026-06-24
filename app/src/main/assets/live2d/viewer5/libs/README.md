# viewer5 libs

This directory is reserved for the official Live2D Cubism 5 SDK for Web runtime.

Do not copy the existing Cubism 4 files here as a workaround. The current
`mindisle-cubism5-adapter.js` is only a diagnostic stub so the Android app can
route Cubism 5 models to `viewer5` and verify asset loading through
WebViewAssetLoader.

Expected next step:

1. Download the official Live2D Cubism SDK for Web from Live2D.
2. Copy the Cubism 5 Core and Framework files required by the official sample.
3. Replace `mindisle-cubism5-adapter.js` with a minimal Cubism 5 WebGL loader.
4. Keep the official license and notice files with the runtime.
