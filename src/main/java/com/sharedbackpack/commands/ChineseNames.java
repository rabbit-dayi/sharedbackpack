package com.sharedbackpack.commands;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.sharedbackpack.SharedBackpackMod;

import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

public class ChineseNames {
    private static final Map<String, String> zhCN = new HashMap<>();
    private static final Gson GSON = new Gson();
    private static final java.lang.reflect.Type MAP_TYPE = new TypeToken<Map<String, String>>(){}.getType();

    public static void load(net.minecraft.server.MinecraftServer server) {
        int total = 0;
        int files = 0;

        // Classpath scan includes this mod's bundled language data on dedicated servers.
        try {
            Enumeration<URL> urls = ChineseNames.class.getClassLoader()
                .getResources("assets/minecraft/lang/zh_cn.json");
            while (urls.hasMoreElements()) {
                URL url = urls.nextElement();
                try (Reader r = new InputStreamReader(url.openStream(), StandardCharsets.UTF_8)) {
                    Map<String, String> m = GSON.fromJson(r, MAP_TYPE);
                    if (m != null) { zhCN.putAll(m); total += m.size(); files++; }
                }
            }
        } catch (Exception e) {
            SharedBackpackMod.LOGGER.debug("Classpath scan: {}", e.getMessage());
        }

        // Direct lookup also works with Minecraft's server JAR.
        try (java.io.InputStream in = net.minecraft.server.MinecraftServer.class
                .getResourceAsStream("/assets/minecraft/lang/zh_cn.json")) {
            if (in != null) {
                try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                    Map<String, String> m = GSON.fromJson(r, MAP_TYPE);
                    if (m != null) { zhCN.putAll(m); total += m.size(); files++; }
                }
            } else {
                SharedBackpackMod.LOGGER.warn("zh_cn.json not found via classloader");
            }
        } catch (Exception e) {
            SharedBackpackMod.LOGGER.warn("Direct class approach failed: {}", e.getMessage());
        }

        SharedBackpackMod.LOGGER.info("Loaded {} zh_cn entries from {} files for pinyin search", total, files);
    }

    public static String get(String key) {
        return zhCN.get(key);
    }
}
