package com.sharedbackpack.client.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

public final class MinecraftGuiCanvas implements GuiCanvas {
    private final DrawContext context;
    private final MinecraftClient client = MinecraftClient.getInstance();

    public MinecraftGuiCanvas(DrawContext context) { this.context = context; }

    public void fill(int x1, int y1, int x2, int y2, int color) {
        context.fill(x1, y1, x2, y2, color);
    }

    public void text(String value, int x, int y, int color) {
        context.drawText(client.textRenderer, value, x, y, color, false);
    }

    public int textWidth(String value) { return client.textRenderer.getWidth(value); }

    public void icon(int index, int x, int y, int size) {
        
        context.drawTexture(GuiIcons.ATLAS, x, y, size, size,
                (float) (index % 8 * 16), (float) (index / 8 * 16), 16, 16, 128, 32);
    }
}

