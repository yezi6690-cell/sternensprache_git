import "dotenv/config";
import express from "express";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

const app = express();
const port = Number(process.env.PORT || 3000);
const requestTimeoutMs = 60_000;
const currentDir = dirname(fileURLToPath(import.meta.url));
const roleConfig = loadRoleConfig();
const personaDirectory = join(currentDir, "personas", "live2d");
const allowedActionTypes = new Set([
  "music.openPanel",
  "music.play",
  "music.pause",
  "music.togglePlay",
  "music.next",
  "music.previous",
  "music.openList",
  "music.setPlayMode",
  "music.togglePlayMode"
]);
const allowedMusicModes = new Set(["sequence", "shuffle", "single"]);
const allowedExpressions = new Set([
  "normal", "smile", "sad", "shy", "thinking", "surprised"
]);
const allowedMotions = new Set([
  "idle", "nod", "wave", "happy", "comfort", "thinking"
]);

const defaultSystemPrompt = `
你叫“心屿”，是心屿 App 中的陪伴型 AI 助手。

回答规则：
1. 默认必须使用简体中文回复。
2. 除非用户明确要求其他语言，否则不要使用英文、法语或其他语言。
3. 语气温柔、自然、简洁，像可靠的朋友一样倾听，不说教。
4. 可以陪用户聊天、安慰用户，并提供一般性的学习建议、生活建议和情绪自助建议。
5. 不能冒充医生或心理咨询师，不能进行医学诊断，也不能声称可以替代专业帮助。
6. 不要主动谈论 innocent、公司面试、招聘、求职、申请动机等与当前对话无关的内容。
7. 如果用户只是说“你好”或类似问候，应自然地用中文打招呼，并询问用户今天想聊什么。
8. 回答不要太长，不要像正式问卷、面试提问或机械客服。

如果用户表达自伤、自杀、伤害他人或无法保证自身安全的想法：
1. 先用平静、尊重的语言表达关心。
2. 鼓励用户立刻联系身边可信任的人、辅导员或学校心理中心。
3. 如果存在紧迫危险，建议联系当地紧急求助渠道并前往有人陪伴的安全地点。
4. 不提供任何实施伤害的方法或细节。

回复内容放在结构化 JSON 的 reply 字段中，不要解释这些规则。
`.trim();

const languageCorrectionPrompt = `
刚才的回复不符合心屿的语言或人设规则，请重新回答用户：
1. 只使用简体中文。
2. 保持温柔、自然、简洁的陪伴语气。
3. 不得出现 innocent、招聘、面试、求职或申请动机等无关内容。
4. 不要解释纠正过程。
5. 仍然严格返回指定的结构化 JSON。
`.trim();

const actionSystemPrompt = `
只有当用户明确表达音乐播放器控制意图时，才可以返回 actions。
本阶段只能使用以下 music action：
music.openPanel
music.play
music.pause
music.togglePlay
music.next
music.previous
music.openList
music.setPlayMode
music.togglePlayMode

参数约束：
- music.setPlayMode: {"mode":"sequence|shuffle|single"}
- 其他允许的 music action 的 params 必须为 {}。
- 所有 music action 的 needConfirm 必须为 false。

意图映射示例：
- “播放音乐 / 放首歌 / 开始播放” -> music.play
- “暂停音乐 / 停一下 / 先别放了” -> music.pause
- “播放或暂停 / 切换播放状态” -> music.togglePlay
- “下一首 / 换一首” -> music.next
- “上一首 / 回到上一首” -> music.previous
- “打开音乐播放器 / 显示音乐播放器” -> music.openPanel
- “打开音乐列表 / 看看歌单 / 显示音乐列表” -> music.openList
- “随机播放” -> music.setPlayMode，params={"mode":"shuffle"}
- “顺序播放” -> music.setPlayMode，params={"mode":"sequence"}
- “单曲循环” -> music.setPlayMode，params={"mode":"single"}
- “切换播放模式” -> music.togglePlayMode

如果用户只是普通聊天，actions 必须为 []。
如果用户要求评估、个人页、小游戏、角色切换或其他非音乐功能，只用文字回答，不要返回 action。
不要返回任意代码、文件路径、shell 命令或系统命令，也不要编造白名单外 action。
必须只输出一个 JSON 对象，不要使用 Markdown 代码块。格式：
{
  "reply": "给用户看的简体中文回复",
  "role": "当前roleId",
  "emotion": "neutral",
  "live2d": {"expression": "normal", "motion": "idle"},
  "actions": [
    {
      "type": "允许的 action 类型",
      "params": {},
      "needConfirm": false,
      "displayText": "给用户看的操作说明"
    }
  ],
  "needConfirm": false
}
reply 必须存在；没有动作时 actions 必须为 []。
`.trim();

