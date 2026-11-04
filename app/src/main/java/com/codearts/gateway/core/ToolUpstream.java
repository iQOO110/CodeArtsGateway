package com.codearts.gateway.core;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/**
 * 「工具模式上游」客户端：面向**原生支持 function calling** 的 OpenAI 兼容接口
 * （例如本机的 raccoon2api）。
 *
 * <p>为什么需要它：码道上游的 messages 接口只收 {@code {content, model_id, repos}}，
 * 客户端 tools 无处可传，其模型又是个自带工具的 agent，用提示词协议扭不动（实测 2 轮 0 调用）。
 * 而第三方 AI App 的 MCP 一定走 {@code tools → tool_calls}，所以带 tools 的请求改走这里，
 * 由真正支持该协议的上游来产出调用。
 *
 * <p>内部统一用流式请求，自己聚合：这样既拿到 {@code delta.tool_calls} 分片，
 * 又能规避"上游非流式响应缺 tool_calls"这类坑。
 */
public final class ToolUpstream {

    /** 一次上游回复。 */
    public static final class Turn {
        public String content = "";
        public String reasoning = "";
        public final List<Call> calls = new ArrayList<>();
        public String finish = "";
    }

    /** 一次工具调用。 */
    public static final class Call {
        public String id;
        public String name;
        public String arguments;

        public Call(String id, String name, String arguments) {
            this.id = id;
            this.name = name;
            this.arguments = arguments == null || arguments.isEmpty() ? "{}" : arguments;
        }
    }

    private ToolUpstream() {
    }

    /** 上游是否配置可用。 */
    public static boolean configured(String base) {
        return base != null && !base.trim().isEmpty();
    }

