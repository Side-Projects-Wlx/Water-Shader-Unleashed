package net.watershader.config;

import net.glasslauncher.mods.gcapi3.api.PostConfigLoadedListener;
import net.glasslauncher.mods.gcapi3.api.PreConfigSavedListener;
import net.glasslauncher.mods.gcapi3.impl.GlassYamlFile;

/**
 * GlassConfigAPI 3 lifecycle hook notifying the active water shader runtime
 * whenever configuration is loaded or saved. Intentionally avoids referencing
 * Minecraft or rendering classes so {@code preLaunch} never triggers early classloading.
 */
public final class WaterShaderConfigListener implements PreConfigSavedListener, PostConfigLoadedListener {
    @Override
    public void onPreConfigSaved(int source, GlassYamlFile oldValues, GlassYamlFile newValues) {
        WaterShaderConfig.notifyChanged();
    }

    @Override
    public void PostConfigLoaded(int source) {
        WaterShaderConfig.notifyChanged();
    }
}
