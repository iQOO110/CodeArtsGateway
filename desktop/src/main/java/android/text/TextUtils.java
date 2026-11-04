package android.text;

/** 桌面桩：androidasync 只用到 isEmpty / equals / join。 */
public final class TextUtils {
    private TextUtils() {
    }

    public static boolean isEmpty(CharSequence s) {
        return s == null || s.length() == 0;
    }

    public static boolean equals(CharSequence a, CharSequence b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        int n = a.length();
        if (n != b.length()) {
            return false;
        }
        for (int i = 0; i < n; i++) {
            if (a.charAt(i) != b.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    public static String join(CharSequence delimiter, Iterable<?> tokens) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Object o : tokens) {
            if (!first) {
                sb.append(delimiter);
            }
            first = false;
            sb.append(o);
        }
        return sb.toString();
    }

    public static String join(CharSequence delimiter, Object[] tokens) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Object o : tokens) {
            if (!first) {
                sb.append(delimiter);
            }
            first = false;
            sb.append(o);
        }
        return sb.toString();
    }
}