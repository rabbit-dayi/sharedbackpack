package com.sharedbackpack.client.gui;

import com.sharedbackpack.client.ClientDiagnostics;
import com.sharedbackpack.client.DashboardClient;
import com.sharedbackpack.client.DashboardModel;
import com.sharedbackpack.client.mixin.SlotAccessor;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.Slot;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Shared layout, local navigation, drawing and button actions for both GUI APIs. */
public final class DashboardView {
    public interface Canvas extends GuiCanvas {
        void item(DashboardModel.Entry entry, int x, int y);
    }
    public static final class Control {
        public final int x, y, width, height;
        public final Supplier<String> label;
        public final BooleanSupplier enabled;
        public final Runnable action;
        Control(int x, int y, int width, int height, Supplier<String> label, BooleanSupplier enabled, Runnable action) {
            this.x = x; this.y = y; this.width = width; this.height = height;
            this.label = label; this.enabled = enabled; this.action = action;
        }
    }

    public final DashboardModel model;
    public int left, top, width, height, side, center, right, gridY, inventoryY, boxRows;
    public boolean infoOpen;
    public String boxDraft = "";
    private int boxScroll;
    private List<Control> controls;

    public DashboardView(DashboardModel model) { this.model = model; }

    public void layout(int screenWidth, int screenHeight, GenericContainerScreenHandler handler) {
        width = Math.min(480, screenWidth - 4);
        height = 236;
        left = (screenWidth - width) / 2;
        top = Math.max(0, (screenHeight - height) / 2);
        side = (width - 178) / 2;
        center = left + side + 4;
        right = center + 174;
        gridY = top + 26;
        inventoryY = top + 151;
        boxRows = (height - 100) / 18;
        clampBoxes();
        for (int i = 0; i < handler.slots.size(); i++) {
            Slot slot = handler.slots.get(i);
            int sx = -10000, sy = -10000;
            if (i == 45) { sx = right + 7; sy = top + 210; }
            else if (i >= 54 && i < 81) {
                sx = center + 4 + (i - 54) % 9 * 18;
                sy = inventoryY + (i - 54) / 9 * 18;
            } else if (i >= 81 && i < 90) {
                sx = center + 4 + (i - 81) * 18;
                sy = inventoryY + 58;
            }
            ((SlotAccessor) slot).sharedbackpack$setX(sx - left);
            ((SlotAccessor) slot).sharedbackpack$setY(sy - top);
        }
        controls = createControls();
    }

    public List<Control> controls() { return controls; }

    private List<Control> createControls() {
        List<Control> controls = new ArrayList<>();
        add(controls, center + 149, top + 5, 17, () -> "×", () -> !model.query().isEmpty(), () -> model.search(""));
        add(controls, left + 4, top + 23, side - 4, () -> tr("team"), () -> true,
                () -> DashboardClient.switchBox(model, ""));
        for (int i = 0; i < boxRows; i++) {
            final int row = i;
            add(controls, left + 4, top + 44 + i * 18, side - 4,
                    () -> box(row) == null ? "" : (box(row).equals(model.snapshot().storage) ? "● " : "") + box(row), () -> box(row) != null,
                    () -> { if (box(row) != null) DashboardClient.switchBox(model, box(row)); });
        }
        add(controls, left + 4, top + height - 23, side - 4, () -> tr("create"), () -> true,
                () -> { DashboardClient.createBox(model, boxDraft.trim()); boxDraft = ""; });
        add(controls, center + 4, top + 119, 19, () -> "<", () -> model.page() > 0,
                () -> page(-1));
        add(controls, center + 25, top + 119, 115, () -> tr("sort." + model.sort()), () -> true,
                () -> { model.sort((model.sort() + 1) % 6); DashboardClient.preferences(model); });
        add(controls, center + 142, top + 119, 24, () -> ">", () -> model.page() + 1 < model.pages(),
                () -> page(1));
        add(controls, right + 3, top + 105, side - 5,
                () -> model.namespace().isEmpty() ? tr("namespaces") : model.namespace(), () -> true, model::cycleNamespace);
        add(controls, right + 3, top + 126, side - 5, () -> tr("upgrade"), () -> true,
                () -> DashboardClient.upgrade(model));
        add(controls, right + 3, top + 146, side - 5, () -> tr("diagnostics"), () -> true,
                () -> { infoOpen = true; ClientDiagnostics.poll(model.snapshot().syncId); });
        add(controls, right + 3, top + 166, side - 5, () -> tr("refresh"), () -> true,
                () -> DashboardClient.refresh(model));
        return controls;
    }

    private static void add(List<Control> controls, int x, int y, int width, Supplier<String> label,
                            BooleanSupplier enabled, Runnable action) {
        controls.add(new Control(x, y, width, 16, label, enabled, action));
    }
    private void clampBoxes() { boxScroll = Math.max(0, Math.min(Math.max(0, model.snapshot().boxes.size() - boxRows), boxScroll)); }
    private String box(int row) {
        clampBoxes();
        int index = boxScroll + row;
        return index < model.snapshot().boxes.size() ? model.snapshot().boxes.get(index) : null;
    }
    private void page(int delta) { model.page(model.page() + delta); DashboardClient.preferences(model); }

    public boolean scroll(double mx, double my, double amount) {
        if (mx >= left && mx < center && my >= top + 42 && my < top + height - 48) {
            boxScroll += amount > 0 ? -1 : 1;
            clampBoxes();
            return true;
        }
        if (cell(mx, my) >= 0) { page(amount > 0 ? -1 : 1); return true; }
        return false;
    }

    public int cell(double mx, double my) {
        int dx = (int) Math.floor(mx) - center - 4;
        int dy = (int) Math.floor(my) - gridY;
        return dx >= 0 && dx < 162 && dy >= 0 && dy < 90 ? dy / 18 * 9 + dx / 18 : -1;
    }