const repeatedMusicActionPrompt = `
每一次用户明确提出音乐播放器控制命令时，都必须返回对应的 music action。
不要因为历史记录中已经执行过相同或相似 action 就省略本次 action。
连续相同命令也必须连续返回 action，例如用户连续说“下一首”，每次都必须返回 music.next。
action 只根据当前最新一条用户消息判断，不要用历史消息抵消当前命令。
`.trim();

app.disable("x-powered-by");
app.use(express.json({ limit: "64kb" }));
app.use((request, _response, next) => {
  console.log(
    `[MindIsleAI] ${new Date().toLocaleString()} ${request.method} ${request.url}`
  );
  next();
});

app.get("/health", (_request, response) => {
  response.json({ status: "ok" });
});

app.post("/api/chat", async (request, response) => {
  const message = typeof request.body?.message === "string"
    ? request.body.message.trim()
    : "";
  const forcedActions = inferMusicActionsFromMessage(message);
  const history = normalizeHistory(request.body?.history);
  const requestedModelId = normalizeModelId(request.body?.modelId);
  const selectedRole = resolveRole(request.body?.roleId, requestedModelId);
  const persona = loadPersonaPrompt(requestedModelId);

  if (!message) {
    response.status(400).json({ error: "message is required" });
    return;
  }

  console.log(`[MindIsleAI] request modelId=${requestedModelId || "(empty)"}`);
  console.log(
    `[MindIsleAI] request roleId=${
      typeof request.body?.roleId === "string" && request.body.roleId.trim()
        ? request.body.roleId.trim()
        : "(empty)"
    }`
  );
  console.log(`[MindIsleAI] using persona file=${persona.fileName}`);
  console.log(
    `[MindIsleAI] roleId=${selectedRole.roleId} modelId=${selectedRole.modelId} history=${history.length}`
  );
  console.log(`[MindIsleAI] forced music actions count=${forcedActions.length}`);
  forcedActions.forEach((action) => {
    console.log(`[MindIsleAI] forced music action type=${action.type}`);
  });

  const apiUrl = process.env.AI_API_URL?.trim();
  const apiKey = process.env.AI_API_KEY?.trim();
  const model = process.env.AI_MODEL?.trim();
  if (!apiUrl || !apiKey || !model) {
    console.error("[MindIsleAI] Missing AI_API_URL, AI_API_KEY, or AI_MODEL");
    response.status(503).json({ error: "AI service is not configured" });
    return;
  }

  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), requestTimeoutMs);

  try {
    const systemPrompt = buildSystemPrompt(selectedRole, persona.content);
    const messages = [
      { role: "system", content: systemPrompt },
      ...history,
      { role: "user", content: message }
    ];
    let providerOutput = await requestAiReply({
      apiUrl,
      apiKey,
      model,
      messages,
      signal: controller.signal
    });
    let structuredReply = normalizeStructuredReply(providerOutput, selectedRole);

    if (structuredReply.reply && needsLanguageCorrection(structuredReply.reply)) {
      console.warn("[MindIsleAI] Reply failed language/persona validation; retrying once");
      providerOutput = await requestAiReply({
        apiUrl,
        apiKey,
        model,
        messages: [
          ...messages,
          { role: "assistant", content: providerOutput },
          { role: "system", content: languageCorrectionPrompt }
        ],
        signal: controller.signal
      });
      structuredReply = normalizeStructuredReply(providerOutput, selectedRole);
    }

    if (!structuredReply.reply) {
      console.error("[MindIsleAI] Provider returned an empty reply");
      response.status(502).json({ error: "AI provider returned an empty reply" });
      return;
    }

    const returnedActionsCount = countProviderActions(providerOutput);
    console.log(`[MindIsleAI] returned actions count=${returnedActionsCount}`);
    console.log(`[MindIsleAI] ai returned actions count=${returnedActionsCount}`);
    console.log(`[MindIsleAI] filtered actions count=${structuredReply.actions.length}`);

    const finalActions = normalizeActions(
      forcedActions.length > 0 ? forcedActions : structuredReply.actions
    );
    structuredReply = {
      ...structuredReply,
      actions: finalActions,
      needConfirm: finalActions.some((action) => action.needConfirm)
    };
    console.log(`[MindIsleAI] final actions count=${finalActions.length}`);
    finalActions.forEach((action) => {
      console.log(`[MindIsleAI] final action type=${action.type}`);
    });
    response.json(structuredReply);
  } catch (error) {
    const reason = error?.name === "AbortError" ? "timeout" : error?.message;
    console.error(`[MindIsleAI] Chat request failed: ${reason}`);
    response.status(502).json({ error: "AI request failed" });
  } finally {
    clearTimeout(timeout);
  }
});

