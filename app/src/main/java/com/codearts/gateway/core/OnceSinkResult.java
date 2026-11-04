package com.codearts.gateway.core;

import java.util.concurrent.atomic.AtomicBoolean;

/** onDone 只触发一次的结果包装。 */
public final class OnceSinkResult {

    public final DevCloudClient.Sink target;
    private final AtomicBoolean finished = new AtomicBoolean(false);

    public OnceSinkResult(DevCloudClient.Sink target) {
        this.target = target;
    }

    /** 幂等收尾：只有第一次生效。 */
    public void finish(boolean ok, String error) {
        if (finished.compareAndSet(false, true)) {
            target.onDone(ok, error);
        }
    }

    /** 上游心跳透传（SSE heartbeat → 客户端 keep-alive，v1.9.9）。 */
    public void heartbeat() {
        target.heartbeat();
    }
}
