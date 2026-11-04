package android.content.res;

/** 桌面桩：仅保证编译/类加载。 */
public class Resources {
    public AssetManager getAssets() {
        return new AssetManager();
    }
}