app.use((_request, response) => {
  response.status(404).json({ error: "not found" });
});

app.use((error, _request, response, _next) => {
  console.error("[MindIsleAI] Unhandled server error", error);
  response.status(500).json({ error: "internal server error" });
});

app.listen(port, "0.0.0.0", () => {
  console.log(`[MindIsleAI] Backend listening on port ${port}`);
});

function loadRoleConfig() {
  try {
    const file = readFileSync(join(currentDir, "roles.json"), "utf8");
    const parsed = JSON.parse(file);
    const roles = Array.isArray(parsed?.roles) ? parsed.roles : [];
    return roles.length > 0 ? roles : [createDefaultRole()];
  } catch (error) {
    console.warn(`[MindIsleAI] Unable to load roles.json: ${error.message}`);
    return [createDefaultRole()];
  }
}

function createDefaultRole() {
  return {
    roleId: "baimao",
    modelId: "baimao",
    displayName: "白猫",
    personaFileName: "baimao.txt",
    enabled: true,
    description: "",
    personalityPrompt: "",
    speakingStyle: "",
    relationshipWithUser: "",
    welcomeMessage: "",
    live2d: {
      defaultExpression: "",
      defaultMotion: ""
    }
  };
}

function resolveRole(roleIdValue, modelIdValue) {
  const roleId = typeof roleIdValue === "string" ? roleIdValue.trim() : "";
  const modelId = typeof modelIdValue === "string" ? modelIdValue.trim() : "";
  const enabledRoles = roleConfig.filter((role) => role?.enabled !== false);
  return enabledRoles.find((role) => role.modelId === modelId)
    || enabledRoles.find((role) => role.roleId === roleId)
    || enabledRoles.find((role) => role.roleId === "baimao")
    || enabledRoles[0]
    || createDefaultRole();
}

function buildSystemPrompt(role, personaPrompt) {
  const additions = [];
  if (personaPrompt) additions.push(personaPrompt);
  if (role.displayName) additions.push(`当前角色显示名：${role.displayName}`);
  if (role.description) additions.push(`角色说明：${role.description}`);
  if (role.personalityPrompt) additions.push(`角色设定：${role.personalityPrompt}`);
  if (role.speakingStyle) additions.push(`说话方式：${role.speakingStyle}`);
  if (role.relationshipWithUser) {
    additions.push(`与用户的关系：${role.relationshipWithUser}`);
  }
  return additions.length > 0
    ? `${defaultSystemPrompt}\n\n当前角色配置：\n${additions.join("\n")}\n\n${actionSystemPrompt}\n\n${repeatedMusicActionPrompt}`
    : `${defaultSystemPrompt}\n\n${actionSystemPrompt}\n\n${repeatedMusicActionPrompt}`;
}

function normalizeModelId(value) {
  if (typeof value !== "string") return "";
  const normalized = value.trim().toLowerCase();
  return /^[a-z0-9_]+$/.test(normalized) ? normalized : "";
}

function loadPersonaPrompt(requestedModelId) {
  const preferredFileName = requestedModelId
    ? `${requestedModelId}.txt`
    : "default.txt";
  const preferred = readPersonaFile(preferredFileName);
  if (preferred) {
    return { fileName: preferredFileName, content: preferred };
  }

  if (preferredFileName !== "default.txt") {
    console.warn(
      `[MindIsleAI] Persona ${preferredFileName} missing or empty; falling back to default.txt`
    );
  }
  const fallback = readPersonaFile("default.txt");
  if (fallback) {
    return { fileName: "default.txt", content: fallback };
  }

  console.warn("[MindIsleAI] default.txt missing or empty; using built-in prompt");
  return {
    fileName: "server.js built-in default",
    content: ""
  };
}

