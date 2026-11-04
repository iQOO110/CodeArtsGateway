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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * MCP 客户端中枢（Streamable HTTP，JSON-RPC 2.0）。
 *
 * <p>让网关自己连**任意第三方 MCP 服务器**：list 工具、执行调用。
 * 兼容两类实现：
 * <ul>
 *   <li>有状态：initialize 返回 {@code Mcp-Session-Id} 头，后续请求带回（MT 目前是无状态的，但别的服务器可能要求）；</li>
 *   <li>返回体既可能是 {@code application/json}，也可能是 {@code text/event-stream}（都会解析）。</li>
 * </ul>
 */
public final class McpHub {

    /** 一个 MCP 服务器配置与运行态。 */
    public static final class Server {
        public String name = "";
        public String url = "";
        public String token = "";
        public String sessionId = "";
        public boolean initialized;
        public final List<Tool> tools = new ArrayList<>();
        public long toolsAt;
        public String lastError = "";

        public Server(String name, String url, String token) {
            this.name = name == null ? "" : name;
            this.url = url == null ? "" : url.trim();
            this.token = token == null ? "" : token;
        }
    }

    /** MCP 工具。 */
    public static final class Tool {
        public String name = "";
        public String description = "";
        public String schema = "{\"type\":\"object\",\"properties\":{}}";
    }

    /** 暴露给模型的一个工具：暴露名 → (服务器, 原工具名)。 */
    public static final class Exposed {
        public final String name;
        public final Server server;
        public final Tool tool;

        public Exposed(String name, Server server, Tool tool) {
            this.name = name;
            this.server = server;
            this.tool = tool;
        }
    }

    /** 写/删/改/构建类工具名（默认拦截）。 */
    private static final Pattern MUTATING = Pattern.compile(
            "(write|delete|remove|move|rename|mkdir|create|build|sign|edit|replace|append|"
                    + "chmod|copy|truncate|patch|inject|install|uninstall|reboot|kill)",
            Pattern.CASE_INSENSITIVE);

    private static final AtomicInteger SEQ = new AtomicInteger();

    private McpHub() {
    }

