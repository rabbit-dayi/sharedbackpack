package com.sharedbackpack.network;

import com.sharedbackpack.gui.BackpackMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class SyncBackpackPacket {
    private final List<ItemStack> items;
    private final int maxPages;
    private final int currentPage;

    public SyncBackpackPacket(List<ItemStack> items, int maxPages, int currentPage) {
        this.items = items;
        this.maxPages = maxPages;
        this.currentPage = currentPage;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(items.size());
        for (ItemStack stack : items) {
            buf.writeItem(stack);
        }
        buf.writeInt(maxPages);
        buf.writeInt(currentPage);
    }

    public static SyncBackpackPacket decode(FriendlyByteBuf buf) {
        int size = buf.readInt();
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            items.add(buf.readItem());
        }
        int maxPages = buf.readInt();
        int currentPage = buf.readInt();
        return new SyncBackpackPacket(items, maxPages, currentPage);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (ctx.get().getSender() != null) {
                BackpackMenu.handleSync(this);
            }
        });
        ctx.get().setPacketHandled(true);
    }

    public List<ItemStack> getItems() { return items; }
    public int getMaxPages() { return maxPages; }
    public int getCurrentPage() { return currentPage; }
}
