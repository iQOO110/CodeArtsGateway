package android.os;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** 桌面桩：androidasync 的 post/postDelayed。 */
public class Handler {
    private static final ScheduledExecutorService POOL =
            Executors.newScheduledThreadPool(2, r -> {
                Thread t = new Thread(r, "android-handler");
                t.setDaemon(true);
                return t;
            });

    public Handler() {
    }

    public Handler(Looper looper) {
    }

    public boolean post(Runnable r) {
        POOL.execute(r);
        return true;
    }

    public boolean postDelayed(Runnable r, long delayMillis) {
        POOL.schedule(r, Math.max(0, delayMillis), TimeUnit.MILLISECONDS);
        return true;
    }

    public void removeCallbacks(Runnable r) {
    }

    public void removeCallbacksAndMessages(Object token) {
    }
}