function readPersonaFile(fileName) {
  try {
    const content = readFileSync(join(personaDirectory, fileName), "utf8").trim();
    return content || "";
  } catch (_error) {
    return "";
  }
}

function normalizeHistory(value) {
  if (!Array.isArray(value)) return [];

  return value
    .slice(-20)
    .map((item) => ({
      role: item?.role === "assistant" ? "assistant" : "user",
      content: typeof item?.content === "string"
        ? item.content.trim().slice(0, 4000)
        : ""
    }))
    .filter((item) => item.content);
}

function safeProviderError(body) {
  if (!body || typeof body !== "object") return "no response body";
  return body.error?.message || body.message || "unknown provider error";
}

async function requestAiReply({ apiUrl, apiKey, model, messages, signal }) {
  const providerResponse = await fetch(apiUrl, {
    method: "POST",
    headers: {
      "Authorization": `Bearer ${apiKey}`,
      "Content-Type": "application/json",
      "Accept": "application/json"
    },
    body: JSON.stringify({
      model,
      messages,
      temperature: 0.55
    }),
    signal
  });

  const providerBody = await providerResponse.json().catch(() => null);
  if (!providerResponse.ok) {
    throw new Error(
      `provider status=${providerResponse.status} message=${safeProviderError(providerBody)}`
    );
  }
  return providerBody?.choices?.[0]?.message?.content?.trim() || "";
}

function normalizeStructuredReply(providerOutput, selectedRole) {
  const fallbackLive2d = {
    expression: selectedRole.live2d?.defaultExpression || "normal",
    motion: selectedRole.live2d?.defaultMotion || "idle"
  };
  const parsed = parseJsonObject(providerOutput);
  if (!parsed) {
    return {
      reply: typeof providerOutput === "string" ? providerOutput.trim() : "",
      role: selectedRole.roleId,
      emotion: "neutral",
      live2d: fallbackLive2d,
      actions: [],
      needConfirm: false
    };
  }

  const actions = normalizeActions(parsed.actions);
  const expression = allowedExpressions.has(parsed.live2d?.expression)
    ? parsed.live2d.expression
    : fallbackLive2d.expression;
  const motion = allowedMotions.has(parsed.live2d?.motion)
    ? parsed.live2d.motion
    : fallbackLive2d.motion;
  return {
    reply: normalizeReply(
      parsed.reply,
      "我暂时没能整理好回复，请再试一次。"
    ),
    role: selectedRole.roleId,
    emotion: normalizeShortString(parsed.emotion, "neutral"),
    live2d: { expression, motion },
    actions,
    needConfirm: actions.some((action) => action.needConfirm)
  };
}

function parseJsonObject(value) {
  if (typeof value !== "string" || !value.trim()) return null;
  const trimmed = value.trim()
    .replace(/^```(?:json)?\s*/i, "")
    .replace(/\s*```$/, "");
  try {
    const parsed = JSON.parse(trimmed);
    return parsed && typeof parsed === "object" && !Array.isArray(parsed)
      ? parsed
      : null;
  } catch (_error) {
    const start = trimmed.indexOf("{");
    const end = trimmed.lastIndexOf("}");
    if (start < 0 || end <= start) return null;
    try {
      const parsed = JSON.parse(trimmed.slice(start, end + 1));
      return parsed && typeof parsed === "object" && !Array.isArray(parsed)
        ? parsed
        : null;
    } catch (_nestedError) {
      return null;
    }
  }
}

function normalizeActions(value) {
  if (!Array.isArray(value)) return [];
  return value.slice(0, 8).map(normalizeAction).filter(Boolean);
}

