package net.watershader.client;

import net.danygames2014.modmenu.api.ConfigScreenFactory;
import net.danygames2014.modmenu.api.ModMenuApi;

/**
 * ModMenu integration exposing {@link WaterShaderConfigScreen} via the {@code modmenu} entrypoint.
 */
public final class WaterShaderModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return WaterShaderConfigScreen::new;
    }
}
