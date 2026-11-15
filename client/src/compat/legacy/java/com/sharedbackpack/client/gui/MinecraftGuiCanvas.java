package com.sharedbackpack.client.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.util.math.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;

public final class MinecraftGuiCanvas implements DashboardView.Canvas {
    private final MatrixStack context;
    private final MinecraftClient client = MinecraftClient.getInstance();

    public MinecraftGuiCanvas(MatrixStack context) { this.context = context; }

    public void fill(int x1, int y1, int x2, int y2, int color) {
        DrawableHelper.fill(context, x1, y1, x2, y2, color);
    }

    public void text(String value, int x, int y, int color) {
        client.textRenderer.draw(context, value, x, y, color);
    }

    public int textWidth(String value) { return client.textRenderer.getWidth(value); }

    public void item(com.sharedbackpack.client.DashboardModel.Entry entry, int x, int y) {
        client.getItemRenderer().renderInGuiWithOverrides(entry.icon, x, y);
        context.push();
        context.translate(x + 16, y + 10, 300);
        context.scale(0.75F, 0.75F, 1);
        client.textRenderer.drawWithShadow(context, entry.countLabel, -client.textRenderer.getWidth(entry.countLabel), 0, 0xFFFFFF);
        context.pop();
    }

    public void icon(int index, int x, int y, int size) {
        client.getTextureManager().bindTexture(GuiIcons.ATLAS);
        RenderSystem.enableBlend();
        RenderSystem.color4f(1, 1, 1, 1);
        DrawableHelper.drawTexture(context, x, y, size, size,
                (float) (index % 8 * 16), (float) (index / 8 * 16), 16, 16, 128, 32);
    }
}

