package com.sharedbackpack.client.gui;

import com.sharedbackpack.client.ClientDiagnostics;
import com.sharedbackpack.client.DashboardModel;
import com.sharedbackpack.protocol.BackpackProtocol;
import com.sharedbackpack.protocol.ServerStatus;

import java.util.Locale;

public final class DiagnosticsPanel {
    public static final int WIDTH = 236;
    public static final int HEIGHT = 232;
    private static final int TEXT = DashboardTheme.TEXT;
    private static final int MUTED = DashboardTheme.MUTED;

    private DiagnosticsPanel() {
    }

    public static int left(int width) { return (width - WIDTH) / 2; }
    public static int top(int height) { return Math.max(2, (height - HEIGHT) / 2); }

    public static boolean closeHit(int width, int height, double mx, double my) {
        int x = left(width), y = top(height);
        return mx >= x + WIDTH - 23 && mx < x + WIDTH - 7 && my >= y + 6 && my < y + 22;
    }
    public static int tabHit(int width, int height, double mx, double my) {
        int x = left(width), y = top(height);
        if (my < y + 22 || my >= y + 34) return -1;
        if (mx >= x + 119 && mx < x + 171) return 0;
        return mx >= x + 173 && mx < x + 225 ? 1 : -1;
    }


    public static void draw(GuiCanvas canvas, int width, int height, DashboardModel model, boolean storageTab) {
        int x = left(width), y = top(height);
        canvas.fill(0, 0, width, height, DashboardTheme.OVERLAY);
        canvas.fill(x, y, x + WIDTH, y + HEIGHT, DashboardTheme.BORDER);
        canvas.fill(x + 1, y + 1, x + WIDTH - 1, y + HEIGHT - 1, DashboardTheme.SURFACE);
        canvas.fill(x + 1, y + 1, x + WIDTH - 1, y + 34, DashboardTheme.SIDEBAR);
        canvas.icon(GuiIcons.SERVER, x + 10, y + 9, 14);
        canvas.text(tr("status.title"), x + 30, y + 9, TEXT);
        canvas.icon(GuiIcons.CLEAR, x + WIDTH - 21, y + 8, 12);
        ServerStatus status = ClientDiagnostics.status();
        String health = !ClientDiagnostics.supported() ? "unsupported" : status == null ? "waiting"
                : ClientDiagnostics.stale() ? "stale" : "live";
        String healthLabel = tr("status." + health);
        canvas.text(model == null ? healthLabel : DashboardView.trim(canvas, healthLabel, 104), x + 10, y + 24,
                ClientDiagnostics.stale() ? DashboardTheme.WARNING : DashboardTheme.SUCCESS);
        if (model != null) {
            tab(canvas, x + 119, y + 22, "server_tab", !storageTab);
            tab(canvas, x + 173, y + 22, "storage_tab", storageTab);
        }

        String unknown = "--";
        if (model != null && storageTab) {
            storageRows(canvas, x, y, model, status);
        } else {
            int row = 0;
            row(canvas, x, y, row++, GuiIcons.SERVER, "minecraft", status == null ? unknown : status.minecraftVersion);
            row(canvas, x, y, row++, GuiIcons.BACKPACK, "mod", status == null ? unknown : status.modVersion);
            row(canvas, x, y, row++, GuiIcons.PROTOCOL, "loader", status == null ? unknown : status.loaderVersion);
            row(canvas, x, y, row++, GuiIcons.PROTOCOL, "protocol", "v" + BackpackProtocol.VERSION);
            row(canvas, x, y, row++, GuiIcons.LATENCY, "rtt", status == null || ClientDiagnostics.stale()
                    ? unknown : ClientDiagnostics.roundTripMillis() + " ms");
            row(canvas, x, y, row++, GuiIcons.CPU, "tick", status == null ? unknown : String.format(Locale.ROOT, "%.2f ms", status.tickMillis));
            row(canvas, x, y, row++, GuiIcons.PLAYERS, "players", status == null ? unknown : status.onlinePlayers + " / " + status.maxPlayers);
            row(canvas, x, y, row++, GuiIcons.DATABASE, "database", status == null ? unknown : tr(status.databaseReady ? "status.ready" : "status.unavailable"));
            row(canvas, x, y, row++, GuiIcons.CLOCK, "uptime", status == null ? unknown : duration(status.uptimeSeconds));
            row(canvas, x, y, row++, GuiIcons.MEMORY, "heap", status == null ? unknown : bytes(status.usedHeap) + " / " + bytes(status.maxHeap));
            row(canvas, x, y, row++, GuiIcons.UPLOAD, "upload", bytes(ClientDiagnostics.uploadRate()) + "/s");
            row(canvas, x, y, row, GuiIcons.DOWNLOAD, "download", bytes(ClientDiagnostics.downloadRate()) + "/s");
        }
        canvas.fill(x + 10, y + 199, x + WIDTH - 10, y + 200, DashboardTheme.BORDER);
        canvas.text(tr("status.scope"), x + 10, y + 204, MUTED);
        canvas.icon(GuiIcons.UPLOAD, x + 10, y + 217, 10);
        canvas.text(bytes(ClientDiagnostics.uploaded()), x + 24, y + 217, TEXT);
        canvas.icon(GuiIcons.DOWNLOAD, x + 120, y + 217, 10);
        canvas.text(bytes(ClientDiagnostics.downloaded()), x + 134, y + 217, TEXT);
    }

