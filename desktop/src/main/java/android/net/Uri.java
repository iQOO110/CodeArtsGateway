package android.net;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** 桌面桩：androidasync / ContentResolver 简单用到的 Uri。 */
public final class Uri {
    private final String value;

    private Uri(String value) {
        this.value = value;
    }

    public static Uri parse(String s) {
        return new Uri(s == null ? "" : s);
    }

    public static Uri fromFile(File f) {
        return new Uri(f == null ? "" : f.toURI().toString());
    }

    public static Uri withAppendedPath(Uri base, String path) {
        String b = base == null ? "" : base.value;
        if (!b.endsWith("/")) {
            b += "/";
        }
        return new Uri(b + path);
    }

    @Override
    public String toString() {
        return value;
    }

    public String getScheme() {
        int i = value.indexOf(':');
        return i < 0 ? null : value.substring(0, i);
    }

    public String getHost() {
        int i = value.indexOf("://");
        if (i < 0) {
            return null;
        }
        int j = value.indexOf('/', i + 3);
        return value.substring(i + 3, j < 0 ? value.length() : j);
    }

    public String getPath() {
        int i = value.indexOf("://");
        int j = i < 0 ? value.indexOf(':') : value.indexOf('/', i + 3);
        return j < 0 ? null : value.substring(j);
    }

    public String getQuery() {
        int i = value.indexOf('?');
        return i < 0 ? null : value.substring(i + 1);
    }

    public List<String> getPathSegments() {
        List<String> out = new ArrayList<>();
        String p = getPath();
        if (p != null) {
            for (String s : p.split("/")) {
                if (!s.isEmpty()) {
                    out.add(s);
                }
            }
        }
        return out;
    }
}