package com.codearts.gateway.core;

import org.json.JSONObject;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;

/**
 * 凭证体检 + 上下游连通性检测。
 * 一次 diagnose() 输出人类可读报告：本机 Cookie 构成、cftk 是否在、
 * 上游三个真实接口的响应（模型 / 福利 / 会话），并给出结论。
 */
public final class Diagnostics {

    /** 单项检测结果。 */
    public static final class Item {
        public final String name;
        public final boolean ok;
        public final String detail;

        public Item(String name, boolean ok, String detail) {
            this.name = name;
            this.ok = ok;
            this.detail = detail;
        }
    }

    public static final class Report {
        public final List<Item> items = new ArrayList<>();
        /** 总结论。 */
        public String verdict = "";
        /** 全部通过。 */
        public boolean allOk;

        JSONObject toJson() {
            JSONObject o = new JSONObject();
            try {
                o.put("verdict", verdict);
                o.put("allOk", allOk);
                org.json.JSONArray arr = new org.json.JSONArray();
                for (Item i : items) {
                    JSONObject it = new JSONObject();
                    it.put("name", i.name);
                    it.put("ok", i.ok);
                    it.put("detail", i.detail);
                    arr.put(it);
                }
                o.put("items", arr);
            } catch (Throwable ignored) {
            }
            return o;
        }
    }

    private Diagnostics() {
    }

    /** 同步执行全套检测（调用方自行放后台线程）。 */
    public static Report diagnose(Store store, Context ctx) {
        Report r = new Report();
        String cookie = store.getCookie();

        // ---------- 本地：Cookie 体检 ----------
        if (cookie.isEmpty()) {
            r.items.add(new Item("本地 Cookie", false,
                    "还没有保存任何 Cookie —— 请先到「登录 / 更新会话」完成登录并保存。"));
            r.verdict = "未登录：本地没有 Cookie，上游检测跳过。";
            r.allOk = false;
            return r;
        }

        List<String> names = new ArrayList<>();
        String cftk = "";
        for (String pair : cookie.split(";")) {
            String p = pair.trim();
            int eq = p.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String n = p.substring(0, eq);
            names.add(n);
            if (n.toLowerCase().contains("cftk")) {
                cftk = p.substring(eq + 1);
            }
        }
        r.items.add(new Item("本地 Cookie", true,
                "已保存，共 " + cookie.length() + " 字符、"
                        + names.size() + " 个键：" + names));

        r.items.add(new Item("cftk（CSRF）", !cftk.isEmpty(),
                cftk.isEmpty()
                        ? "Cookie 里没有 cftk 键 —— 上游大概率 302 到登录页。请确认登录成功后再点保存。"
                        : "已找到（值 " + cftk.length() + " 字符），会随请求头 double-submit 发送。"));

        if (cftk.isEmpty()) {
            r.verdict = "Cookie 不完整（缺 cftk）：请重新登录并在登录成功后保存。";
            r.allOk = false;
            return r;
        }

        // ---------- 上游探针 1：模型拉取（与模型页完全同一条链路） ----------
        List<Store.ModelInfo> models = DevCloudClient.fetchModels(cookie);
        boolean onlyKernel = models.size() == 1 && "GLM-5.2".equals(models.get(0).id);
        r.items.add(new Item("上游 模型拉取（agents主源+福利合并）", !models.isEmpty(),
                models.isEmpty()
                        ? "0 个模型：agents 主源与福利源都为空，请看详细日志中 devcloud 开头行"
                        : "获取到 " + models.size() + " 个模型"
                        + (onlyKernel ? "（注意：agents 主源为空，只有静态兜底 GLM-5.2，"
                        + "请把日志发我进一步定位）" : "")));

        // ---------- 上游探针 2：福利（签到）接口 ----------
        DevCloudClient.WelfareResult w = DevCloudClient.listWelfare(cookie);
        StringBuilder wd = new StringBuilder();
        if (w.success) {
            wd.append("解析出 ").append(w.items.size()).append(" 条福利");
            for (int i = 0; i < w.items.size() && i < 6; i++) {
                DevCloudClient.Welfare it = w.items.get(i);
                wd.append("，").append(it.typeNorm).append('/')
                        .append(it.rawStatus == null || it.rawStatus.isEmpty() ? "-" : it.rawStatus);
            }
        } else {
            wd.append(w.message == null ? "未知失败" : w.message);
        }
        // 原始报文落盘（/sdcard/Download/codearts-welfare.json），便于外部定位 schema 差异
        String where = DiagDump.save(ctx, "codearts-welfare.json", w.trace.toString());
        r.items.add(new Item("上游 /ops/delivery（签到）", w.success,
                wd + "；诊断报文: " + where));

        // ---------- 上游探针 3：积分/套餐用量（官网「用量统计」同源） ----------
        DevCloudClient.PackageStats pk = DevCloudClient.fetchPackageStats(cookie);
        String pkDesc;
        if (pk.ok) {
            pkDesc = "套餐 " + (pk.packageName.isEmpty() ? "?" : pk.packageName)
                    + "，积分 剩余 " + DevCloudClient.fmtNum(pk.creditRemain)
                    + " / " + DevCloudClient.fmtNum(pk.creditTotal)
                    + "，已用 " + DevCloudClient.fmtNum(pk.creditUsed)
                    + (pk.tokenTotal == null ? "" : "，token "
                    + DevCloudClient.fmtNum(pk.tokenUsed) + " / "
                    + DevCloudClient.fmtNum(pk.tokenTotal));
        } else {
            pkDesc = pk.message == null || pk.message.isEmpty() ? "未知失败" : pk.message;
        }
        r.items.add(new Item("上游 积分/套餐用量（snap-manager）", pk.ok, pkDesc));

        // ---------- 上游探针 4：建会话（对话链路） ----------
        String sessionProbe;
        boolean sessOk;
        try {
            String sid = DevCloudClient.createSession(cookie, "");
            sessOk = sid != null && !sid.isEmpty();
            sessionProbe = sessOk ? "成功，session_id=" + sid : "返回空 id";
        } catch (Throwable t) {
            sessOk = false;
            sessionProbe = String.valueOf(t);
        }
        r.items.add(new Item("上游 建会话（对话链路）", sessOk, sessionProbe));

        // ---------- 结论 ----------
        boolean all = true;
        for (Item i : r.items) {
            all = all && i.ok;
        }
        r.allOk = all;
        r.verdict = all
                ? "全部通过：Cookie 有效、上游三个接口可达，可以正常使用网关与签到。"
                : "存在失败项：看上面红叉项的说明；多数是 Cookie 失效/不完整，重新登录可解。";
        return r;
    }
}
