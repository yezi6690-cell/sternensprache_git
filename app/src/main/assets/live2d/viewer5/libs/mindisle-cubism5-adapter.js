(function () {
    "use strict";

    window.MindIsleCubism5Adapter = {
        async load(modelPath) {
            console.log("[Live2DViewer5] official Cubism 5 bundle owns loading", modelPath);
            return {
                handledBy: "viewer5/assets/mindisle-viewer5.js"
            };
        }
    };
})();
