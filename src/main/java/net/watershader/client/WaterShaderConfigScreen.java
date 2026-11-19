package net.watershader.client;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.watershader.WaterShaderClient;
import net.watershader.config.WaterShaderConfig;
import org.lwjgl.input.Keyboard;

/**
 * Native Beta 1.7.3 ModMenu configuration screen for Water Shader Unleashed.
 * Allows live switching of planar reflection FBO resolution and persists changes to
 * {@code config/watershader.txt}.
 */
public final class WaterShaderConfigScreen extends Screen {
    private static final int BUTTON_REFLECTION_RES = 1;
    private static final int BUTTON_DONE = 2;

    private final Screen parent;
    private ButtonWidget reflectionResButton;

    public WaterShaderConfigScreen(Screen parent) {
        this.parent = parent;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void init() {
        this.buttons.clear();
        int centerX = this.width / 2;
        int rowY = this.height / 6 + 48;

        this.reflectionResButton = new ButtonWidget(
                BUTTON_REFLECTION_RES,
                centerX - 110,
                rowY,
                220,
                20,
                formatReflectionButtonText());
        this.buttons.add(this.reflectionResButton);

        this.buttons.add(new ButtonWidget(
                BUTTON_DONE,
                centerX - 100,
                this.height / 6 + 150,
                200,
                20,
                "Done"));
    }

    @Override
    protected void buttonClicked(ButtonWidget button) {
        if (!button.active) {
            return;
        }
        if (button.id == BUTTON_REFLECTION_RES) {
            WaterShaderConfig cfg = WaterShaderClient.getConfig();
            if (cfg != null) {
                int nextDiv = cfg.cycleReflectionResolution();
                WaterShaderConfig.saveOption(
                        WaterShaderClient.getConfigPath(),
                        "reflection_resolution",
                        Integer.toString(nextDiv),
                        WaterShaderClient.LOGGER);
                WaterShaderClient.LOGGER.info(
                        "[WaterShader] Reflection resolution set to {} (divisor={})",
                        WaterShaderConfig.formatReflectionResolutionLabel(nextDiv),
                        nextDiv);
            }
            if (this.reflectionResButton != null) {
                this.reflectionResButton.text = formatReflectionButtonText();
            }
        } else if (button.id == BUTTON_DONE) {
            this.minecraft.setScreen(this.parent);
        }
    }

    @Override
    protected void keyPressed(char character, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            this.minecraft.setScreen(this.parent);
            return;
        }
        super.keyPressed(character, keyCode);
    }

    @Override
    public void render(int mouseX, int mouseY, float delta) {
        this.renderBackground();
        int centerX = this.width / 2;
        this.drawCenteredTextWithShadow(this.textRenderer, "Water Shader Unleashed", centerX, 20, 0xFFFFFF);
        this.drawCenteredTextWithShadow(
                this.textRenderer,
                "Planar reflection FBO quality (applies live, saved to watershader.txt)",
                centerX,
                this.height / 6 + 78,
                0xA0A0A0);
        this.drawCenteredTextWithShadow(
                this.textRenderer,
                "Tip: Press F9 in-game to toggle Water Shader ON/OFF",
                centerX,
                this.height / 6 + 98,
                0x80C8C0);
        super.render(mouseX, mouseY, delta);
    }

    private static String formatReflectionButtonText() {
        WaterShaderConfig cfg = WaterShaderClient.getConfig();
        int div = (cfg != null) ? cfg.getReflectionResolution() : 1;
        return "Reflection Resolution: " + WaterShaderConfig.formatReflectionResolutionLabel(div);
    }
}
