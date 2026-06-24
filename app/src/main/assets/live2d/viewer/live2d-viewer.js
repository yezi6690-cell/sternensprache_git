(function () {
    "use strict";

    const DEFAULT_ERROR = "模型加载失败，请检查资源路径";
    const params = new URLSearchParams(window.location.search);
    const canvas = document.getElementById("live2d-canvas");
    const stage = document.getElementById("stage");
    const errorView = document.getElementById("error");
    if (params.get("floating") === "1") {
        document.body.classList.add("floating");
    }

    let modelPath = params.get("model") || "../models/qq/1-qq.model3.json";
    let modelId = params.get("modelId") || inferModelId(modelPath);
    let app = null;
    let model = null;
    let currentState = "idle";
    let baseTransform = { scale: 1, canvasWidth: 1, canvasHeight: 1, bounds: null, padding: 10 };
    let modelTransform = { scale: 1, offsetX: 0, offsetY: 0 };
    let eyeCurrentX = 0;
    let eyeCurrentY = 0;
    let headCurrentX = 0;
    let headCurrentY = 0;
    let bodyCurrentX = 0;
    let bodyCurrentY = 0;
    let eyeTargetX = 0;
    let eyeTargetY = 0;
    let headTargetX = 0;
    let headTargetY = 0;
    let bodyTargetX = 0;
    let bodyTargetY = 0;
    let lookResetTimer = null;
    let headDelayTimer = null;
    let bodyDelayTimer = null;
    let parameterRanges = {};
    let smoothLookParams = {};

    const LOOK_PARAMETER_CONFIG = {
        ParamEyeBallX: { axis: "x", group: "eye" },
        ParamEyeBallY: { axis: "y", group: "eye" },
        ParamAngleX: { axis: "x", group: "head" },
        ParamAngleY: { axis: "y", group: "head" },
        ParamAngleZ: { axis: "x", group: "head" },
        ParamBodyAngleX: { axis: "x", group: "body" },
        ParamBodyAngleY: { axis: "y", group: "body" }
    };

    const LOOK_SMOOTH_FACTORS = {
        eye: 0.12,
        head: 0.06,
        body: 0.035
    };

    function renderResolution() {
        return Math.min(Math.max(window.devicePixelRatio || 1, 1), 3);
    }

    function resizeCanvasForHighDpi() {
        const rect = stage.getBoundingClientRect();
        const resolution = renderResolution();
        const width = Math.max(1, Math.round(rect.width));
        const height = Math.max(1, Math.round(rect.height));
        const pixelWidth = Math.round(width * resolution);
        const pixelHeight = Math.round(height * resolution);

        canvas.style.width = width + "px";
        canvas.style.height = height + "px";
        if (canvas.width !== pixelWidth || canvas.height !== pixelHeight) {
            canvas.width = pixelWidth;
            canvas.height = pixelHeight;
        }
        return { width, height, resolution };
    }

    function showError(message) {
        const errorMessage = message || DEFAULT_ERROR;
        errorView.textContent = errorMessage;
        errorView.style.display = "block";
        try {
            window.AndroidLive2D?.onModelLoadFailed(errorMessage);
        } catch (error) {
            console.warn("[MindIsle Live2D] Android error bridge ignored", error);
        }
    }

    function currentModelErrorMessage() {
        const path = String(modelPath || "");
        const modelErrors = [
            ["xingyue_shuimu", "星月水母模型加载失败，请检查模型文件是否完整。"],
            ["xiaotuji", "小兔叽模型加载失败，请检查模型文件是否完整。"],
            ["hehua_xiaojiangshi", "荷花小僵尸模型加载失败，请检查模型文件是否完整。"],
            ["tianshi_xiaoxiaoyang", "天使小小羊模型加载失败，请检查模型文件是否完整。"],
            ["xiaoxiong", "小熊模型加载失败，请检查模型文件是否完整。"]
        ];
        const match = modelErrors.find(([key]) => path.includes(key));
        return match ? match[1] : DEFAULT_ERROR;
    }

    function inferModelId(path) {
        const text = String(path || "");
        if (text.includes("xingyue_shuimu")) return "xingyue_shuimu";
        if (text.includes("xiaotuji")) return "xiaotuji";
        if (text.includes("hehua_xiaojiangshi")) return "hehua_xiaojiangshi";
        if (text.includes("tianshi_xiaoxiaoyang")) return "tianshi_xiaoxiaoyang";
        if (text.includes("xiaoxiong")) return "xiaoxiong";
        if (text.includes("whitecat")) return "baimao";
        if (text.includes("qq")) return "xiaoyu";
        return "unknown";
    }

    function clearError() {
        errorView.style.display = "none";
    }

    async function fetchProbe(url) {
        try {
            const response = await fetch(url);
            console.log("[Live2DViewer] fetch", url, response.status, response.ok);
            return response.ok;
        } catch (error) {
            console.error("[Live2DViewer] fetch failed", url, error);
            return false;
        }
    }

    async function checkAsset(url) {
        return fetchProbe(url);
    }

    function modelDirectory(path) {
        const text = String(path || "");
        const index = text.lastIndexOf("/");
        return index >= 0 ? text.slice(0, index + 1) : "";
    }

    async function runPreflightFetches(path) {
        await checkAsset(path);
        if (String(path).includes("xingyue_shuimu_safe")) {
            await checkAsset("../xingyue_shuimu_safe/xingyue_shuimu.moc3");
            await checkAsset("../xingyue_shuimu_safe/textures/texture_00.png");
            await checkAsset("../xingyue_shuimu_safe/textures/texture_01.png");
        } else {
            const dir = modelDirectory(path);
            await checkAsset(dir + path.split("/").pop());
        }
    }

    function fitModel() {
        if (!model || !stage) return;
        const currentMultiplier = clamp(modelTransform.scale || 1, 0.8, 1.2);
        modelTransform = { scale: currentMultiplier, offsetX: 0, offsetY: 0 };
        const size = resizeCanvasForHighDpi();
        const width = size.width || stage.clientWidth || window.innerWidth;
        const height = size.height || stage.clientHeight || window.innerHeight;
        const bounds = measureNaturalModelBounds();
        const padding = Math.max(8, Math.min(width, height) * 0.04);
        const availableWidth = Math.max(1, width - padding * 2);
        const availableHeight = Math.max(1, height - padding * 2);
        const scale = Math.min(
            availableWidth / bounds.width,
            availableHeight / bounds.height
        ) / currentMultiplier;

        baseTransform = {
            scale,
            canvasWidth: width,
            canvasHeight: height,
            bounds,
            padding
        };
        applyModelTransform();
    }

    function applyModelTransform() {
        if (!model) return;
        const scale = baseTransform.scale * modelTransform.scale;
        if (typeof model.scale?.set === "function") {
            model.scale.set(scale);
        } else {
            model.scale = { x: scale, y: scale };
        }
        const bounds = baseTransform.bounds || measureNaturalModelBounds();
        const targetCenterX = baseTransform.canvasWidth / 2;
        const targetBottomY = baseTransform.canvasHeight - baseTransform.padding;
        const visualCenterX = (bounds.left + bounds.width / 2) * scale;
        const visualBottomY = bounds.bottom * scale;
        model.x = targetCenterX - visualCenterX + modelTransform.offsetX;
        model.y = targetBottomY - visualBottomY + modelTransform.offsetY;
    }

    function setModelScaleForMeasure(scale) {
        if (typeof model.scale?.set === "function") {
            model.scale.set(scale);
        } else {
            model.scale = { x: scale, y: scale };
        }
    }

    function measureNaturalModelBounds() {
        if (!model) {
            return { left: 0, top: 0, right: 1, bottom: 1, width: 1, height: 1 };
        }

        const previousX = Number(model.x) || 0;
        const previousY = Number(model.y) || 0;
        const previousScaleX = Number(model.scale?.x ?? model.scale ?? 1) || 1;
        const previousScaleY = Number(model.scale?.y ?? model.scale ?? 1) || previousScaleX;

        model.x = 0;
        model.y = 0;
        setModelScaleForMeasure(1);

        let bounds = null;
        try {
            bounds = typeof model.getBounds === "function" ? model.getBounds(false) : null;
        } catch (error) {
            bounds = null;
        }

        let left = Number(bounds?.left ?? bounds?.x ?? 0);
        let top = Number(bounds?.top ?? bounds?.y ?? 0);
        let width = Number(bounds?.width ?? model.width ?? 1);
        let height = Number(bounds?.height ?? model.height ?? 1);

        if (!Number.isFinite(width) || width <= 1) width = Math.max(1, Number(model.width) || 1);
        if (!Number.isFinite(height) || height <= 1) height = Math.max(1, Number(model.height) || 1);
        if (!Number.isFinite(left)) left = -width / 2;
        if (!Number.isFinite(top)) top = -height;

        setModelScaleForMeasure(1);
        model.x = previousX;
        model.y = previousY;
        if (typeof model.scale?.set === "function") {
            model.scale.set(previousScaleX, previousScaleY);
        } else {
            model.scale = { x: previousScaleX, y: previousScaleY };
        }

        return {
            left,
            top,
            right: left + width,
            bottom: top + height,
            width,
            height
        };
    }

    async function loadWithPixiLive2D(path) {
        if (!window.PIXI || !window.PIXI.live2d || !window.PIXI.live2d.Live2DModel) {
            return false;
        }
        resizeCanvasForHighDpi();

        if (app && typeof app.destroy === "function") {
            app.destroy(true);
        }

        app = new window.PIXI.Application({
            view: canvas,
            resizeTo: stage,
            backgroundAlpha: 0,
            autoDensity: true,
            resolution: renderResolution(),
            antialias: true,
            autoStart: true
        });

        model = null;
        modelTransform = { scale: 1, offsetX: 0, offsetY: 0 };
        console.log("[MindIsle Live2D] PIXI Live2D loader start:", path);
        try {
            model = await window.PIXI.live2d.Live2DModel.from(path);
        } catch (error) {
            console.error("[Live2DViewer] model load failed");
            console.error("[Live2DViewer] error name =", error?.name);
            console.error("[Live2DViewer] error message =", error?.message);
            console.error("[Live2DViewer] error stack =", error?.stack);
            console.error("[Live2DViewer] failed modelPath =", modelPath);
            console.error("[MindIsle Live2D] Live2D load failed name:", error?.name);
            console.error("[MindIsle Live2D] Live2D load failed message:", error?.message);
            console.error("[MindIsle Live2D] Live2D load failed stack:", error?.stack);
            throw error;
        }
        console.log("[MindIsle Live2D] PIXI Live2D model loaded:", path);
        app.stage.addChild(model);
        cacheParameterRanges();
        resetLookStateToDefault();
        fitModel();
        return true;
    }

    async function loadWithProjectAdapter(path) {
        if (!window.MindIsleCubismAdapter || typeof window.MindIsleCubismAdapter.load !== "function") {
            return false;
        }
        resizeCanvasForHighDpi();
        model = null;
        modelTransform = { scale: 1, offsetX: 0, offsetY: 0 };
        console.log("[MindIsle Live2D] Project adapter loader start:", path);
        try {
            model = await window.MindIsleCubismAdapter.load({
                canvas,
                stage,
                modelPath: path,
                resolution: renderResolution()
            });
        } catch (error) {
            console.error("[Live2DViewer] model load failed");
            console.error("[Live2DViewer] error name =", error?.name);
            console.error("[Live2DViewer] error message =", error?.message);
            console.error("[Live2DViewer] error stack =", error?.stack);
            console.error("[Live2DViewer] failed modelPath =", modelPath);
            console.error("[MindIsle Live2D] adapter load failed name:", error?.name);
            console.error("[MindIsle Live2D] adapter load failed message:", error?.message);
            console.error("[MindIsle Live2D] adapter load failed stack:", error?.stack);
            throw error;
        }
        console.log("[MindIsle Live2D] Project adapter model loaded:", path);
        cacheParameterRanges();
        resetLookStateToDefault();
        fitModel();
        return true;
    }

    async function loadModel(path) {
        clearError();
        modelPath = path || modelPath;
        modelId = params.get("modelId") || inferModelId(modelPath);
        model = null;
        modelTransform = { scale: 1, offsetX: 0, offsetY: 0 };
        console.log("[Live2DViewer] start load model");
        console.log("[Live2DViewer] modelId =", modelId);
        console.log("[Live2DViewer] modelPath =", modelPath);

        try {
            await runPreflightFetches(modelPath);
            const loaded = await loadWithProjectAdapter(modelPath) || await loadWithPixiLive2D(modelPath);
            if (!loaded) {
                console.error("[Live2DViewer] model load failed");
                console.error("[Live2DViewer] error name =", "NoLive2DLoader");
                console.error("[Live2DViewer] error message =", "No Live2D loader available");
                console.error("[Live2DViewer] error stack =", "");
                console.error("[Live2DViewer] failed modelPath =", modelPath);
                console.error("[MindIsle Live2D] no loader available for model:", modelPath);
                showError(currentModelErrorMessage());
            }
        } catch (error) {
            console.error("[Live2DViewer] model load failed");
            console.error("[Live2DViewer] error name =", error?.name);
            console.error("[Live2DViewer] error message =", error?.message);
            console.error("[Live2DViewer] error stack =", error?.stack);
            console.error("[Live2DViewer] failed modelPath =", modelPath);
            console.warn("[MindIsle Live2D] load failed", error);
            console.error("[MindIsle Live2D] load failed name:", error?.name);
            console.error("[MindIsle Live2D] load failed message:", error?.message);
            console.error("[MindIsle Live2D] load failed stack:", error?.stack);
            showError(currentModelErrorMessage());
        }
    }

    function playMotion(motionName) {
        try {
            if (model?.motion) {
                model.motion(motionName);
            } else if (window.MindIsleCubismAdapter?.playMotion) {
                window.MindIsleCubismAdapter.playMotion(motionName);
            }
        } catch (error) {
            console.warn("[MindIsle Live2D] motion ignored", motionName, error);
        }
    }

    function setExpression(expressionName) {
        try {
            if (model?.expression) {
                model.expression(expressionName);
            } else if (window.MindIsleCubismAdapter?.setExpression) {
                window.MindIsleCubismAdapter.setExpression(expressionName);
            }
        } catch (error) {
            console.warn("[MindIsle Live2D] expression ignored", expressionName, error);
        }
    }

    function setEmotionState(state) {
        currentState = state || "idle";
        if (currentState === "talking") {
            playMotion("talking");
        } else if (currentState === "happy") {
            setExpression("happy");
        } else if (currentState === "rest") {
            playMotion("rest");
        }
    }

    function resetModel() {
        setEmotionState("idle");
        setExpression("default");
    }

    function changeModel(path) {
        modelTransform = { scale: 1, offsetX: 0, offsetY: 0 };
        return loadModel(path);
    }

    function setModelScale(scale) {
        const nextScale = Number(scale);
        if (!Number.isFinite(nextScale)) return;
        modelTransform.scale = Math.min(Math.max(nextScale, 0.8), 1.2);
        applyModelTransform();
    }

    function setUserScaleMultiplier(multiplier) {
        const nextMultiplier = clamp(multiplier, 0.8, 1.2);
        modelTransform.scale = nextMultiplier;
        applyModelTransform();
        return "ok";
    }

    function resizeCanvas(width, height) {
        const nextWidth = Number(width);
        const nextHeight = Number(height);
        if (!Number.isFinite(nextWidth) || !Number.isFinite(nextHeight) || nextWidth <= 0 || nextHeight <= 0) {
            return "invalid_size";
        }
        canvas.style.width = Math.round(nextWidth) + "px";
        canvas.style.height = Math.round(nextHeight) + "px";
        resizeCanvasForHighDpi();
        return "ok";
    }

    function fitModelToCanvas() {
        fitModel();
        return "ok";
    }

    function getModelVisualBounds() {
        if (!model) return null;
        const bounds = typeof model.getBounds === "function" ? model.getBounds() : null;
        if (!bounds) {
            return {
                left: model.x - (model.width || 0) / 2,
                top: model.y - (model.height || 0),
                right: model.x + (model.width || 0) / 2,
                bottom: model.y,
                width: model.width || 0,
                height: model.height || 0
            };
        }
        return {
            left: bounds.left ?? bounds.x ?? 0,
            top: bounds.top ?? bounds.y ?? 0,
            right: bounds.right ?? ((bounds.x ?? 0) + (bounds.width ?? 0)),
            bottom: bounds.bottom ?? ((bounds.y ?? 0) + (bounds.height ?? 0)),
            width: bounds.width ?? 0,
            height: bounds.height ?? 0
        };
    }

    function setModelPosition(x, y) {
        const nextX = Number(x);
        const nextY = Number(y);
        if (!Number.isFinite(nextX) || !Number.isFinite(nextY)) return;
        modelTransform.offsetX = nextX;
        modelTransform.offsetY = nextY;
        applyModelTransform();
    }

    function resetModelTransform() {
        modelTransform = { scale: 1, offsetX: 0, offsetY: 0 };
        fitModelToCanvas();
        setUserScaleMultiplier(1);
    }

    function getCoreModel() {
        return model?.internalModel?.coreModel || model?.coreModel || null;
    }

    function clamp(value, min, max) {
        const nextValue = Number(value);
        if (!Number.isFinite(nextValue)) return 0;
        return Math.min(Math.max(nextValue, min), max);
    }

    function setLive2DParameter(coreModel, id, value) {
        try {
            if (coreModel && typeof coreModel.setParameterValueById === "function") {
                coreModel.setParameterValueById(id, value);
                return true;
            }
            const range = parameterRanges[id];
            if (
                coreModel &&
                range &&
                Number.isInteger(range.index) &&
                typeof coreModel.setParameterValueByIndex === "function"
            ) {
                coreModel.setParameterValueByIndex(range.index, value);
                return true;
            }
        } catch (error) {
            console.warn("[MindIsle Live2D] set parameter ignored", id, error);
        }
        return false;
    }

    function idToString(id) {
        if (typeof id === "string") return id;
        if (id && typeof id.id === "string") return id.id;
        if (id && typeof id._id === "string") return id._id;
        if (id && typeof id.toString === "function") return id.toString();
        return "";
    }

    function readArrayValue(coreModel, keys, index) {
        for (const key of keys) {
            const value = coreModel?.[key];
            if (Array.isArray(value) || ArrayBuffer.isView(value)) {
                const item = value[index];
                if (Number.isFinite(Number(item))) return Number(item);
            }
        }
        const parameters = coreModel?.parameters;
        if (parameters) {
            for (const key of keys) {
                const value = parameters[key];
                if (Array.isArray(value) || ArrayBuffer.isView(value)) {
                    const item = value[index];
                    if (Number.isFinite(Number(item))) return Number(item);
                }
            }
        }
        return null;
    }

    function findParameterIndex(coreModel, paramId) {
        try {
            if (typeof coreModel?.getParameterIndex === "function") {
                const index = coreModel.getParameterIndex(paramId);
                if (Number.isInteger(index) && index >= 0) return index;
            }
        } catch (error) {
            // Fall through to array lookup.
        }

        const ids = coreModel?._parameterIds || coreModel?.parameterIds || coreModel?.parameters?.ids || [];
        for (let index = 0; index < ids.length; index += 1) {
            if (idToString(ids[index]) === paramId) return index;
        }
        return -1;
    }

    function readParameterRange(coreModel, paramId) {
        const index = findParameterIndex(coreModel, paramId);
        if (index < 0) return null;

        let min = null;
        let max = null;
        let def = null;

        try {
            if (typeof coreModel.getParameterMinimumValue === "function") {
                min = Number(coreModel.getParameterMinimumValue(index));
            }
            if (typeof coreModel.getParameterMaximumValue === "function") {
                max = Number(coreModel.getParameterMaximumValue(index));
            }
            if (typeof coreModel.getParameterDefaultValue === "function") {
                def = Number(coreModel.getParameterDefaultValue(index));
            }
        } catch (error) {
            // Fall through to direct arrays.
        }

        min = Number.isFinite(min) ? min : readArrayValue(coreModel, ["_parameterMinimumValues", "parameterMinimumValues", "minimumValues"], index);
        max = Number.isFinite(max) ? max : readArrayValue(coreModel, ["_parameterMaximumValues", "parameterMaximumValues", "maximumValues"], index);
        def = Number.isFinite(def) ? def : readArrayValue(coreModel, ["_parameterDefaultValues", "parameterDefaultValues", "defaultValues"], index);

        if (!Number.isFinite(min) || !Number.isFinite(max)) return null;
        if (!Number.isFinite(def)) def = (min + max) / 2;
        return {
            index,
            min,
            max,
            default: clamp(def, min, max)
        };
    }

    function cacheParameterRanges() {
        const coreModel = getCoreModel();
        parameterRanges = {};
        smoothLookParams = {};
        if (!coreModel) return;

        Object.keys(LOOK_PARAMETER_CONFIG).forEach(function (paramId) {
            const range = readParameterRange(coreModel, paramId);
            if (!range) return;
            parameterRanges[paramId] = range;
            smoothLookParams[paramId] = {
                current: range.default,
                target: range.default
            };
        });

        ["ParamMouthOpenY", "ParamCheek"].forEach(function (paramId) {
            const range = readParameterRange(coreModel, paramId);
            if (range) parameterRanges[paramId] = range;
        });
    }

    function resetLookStateToDefault() {
        Object.keys(smoothLookParams).forEach(function (paramId) {
            const range = parameterRanges[paramId];
            if (!range) return;
            smoothLookParams[paramId].current = range.default;
            smoothLookParams[paramId].target = range.default;
        });
    }

    function mapToModelRange(paramId, normalizedValue) {
        const range = parameterRanges[paramId];
        if (!range) return null;

        const normalized = clamp(normalizedValue, -1, 1);
        let value;
        if (normalized >= 0) {
            value = range.default + normalized * (range.max - range.default);
        } else {
            value = range.default + normalized * (range.default - range.min);
        }
        return clamp(value, range.min, range.max);
    }

    function getParameterRange(paramId) {
        if (parameterRanges[paramId]) return parameterRanges[paramId];
        const coreModel = getCoreModel();
        if (!coreModel) return null;
        const range = readParameterRange(coreModel, paramId);
        if (range) parameterRanges[paramId] = range;
        return range;
    }

    function applySmoothLookAt() {
        eyeCurrentX += (eyeTargetX - eyeCurrentX) * 0.12;
        eyeCurrentY += (eyeTargetY - eyeCurrentY) * 0.12;
        headCurrentX += (headTargetX - headCurrentX) * 0.06;
        headCurrentY += (headTargetY - headCurrentY) * 0.06;
        bodyCurrentX += (bodyTargetX - bodyCurrentX) * 0.035;
        bodyCurrentY += (bodyTargetY - bodyCurrentY) * 0.035;

        const coreModel = getCoreModel();
        if (!coreModel) return;

        Object.keys(smoothLookParams).forEach(function (paramId) {
            const config = LOOK_PARAMETER_CONFIG[paramId];
            const factor = LOOK_SMOOTH_FACTORS[config.group] || 0.06;
            const state = smoothLookParams[paramId];
            state.current += (state.target - state.current) * factor;
            setLive2DParameter(coreModel, paramId, state.current);
        });
    }

    function setLookGroupTargets(group, x, y) {
        Object.keys(LOOK_PARAMETER_CONFIG).forEach(function (paramId) {
            const config = LOOK_PARAMETER_CONFIG[paramId];
            if (config.group !== group || !smoothLookParams[paramId]) return;
            const normalized = config.axis === "y" ? y : x;
            const targetValue = mapToModelRange(paramId, normalized);
            if (targetValue === null) return;
            smoothLookParams[paramId].target = targetValue;
        });
    }

    function lookAtSmooth(x, y, source) {
        const nextX = clamp(x, -1, 1);
        const nextY = clamp(y, -1, 1);
        const nextSource = source || "external";
        if (Object.keys(parameterRanges).length === 0) {
            cacheParameterRanges();
        }

        window.clearTimeout(headDelayTimer);
        window.clearTimeout(bodyDelayTimer);
        window.clearTimeout(lookResetTimer);

        if (nextSource === "external") {
            const externalX = nextX * 0.7;
            const externalY = nextY * 0.7;
            setLookGroupTargets("eye", externalX, externalY);
            headDelayTimer = window.setTimeout(function () {
                setLookGroupTargets("head", externalX, externalY);
            }, 80);
            bodyDelayTimer = window.setTimeout(function () {
                setLookGroupTargets("body", externalX, externalY);
            }, 160);
        } else {
            setLookGroupTargets("eye", nextX, nextY);
            setLookGroupTargets("head", nextX, nextY);
            setLookGroupTargets("body", nextX, nextY);
        }

        lookResetTimer = window.setTimeout(resetLookAtSmooth, 1100);
        return "ok";
    }

    function playTapReactionSmooth(x, y, source) {
        lookAtSmooth(x, y, source || "external");
        if (window.MindIsleCubismAdapter?.playTapReaction) {
            window.MindIsleCubismAdapter.playTapReaction(x, y);
            return "ok";
        }

        const coreModel = getCoreModel();
        if (!coreModel) return "model_null";
        const mouthRange = parameterRanges.ParamMouthOpenY;
        const cheekRange = parameterRanges.ParamCheek;
        if (mouthRange) setLive2DParameter(coreModel, "ParamMouthOpenY", mouthRange.max);
        if (cheekRange) setLive2DParameter(coreModel, "ParamCheek", cheekRange.max);
        window.setTimeout(function () {
            const nextCoreModel = getCoreModel();
            if (!nextCoreModel) return;
            if (mouthRange) setLive2DParameter(nextCoreModel, "ParamMouthOpenY", mouthRange.default);
            if (cheekRange) setLive2DParameter(nextCoreModel, "ParamCheek", cheekRange.default);
        }, 300);
        return "ok";
    }

    function showExpression(expressionName) {
        setExpression(expressionName);
        return "ok";
    }

    function setTemporaryParameter(paramId, normalizedValue, duration) {
        const coreModel = getCoreModel();
        const range = getParameterRange(paramId);
        if (!coreModel || !range) return false;

        const value = mapToModelRange(paramId, normalizedValue);
        if (value === null) return false;
        setLive2DParameter(coreModel, paramId, value);
        window.setTimeout(function () {
            const nextCoreModel = getCoreModel();
            const nextRange = getParameterRange(paramId);
            if (!nextCoreModel || !nextRange) return;
            setLive2DParameter(nextCoreModel, paramId, nextRange.default);
        }, duration || 320);
        return true;
    }

    function playIdleAction(actionName) {
        const action = actionName || "reset";
        if (action === "reset") {
            resetLookAtSmooth();
            return "ok";
        }

        if (action === "blink") {
            playMotion("blink");
            setTemporaryParameter("ParamEyeLOpen", -1, 180);
            setTemporaryParameter("ParamEyeROpen", -1, 180);
            return "ok";
        }

        if (action === "lookLeft") {
            lookAtSmooth(-0.35, 0, "external");
            return "ok";
        }
        if (action === "lookRight") {
            lookAtSmooth(0.35, 0, "external");
            return "ok";
        }
        if (action === "lookUp") {
            lookAtSmooth(0, 0.3, "external");
            return "ok";
        }
        if (action === "lookDown") {
            lookAtSmooth(0, -0.3, "external");
            return "ok";
        }

        if (action === "smile") {
            setExpression("happy");
            setExpression("smile");
            setTemporaryParameter("ParamMouthForm", 1, 900);
            setTemporaryParameter("ParamCheek", 1, 900);
            return "ok";
        }

        if (action === "breathe") {
            playMotion("idle");
            playMotion("breathe");
            setTemporaryParameter("ParamBreath", 1, 1200);
            return "ok";
        }

        playMotion(action);
        return "ok";
    }

    function resetLookAtSmooth() {
        window.clearTimeout(headDelayTimer);
        window.clearTimeout(bodyDelayTimer);
        Object.keys(smoothLookParams).forEach(function (paramId) {
            const range = parameterRanges[paramId];
            if (range) smoothLookParams[paramId].target = range.default;
        });
        return "ok";
    }

    function lookAt(x, y) {
        return lookAtSmooth(x, y, "external");
    }

    function playTapReaction(x, y) {
        if (typeof x === "string") {
            return playTapReactionSmooth(0, 0, x);
        }
        return playTapReactionSmooth(x, y, "external");
    }

    function resetLookAt() {
        return resetLookAtSmooth();
    }

    function startLookAtLoop() {
        applySmoothLookAt();
        window.requestAnimationFrame(startLookAtLoop);
    }

    window.playMotion = playMotion;
    window.setExpression = setExpression;
    window.setEmotionState = setEmotionState;
    window.resetModel = resetModel;
    window.changeModel = changeModel;
    window.setModelScale = setModelScale;
    window.setUserScaleMultiplier = setUserScaleMultiplier;
    window.setModelPosition = setModelPosition;
    window.resetModelTransform = resetModelTransform;
    window.resizeCanvas = resizeCanvas;
    window.fitModelToCanvas = fitModelToCanvas;
    window.getModelVisualBounds = getModelVisualBounds;
    window.lookAt = lookAt;
    window.playTapReaction = playTapReaction;
    window.resetLookAt = resetLookAt;
    window.smoothLookAt = lookAtSmooth;
    window.lookAtSmooth = lookAtSmooth;
    window.playTapReactionSmooth = playTapReactionSmooth;
    window.resetLookAtSmooth = resetLookAtSmooth;
    window.playIdleAction = playIdleAction;
    window.showExpression = showExpression;

    window.addEventListener("resize", fitModel);
    window.requestAnimationFrame(startLookAtLoop);
    document.addEventListener("DOMContentLoaded", function () {
        loadModel(modelPath);
    });
})();
