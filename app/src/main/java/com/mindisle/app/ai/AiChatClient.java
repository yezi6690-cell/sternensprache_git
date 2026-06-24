package com.mindisle.app.ai;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import com.mindisle.app.BuildConfig;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public final class AiChatClient implements ApiService {
    private static final String TAG = "MindIsleAi";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    @Override
    public void sendMessage(
            @NonNull String message,
            @NonNull String roleId,
            @NonNull String modelId,
            @NonNull List<ChatHistoryItem> history,
            @NonNull ApiService.Callback callback
    ) {
        String normalizedRoleId = roleId.trim();
        String normalizedModelId = modelId.trim();
        String effectiveRoleId = !normalizedRoleId.isEmpty()
                ? normalizedRoleId
                : (!normalizedModelId.isEmpty() ? normalizedModelId : "baimao");
        String effectiveModelId = !normalizedModelId.isEmpty()
                ? normalizedModelId
                : (!normalizedRoleId.isEmpty() ? normalizedRoleId : "baimao");
        Log.d(TAG, "Sending message length=" + message.length());
        Log.d(TAG, "Sending roleId=" + effectiveRoleId + " modelId=" + effectiveModelId);
        Log.d(TAG, "Sending history count=" + history.size());
        JSONObject payload = new JSONObject();
        JSONArray historyJson = new JSONArray();
        try {
            payload.put("message", message);
            for (ChatHistoryItem item : history) {
                JSONObject historyEntry = new JSONObject();
                historyEntry.put("role", item.getRole());
                historyEntry.put("content", item.getContent());
                historyJson.put(historyEntry);
            }
            payload.put("roleId", effectiveRoleId);
            payload.put("modelId", effectiveModelId);
            payload.put("history", historyJson);
        } catch (JSONException error) {
            Log.e(TAG, "Unable to build chat request", error);
            mainHandler.post(callback::onFailure);
            return;
        }

        String baseUrl = normalizeBaseUrl(BuildConfig.AI_BACKEND_BASE_URL);
        String chatUrl = baseUrl + "api/chat";
        Log.d(TAG, "backend baseUrl=" + baseUrl);
        Log.d(TAG, "full request url=" + chatUrl);
        Request request = new Request.Builder()
                .url(chatUrl)
                .post(RequestBody.create(payload.toString(), JSON))
                .header("Accept", "application/json")
                .build();

        Log.d(TAG, "request start");
        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException error) {
                if (!call.isCanceled()) {
                    Log.e(
                            TAG,
                            "request failure throwable class="
                                    + error.getClass().getName()
                                    + " message="
                                    + error.getMessage(),
                            error
                    );
                    mainHandler.post(callback::onFailure);
                }
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try (Response closeableResponse = response) {
                    Log.d(TAG, "request response HTTP " + closeableResponse.code());
                    if (!closeableResponse.isSuccessful() || closeableResponse.body() == null) {
                        Log.e(TAG, "Chat backend returned HTTP " + closeableResponse.code());
                        mainHandler.post(callback::onFailure);
                        return;
                    }
                    String body = closeableResponse.body().string();
                    JSONObject responseJson = new JSONObject(body);
                    String reply = responseJson.optString("reply", "").trim();
                    if (reply.isEmpty()) {
                        Log.e(TAG, "Chat backend returned an empty reply");
                        mainHandler.post(callback::onFailure);
                        return;
                    }
                    String role = responseJson.optString("role", effectiveRoleId);
                    String emotion = responseJson.optString("emotion", "neutral");
                    JSONObject live2d = responseJson.optJSONObject("live2d");
                    String expression = live2d != null
                            ? live2d.optString("expression", "normal")
                            : "normal";
                    String motion = live2d != null
                            ? live2d.optString("motion", "idle")
                            : "idle";
                    List<AiAction> actions = new ArrayList<>();
                    JSONArray actionsJson = responseJson.optJSONArray("actions");
                    if (actionsJson != null) {
                        for (int index = 0; index < actionsJson.length(); index++) {
                            JSONObject actionJson = actionsJson.optJSONObject(index);
                            if (actionJson == null) continue;
                            String type = actionJson.optString("type", "").trim();
                            if (type.isEmpty()) continue;
                            JSONObject paramsJson = actionJson.optJSONObject("params");
                            if (paramsJson == null) {
                                Log.w(TAG, "ignored action without params type=" + type);
                                continue;
                            }
                            Map<String, Object> params = jsonObjectToMap(paramsJson);
                            actions.add(new AiAction(
                                    type,
                                    params,
                                    actionJson.optBoolean("needConfirm", false),
                                    actionJson.optString("displayText", "").trim()
                            ));
                        }
                    }
                    boolean needConfirm = responseJson.optBoolean("needConfirm", false);
                    AiChatResponse chatResponse = new AiChatResponse(
                            reply,
                            role,
                            emotion,
                            expression,
                            motion,
                            actions,
                            needConfirm
                    );
                    Log.d(TAG, "request success");
                    Log.d(
                            TAG,
                            "structured response role=" + role
                                    + " emotion=" + emotion
                                    + " expression=" + expression
                                    + " motion=" + motion
                                    + " actions=" + actions.size()
                                    + " needConfirm=" + needConfirm
                    );
                    mainHandler.post(() -> callback.onSuccess(chatResponse));
                } catch (Exception error) {
                    Log.e(
                            TAG,
                            "request failure throwable class="
                                    + error.getClass().getName()
                                    + " message="
                                    + error.getMessage(),
                            error
                    );
                    mainHandler.post(callback::onFailure);
                }
            }
        });
    }

    @Override
    public void cancelAll() {
        client.dispatcher().cancelAll();
    }

    @NonNull
    private static String normalizeBaseUrl(@NonNull String configuredUrl) {
        String baseUrl = configuredUrl.trim();
        return baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
    }

    @NonNull
    private static Map<String, Object> jsonObjectToMap(JSONObject object) {
        Map<String, Object> values = new LinkedHashMap<>();
        if (object == null) return values;
        java.util.Iterator<String> keys = object.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            Object value = object.opt(key);
            if (value instanceof String
                    || value instanceof Boolean
                    || value instanceof Number) {
                values.put(key, value);
            }
        }
        return values;
    }
}
