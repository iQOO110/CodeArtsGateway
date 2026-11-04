package com.codearts.gateway.core;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 工具链一键自检：把「App 配置 → 工具上游 → MT/任意 MCP → 真实工具调用」每一环的
 * 真实报错原文抓出来，落盘到 /sdcard/Download/codearts-selftest.json。
 * 目的：不再猜——哪一环断的、断在哪个报错上，一目了然。
 */
public final class ToolSelfTest {

    /** 单项检查结果。 */
    public static final class Item {
        public String name;
        public boolean ok;
        public String detail = "";

        public Item(String name, boolean ok, String detail) {
            this.name = name;
            this.ok = ok;
            this.detail = detail == null ? "" : detail;
        }
    }

    /** 完整自检报告。 */
    public static final class Report {
        public final List<Item> items = new ArrayList<>();
        public String build = GatewayServer.BUILD_TAG;
        public String ts = new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.ROOT)
                .format(new java.util.Date());
        public boolean allOk = true;
        public String fixHint = "";
    }

    private ToolSelfTest() {
    }

    /** 同步执行全套检查（调用方放后台线程）。 */
    public static Report run(Store store) {
        Report r = new Report();

        // ① 配置层
        String base = store.getToolBase();
        boolean baseOk = base != null && !base.trim().isEmpty();
        r.items.add(new Item("① 工具上游地址", baseOk,
                baseOk ? base
                        : "（空）→ 这就是“还是不行”的直接原因：带 tools 的请求没地方去，仍走码道老路。"
                        + "打开 设置→工具/MCP设置，把「工具上游地址」填成 http://127.0.0.1:3065/v1"));
        if (!baseOk) {
            r.allOk = false;
            r.fixHint = "先完成第①项：设置→工具/MCP设置 填地址并保存。";
            dump(r);
            return r;
        }
        r.items.add(new Item("①b 上游 Key", !store.getToolKey().trim().isEmpty(),
                store.getToolKey().trim().isEmpty()
                        ? "（空）→ 多数网关要求 Bearer Key，缺了会 401"
                        : "已填（长度 " + store.getToolKey().trim().length() + "）"));
        r.items.add(new Item("①c MCP 自动执行开关", store.isMcpEnabled(),
                store.isMcpEnabled() ? "开" : "关（网关不会自动执行 MCP 工具）"));

        // ② 工具上游连通性
        String key = store.getToolKey().trim();
        String model = store.getToolModel().trim();
        try {
            // 先探端口，把"服务没跑"和"服务在但返回异常"区分开（v1.9.4）
            java.net.URL u = new java.net.URL(base);
            int pport = u.getPort() > 0 ? u.getPort() : 80;
            java.net.Socket sock = new java.net.Socket();
            int crc;
            try { sock.connect(new java.net.InetSocketAddress(u.getHost(), pport), 4000); crc = 0; }
            catch (Throwable cex) { crc = -1; }
            finally { try { sock.close(); } catch (Throwable ignore) { } }
            if (crc != 0) {
                throw new IllegalStateException("端口 " + pport + " 无监听（连接被拒绝）——"
                        + "「工具上游」那个 App 没在跑或已被系统冻结，先把它打开并锁定后台，再点一次自检");
            }
            JSONArray models = ToolUpstream.models(base, key, 20000);
            if (models == null) {
                throw new IllegalStateException("端口通，但 /models 返回空或非 JSON——地址路径可能不对"
                        + "（正确形如 http://127.0.0.1:3065/v1），或 Key 无效（看上游日志确认 401/404）");
            }
            List<String> ids = new ArrayList<>();
            for (int i = 0; i < models.length() && i < 10; i++) {
                JSONObject m = models.optJSONObject(i);
                if (m != null) {
                    ids.add(m.optString("id", ""));
                }
            }
            boolean modelOk = ids.contains(model);
            r.items.add(new Item("② 工具上游连通", true,
                    "OK，模型 " + ids + (modelOk ? "" : "；⚠️ 配置的模型「" + model + "」不在列表里")));
            if (!modelOk) {
                r.items.add(new Item("②b 模型名", false,
                        "「" + model + "」不在上游列表 " + ids + "，请改成列表里的一个"));
                r.allOk = false;
            }
        } catch (Throwable t) {
            r.allOk = false;
            r.items.add(new Item("② 工具上游连通", false, "真实报错: " + t));
            r.fixHint = "第②项失败=地址或 Key 不对；把 detail 里的 HTTP 状态码发我即可定位。";
            dump(r);
            return r;
        }

        // ③ 每个 MCP 服务器的连通性
        List<McpHub.Server> servers = McpHub.parseServers(store.getMcpServers());
        if (servers.isEmpty()) {
            r.allOk = false;
            r.items.add(new Item("③ MCP 服务器列表", false, "配置为空"));
        }
        int totalTools = 0;
        boolean anyMcpOk = false;
        for (McpHub.Server s : servers) {
            try {
                McpHub.refresh(s, 30000, true);
                List<String> sample = new ArrayList<>();
                for (int i = 0; i < s.tools.size() && i < 5; i++) {
                    sample.add(s.tools.get(i).name);
                }
                totalTools += s.tools.size();
                anyMcpOk = true;
                r.items.add(new Item("③ MCP[" + s.name + "] " + s.url, true,
                        "OK，" + s.tools.size() + " 个工具，如 " + sample));
            } catch (Throwable t) {
                r.allOk = false;
                r.items.add(new Item("③ MCP[" + s.name + "] " + s.url, false,
                        "真实报错: " + t + " —— 典型原因：MT 的 MCP 服务被系统冻结（回 MT 重新启动它，"
                                + "开悬浮球+锁后台），或地址/端口不对"));
            }
        }
        r.items.add(new Item("③t MCP 工具总数", totalTools > 0, totalTools + " 个（只读类会被放行）"));

        // ④ 真实执行一个只读工具（端到端铁证）
        if (anyMcpOk) {
            boolean probed = false;
            for (McpHub.Server s : servers) {
                for (McpHub.Tool t : s.tools) {
                    if ("mt_file_list".equals(t.name)) {
                        try {
                            String out = McpHub.call(s, t.name,
                                    "{\"path\":\"\",\"recursive\":false,\"cursor\":\"\",\"limit\":5}", 30000);
                            r.items.add(new Item("④ 真实调用 mt_file_list", true,
                                    "返回: " + DevCloudClient.trim(out, 220)));
                            probed = true;
                        } catch (Throwable t2) {
                            r.allOk = false;
                            r.items.add(new Item("④ 真实调用 mt_file_list", false,
                                    "真实报错: " + t2));
                        }
                        break;
                    }
                }
                if (probed) {
                    break;
                }
            }
            if (!probed) {
                r.items.add(new Item("④ 真实调用 mt_file_list", false,
                        "上游工具列表里没有 mt_file_list（MCP 服务器不是 MT？）"));
            }
        }

        // ⑤ 一句话结论
        r.items.add(new Item("⑤ 下一轮对话走向", true,
                "带 tools 的请求 → 工具上游（原生 function calling）；MCP 工具由网关执行"));
        r.fixHint = r.allOk
                ? "全部通过：去第三方 App 里再发一次消息，MCP 工具会被真实执行。"
                : "按上面 FAIL 项的 detail 逐个处理；处理完再点一次自检。";
        dump(r);
        return r;
    }

    /** 报告落盘（DiagDump 内部会处理 null context 的场景）。 */
    private static void dump(Report r) {
        try {
            JSONObject o = new JSONObject();
            o.put("build", r.build);
            o.put("ts", r.ts);
            o.put("allOk", r.allOk);
            o.put("fixHint", r.fixHint);
            JSONArray arr = new JSONArray();
            for (Item it : r.items) {
                JSONObject i = new JSONObject();
                i.put("name", it.name);
                i.put("ok", it.ok);
                i.put("detail", it.detail);
                arr.put(i);
            }
            o.put("items", arr);
            String json = o.toString(2);
            String where = DiagDump.save(null, "codearts-selftest.json", json);
            LogRing.log("selftest", "自检报告 " + (r.allOk ? "全过" : "有失败项") + " → " + where);
        } catch (Throwable ignore) {
            // 落盘失败不影响界面显示
        }
    }
}
