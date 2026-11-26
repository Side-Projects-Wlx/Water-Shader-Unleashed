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
 * StationAPI client event listener registering the F9 Water Shader quick toggle plus unbound
 * (assignable in Controls) stencil debug and live GLSL hot-reload keybinds.
 */
public final class WaterShaderKeybinds {
    private static KeyBinding toggleShaderKey;
    private static KeyBinding reloadShadersKey;
    private static KeyBinding debugStencilKey;

    @EventListener
    public void registerKeyBindings(KeyBindingRegisterEvent event) {
        toggleShaderKey = new KeyBinding("Toggle Water Shader", Keyboard.KEY_F9);
        reloadShadersKey = new KeyBinding("Reload Water Shaders", Keyboard.KEY_NONE);
        debugStencilKey = new KeyBinding("Water Stencil Debug", Keyboard.KEY_NONE);
        event.register(toggleShaderKey);
        event.register(reloadShadersKey);
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
                WaterShaderClient.LOGGER.info("[WaterShader] Toggle ignored: disabled in config (Water Mode: Off)");
                if (mc != null && mc.inGameHud != null) {
                    mc.inGameHud.addChatMessage("[WaterShader] Disabled in config (Water Mode: Off)");
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
        if (reloadShadersKey != null && reloadShadersKey.code != Keyboard.KEY_NONE && key == reloadShadersKey.code) {
            boolean ok = WaterShaderClient.reloadShaders();
            WaterShaderClient.LOGGER.info("[WaterShader] Live shader hot-reload: {}", ok ? "SUCCESS" : "FAILED");
            if (mc != null && mc.inGameHud != null) {
                mc.inGameHud.addChatMessage(ok
                    ? "[WaterShader] Reloaded all 4 shaders cleanly."
                    : "[WaterShader] Shader reload failed — kept previous shaders (see log).");
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
