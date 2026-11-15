package com.sharedbackpack.client.gui;

/** Small rendering adapter for the two supported Minecraft GUI APIs. */
public interface GuiCanvas {
    void fill(int x1, int y1, int x2, int y2, int color);
    void text(String value, int x, int y, int color);
    int textWidth(String value);
    void icon(int index, int x, int y, int size);
}
