package com.sharedbackpack.client.gui;

import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.MinecraftClient;

public final class IconButton extends ButtonWidget {
    private final int icon;

    public IconButton(int x, int y, Text label, int icon, PressAction action) {
        super(x, y, 16, 14, label, action);
        this.icon = icon;
        
    }

    @Override
    public void renderButton(MatrixStack context, int mouseX, int mouseY, float delta) {
        GuiCanvas canvas = new MinecraftGuiCanvas(context);
        int left = x, top = y;
        canvas.fill(left, top, left + 16, top + 14, isHovered() ? 0xFF406079 : 0xFF23394B);
        canvas.icon(icon, left + 2, top + 1, 12);
        if (isHovered()) {
            Screen screen = MinecraftClient.getInstance().currentScreen;
            if (screen != null) screen.renderTooltip(context, getMessage(), mouseX, mouseY);
        }
    }
}