function inferMusicActionsFromMessage(message) {
  const command = normalizeMusicCommand(message);
  if (!command) return [];

  const definitions = [
    {
      aliases: ["打开音乐列表", "显示音乐列表", "看看歌单"],
      type: "music.openList",
      displayText: "打开音乐列表"
    },
    {
      aliases: ["打开音乐播放器", "显示音乐播放器"],
      type: "music.openPanel",
      displayText: "打开音乐播放器"
    },
    {
      aliases: ["切换播放模式"],
      type: "music.togglePlayMode",
      displayText: "切换播放模式"
    },
    {
      aliases: ["随机播放"],
      type: "music.setPlayMode",
      params: { mode: "shuffle" },
      displayText: "切换到随机播放"
    },
    {
      aliases: ["顺序播放"],
      type: "music.setPlayMode",
      params: { mode: "sequence" },
      displayText: "切换到顺序播放"
    },
    {
      aliases: ["单曲循环"],
      type: "music.setPlayMode",
      params: { mode: "single" },
      displayText: "切换到单曲循环"
    },
    {
      aliases: ["暂停音乐", "停一下", "先别放了"],
      type: "music.pause",
      displayText: "暂停音乐"
    },
    {
      aliases: ["下一首", "换一首"],
      type: "music.next",
      displayText: "播放下一首"
    },
    {
      aliases: ["上一首", "回到上一首"],
      type: "music.previous",
      displayText: "播放上一首"
    },
    {
      aliases: ["播放或暂停", "切换播放状态"],
      type: "music.togglePlay",
      displayText: "切换播放状态"
    },
    {
      aliases: ["播放音乐", "放首歌", "开始播放"],
      type: "music.play",
      displayText: "播放音乐"
    }
  ];

  const matched = definitions.find(({ aliases }) =>
    aliases.some((alias) => command === normalizeMusicCommand(alias))
  );
  if (!matched) return [];
  return [{
    type: matched.type,
    params: matched.params || {},
    needConfirm: false,
    displayText: matched.displayText
  }];
}

function normalizeMusicCommand(value) {
  if (typeof value !== "string") return "";
  return value
    .trim()
    .toLowerCase()
    .replace(/[\s，。！？、,.!?；;：:]/g, "")
    .replace(/^(请|麻烦你|麻烦|可以|能不能|请你)?(帮我|给我)?/, "")
    .replace(/(一下|吧|呀|啊|哦|呢)$/, "");
}

function countProviderActions(providerOutput) {
  const parsed = parseJsonObject(providerOutput);
  return Array.isArray(parsed?.actions) ? parsed.actions.length : 0;
}

function normalizeAction(action) {
  if (!action || typeof action !== "object" || Array.isArray(action)) return null;
  const type = typeof action.type === "string" ? action.type.trim() : "";
  if (!allowedActionTypes.has(type)) {
    if (type) console.warn(`[MindIsleAI] ignored provider action=${type}`);
    return null;
  }

  const params = normalizeActionParams(type, action.params);
  if (params === null) {
    console.warn(`[MindIsleAI] rejected invalid params for action=${type}`);
    return null;
  }
  return {
    type,
    params,
    needConfirm: false,
    displayText: normalizeShortString(
      action.displayText,
      defaultActionDisplayText(type)
    )
  };
}

function normalizeActionParams(type, value) {
  if (type === "music.setPlayMode") {
    if (!value || typeof value !== "object" || Array.isArray(value)) return null;
    return allowedMusicModes.has(value.mode) ? { mode: value.mode } : null;
  }
  if (value === undefined || value === null) return {};
  if (typeof value !== "object" || Array.isArray(value)) return null;
  const params = value;
  return Object.keys(params).length === 0 ? {} : null;
}

function normalizeShortString(value, fallback) {
  if (typeof value !== "string") return fallback;
  const normalized = value.trim().slice(0, 160);
  return normalized || fallback;
}

function normalizeReply(value, fallback) {
  if (typeof value !== "string") return fallback;
  const normalized = value.trim().slice(0, 8000);
  return normalized || fallback;
}

function defaultActionDisplayText(type) {
  if (type === "chat.clearCurrentRoleHistory") {
    return "确定要清空当前角色的聊天记录吗？";
  }
  return "执行此操作";
}

function needsLanguageCorrection(reply) {
  const irrelevantPattern = /\binnocent\b|招聘|面试|求职|申请动机/i;
  if (irrelevantPattern.test(reply)) return true;

  const chineseCount = (reply.match(/[\u3400-\u9fff]/g) || []).length;
  const latinCount = (reply.match(/[A-Za-zÀ-ÖØ-öø-ÿ]/g) || []).length;
  return chineseCount === 0 || latinCount > Math.max(12, chineseCount);
}
