package android.content.res;

/** 桌面桩：仅保证编译/类加载。 */
public class AssetManager {
    public void close() {
    }

    public java.io.InputStream open(String fileName) throws java.io.IOException {
        throw new java.io.FileNotFoundException(fileName);
    }
}