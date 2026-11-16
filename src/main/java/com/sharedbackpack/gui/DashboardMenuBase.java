package com.sharedbackpack.gui;

import com.sharedbackpack.SharedBackpackMod;
import com.sharedbackpack.backpack.TeamResolver;
import com.sharedbackpack.compat.MinecraftCompat;
import com.sharedbackpack.database.DatabaseManager;
import com.sharedbackpack.protocol.DashboardProtocol;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Catalog cells are not inventories. Only slot 45 and the player's inventory contain real items. */
public abstract class DashboardMenuBase extends ScreenHandler {
    private static final int DEPOSIT = 45;
    protected final ServerPlayerEntity player;
    private final String owner;
    private String box;
    private String initialQuery = "";
    private final SimpleInventory inventory = new SimpleInventory(54);
    private List<DatabaseManager.BackpackItem> previousRows = Collections.emptyList();
    private List<String> previousBoxes = Collections.emptyList();
    private List<Group> catalog = Collections.emptyList();
    private int previousMaxPages = -1;
    private int revision;
    private int ticks;
    private int page;
    private int sort;
    private boolean closed;

    protected DashboardMenuBase(int syncId, ServerPlayerEntity player, String owner, String box) {
        super(ScreenHandlerType.GENERIC_9X6, syncId);
        this.player = player;
        this.owner = owner;
        this.box = box;
        int[] preferences = db().loadViewPreferences(player.getUuidAsString(), owner, box);
        page = preferences[0];
        sort = preferences[1];
        for (int i = 0; i < 54; i++) {
            final boolean deposit = i == DEPOSIT;
            addSlot(new Slot(inventory, i, 8 + i % 9 * 18, 18 + i / 9 * 18) {
                @Override public boolean canInsert(ItemStack stack) { return deposit; }
                @Override public boolean canTakeItems(PlayerEntity p) { return deposit; }
            });
        }
        PlayerInventory inv = MinecraftCompat.inventory(player);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) addSlot(new Slot(inv, col + row * 9 + 9, 8 + col * 18, 139 + row * 18));
        }
        for (int col = 0; col < 9; col++) addSlot(new Slot(inv, col, 8 + col * 18, 197));
    }

    private DatabaseManager db() { return SharedBackpackMod.database; }
    private boolean authorized() {
        return !closed && player.currentScreenHandler == this && player.isAlive() && !player.isSpectator()
                && owner.equals(TeamResolver.resolvePrimaryTeam(player)) && db() != null && db().isReady()
                && (box == null || db().listBoxes(owner).contains(box));
    }
    private List<DatabaseManager.BackpackItem> rows() { return box == null ? db().getItems(owner) : db().getBoxItems(owner, box); }
    private int maxPages() { return box == null ? db().getMaxPages(owner) : db().getBoxMaxPages(owner, box); }
    private boolean deposit(ItemStack stack) {
        return !stack.isEmpty() && db().depositAtomic(owner, box,
                MinecraftCompat.getItemId(stack.getItem()).toString(), stack.getCount(),
                BackpackMenuBase.nbtForStorage(stack), player.getEntityName());
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                if (player.currentScreenHandler instanceof DashboardMenuBase) {
                    ((DashboardMenuBase) player.currentScreenHandler).tick();
                }
            }
        });
        ServerPlayNetworking.registerGlobalReceiver(DashboardProtocol.REQUEST, (server, player, handler, buf, sender) -> {
            final int syncId, action, requestedRevision, key, requestedPage, requestedSort;
            final boolean wholeStack, intoInventory;
            final String name;
            try {
                if (buf.readVarInt() != DashboardProtocol.VERSION) return;
                syncId = buf.readVarInt();
                action = buf.readVarInt();
                if (action < DashboardProtocol.REFRESH || action > DashboardProtocol.DEPOSIT_CURSOR) return;
                requestedRevision = action == DashboardProtocol.TAKE ? buf.readVarInt() : -1;
                key = action == DashboardProtocol.TAKE ? buf.readVarInt() : -1;
                wholeStack = action == DashboardProtocol.TAKE && buf.readBoolean();
                intoInventory = action == DashboardProtocol.TAKE && buf.readBoolean();
                name = action == DashboardProtocol.SWITCH || action == DashboardProtocol.CREATE_BOX
                        ? buf.readString(DashboardProtocol.MAX_TEXT) : "";
                requestedPage = action == DashboardProtocol.PREFERENCES ? buf.readVarInt() : 0;
                requestedSort = action == DashboardProtocol.PREFERENCES ? buf.readVarInt() : 0;
                if (buf.isReadable() || requestedPage < 0 || requestedSort < 0 || requestedSort > 5) return;
            } catch (RuntimeException malformed) { return; }
            server.execute(() -> {
                if (!(player.currentScreenHandler instanceof DashboardMenuBase) || player.currentScreenHandler.syncId != syncId) return;
                DashboardMenuBase menu = (DashboardMenuBase) player.currentScreenHandler;
                if (!menu.authorized()) return;
                switch (action) {
                    case DashboardProtocol.REFRESH: menu.refresh(true); break;
                    case DashboardProtocol.TAKE: menu.take(requestedRevision, key, wholeStack, intoInventory); break;
                    case DashboardProtocol.SWITCH: menu.switchStorage(name); break;
                    case DashboardProtocol.CREATE_BOX: menu.createBox(name.trim()); break;
                    case DashboardProtocol.PREFERENCES:
                        menu.page = requestedPage;
                        menu.sort = requestedSort;
                        menu.db().saveViewPreferences(player.getUuidAsString(), menu.owner, menu.box, menu.page, menu.sort);
                        break;
                    case DashboardProtocol.UPGRADE: menu.upgrade(); break;
                    case DashboardProtocol.DEPOSIT_CURSOR:
                        if (menu.deposit(menu.cursor())) { menu.cursor(ItemStack.EMPTY); menu.refresh(true); }
                        menu.sendContentUpdates();
                        break;
                    default: break;
                }
            });
        });
    }

    private void tick() {
        ItemStack pending = inventory.getStack(DEPOSIT);
        boolean refreshDue = ++ticks % 20 == 0;
        if (!pending.isEmpty() || refreshDue) {
            if (!authorized()) { player.closeHandledScreen(); return; }
        }
        // Run after vanilla click handling, never while cursor/inventory ownership is in transition.
        if (!pending.isEmpty() && deposit(pending)) {
            inventory.setStack(DEPOSIT, ItemStack.EMPTY);
            refresh(true);
            sendContentUpdates();
        }
        if (refreshDue) refresh(false);
    }

    protected final boolean blockClick(int slotId, SlotActionType type) {
        if (!authorized()) return true;
        // Disallow collecting virtual cells and hidden dummy slots even from forged vanilla packets.
        return (slotId >= 0 && slotId < 54 && slotId != DEPOSIT) || type == SlotActionType.PICKUP_ALL;
    }

    protected final ItemStack move(PlayerEntity who, int index) {
        if (!authorized() || index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = getSlot(index);
        if (!slot.hasStack()) return ItemStack.EMPTY;
        ItemStack source = slot.getStack();
        if (index == DEPOSIT || index >= 54) {
            ItemStack result = source.copy();
            if (!deposit(source)) return ItemStack.EMPTY;
            slot.setStack(ItemStack.EMPTY);
            refresh(true);
            sendContentUpdates();
            return result;
        }
        return ItemStack.EMPTY;
    }

    private void take(int requestedRevision, int key, boolean wholeStack, boolean intoInventory) {
        if (requestedRevision != revision || key < 0 || key >= catalog.size()) { refresh(true); return; }
        Group group = catalog.get(key);
        if (!group.valid) return;
        ItemStack icon = group.icon;
        int amount = (int) Math.min(group.count, wholeStack || intoInventory ? icon.getMaxCount() : 1);
        ItemStack carried = cursor();
        if (intoInventory) {
            amount = Math.min(amount, inventorySpace(icon));
        } else if (!carried.isEmpty()) {
            if (!BackpackMenuBase.tagsMatchExactly(carried, icon)) return;
            amount = Math.min(amount, carried.getMaxCount() - carried.getCount());
        }
        if (amount <= 0) return;
        // The DB rechecks live totals and all matching NBT rows inside one transaction.
        if (!db().removeMatchingAtomic(owner, box, group.itemId, group.nbt, amount)) { refresh(true); return; }
        ItemStack taken = icon.copy();
        taken.setCount(amount);
        if (intoInventory) {
            // Capacity was computed on this same server thread; insertStack cannot race another click.
            if (!MinecraftCompat.inventory(player).insertStack(taken) && !taken.isEmpty()) player.dropItem(taken, false);
        } else if (carried.isEmpty()) cursor(taken);
        else { carried.increment(amount); cursor(carried); }
        refresh(true);
        sendContentUpdates();
    }

    private int inventorySpace(ItemStack stack) {
        PlayerInventory inv = MinecraftCompat.inventory(player);
        int capacity = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack existing = inv.getStack(i);
            if (existing.isEmpty()) capacity += stack.getMaxCount();
            else if (BackpackMenuBase.tagsMatchExactly(existing, stack)) capacity += Math.max(0, existing.getMaxCount() - existing.getCount());
        }
        return capacity;
    }

    private void switchStorage(String name) {
        if (!name.isEmpty() && !db().listBoxes(owner).contains(name)) return;
        String target = name.isEmpty() ? null : name;
        if (Objects.equals(box, target)) return;
        // A pending physical deposit belongs to the old storage, never the newly selected box.
        ItemStack pending = inventory.removeStack(DEPOSIT);
        if (!pending.isEmpty() && !deposit(pending)
                && !MinecraftCompat.inventory(player).insertStack(pending)) player.dropItem(pending, false);
        box = target;
        initialQuery = "";
        int[] preferences = db().loadViewPreferences(player.getUuidAsString(), owner, box);
        page = preferences[0];
        sort = preferences[1];
        refresh(true);
        sendContentUpdates();
    }

    private void createBox(String name) {
        List<String> boxes = db().listBoxes(owner);
        if (name.isEmpty()) {
            int number = 1;
            do { name = "Box " + number++; } while (boxes.contains(name));
        }
        if (name.length() > DashboardProtocol.MAX_TEXT || name.indexOf(':') >= 0 || name.indexOf('\n') >= 0
                || name.indexOf('\r') >= 0 || boxes.contains(name)) return;
        db().createBox(owner, name);
        if (db().listBoxes(owner).contains(name)) switchStorage(name);
    }

    private void upgrade() {
        PlayerInventory inv = MinecraftCompat.inventory(player);
        for (int i = 0; i < 36; i++) {
            ItemStack stack = inv.getStack(i);
            if (stack.getItem() != Items.DIAMOND || stack.isEmpty()) continue;
            boolean upgraded = box == null ? db().upgradePages(owner, 1) : db().upgradeBoxPages(owner, box, 1);
            if (upgraded) { stack.decrement(1); inv.markDirty(); refresh(true); sendContentUpdates(); }
            return;
        }
        player.sendMessage(MinecraftCompat.text("\u00a7c\u9700\u8981 1 \u4e2a\u94bb\u77f3\u6765\u6269\u5bb9"), false);
    }

    public final void refresh(boolean force) {
        if (!authorized()) return;
        List<DatabaseManager.BackpackItem> current = rows();
        List<String> boxes = db().listBoxes(owner);
        int capacity = maxPages();
        if (!force && capacity == previousMaxPages && boxes.equals(previousBoxes) && sameRows(current, previousRows)) return;
        previousRows = current;
        previousBoxes = boxes;
        previousMaxPages = capacity;
        Map<GroupKey, Group> groups = new LinkedHashMap<>();
        for (DatabaseManager.BackpackItem row : current) {
            if (row.count <= 0) continue;
            GroupKey key = new GroupKey(row.itemId, row.nbt);
            Group group = groups.get(key);
            if (group == null) { group = new Group(row, key); groups.put(key, group); }
            else group.count += row.count;
            String modified = row.lastModifiedTime == null ? row.placedTime : row.lastModifiedTime;
            if (modified != null && modified.compareTo(group.modified) > 0) group.modified = modified;
        }
        catalog = new ArrayList<>(groups.values());
        revision = revision == Integer.MAX_VALUE ? 1 : revision + 1;
        int offset = 0;
        do {
            int end = Math.min(catalog.size(), offset + DashboardProtocol.CHUNK_SIZE);
            PacketByteBuf response = PacketByteBufs.create();
            response.writeVarInt(DashboardProtocol.VERSION);
            response.writeVarInt(syncId);
            response.writeVarInt(revision);
            response.writeBoolean(offset == 0);
            response.writeBoolean(end == catalog.size());
            if (offset == 0) {
                response.writeString(box == null ? "" : box, DashboardProtocol.MAX_TEXT);
                response.writeString(initialQuery, DashboardProtocol.MAX_TEXT);
                response.writeVarInt(capacity);
                response.writeVarInt(current.size());
                response.writeVarInt(page);
                response.writeVarInt(sort);
                response.writeVarInt(boxes.size());
                for (String name : boxes) response.writeString(name, DashboardProtocol.MAX_TEXT);
            }
            response.writeVarInt(end - offset);
            for (int i = offset; i < end; i++) {
                Group group = catalog.get(i);
                response.writeVarInt(i);
                response.writeItemStack(group.icon);
                response.writeVarLong(group.count);
                response.writeString(group.modified, DashboardProtocol.MAX_TEXT);
            }
            ServerPlayNetworking.send(player, DashboardProtocol.STATE, response);
            offset = end;
        } while (offset < catalog.size());
    }

    private static boolean sameRows(List<DatabaseManager.BackpackItem> a, List<DatabaseManager.BackpackItem> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            DatabaseManager.BackpackItem x = a.get(i), y = b.get(i);
            if (x.slot != y.slot || x.count != y.count || !x.itemId.equals(y.itemId)
                    || !Objects.equals(x.nbt, y.nbt) || !Objects.equals(x.lastModifiedTime, y.lastModifiedTime)) return false;
        }
        return true;
    }

    @Override public boolean canUse(PlayerEntity who) { return who == player && owner.equals(TeamResolver.resolvePrimaryTeam(player)); }
    protected final void releaseInventory() {
        closed = true;
        ItemStack pending = inventory.removeStack(DEPOSIT);
        if (!pending.isEmpty() && !MinecraftCompat.inventory(player).insertStack(pending)) player.dropItem(pending, false);
        ItemStack carried = cursor();
        if (!carried.isEmpty()) {
            cursor(ItemStack.EMPTY);
            if (!MinecraftCompat.inventory(player).insertStack(carried)) player.dropItem(carried, false);
        }
    }
    protected abstract ItemStack cursor();
    protected abstract void cursor(ItemStack stack);

    public static void open(ServerPlayerEntity player, String owner, String box, String query) {
        if (!owner.equals(TeamResolver.resolvePrimaryTeam(player))) return;
        player.openHandledScreen(new NamedScreenHandlerFactory() {
            @Override public ScreenHandler createMenu(int id, PlayerInventory inv, PlayerEntity p) {
                return new DashboardMenu(id, player, owner, box);
            }
            @Override public Text getDisplayName() { return MinecraftCompat.text(box == null ? "Shared Backpack" : box); }
        });
        if (player.currentScreenHandler instanceof DashboardMenuBase) {
            DashboardMenuBase menu = (DashboardMenuBase) player.currentScreenHandler;
            menu.initialQuery = query == null ? "" : query.substring(0, Math.min(query.length(), DashboardProtocol.MAX_TEXT));
            menu.refresh(true);
        }
    }

    private static final class GroupKey {
        final String itemId;
        final Object tag;
        GroupKey(String itemId, String nbt) {
            this.itemId = itemId;
            Object parsed = null;
            if (nbt != null && !nbt.isEmpty()) {
                try { parsed = StringNbtReader.parse(nbt); } catch (Exception invalid) { parsed = nbt; }
            }
            tag = parsed instanceof NbtCompound && ((NbtCompound) parsed).isEmpty() ? null : parsed;
        }
        @Override public boolean equals(Object other) {
            if (!(other instanceof GroupKey)) return false;
            GroupKey key = (GroupKey) other;
            return itemId.equals(key.itemId) && Objects.equals(tag, key.tag);
        }
        @Override public int hashCode() { return 31 * itemId.hashCode() + Objects.hashCode(tag); }
    }

    private static final class Group {
        final String itemId;
        final String nbt;
        final ItemStack icon;
        final boolean valid;
        long count;
        String modified = "";
        Group(DatabaseManager.BackpackItem row, GroupKey key) {
            itemId = row.itemId;
            nbt = row.nbt;
            count = row.count;
            Item item = MinecraftCompat.getItem(new Identifier(itemId));
            valid = item != null && item != Items.AIR && !(key.tag instanceof String);
            icon = new ItemStack(valid ? item : Items.BARRIER);
            if (valid && key.tag instanceof NbtCompound) MinecraftCompat.setNbt(icon, ((NbtCompound) key.tag).copy());
        }
    }
}
