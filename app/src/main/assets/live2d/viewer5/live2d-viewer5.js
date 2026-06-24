(function () {
    "use strict";

    const params = new URLSearchParams(window.location.search);
    const modelPath = params.get("model") || "../xingyue_shuimu_safe/xingyue_shuimu_safe.model3.json";
    const modelId = params.get("modelId") || "unknown";
    const canvas = document.getElementById("live2d-canvas");
    const stage = document.getElementById("stage");
    const errorView = document.getElementById("error");

    if (params.get("floating") === "1") {
        document.body.classList.add("floating");
    }

    function showError(message) {
        errorView.textContent = message || "该模型为 Cubism 5 模型，正在适配新版运行库。";
        errorView.style.display = "block";
        try {
            window.AndroidLive2D?.onModelLoadFailed(errorView.textContent);
        } catch (error) {
            console.warn("[Live2DViewer5] Android error bridge ignored", error);
        }
    }

    function modelDirectory(path) {
        const text = String(path || "");
        const index = text.lastIndexOf("/");
        return index >= 0 ? text.slice(0, index + 1) : "";
    }

    function joinRelative(baseDir, relativePath) {
        const parts = baseDir.split("/").filter(Boolean);
        String(relativePath || "")
            .replace(/\\/g, "/")
            .split("/")
            .filter(Boolean)
            .forEach(function (segment) {
                if (segment === ".") return;
                if (segment === "..") {
                    if (parts.length > 0) parts.pop();
                    return;
                }
                parts.push(segment);
            });
        return parts.join("/");
    }

    async function fetchJson(url, label) {
        const response = await fetchWithLog(url, label);
        return response.json();
    }

    async function fetchWithLog(url, label) {
        try {
            const response = await fetch(url);
            console.log("[Live2DViewer5] fetch " + label + " =", response.status, response.ok);
            if (!response.ok) {
                throw new Error(label + " fetch failed: " + response.status + " " + response.statusText);
            }
            return response;
        } catch (error) {
            console.error("[Live2DViewer5] fetch " + label + " failed", error);
            throw error;
        }
    }

    async function preflightModelAssets() {
        const modelJson = await fetchJson(modelPath, "model3");
        const baseDir = modelDirectory(modelPath);
        const refs = modelJson.FileReferences || {};

        if (refs.Moc) {
            await fetchWithLog(joinRelative(baseDir, refs.Moc), "moc3");
        }

        if (Array.isArray(refs.Textures)) {
            for (let index = 0; index < refs.Textures.length; index += 1) {
                await fetchWithLog(joinRelative(baseDir, refs.Textures[index]), "texture_" + index);
            }
        }

        if (refs.Physics) {
            await fetchWithLog(joinRelative(baseDir, refs.Physics), "physics");
        }

        return modelJson;
    }

    function resizeCanvas() {
        const rect = stage.getBoundingClientRect();
        const resolution = Math.min(Math.max(window.devicePixelRatio || 1, 1), 3);
        const width = Math.max(1, Math.round(rect.width));
        const height = Math.max(1, Math.round(rect.height));
        canvas.style.width = width + "px";
        canvas.style.height = height + "px";
        canvas.width = Math.round(width * resolution);
        canvas.height = Math.round(height * resolution);
        return { width, height, resolution };
    }

    async function loadModel() {
        console.log("[Live2DViewer5] start");
        console.log("[Live2DViewer5] modelId =", modelId);
        console.log("[Live2DViewer5] modelPath =", modelPath);

        try {
            resizeCanvas();
            const modelJson = await preflightModelAssets();
            const adapter = window.MindIsleCubism5Adapter;
            if (!adapter || typeof adapter.load !== "function") {
                throw new Error("Cubism 5 official Web SDK adapter is not installed in viewer5/libs.");
            }

            console.log("[Live2DViewer5] Cubism Core loaded");
            await adapter.load({
                canvas,
                stage,
                modelPath,
                modelJson
            });
            console.log("[Live2DViewer5] model loaded success");
            errorView.style.display = "none";
        } catch (error) {
            console.error("[Live2DViewer5] load failed");
            console.error("[Live2DViewer5] error name =", error?.name);
            console.error("[Live2DViewer5] error message =", error?.message);
            console.error("[Live2DViewer5] error stack =", error?.stack);
            showError("该模型为 Cubism 5 模型，正在适配新版运行库。");
        }
    }

    window.addEventListener("resize", resizeCanvas);
    loadModel();
})();
