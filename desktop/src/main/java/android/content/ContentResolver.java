package android.content;

import android.database.Cursor;
import android.net.Uri;

/** 桌面桩：SDK_INT=28 下不会走 MediaStore 分支，这里只保证编译/类加载。 */
public class ContentResolver {
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    public int update(Uri uri, ContentValues values, String where, String[] args) {
        return 0;
    }

    public int delete(Uri uri, String where, String[] args) {
        return 0;
    }

    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        return null;
    }

    public java.io.OutputStream openOutputStream(Uri uri) {
        return null;
    }
}