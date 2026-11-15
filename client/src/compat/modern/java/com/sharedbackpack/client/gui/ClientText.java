package com.sharedbackpack.client.gui;

import net.minecraft.text.Text;

public final class ClientText {
    private ClientText() {}
    public static Text text(String key) { return Text.translatable(key); }
}

