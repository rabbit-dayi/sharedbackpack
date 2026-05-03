package com.sharedbackpack.commands;

import me.towdium.pinin.PinIn;

public class PinyinUtil {
    private static final PinIn PININ = new PinIn();

    static {
        PININ.config().fZh2Z(true).fCh2C(true).fSh2S(true).commit();
    }

    public static String toPinyin(String input) {
        return input;
    }

    public static String toPinyinInitials(String input) {
        return input;
    }

    public static boolean matches(String chineseText, String query) {
        if (chineseText == null || chineseText.isEmpty()) return false;
        if (query == null || query.isEmpty()) return true;
        return PININ.contains(chineseText.toLowerCase(), query.toLowerCase().trim());
    }
}
