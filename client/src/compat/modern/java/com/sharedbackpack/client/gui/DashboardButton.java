package com.sharedbackpack.client.gui;

import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.DrawContext;

final class DashboardButton extends ButtonWidget {
    private final DashboardView.Control control;
    DashboardButton(DashboardView.Control control) {
        super(control.x, control.y, control.width, control.height, DashboardPlatform.literal(control.label.get()),
                button -> { if (control.enabled.getAsBoolean()) control.action.run(); }, DEFAULT_NARRATION_SUPPLIER);
        this.control = control;
    }
    @Override public void renderButton(DrawContext context, int mouseX, int mouseY, float delta) {
        active = control.enabled.getAsBoolean();
        String label = control.label.get();
        if (!getMessage().getString().equals(label)) setMessage(DashboardPlatform.literal(label));
        DashboardView.drawButton(new MinecraftGuiCanvas(context), control, isHovered() || isFocused());
    }
}
