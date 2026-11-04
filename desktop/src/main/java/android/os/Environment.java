package android.os;

import java.io.File;

/** 桌面桩：把公共下载目录映射到用户目录下的 CodeArtsGateway/Download。 */
public final class Environment {
    private Environment() {
    }

    public static final String DIRECTORY_DOWNLOADS = "Download";

    public static File getExternalStoragePublicDirectory(String type) {
        File base = new File(System.getProperty("user.home", "."), "CodeArtsGateway");
        return new File(base, type == null ? "Download" : type);
    }

    public static String getExternalStorageDirectory() {
        File base = new File(System.getProperty("user.home", "."), "CodeArtsGateway");
        return base.getAbsolutePath();
    }
}