package android.provider;

import android.net.Uri;

/** 桌面桩：SDK_INT=28 下不会走 MediaStore 分支，仅保证编译。 */
public final class MediaStore {
    private MediaStore() {
    }

    public static final class Downloads {
        private Downloads() {
        }

        public static final Uri EXTERNAL_CONTENT_URI =
                Uri.parse("content://media/external/downloads");
        public static final String _ID = "_id";
        public static final String DISPLAY_NAME = "_display_name";
        public static final String MIME_TYPE = "mime_type";
        public static final String RELATIVE_PATH = "relative_path";
        public static final String IS_PENDING = "is_pending";
    }
}