    private static void tab(GuiCanvas canvas, int x, int y, String key, boolean selected) {
        canvas.fill(x, y, x + 52, y + 12, selected ? DashboardTheme.BUTTON_HOVER : DashboardTheme.BUTTON);
        String label = tr("status." + key);
        canvas.text(label, x + (52 - canvas.textWidth(label)) / 2, y + 2,
                selected ? DashboardTheme.ACCENT : DashboardTheme.MUTED);
    }

    private static void storageRows(GuiCanvas canvas, int x, int y, DashboardModel model, ServerStatus status) {
        DashboardModel.Snapshot snapshot = model.snapshot();
        long capacity = (long) snapshot.maxPages * 45;
        int row = 0;
        row(canvas, x, y, row++, GuiIcons.BACKPACK, "storage", snapshot.storage.isEmpty() ? DashboardView.tr("team") : snapshot.storage);
        row(canvas, x, y, row++, GuiIcons.DATABASE, "capacity", snapshot.usedSlots + " / " + capacity);
        row(canvas, x, y, row++, GuiIcons.DATABASE, "free_slots", Long.toString(Math.max(0, capacity - snapshot.usedSlots)));
        row(canvas, x, y, row++, GuiIcons.BACKPACK, "kinds", Integer.toString(snapshot.entries.size()));
        row(canvas, x, y, row++, GuiIcons.SEARCH, "matches", Integer.toString(model.matches()));
        row(canvas, x, y, row++, GuiIcons.BACKPACK, "total_items", Long.toString(model.total()));
        row(canvas, x, y, row++, GuiIcons.PROTOCOL, "page", (model.page() + 1) + " / " + model.pages());
        row(canvas, x, y, row++, GuiIcons.PROTOCOL, "sort", DashboardView.tr("sort." + model.sort()));
        row(canvas, x, y, row++, GuiIcons.SEARCH, "namespace", model.namespace().isEmpty() ? DashboardView.tr("namespaces") : model.namespace());
        row(canvas, x, y, row++, GuiIcons.SEARCH, "query", model.query().isEmpty() ? tr("status.none") : model.query());
        row(canvas, x, y, row++, GuiIcons.CPU, "tick_budget", status == null ? "--" : percent(status.tickMillis / 50.0));
        row(canvas, x, y, row, GuiIcons.MEMORY, "heap_ratio", status == null || status.maxHeap <= 0 ? "--" : percent((double) status.usedHeap / status.maxHeap));
    }

    private static String percent(double fraction) { return String.format(Locale.ROOT, "%.1f%%", fraction * 100); }

    private static void row(GuiCanvas canvas, int x, int y, int row, int icon, String key, String value) {
        int ry = y + 41 + row * 13;
        canvas.icon(icon, x + 10, ry, 10);
        String label = tr("status." + key);
        canvas.text(label, x + 25, ry + 1, MUTED);
        int maxWidth = WIDTH - 45 - canvas.textWidth(label);
        while (canvas.textWidth(value) > maxWidth && value.length() > 3) value = value.substring(0, value.length() - 4) + "...";
        canvas.text(value, x + WIDTH - 10 - canvas.textWidth(value), ry + 1, TEXT);
    }

    private static String tr(String key) { return ClientText.text("gui.sharedbackpack." + key).getString(); }

    private static String duration(long seconds) {
        return String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60);
    }

    private static String bytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.ROOT, "%.1f KiB", bytes / 1024D);
        if (bytes < 1024L * 1024 * 1024) return String.format(Locale.ROOT, "%.1f MiB", bytes / (1024D * 1024));
        return String.format(Locale.ROOT, "%.1f GiB", bytes / (1024D * 1024 * 1024));
    }
}
