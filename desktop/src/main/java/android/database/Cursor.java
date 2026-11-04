package android.database;

/** 桌面桩：MediaStore 查询用不到，仅保证编译。 */
public interface Cursor extends AutoCloseable {
    boolean moveToNext();

    String getString(int columnIndex);

    int getColumnIndex(String columnName);

    @Override
    void close();
}