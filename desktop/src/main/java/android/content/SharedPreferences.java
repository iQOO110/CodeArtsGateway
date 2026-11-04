package android.content;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Properties;

/**
 * 桌面桩：文件型 SharedPreferences。
 * 存到 &lt;用户目录&gt;/CodeArtsGateway/config/&lt;name&gt;.properties，
 * 这样 Android 版 Store 的读写逻辑可以原样复用。
 */
public class SharedPreferences {
    private final File file;
    private final Properties props = new Properties();

    public SharedPreferences(File file) {
        this.file = file;
        if (file != null && file.isFile()) {
            try (FileInputStream in = new FileInputStream(file)) {
                props.load(in);
            } catch (Throwable ignored) {
                // 读取失败按空配置处理
            }
        }
    }

    public interface Editor {
        Editor putBoolean(String key, boolean value);

        Editor putInt(String key, int value);

        Editor putLong(String key, long value);

        Editor putString(String key, String value);

        Editor remove(String key);

        boolean commit();

        void apply();
    }

    private synchronized void persist() {
        if (file == null) {
            return;
        }
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            return;
        }
        try (FileOutputStream out = new FileOutputStream(file)) {
            props.store(out, "CodeArts Gateway desktop config");
        } catch (Throwable ignored) {
            // 写入失败不影响运行
        }
    }

    public boolean getBoolean(String key, boolean def) {
        return Boolean.parseBoolean(props.getProperty(key, String.valueOf(def)));
    }

    public int getInt(String key, int def) {
        try {
            return Integer.parseInt(props.getProperty(key, String.valueOf(def)));
        } catch (Throwable t) {
            return def;
        }
    }

    public long getLong(String key, long def) {
        try {
            return Long.parseLong(props.getProperty(key, String.valueOf(def)));
        } catch (Throwable t) {
            return def;
        }
    }

    public String getString(String key, String def) {
        return props.getProperty(key, def);
    }

    public boolean contains(String key) {
        return props.containsKey(key);
    }

    public Editor edit() {
        return new Editor() {
            @Override
            public Editor putBoolean(String key, boolean value) {
                props.setProperty(key, String.valueOf(value));
                return this;
            }

            @Override
            public Editor putInt(String key, int value) {
                props.setProperty(key, String.valueOf(value));
                return this;
            }

            @Override
            public Editor putLong(String key, long value) {
                props.setProperty(key, String.valueOf(value));
                return this;
            }

            @Override
            public Editor putString(String key, String value) {
                props.setProperty(key, value == null ? "" : value);
                return this;
            }

            @Override
            public Editor remove(String key) {
                props.remove(key);
                return this;
            }

            @Override
            public boolean commit() {
                persist();
                return true;
            }

            @Override
            public void apply() {
                persist();
            }
        };
    }
}