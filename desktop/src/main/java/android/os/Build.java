package android.os;

/**
 * 桌面桩：固定 SDK_INT=28，让 ArtifactStore/DiagDump 走“直接文件写”分支，
 * 从而在 Windows 上落到真实目录，而不是 MediaStore 逻辑。
 */
public final class Build {
    private Build() {
    }

    public static final class VERSION {
        private VERSION() {
        }

        public static final int SDK_INT = 28;
        public static final String RELEASE = "9";
    }

    public static final class VERSION_CODES {
        private VERSION_CODES() {
        }

        public static final int Q = 29;
    }
}