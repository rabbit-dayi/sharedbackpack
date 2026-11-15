package com.sharedbackpack.client.gui;

import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
public final class ClientText {
    private ClientText() {}
    public static Text text(String key) { return new TranslatableText(key); }
}

