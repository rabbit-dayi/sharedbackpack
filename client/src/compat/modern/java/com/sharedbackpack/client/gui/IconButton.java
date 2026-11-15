package com.sharedbackpack.client.gui;

import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.Tooltip;

public final class IconButton extends ButtonWidget {
    private final int icon;

    public IconButton(int x, int y, Text label, int icon, PressAction action) {
        super(x, y, 16, 14, label, action, DEFAULT_NARRATION_SUPPLIER);
        this.icon = icon;
        setTooltip(Tooltip.of(label));
    }

    @Override
    public void renderButton(DrawContext context, int mouseX, int mouseY, float delta) {
        GuiCanvas canvas = new MinecraftGuiCanvas(context);
        int left = getX(), top = getY();
        canvas.fill(left, top, left + 16, top + 14, isHovered() || isFocused() ? 0xFF406079 : 0xFF23394B);
        canvas.icon(icon, left + 2, top + 1, 12);
        
    }
}

