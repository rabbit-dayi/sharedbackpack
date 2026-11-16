package com.sharedbackpack.client.mixin;

import com.sharedbackpack.client.BackpackScreenExtension;
import com.sharedbackpack.client.SharedBackpackClient;
import com.sharedbackpack.client.ClientDiagnostics;
import com.sharedbackpack.client.gui.ClientText;
import com.sharedbackpack.client.gui.DiagnosticsPanel;
import com.sharedbackpack.client.gui.GuiIcons;
import com.sharedbackpack.client.gui.IconButton;
import com.sharedbackpack.client.gui.MinecraftGuiCanvas;
import com.sharedbackpack.protocol.BackpackProtocol;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds widgets to the existing screen without replacing its handler or inventory interactions. */
@Mixin(GenericContainerScreen.class)
public abstract class GenericContainerScreenMixin extends HandledScreen<GenericContainerScreenHandler>
        implements BackpackScreenExtension {
    @Unique private TextFieldWidget sharedbackpack$search;
    @Unique private String sharedbackpack$confirmedQuery;
    @Unique private boolean sharedbackpack$infoOpen;

    protected GenericContainerScreenMixin(GenericContainerScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
    }

    @Override
    protected void init() {
        String draft = sharedbackpack$search == null ? null : sharedbackpack$search.getText();
        super.init();
        sharedbackpack$search = null;
        SharedBackpackClient.restoreScreen(this, handler.syncId);
        if (draft != null && sharedbackpack$search != null) sharedbackpack$search.setText(draft);
    }

    @Override
    public void sharedbackpack$updateSearch(String query, boolean unload) {
        if (unload) return;
        if (sharedbackpack$search == null) {
            sharedbackpack$search = addDrawableChild(new TextFieldWidget(textRenderer, x + 8, y + 4, 108, 12,
                    Text.translatable("gui.sharedbackpack.search_hint")));
            sharedbackpack$search.setMaxLength(BackpackProtocol.MAX_QUERY_LENGTH);
            sharedbackpack$search.setText(query);
            sharedbackpack$search.setSuggestion(query.isEmpty() ? Text.translatable("gui.sharedbackpack.search_hint").getString() : null);
            sharedbackpack$search.setChangedListener(value ->
                    sharedbackpack$search.setSuggestion(value.isEmpty() ? Text.translatable("gui.sharedbackpack.search_hint").getString() : null));
            addDrawableChild(new IconButton(x + 120, y + 3, ClientText.text("gui.sharedbackpack.search"),
                    GuiIcons.SEARCH, button -> sharedbackpack$submit()));
            addDrawableChild(new IconButton(x + 138, y + 3, ClientText.text("gui.sharedbackpack.clear"),
                    GuiIcons.CLEAR, button -> sharedbackpack$clear()));
            addDrawableChild(new IconButton(x + 156, y + 3, ClientText.text("gui.sharedbackpack.status.title"),
                    GuiIcons.INFO, button -> {
                        sharedbackpack$infoOpen = true;
                        sharedbackpack$search.setFocused(false);
                        setFocused(null);
                        ClientDiagnostics.poll(handler.syncId);
                    }));
        } else if (!query.equals(sharedbackpack$confirmedQuery)) {
            sharedbackpack$search.setText(query);
        }
        sharedbackpack$confirmedQuery = query;
    }

    @Unique
    private void sharedbackpack$submit() {
        SharedBackpackClient.search(handler.syncId, sharedbackpack$search.getText());
    }

    @Unique
    private void sharedbackpack$clear() {
        sharedbackpack$search.setText("");
        sharedbackpack$submit();
    }

    @Override
    public void sharedbackpack$tickSearch() {
        if (sharedbackpack$search != null) sharedbackpack$search.tick();
        if (sharedbackpack$infoOpen) ClientDiagnostics.poll(handler.syncId);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (sharedbackpack$infoOpen) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) sharedbackpack$infoOpen = false;
            return true;
        }
        if (sharedbackpack$search != null && sharedbackpack$search.isFocused()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                sharedbackpack$search.setFocused(false);
                setFocused(null);
            } else if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                sharedbackpack$submit();
            } else {
                sharedbackpack$search.keyPressed(keyCode, scanCode, modifiers);
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        if (sharedbackpack$search == null) {
            super.drawForeground(context, mouseX, mouseY);
            return;
        }
        context.drawText(textRenderer, Text.translatable("container.inventory"), playerInventoryTitleX, playerInventoryTitleY, 0x404040, false);
    }

    @Override
    public void removed() {
        SharedBackpackClient.clearScreen();
        super.removed();
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void sharedbackpack$renderInfo(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!sharedbackpack$infoOpen) return;
        context.getMatrices().push();
        context.getMatrices().translate(0, 0, 500);
        DiagnosticsPanel.draw(new MinecraftGuiCanvas(context), width, height, null, false);
        context.getMatrices().pop();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!sharedbackpack$infoOpen) return super.mouseClicked(mouseX, mouseY, button);
        if (button == 0 && DiagnosticsPanel.closeHit(width, height, mouseX, mouseY)) sharedbackpack$infoOpen = false;
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return sharedbackpack$infoOpen || super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        return sharedbackpack$infoOpen || super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        return sharedbackpack$infoOpen || super.mouseScrolled(mouseX, mouseY, amount);
    }

    @Override
    public boolean charTyped(char character, int modifiers) {
        return sharedbackpack$infoOpen || super.charTyped(character, modifiers);
    }
}

