package net.watershader.client;

import net.mine_diver.unsafeevents.listener.EventListener;
import net.minecraft.client.Minecraft;
import net.minecraft.client.option.KeyBinding;
import net.modificationstation.stationapi.api.client.event.keyboard.KeyStateChangedEvent;
import net.modificationstation.stationapi.api.client.event.option.KeyBindingRegisterEvent;
import net.watershader.WaterShaderClient;
import net.watershader.render.WaterShaderPipeline;
import org.lwjgl.input.Keyboard;

/**
 * StationAPI client event listener registering the F9 Water Shader quick toggle and an unbound
 * (assignable in Controls) stencil debug toggle.
 */
public final class WaterShaderKeybinds {
    private static KeyBinding toggleShaderKey;
    private static KeyBinding debugStencilKey;

    @EventListener
    public void registerKeyBindings(KeyBindingRegisterEvent event) {
        toggleShaderKey = new KeyBinding("Toggle Water Shader", Keyboard.KEY_F9);
        debugStencilKey = new KeyBinding("Water Stencil Debug", Keyboard.KEY_NONE);
        event.register(toggleShaderKey);
        event.register(debugStencilKey);
    }

    @EventListener
    public void onKeyStateChanged(KeyStateChangedEvent event) {
        if (event.environment != KeyStateChangedEvent.Environment.IN_GAME) {
            return;
        }
        int key = Keyboard.getEventKey();
        if (key == Keyboard.KEY_NONE || !Keyboard.getEventKeyState()) {
            return;
        }
        Minecraft mc = WaterShaderClient.getMinecraft();
        if (toggleShaderKey != null && toggleShaderKey.code != Keyboard.KEY_NONE && key == toggleShaderKey.code) {
            if (!WaterShaderPipeline.isConfiguredEnabled()) {
                WaterShaderClient.LOGGER.info("[WaterShader] Toggle ignored: disabled in config (water_mode 0)");
                if (mc != null && mc.inGameHud != null) {
                    mc.inGameHud.addChatMessage("[WaterShader] Disabled in config (water_mode 0)");
                }
                return;
            }
            boolean enabled = WaterShaderPipeline.toggleUserEnabled();
            WaterShaderClient.LOGGER.info("[WaterShader] Water Shader toggled: {}", enabled ? "ON" : "OFF");
            if (mc != null && mc.inGameHud != null) {
                mc.inGameHud.addChatMessage("[WaterShader] Water Shader: " + (enabled ? "ON" : "OFF"));
            }
            return;
        }
        if (debugStencilKey != null && debugStencilKey.code != Keyboard.KEY_NONE && key == debugStencilKey.code) {
            boolean enabled = WaterShaderPipeline.toggleDebugStencil();
            WaterShaderClient.LOGGER.info("[WaterShader] Stencil debug view: {}", enabled ? "ON" : "OFF");
            if (mc != null && mc.inGameHud != null) {
                mc.inGameHud.addChatMessage("[WaterShader] Stencil debug: " + (enabled ? "ON" : "OFF"));
            }
        }
    }
}