    /** 拉取模型列表（失败返回 null）。 */
    public static JSONArray models(String base, String key, int timeoutMs) {
        HttpURLConnection conn = null;
        try {
            conn = open(join(base, "/models"), "GET", key, null, timeoutMs, false);
            String raw = read(conn.getInputStream());
            JSONObject o = new JSONObject(raw);
            return o.optJSONArray("data");
        } catch (Throwable t) {
            LogRing.log("toolup", "模型列表失败: " + t);
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /** 发起一次对话（内部流式 + 聚合）。 */
    public static Turn chat(String base, String key, String bodyJson, int timeoutMs) throws Exception {
        HttpURLConnection conn = open(join(base, "/chat/completions"), "POST", key, bodyJson,
                timeoutMs, true);
        String ctype = String.valueOf(conn.getContentType()).toLowerCase();
        InputStream in = conn.getInputStream();
        if (ctype.contains("text/event-stream")) {
            return parseSse(in);
        }
        String raw = read(in);
        Turn t = new Turn();
        try {
            JSONObject o = new JSONObject(raw);
            JSONObject ch = o.optJSONArray("choices") == null || o.optJSONArray("choices").length() == 0
                    ? new JSONObject() : o.getJSONArray("choices").getJSONObject(0);
            JSONObject msg = ch.optJSONObject("message") == null ? new JSONObject() : ch.getJSONObject("message");
            t.content = msg.optString("content", "");
            t.reasoning = msg.optString("reasoning_content", "");
            t.finish = ch.optString("finish_reason", "");
            JSONArray tcs = msg.optJSONArray("tool_calls");
            if (tcs != null) {
                for (int i = 0; i < tcs.length(); i++) {
                    JSONObject tc = tcs.optJSONObject(i);
                    if (tc == null) {
                        continue;
                    }
                    JSONObject f = tc.optJSONObject("function") == null
                            ? new JSONObject() : tc.getJSONObject("function");
                    t.calls.add(new Call(tc.optString("id", "call_" + i),
                            f.optString("name", ""), f.optString("arguments", "{}")));
                }
            }
        } catch (Throwable ignore) {
            t.content = raw;
        }
        return t;
    }

    /** 解析 SSE，聚合 content 与 tool_calls（工具调用参数按 index 分片拼接）。 */
    public static Turn parseSse(InputStream in) throws Exception {
        Turn t = new Turn();
        TreeMap<Integer, Call> calls = new TreeMap<>();
        StringBuilder content = new StringBuilder();
        StringBuilder reasoning = new StringBuilder();
        BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        String line;
        while ((line = br.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty() || !line.startsWith("data:")) {
                continue;
            }
            String body = line.substring(5).trim();
            if ("[DONE]".equals(body)) {
                break;
            }
            JSONObject d;
            try {
                d = new JSONObject(body);
            } catch (Throwable ignore) {
                continue;
            }
            JSONArray choices = d.optJSONArray("choices");
            if (choices == null || choices.length() == 0) {
                continue;
            }
            JSONObject ch = choices.optJSONObject(0);
            if (ch == null) {
                continue;
            }
            JSONObject delta = ch.optJSONObject("delta");
            if (delta != null) {
                if (delta.has("content") && !delta.isNull("content")) {
                    content.append(delta.optString("content", ""));
                }
                if (delta.has("reasoning_content") && !delta.isNull("reasoning_content")) {
                    reasoning.append(delta.optString("reasoning_content", ""));
                }
                JSONArray tcs = delta.optJSONArray("tool_calls");
                if (tcs != null) {
                    for (int i = 0; i < tcs.length(); i++) {
                        JSONObject tc = tcs.optJSONObject(i);
                        if (tc == null) {
                            continue;
                        }
                        int idx = tc.optInt("index", i);
                        Call c = calls.get(idx);
                        if (c == null) {
                            // 注意：占位对象必须把 arguments 清空，否则构造器的默认 "{}"
                            // 会被后续分片拼进去（"{}{\"path\":\"\"}" 就是这么来的）
                            c = new Call("", "", null);
                            c.arguments = "";
                            calls.put(idx, c);
                        }
                        if (tc.has("id") && !tc.isNull("id") && !tc.optString("id").isEmpty()) {
                            c.id = tc.optString("id");
                        }
                        JSONObject f = tc.optJSONObject("function");
                        if (f != null) {
                            if (f.has("name") && !f.isNull("name")) {
                                c.name = (c.name == null ? "" : c.name) + f.optString("name", "");
                            }
                            if (f.has("arguments") && !f.isNull("arguments")) {
                                String frag = f.optString("arguments", "");
                                c.arguments = (c.arguments == null ? "" : c.arguments) + frag;
                            }
                        }
                    }
                }
            }
            if (ch.has("finish_reason") && !ch.isNull("finish_reason")) {
                t.finish = ch.optString("finish_reason", "");
            }
        }
        t.content = content.toString();
        t.reasoning = reasoning.toString();
        int n = 0;
        for (Call c : calls.values()) {
            if (c.name == null || c.name.isEmpty()) {
                continue;
            }
            if (c.id == null || c.id.isEmpty()) {
                c.id = "call_" + Integer.toHexString((int) (System.nanoTime() & 0x7fffffff)) + n;
            }
            if (c.arguments == null || c.arguments.isEmpty()) {
                c.arguments = "{}";
            }
            t.calls.add(c);
            n++;
        }
        return t;
    }

    private static String join(String base, String path) {
        String b = base == null ? "" : base.trim();
        if (b.endsWith("/")) {
            b = b.substring(0, b.length() - 1);
        }
        return b + path;
    }

    private static HttpURLConnection open(String url, String method, String key, String body,
                                          int timeoutMs, boolean acceptStream) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod(method);
        conn.setConnectTimeout(Math.min(timeoutMs, 20000));
        conn.setReadTimeout(timeoutMs);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", acceptStream
                ? "text/event-stream, application/json" : "application/json");
        if (key != null && !key.trim().isEmpty()) {
            conn.setRequestProperty("Authorization", "Bearer " + key.trim());
        }
        if (body != null) {
            conn.setDoOutput(true);
            byte[] data = body.getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(data);
            }
        }
        int code = conn.getResponseCode();
        if (code >= 400) {
            String err = "";
            try {
                err = read(conn.getErrorStream());
            } catch (Throwable ignore) {
                // 忽略
            }
            throw new IllegalStateException("上游 HTTP " + code + ": "
                    + DevCloudClient.trim(err, 300));
        }
        return conn;
    }

    private static String read(InputStream in) throws Exception {
        if (in == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String l;
            while ((l = br.readLine()) != null) {
                sb.append(l).append('\n');
            }
        }
        return sb.toString();
    }
}