    public boolean clickGrid(double mx, double my, int button, boolean shift) {
        int cell = cell(mx, my);
        if (cell < 0) return false;
        DashboardModel.Entry entry = model.entry(cell);
        if (entry != null && (button == 0 || button == 1)) DashboardClient.take(model, entry, button == 1 || shift, shift && button == 0);
        return true;
    }

    public boolean depositHit(double mx, double my) {
        return mx >= right + 6 && mx < right + 24 && my >= top + 209 && my < top + 227;
    }

    public void draw(Canvas canvas, int mouseX, int mouseY) {
        DashboardModel.Snapshot snapshot = model.snapshot();
        canvas.fill(left, top, left + width, top + height, 0xFF101923);
        canvas.fill(left + 1, top + 1, center - 2, top + height - 1, 0xFF1B2B3A);
        canvas.fill(center, top + 1, right - 3, top + height - 1, 0xFF172432);
        canvas.fill(right, top + 1, left + width - 1, top + height - 1, 0xFF1B2B3A);
        label(canvas, tr("boxes"), left + 5, top + 7, side - 6, 0xE7F0FA);
        label(canvas, tr("info"), right + 5, top + 7, side - 7, 0xE7F0FA);
        label(canvas, snapshot.storage.isEmpty() ? tr("team") : snapshot.storage, right + 5, top + 25, side - 7, 0x73DDB9);
        label(canvas, tr("capacity"), right + 5, top + 43, side - 7, 0x9DAFC3);
        label(canvas, snapshot.usedSlots + "/" + ((long) snapshot.maxPages * 45), right + 5, top + 56, side - 7, 0xE7F0FA);
        label(canvas, tr("total"), right + 5, top + 74, side - 7, 0x9DAFC3);
        label(canvas, Long.toString(model.total()), right + 5, top + 87, side - 7, 0xE7F0FA);
        String health = !ClientDiagnostics.supported() ? "unsupported" : ClientDiagnostics.status() == null ? "waiting"
                : ClientDiagnostics.stale() ? "stale" : "live";
        label(canvas, tr("status." + health), right + 5, top + 185, side - 7, 0x73DDB9);
        label(canvas, tr("deposit"), right + 5, top + 197, side - 7, 0xE7F0FA);
        slot(canvas, right + 7, top + 210);
        for (int i = 0; i < 45; i++) {
            int x = center + 4 + i % 9 * 18, y = gridY + i / 9 * 18;
            slot(canvas, x, y);
            DashboardModel.Entry entry = model.entry(i);
            if (entry != null) {
                canvas.item(entry, x, y);
            }
        }
        int hover = cell(mouseX, mouseY);
        if (hover >= 0) {
            int x = center + 4 + hover % 9 * 18, y = gridY + hover / 9 * 18;
            canvas.fill(x, y, x + 16, y + 16, 0x50FFFFFF);
        }
        label(canvas, tr("inventory") + "  " + (model.page() + 1) + "/" + model.pages(), center + 4, top + 139, 164, 0x9DAFC3);
        for (int i = 0; i < 36; i++) slot(canvas, center + 4 + i % 9 * 18,
                inventoryY + (i < 27 ? i / 9 * 18 : 58));
        if (snapshot.boxes.size() > boxRows) {
            int trackTop = top + 44, trackHeight = boxRows * 18 - 2;
            int thumb = Math.max(8, trackHeight * boxRows / snapshot.boxes.size());
            int offset = (trackHeight - thumb) * boxScroll / Math.max(1, snapshot.boxes.size() - boxRows);
            canvas.fill(center - 4, trackTop + offset, center - 2, trackTop + offset + thumb, 0xFF72B7C9);
        }
    }

    public static void drawButton(GuiCanvas canvas, Control control, boolean hovered) {
        boolean enabled = control.enabled.getAsBoolean();
        canvas.fill(control.x, control.y, control.x + control.width, control.y + control.height,
                !enabled ? 0xFF1B2B3A : hovered ? 0xFF42677D : 0xFF2A4458);
        String label = trim(canvas, control.label.get(), control.width - 5);
        canvas.text(label, control.x + (control.width - canvas.textWidth(label)) / 2, control.y + 4,
                enabled ? 0xE8F2FA : 0x64768A);
    }
    private static void slot(GuiCanvas canvas, int x, int y) {
        canvas.fill(x - 1, y - 1, x + 17, y + 17, 0xFF385064);
        canvas.fill(x, y, x + 16, y + 16, 0xFF0C1620);
    }
    private static void label(GuiCanvas canvas, String text, int x, int y, int width, int color) {
        canvas.text(trim(canvas, text, width), x, y, color);
    }
    public static String trim(GuiCanvas canvas, String text, int width) {
        if (canvas.textWidth(text) <= width) return text;
        int end = text.length();
        while (end > 0 && canvas.textWidth(text.substring(0, end) + "…") > width) end--;
        return text.substring(0, end) + "…";
    }
    public static String compact(long count) {
        if (count < 1000) return Long.toString(count);
        if (count < 1_000_000) return count / 1000 + "k";
        if (count < 1_000_000_000) return count / 1_000_000 + "m";
        if (count < 1_000_000_000_000L) return count / 1_000_000_000 + "b";
        if (count < 1_000_000_000_000_000L) return count / 1_000_000_000_000L + "t";
        if (count < 1_000_000_000_000_000_000L) return count / 1_000_000_000_000_000L + "q";
        return count / 1_000_000_000_000_000_000L + "Q";
    }
    public static String tr(String key, Object... args) { return I18n.translate("gui.sharedbackpack.dashboard." + key, args); }
}
