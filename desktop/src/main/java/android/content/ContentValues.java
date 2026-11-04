package android.content;

import java.util.HashMap;
import java.util.Map;

/** 桌面桩：ContentValues。 */
public class ContentValues {
    private final Map<String, Object> map = new HashMap<>();

    public void put(String key, String value) {
        map.put(key, value);
    }

    public void put(String key, Integer value) {
        map.put(key, value);
    }

    public void put(String key, Long value) {
        map.put(key, value);
    }

    public void put(String key, Boolean value) {
        map.put(key, value);
    }

    public void put(String key, byte[] value) {
        map.put(key, value);
    }

    public String getAsString(String key) {
        Object o = map.get(key);
        return o == null ? null : String.valueOf(o);
    }

    public Long getAsLong(String key) {
        Object o = map.get(key);
        return o instanceof Number ? ((Number) o).longValue() : null;
    }

    public Object get(String key) {
        return map.get(key);
    }

    public boolean containsKey(String key) {
        return map.containsKey(key);
    }

    public void clear() {
        map.clear();
    }
}