    /** 解析配置里的 MCP 服务器列表：[{"name","url","token"}]。 */
    public static List<Server> parseServers(String json) {
        List<Server> out = new ArrayList<>();
        if (json == null || json.trim().isEmpty()) {
            return out;
        }
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) {
                    continue;
                }
                String url = o.optString("url", "");
                if (url.isEmpty()) {
                    continue;
                }
                String name = o.optString("name", "");
                if (name.isEmpty()) {
                    name = "mcp" + (i + 1);
                }
                out.add(new Server(name, url, o.optString("token", "")));
            }
        } catch (Throwable t) {
            LogRing.log("mcp", "MCP 配置解析失败: " + t);
        }
        return out;
    }

    /** 是否写入类工具（默认不允许自动执行）。 */
    public static boolean mutating(String name) {
        return name != null && MUTATING.matcher(name).find();
    }

    /** 刷新工具列表（默认 60 秒缓存；force=true 强制）。 */
    public static void refresh(Server s, int timeoutMs, boolean force) throws Exception {
        if (!force && !s.tools.isEmpty() && System.currentTimeMillis() - s.toolsAt < 60000L) {
            return;
        }
        if (!s.initialized || s.sessionId.isEmpty()) {
            JSONObject params = new JSONObject();
            params.put("protocolVersion", "2025-06-18");
            params.put("capabilities", new JSONObject());
            JSONObject ci = new JSONObject();
            ci.put("name", "taixu-codearts-gateway");
            ci.put("version", "1.9.0");
            params.put("clientInfo", ci);
            rpc(s, "initialize", params, timeoutMs, false);
            try {
                rpc(s, "notifications/initialized", null, timeoutMs, true);
            } catch (Throwable ignore) {
                // 通知失败不影响后续
            }
            s.initialized = true;
        }
        JSONObject r = rpc(s, "tools/list", new JSONObject(), timeoutMs, false);
        JSONObject result = r == null ? null : r.optJSONObject("result");
        JSONArray arr = result == null ? null : result.optJSONArray("tools");
        List<Tool> list = new ArrayList<>();
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject t = arr.optJSONObject(i);
                if (t == null) {
                    continue;
                }
                String name = t.optString("name", "");
                if (name.isEmpty()) {
                    continue;
                }
                Tool tool = new Tool();
                tool.name = name;
                tool.description = t.optString("description", "");
                JSONObject schema = t.optJSONObject("inputSchema");
                tool.schema = schema == null
                        ? "{\"type\":\"object\",\"properties\":{}}" : schema.toString();
                list.add(tool);
            }
        }
        s.tools.clear();
        s.tools.addAll(list);
        s.toolsAt = System.currentTimeMillis();
        s.lastError = "";
    }

    /** 执行一次工具调用，返回结果文本（内部已截断）。 */
    public static String call(Server s, String tool, String argsJson, int timeoutMs) throws Exception {
        JSONObject args;
        try {
            args = new JSONObject(argsJson == null || argsJson.trim().isEmpty() ? "{}" : argsJson);
        } catch (Throwable t) {
            args = new JSONObject();
            args.put("value", argsJson == null ? "" : argsJson);
        }
        JSONObject params = new JSONObject();
        params.put("name", tool);
        params.put("arguments", args);
        JSONObject r = rpc(s, "tools/call", params, timeoutMs, false);
        if (r == null) {
            return "(无响应)";
        }
        JSONObject result = r.optJSONObject("result");
        if (result == null) {
            return "MCP 错误: " + (r.optJSONObject("error") == null
                    ? "未知" : r.optJSONObject("error").toString());
        }
        if (result.optBoolean("isError", false)) {
            String txt0 = textOf(result);
            return "工具报错: " + DevCloudClient.trim(txt0, 2000);
        }
        if (result.has("structuredContent") && !result.isNull("structuredContent")) {
            return DevCloudClient.trim(result.get("structuredContent").toString(), 20000);
        }
        String txt = textOf(result);
        return txt.isEmpty() ? "(空结果)" : DevCloudClient.trim(txt, 20000);
    }

    private static String textOf(JSONObject result) {
        StringBuilder sb = new StringBuilder();
        JSONArray arr = result.optJSONArray("content");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject c = arr.optJSONObject(i);
                if (c == null) {
                    continue;
                }
                if ("text".equals(c.optString("type", ""))) {
                    sb.append(c.optString("text", ""));
                } else {
                    sb.append(c.toString());
                }
                sb.append('\n');
            }
        }
        return sb.toString().trim();
    }

    /** JSON-RPC 调用（notify=true 时是通知，不解析响应）。 */
    private static JSONObject rpc(Server s, String method, JSONObject params, int timeoutMs,
                                  boolean notify) throws Exception {
        JSONObject msg = new JSONObject();
        msg.put("jsonrpc", "2.0");
        if (!notify) {
            msg.put("id", SEQ.incrementAndGet());
        }
        msg.put("method", method);
        if (params != null) {
            msg.put("params", params);
        }
        HttpURLConnection conn = (HttpURLConnection) new URL(s.url).openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(Math.min(timeoutMs, 15000));
        conn.setReadTimeout(timeoutMs);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "application/json, text/event-stream");
        if (!s.token.isEmpty()) {
            conn.setRequestProperty("Authorization", "Bearer " + s.token);
        }
        if (!s.sessionId.isEmpty()) {
            conn.setRequestProperty("Mcp-Session-Id", s.sessionId);
        }
        conn.setDoOutput(true);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(msg.toString().getBytes(StandardCharsets.UTF_8));
        }
        int code = conn.getResponseCode();
        String sid = conn.getHeaderField("Mcp-Session-Id");
        if (sid == null || sid.isEmpty()) {
            sid = conn.getHeaderField("mcp-session-id");
        }
        if (sid != null && !sid.isEmpty()) {
            s.sessionId = sid;
        }
        if (code >= 400) {
            String err = "";
            try {
                err = readAll(conn.getErrorStream());
            } catch (Throwable ignore) {
                // 忽略
            }
            if (code == 404 || code == 400 || code == 401) {
                // 会话可能过期/未初始化，下次重来
                s.sessionId = "";
                s.initialized = false;
            }
            s.lastError = "HTTP " + code;
            throw new IllegalStateException("MCP HTTP " + code + ": " + DevCloudClient.trim(err, 200));
        }
        if (notify) {
            try {
                conn.getInputStream().close();
            } catch (Throwable ignore) {
                // 忽略
            }
            return null;
        }
        String raw = readAll(conn.getInputStream());
        return parseBody(raw);
    }

    /** 响应体可能是 JSON，也可能是 SSE（取第一条 data:）。 */
    public static JSONObject parseBody(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim();
        if (s.isEmpty()) {
            return null;
        }
        if (s.startsWith("{")) {
            try {
                return new JSONObject(s);
            } catch (Throwable ignore) {
                return null;
            }
        }
        for (String line : s.split("\n")) {
            String t = line.trim();
            if (t.startsWith("data:")) {
                try {
                    return new JSONObject(t.substring(5).trim());
                } catch (Throwable ignore) {
                    // 继续找
                }
            }
        }
        return null;
    }

    private static String readAll(InputStream in) throws Exception {
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
