package android.content;

import android.content.res.Resources;

import java.io.File;

/** 桌面桩：桌面 App 目录、配置与 SharedPreferences。 */
public class Context {
    public static final int MODE_PRIVATE = 0;

    private final File baseDir;

    public Context() {
        this(new File(System.getProperty("user.home", "."), "CodeArtsGateway"));
    }

    public Context(File baseDir) {
        this.baseDir = baseDir;
    }

    public File getBaseDir() {
        return baseDir;
    }

    public Context getApplicationContext() {
        return this;
    }

    public SharedPreferences getSharedPreferences(String name, int mode) {
        File dir = new File(baseDir, "config");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return new SharedPreferences(new File(dir, name + ".properties"));
    }

    public File getFilesDir() {
        File f = new File(baseDir, "files");
        f.mkdirs();
        return f;
    }

    public File getCacheDir() {
        File f = new File(baseDir, "cache");
        f.mkdirs();
        return f;
    }

    public File getExternalFilesDir(String type) {
        File f = new File(baseDir, type == null || type.isEmpty() ? "files" : type);
        f.mkdirs();
        return f;
    }

    public ContentResolver getContentResolver() {
        return new ContentResolver();
    }

    public Resources getResources() {
        return new Resources();
    }

    public String getPackageName() {
        return "com.codearts.gateway.desktop";
    }
}