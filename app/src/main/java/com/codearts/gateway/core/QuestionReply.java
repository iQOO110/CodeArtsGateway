package com.codearts.gateway.core;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 码道提问应答协议辅助（A14）。
 *
 * <p>协议证据（线上前端 bundle 126.10.7）：
 * {@code QUESTION_REPLY = "/v1/cloudagent/sessions/:id/messages/reply"}，
 * 请求体 {@code {"question_id": <requestId>, "answers": string[][]}}。
 * 前端 mcQuestion 组件构造每项答案的格式为
 * {@code "选项label" - 选项description}；"其他" 则直接填用户文本。
 */
public final class QuestionReply {

    private QuestionReply() {
    }

    /** 取上一轮码道提问的 question_id；缺失返回空串。 */
    public static String questionId(String pendingJson) {
        JSONObject p = unwrap(pendingJson);
        if (p == null) {
            return "";
        }
        return p.optString("question_id", p.optString("requestId",
                p.optString("id", "")));
    }

    /** 事件可能被 ask / data 包一层，这里统一解包。 */
    static JSONObject unwrap(String pendingJson) {
        try {
            JSONObject p = new JSONObject(pendingJson == null ? "{}" : pendingJson);
            JSONObject ask = p.optJSONObject("ask");
            if (ask != null) {
                p = ask;
            }
            JSONObject data = p.optJSONObject("data");
            if (data != null && (data.has("question_id") || data.has("questions")
                    || data.has("requestId") || data.has("id"))) {
                p = data;
            }
            JSONObject state = p.optJSONObject("state");
            if (state != null) {
                JSONObject input = state.optJSONObject("input");
                if (input != null && input.has("questions")) {
                    p.put("questions", input.optJSONArray("questions"));
                }
            }
            return p;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 构造应答接口要求的 {@code answers: string[][]}。
     *
     * <p>优先把 agent 回复（可能是选项 label / id / 序号 / 自然语言）匹配回题目选项，
     * 并按前端格式 {@code "label" - description} 输出；匹配不到则作为"其他"原文提交。
     */
    public static JSONArray buildAnswers(String pendingJson, String userReply) {
        JSONArray out = new JSONArray();
        JSONObject p = unwrap(pendingJson);
        JSONArray qs = p == null ? null : p.optJSONArray("questions");
        int count = qs == null || qs.length() == 0 ? 1 : qs.length();
        String reply = userReply == null ? "" : userReply.trim();
        List<String> tokens = extractTokens(reply);
        for (int i = 0; i < count; i++) {
            JSONObject q = qs == null ? null : qs.optJSONObject(i);
            JSONArray opts = q == null ? null : q.optJSONArray("options");
            JSONArray one = new JSONArray();
            if (opts == null || opts.length() == 0) {
                // 无选项：多问且行数正好对应时逐题分配，否则整段作为该题答案
                one.put(count > 1 && tokens.size() == count ? tokens.get(i) : reply);
            } else if (count > 1 && tokens.size() == count) {
                // 多问多行：逐题匹配，避免把同一批答案复制到每一题
                String token = tokens.get(i);
                if (looksFormatted(token)) {
                    one.put(token);
                } else {
                    String matched = matchOption(opts, token);
                    one.put(matched != null ? matched : token);
                }
            } else {
                for (String token : tokens) {
                    if (looksFormatted(token)) {
                        if (!contains(one, token)) {
                            one.put(token);
                        }
                        continue;
                    }
                    String matched = matchOption(opts, token);
                    if (matched != null) {
                        if (!contains(one, matched)) {
                            one.put(matched);
                        }
                    } else if (!token.isEmpty() && !contains(one, token)) {
                        one.put(token);
                    }
                }
            }
            if (one.length() == 0) {
                one.put(reply);
            }
            out.put(one);
        }
        return out;
    }

    /** 选项匹配：id / label / label 片段 / 1-based 序号。 */
    private static String matchOption(JSONArray opts, String token) {
        if (opts == null || token == null || token.isEmpty()) {
            return null;
        }
        String t = clean(token);
        if (t.isEmpty()) {
            return null;
        }
        String low = t.toLowerCase(java.util.Locale.ROOT);
        for (int j = 0; j < opts.length(); j++) {
            JSONObject o = opts.optJSONObject(j);
            if (o == null) {
                continue;
            }
            String id = o.optString("id", "").toLowerCase(java.util.Locale.ROOT);
            String label = o.optString("label", o.optString("text", ""));
            String labelLow = label.toLowerCase(java.util.Locale.ROOT);
            boolean hit = (!id.isEmpty() && id.equals(low))
                    || (!labelLow.isEmpty() && (labelLow.equals(low)
                    || labelLow.contains(low) || low.contains(labelLow)));
            if (!hit) {
                String num = String.valueOf(j + 1);
                hit = t.equals(num) || t.equals("选项" + num) || t.equals("第" + num + "项")
                        || t.equals("第" + num + "个");
            }
            if (hit) {
                return formatOption(o);
            }
        }
        return null;
    }

    /** 前端 mcQuestion 同款格式：{@code "label" - description}。 */
    private static String formatOption(JSONObject o) {
        String label = o.optString("label", o.optString("text", ""));
        String desc = o.optString("description", "");
        if (label.isEmpty()) {
            return desc.isEmpty() ? "" : desc;
        }
        return "\"" + label + "\"" + (desc.isEmpty() ? "" : " - " + desc);
    }

    /** 逐级剥离引号、列表前缀、选项前缀，便于与 label 比对。 */
    private static String clean(String s) {
        String v = s == null ? "" : s.trim();
        if (v.isEmpty()) {
            return v;
        }
        v = v.replaceFirst("^(?:[-*•]|\\d+[.、)]|[①②③④⑤⑥⑦⑧⑨⑩])\\s*", "").trim();
        if (v.length() >= 2 && v.startsWith("\"") && v.endsWith("\"")) {
            v = v.substring(1, v.length() - 1).trim();
        }
        return v;
    }

    /** 已经是前端格式的答案串（{@code "label" - description}），直接原样使用。 */
    private static boolean looksFormatted(String s) {
        return s != null && s.startsWith("\"") && s.contains("\" - ");
    }

    private static boolean contains(JSONArray a, String v) {
        for (int i = 0; i < a.length(); i++) {
            if (v.equals(a.optString(i))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 解析 Claude Code / DSHA 的固定提问答复文案：
     * {@code User has answered your questions: "Q"="A", "Q2"="A2". You can now continue...}
     * 返回按顺序的 A 串（本身已是 {@code "label" - description} 格式）。
     * A 内部可能再带引号（label 被引号包住），因此用后向断句而非简单 split。
     */
    public static List<String> extractClaudeAnswers(String text) {
        List<String> out = new ArrayList<>();
        if (text == null) {
            return out;
        }
        // 支持箭头格式：
        // · 你希望我这次帮你做什么？ -> 写代码/改功能
        // └ 你希望我这次帮你做什么？ -> 写代码/改功能
        if (text.contains("->") || text.contains("→")) {
            for (String line : text.split("\\r?\\n")) {
                int arrow = line.indexOf("->");
                if (arrow < 0) {
                    arrow = line.indexOf("→");
                }
                if (arrow >= 0) {
                    String a = clean(line.substring(arrow + (line.charAt(arrow) == '→' ? 1 : 2)));
                    if (!a.isEmpty()) {
                        out.add(a);
                    }
                }
            }
            if (!out.isEmpty()) {
                return out;
            }
        }
        int marker = text.indexOf("has answered your questions");
        if (marker < 0) {
            marker = text.indexOf("have been answered");
        }
        if (marker < 0) {
            marker = text.indexOf("has been answered");
        }
        if (marker < 0) {
            marker = text.indexOf("answered Claude's questions");
        }
        if (marker < 0) {
            marker = text.indexOf("answered your questions");
        }
        if (marker < 0) {
            return out;
        }
        int p = text.indexOf(':', marker);
        if (p < 0) {
            return out;
        }
        p++;
        int n = text.length();
        while (p < n) {
            while (p < n && Character.isWhitespace(text.charAt(p))) {
                p++;
            }
            if (p >= n || text.charAt(p) != '"') {
                break;
            }
            int qEnd = nextUnescapedQuote(text, p + 1);
            if (qEnd < 0) {
                break;
            }
            int eq = text.indexOf('=', qEnd + 1);
            if (eq < 0) {
                break;
            }
            int aStart = eq + 1;
            while (aStart < n && Character.isWhitespace(text.charAt(aStart))) {
                aStart++;
            }
            if (aStart >= n || text.charAt(aStart) != '"') {
                break;
            }
            int aEnd = answerEnd(text, aStart + 1);
            if (aEnd < 0) {
                break;
            }
            String a = text.substring(aStart + 1, aEnd).trim();
            if (!a.isEmpty()) {
                out.add(a);
            }
            p = aEnd + 1;
            while (p < n && (Character.isWhitespace(text.charAt(p)) || text.charAt(p) == ',')) {
                p++;
            }
            if (text.startsWith("You can now", p)) {
                break;
            }
        }
        return out;
    }

    private static int nextUnescapedQuote(String s, int from) {
        for (int i = from; i < s.length(); i++) {
            if (s.charAt(i) == '"' && (i == 0 || s.charAt(i - 1) != '\\')) {
                return i;
            }
        }
        return -1;
    }

    /** 答案结束引号：后面跟 {@code ", "}（下一对）或 {@code ". You can now"} 或串尾。 */
    private static int answerEnd(String s, int from) {
        for (int i = from; i < s.length(); i++) {
            if (s.charAt(i) != '"' || (i > 0 && s.charAt(i - 1) == '\\')) {
                continue;
            }
            if (i == s.length() - 1) {
                return i;
            }
            int j = i + 1;
            while (j < s.length() && Character.isWhitespace(s.charAt(j))) {
                j++;
            }
            if (j < s.length() && s.charAt(j) == ',') {
                return i;
            }
            if (j < s.length() && s.charAt(j) == '.') {
                return i;
            }
            if (s.startsWith("\". You can now", i) || s.startsWith("\".You can now", i)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 从 agent 回复里提取候选答案：优先 JSON 中的 answers/selected/values/choices 数组，
     * 否则按行 / 逗号 / 顿号拆分。多个问题且行数与题数一致时天然逐题对应。
     */
    static List<String> extractTokens(String reply) {
        List<String> out = new ArrayList<>();
        if (reply == null || reply.trim().isEmpty()) {
            return out;
        }
        String t = reply.trim();
        // Claude Code / DSHA 固定文案：
        // User has answered your questions: "问题"="选项label" - 描述". You can now continue...
        // 其 A 部分就是 reply 需要的完整答案串，优先原样取出。
        List<String> claude = extractClaudeAnswers(t);
        if (!claude.isEmpty()) {
            return claude;
        }
        if (t.startsWith("{") || t.startsWith("[")) {
            try {
                Object parsed = t.startsWith("{") ? new JSONObject(t) : new JSONArray(t);
                List<String> picked = new ArrayList<>();
                collectAnswerStrings(parsed, picked, false);
                if (!picked.isEmpty()) {
                    return picked;
                }
            } catch (Throwable ignore) {
                // 不是 JSON，继续按文本拆
            }
        }
        for (String line : t.split("\\r?\\n")) {
            for (String part : line.split("[,，、;；]")) {
                String v = clean(part);
                if (!v.isEmpty() && !out.contains(v)) {
                    out.add(v);
                }
            }
        }
        return out;
    }

    /** 递归收集答案字段；无命中时不再收集任意字符串，避免把题面当答案。 */
    private static void collectAnswerStrings(Object node, List<String> out, boolean inAnswer) {
        if (node == null) {
            return;
        }
        if (node instanceof JSONArray) {
            JSONArray a = (JSONArray) node;
            for (int i = 0; i < a.length(); i++) {
                collectAnswerStrings(a.opt(i), out, inAnswer);
            }
            return;
        }
        if (node instanceof JSONObject) {
            JSONObject o = (JSONObject) node;
            java.util.Iterator<String> it = o.keys();
            while (it.hasNext()) {
                String k = it.next();
                String low = k.toLowerCase(java.util.Locale.ROOT);
                boolean hit = inAnswer || low.contains("answer") || low.contains("selected")
                        || low.contains("choice") || low.contains("value");
                collectAnswerStrings(o.opt(k), out, hit);
            }
            return;
        }
        if (inAnswer && node instanceof String) {
            String v = clean((String) node);
            if (!v.isEmpty() && !out.contains(v)) {
                out.add(v);
            }
            return;
        }
        if (inAnswer && node instanceof Number) {
            String v = String.valueOf(node);
            if (!out.contains(v)) {
                out.add(v);
            }
        }
    }
}
