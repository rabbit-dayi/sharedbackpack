package com.sharedbackpack.client.gui;

import com.sharedbackpack.client.DashboardClient;
import com.sharedbackpack.client.DashboardModel;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** A native handled inventory with an independent, purely virtual catalog. */
public final class DashboardScreen extends HandledScreen<GenericContainerScreenHandler> {
    private final DashboardView view;
    private TextFieldWidget search;
    private TextFieldWidget boxName;
    private boolean virtualGesture;

    public DashboardScreen(GenericContainerScreenHandler handler, PlayerInventory inventory, Text title, DashboardModel model) {
        super(handler, inventory, title);
        view = new DashboardView(model);
    }

    @Override protected void init() {
        if (boxName != null) view.boxDraft = boxName.getText();
        view.layout(width, height, handler);
        backgroundWidth = view.width;
        backgroundHeight = view.height;
        super.init();
        x = view.left;
        y = view.top;
        search = addButton(new TextFieldWidget(textRenderer, view.center + 4, y + 5, 142, 16,
                DashboardPlatform.literal(DashboardView.tr("search"))));
        search.setMaxLength(128);
        search.setText(view.model.query());
        search.setSuggestion(search.getText().isEmpty() ? DashboardView.tr("search") : null);
        search.setChangedListener(value -> {
            view.model.search(value);
            search.setSuggestion(value.isEmpty() ? DashboardView.tr("search") : null);
        });
        boxName = addButton(new TextFieldWidget(textRenderer, x + 4, y + view.height - 44, view.side - 4, 16,
                DashboardPlatform.literal(DashboardView.tr("box_name"))));
        boxName.setMaxLength(128);
        boxName.setText(view.boxDraft);
        boxName.setSuggestion(boxName.getText().isEmpty() ? DashboardView.tr("box_name") : null);
        boxName.setChangedListener(value -> {
            view.boxDraft = value;
            boxName.setSuggestion(value.isEmpty() ? DashboardView.tr("box_name") : null);
        });
        for (DashboardView.Control control : view.controls()) addButton(new DashboardButton(control));
    }

    @Override protected void drawBackground(MatrixStack context, float delta, int mouseX, int mouseY) {
        view.draw(new MinecraftGuiCanvas(context), mouseX, mouseY);
    }
    @Override protected void drawForeground(MatrixStack context, int mouseX, int mouseY) {}

    @Override public void render(MatrixStack context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        super.render(context, mouseX, mouseY, delta);
        if (view.infoOpen) {
            DiagnosticsPanel.draw(new MinecraftGuiCanvas(context), width, height);
            return;
        }
        int cell = view.cell(mouseX, mouseY);
        DashboardModel.Entry entry = cell < 0 ? null : view.model.entry(cell);
        if (entry != null) {
            List<Text> tooltip = new ArrayList<>(getTooltipFromItem(entry.icon));
            tooltip.add(DashboardPlatform.literal(DashboardView.tr("exact_count", entry.count)));
            tooltip.add(DashboardPlatform.literal(DashboardView.tr("take_hint")));
            renderTooltip(context, tooltip, mouseX, mouseY);
        } else if (view.depositHit(mouseX, mouseY)) {
            wrappedTooltip(context, DashboardView.tr("deposit_hint"), mouseX, mouseY);
        } else drawMouseoverTooltip(context, mouseX, mouseY);
        for (DashboardView.Control control : view.controls()) {
            if (mouseX >= control.x && mouseX < control.x + control.width && mouseY >= control.y && mouseY < control.y + control.height
                    && !control.label.get().isEmpty()) {
                wrappedTooltip(context, control.label.get(), mouseX, mouseY);
                break;
            }
        }
    }

    private void wrappedTooltip(MatrixStack context, String text, int mouseX, int mouseY) {
        renderOrderedTooltip(context, textRenderer.wrapLines(DashboardPlatform.literal(text), Math.min(280, width - 20)), mouseX, mouseY);
    }

    @Override public void tick() { super.tick(); search.tick(); boxName.tick(); }

    @Override public boolean mouseClicked(double mx, double my, int button) {
        if (view.infoOpen) {
            if (DiagnosticsPanel.closeHit(width, height, mx, my)) view.infoOpen = false;
            return true;
        }
        if (view.clickGrid(mx, my, button, hasShiftDown())) {
            search.setTextFieldFocused(false);
            boxName.setTextFieldFocused(false);
            virtualGesture = true;
            return true;
        }
        if (button == 0 && view.depositHit(mx, my) && !client.player.inventory.getCursorStack().isEmpty()) {
            DashboardClient.depositCursor(view.model);
            virtualGesture = true;
            return true;
        }
        search.setTextFieldFocused(false);
        boxName.setTextFieldFocused(false);
        boolean result = super.mouseClicked(mx, my, button);
        if (!boxName.getText().equals(view.boxDraft)) boxName.setText(view.boxDraft);
        if (!search.getText().equals(view.model.query())) search.setText(view.model.query());
        return result;
    }
    @Override public boolean mouseReleased(double mx, double my, int button) {
        if (view.infoOpen) return true;
        if (virtualGesture) { virtualGesture = false; return true; }
        return super.mouseReleased(mx, my, button);
    }
    @Override public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        return view.infoOpen || virtualGesture || super.mouseDragged(mx, my, button, dx, dy);
    }
    @Override public boolean mouseScrolled(double mx, double my, double amount) {
        return view.infoOpen || view.scroll(mx, my, amount) || super.mouseScrolled(mx, my, amount);
    }
    @Override protected void onMouseClick(net.minecraft.screen.slot.Slot slot, int slotId, int button,
                                          net.minecraft.screen.slot.SlotActionType action) {
        if (action == net.minecraft.screen.slot.SlotActionType.QUICK_MOVE && (slotId == 45 || slotId >= 54)) {
            DashboardPlatform.quickDeposit(client, handler, slotId, button);
        } else super.onMouseClick(slot, slotId, button, action);
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (view.infoOpen) {
            if (key == GLFW.GLFW_KEY_ESCAPE) view.infoOpen = false;
            return true;
        }
        TextFieldWidget focused = search.isFocused() ? search : boxName.isFocused() ? boxName : null;
        if (focused != null) {
            if (key == GLFW.GLFW_KEY_ESCAPE) { focused.setTextFieldFocused(false); setFocused(null); }
            else if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                if (focused == boxName) { DashboardClient.createBox(view.model, boxName.getText().trim()); boxName.setText(""); }
            } else focused.keyPressed(key, scan, modifiers);
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }
    @Override public boolean charTyped(char character, int modifiers) {
        if (view.infoOpen) return true;
        if (search.isFocused()) return search.charTyped(character, modifiers);
        if (boxName.isFocused()) return boxName.charTyped(character, modifiers);
        return super.charTyped(character, modifiers);
    }
    @Override public void removed() { DashboardClient.clear(); super.removed(); }
}
