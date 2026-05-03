package com.sharedbackpack.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.sharedbackpack.SharedBackpackMod;
import com.sharedbackpack.backpack.TeamResolver;
import com.sharedbackpack.database.DatabaseManager;
import com.sharedbackpack.gui.BackpackMenu;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.List;

public class CCCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("cc")
            .then(Commands.literal("help")
                .executes(ctx -> showHelp(ctx.getSource())))
            .then(Commands.literal("admin")
                .then(Commands.literal("reload")
                    .requires(src -> src.hasPermission(2))
                    .executes(ctx -> reloadDatabase(ctx.getSource())))
                .then(Commands.literal("backup")
                    .requires(src -> src.hasPermission(2))
                    .executes(ctx -> forceBackup(ctx.getSource())))
                .then(Commands.literal("info")
                    .requires(src -> src.hasPermission(2))
                    .then(Commands.argument("team", StringArgumentType.string())
                        .executes(ctx -> showTeamInfo(ctx.getSource(), StringArgumentType.getString(ctx, "team")))))
                .then(Commands.literal("clear")
                    .requires(src -> src.hasPermission(2))
                    .then(Commands.argument("team", StringArgumentType.string())
                        .executes(ctx -> clearTeam(ctx.getSource(), StringArgumentType.getString(ctx, "team")))))
                .then(Commands.literal("upgrade")
                    .requires(src -> src.hasPermission(2))
                    .then(Commands.argument("team", StringArgumentType.string())
                        .then(Commands.argument("pages", IntegerArgumentType.integer(1))
                            .executes(ctx -> upgradeTeam(ctx.getSource(),
                                StringArgumentType.getString(ctx, "team"),
                                IntegerArgumentType.getInteger(ctx, "pages"))))))
                .then(Commands.literal("listteams")
                    .requires(src -> src.hasPermission(2))
                    .executes(ctx -> listTeams(ctx.getSource()))))
            .then(Commands.literal("search")
                .then(Commands.argument("query", StringArgumentType.greedyString())
                    .executes(ctx -> searchItems(ctx.getSource(), StringArgumentType.getString(ctx, "query")))))
            .then(Commands.literal("unload")
                .executes(ctx -> openUnload(ctx.getSource())))
            .then(Commands.literal("pinyin")
                .then(Commands.argument("text", StringArgumentType.greedyString())
                    .executes(ctx -> testPinyin(ctx.getSource(), StringArgumentType.getString(ctx, "text")))))
            .then(Commands.literal("lookup")
                .then(Commands.argument("id", StringArgumentType.greedyString())
                    .executes(ctx -> lookupItem(ctx.getSource(), StringArgumentType.getString(ctx, "id")))))
            .then(Commands.literal("box")
                .then(Commands.literal("create")
                    .then(Commands.argument("name", StringArgumentType.string())
                        .executes(ctx -> createBox(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
                .then(Commands.literal("open")
                    .then(Commands.argument("name", StringArgumentType.string())
                        .executes(ctx -> openBox(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
                .then(Commands.literal("list")
                    .executes(ctx -> listBoxes(ctx.getSource())))
                .then(Commands.literal("delete")
                    .then(Commands.argument("name", StringArgumentType.string())
                        .executes(ctx -> deleteBox(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
                .then(Commands.literal("upgrade")
                    .then(Commands.argument("name", StringArgumentType.string())
                        .executes(ctx -> upgradeBox(ctx.getSource(), StringArgumentType.getString(ctx, "name"))))))
        );
    }

    private static int showHelp(CommandSourceStack src) {
        src.sendSuccess(() -> Component.literal("§6=== Shared Backpack Help ==="), false);
        src.sendSuccess(() -> Component.literal("§e/c §7- 打开共享背包"), false);
        src.sendSuccess(() -> Component.literal("§e/c <搜索词> §7- 打开并搜索物品(拼音/英文/ID)"), false);
        src.sendSuccess(() -> Component.literal("§e/cc help §7- 显示此帮助"), false);
        src.sendSuccess(() -> Component.literal("§e/cc unload §7- 卸货模式(无按钮,纯格子)"), false);
        src.sendSuccess(() -> Component.literal("§e/cc admin info <team> §7- 查看队伍背包信息"), false);
        src.sendSuccess(() -> Component.literal("§e/cc admin clear <team> §7- 清空队伍背包"), false);
        src.sendSuccess(() -> Component.literal("§e/cc admin upgrade <team> <pages> §7- 升级队伍背包页数"), false);
        src.sendSuccess(() -> Component.literal("§e/cc admin backup §7- 强制备份数据库"), false);
        src.sendSuccess(() -> Component.literal("§e/cc admin reload §7- 重新加载数据库"), false);
        src.sendSuccess(() -> Component.literal("§e/cc search <query> §7- 搜索物品（支持拼音）"), false);
        src.sendSuccess(() -> Component.literal("§e/cc box create <name> §7- 创建个人盒子"), false);
        src.sendSuccess(() -> Component.literal("§e/cc box open <name> §7- 打开个人盒子"), false);
        src.sendSuccess(() -> Component.literal("§e/cc box list §7- 列出个人盒子"), false);
        src.sendSuccess(() -> Component.literal("§e/cc box delete <name> §7- 删除个人盒子"), false);
        return 1;
    }

    private static int reloadDatabase(CommandSourceStack src) {
        if (SharedBackpackMod.database != null) {
            SharedBackpackMod.database.close();
        }
        SharedBackpackMod.database = new com.sharedbackpack.database.DatabaseManager(src.getServer());
        SharedBackpackMod.database.init();
        src.sendSuccess(() -> Component.literal("§a数据库已重新加载"), false);
        return 1;
    }

    private static int forceBackup(CommandSourceStack src) {
        try {
            java.io.File dbFile = new java.io.File(src.getServer().getServerDirectory(), "config/sharedbackpack/backpack.db");
            if (dbFile.exists()) {
                String timestamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
                java.io.File backupDir = new java.io.File(dbFile.getParent(), "backups");
                backupDir.mkdirs();
                java.io.File backup = new java.io.File(backupDir, "backpack_backup_" + timestamp + ".db");
                java.nio.file.Files.copy(dbFile.toPath(), backup.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                src.sendSuccess(() -> Component.literal("§a备份已创建: " + backup.getName()), false);
            } else {
                src.sendSuccess(() -> Component.literal("§c数据库文件不存在"), false);
            }
        } catch (Exception e) {
            src.sendSuccess(() -> Component.literal("§c备份失败: " + e.getMessage()), false);
        }
        return 1;
    }

    private static int showTeamInfo(CommandSourceStack src, String teamId) {
        String info = SharedBackpackMod.database.getBackpackInfo(teamId);
        src.sendSuccess(() -> Component.literal("§6" + info), false);
        return 1;
    }

    private static int clearTeam(CommandSourceStack src, String teamId) {
        src.sendSuccess(() -> Component.literal("§c清空功能开发中..."), false);
        return 1;
    }

    private static int upgradeTeam(CommandSourceStack src, String teamId, int pages) {
        if (SharedBackpackMod.database.upgradePages(teamId, pages)) {
            src.sendSuccess(() -> Component.literal("§a已为队伍 " + teamId + " 添加 " + pages + " 页"), false);
        } else {
            src.sendFailure(Component.literal("§c升级失败"));
        }
        return 1;
    }

    private static int listTeams(CommandSourceStack src) {
        src.sendSuccess(() -> Component.literal("§6队伍列表功能开发中..."), false);
        return 1;
    }

    private static int searchItems(CommandSourceStack src, String query) {
        if (src.getEntity() instanceof ServerPlayer player) {
            List<DatabaseManager.BackpackItem> items = SharedBackpackMod.database.getItems(TeamResolver.resolvePrimaryTeam(player));
            List<DatabaseManager.BackpackItem> results = PinyinSearch.search(items, query);
            src.sendSuccess(() -> Component.literal("§6找到 " + results.size() + " 个匹配物品"), false);
        }
        return 1;
    }

    private static int openUnload(CommandSourceStack src) {
        if (src.getEntity() instanceof ServerPlayer player) {
            BackpackMenu.openForPlayer(player, "", 0, true, false, null);
            src.sendSuccess(() -> Component.literal("§a已打开卸货模式（无功能按钮）"), false);
        } else {
            src.sendFailure(Component.literal("此命令只能由玩家使用"));
        }
        return 1;
    }

    private static int testPinyin(CommandSourceStack src, String text) {
        boolean match = PinyinUtil.matches("泥土", text);
        src.sendSuccess(() -> Component.literal("§6" + text + " 匹配泥土: " + match), false);
        return 1;
    }

    private static int lookupItem(CommandSourceStack src, String id) {
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(id));
        if (item == null || item == Items.AIR) {
            src.sendSuccess(() -> Component.literal("§c物品 " + id + " 未找到"), false);
        } else {
            String name = item.getDescription().getString();
            src.sendSuccess(() -> Component.literal("§6" + id + " = " + name + " | py:" + PinyinUtil.toPinyin(name)), false);
        }
        return 1;
    }

    private static int createBox(CommandSourceStack src, String name) {
        if (!(src.getEntity() instanceof ServerPlayer player)) {
            src.sendFailure(Component.literal("此命令只能由玩家使用")); return 0;
        }
        SharedBackpackMod.database.createBox(player.getStringUUID(), name);
        src.sendSuccess(() -> Component.literal("§a盒子 '" + name + "' 已创建"), false);
        return 1;
    }

    private static int openBox(CommandSourceStack src, String name) {
        if (!(src.getEntity() instanceof ServerPlayer player)) {
            src.sendFailure(Component.literal("此命令只能由玩家使用")); return 0;
        }
        BackpackMenu.openForPlayer(player, "", 0, false, true, player.getStringUUID() + ":" + name);
        return 1;
    }

    private static int listBoxes(CommandSourceStack src) {
        if (!(src.getEntity() instanceof ServerPlayer player)) {
            src.sendFailure(Component.literal("此命令只能由玩家使用")); return 0;
        }
        List<String> boxes = SharedBackpackMod.database.listBoxes(player.getStringUUID());
        if (boxes.isEmpty()) src.sendSuccess(() -> Component.literal("§7没有个人盒子"), false);
        else {
            src.sendSuccess(() -> Component.literal("§6你的盒子:"), false);
            for (String b : boxes) src.sendSuccess(() -> Component.literal(" §e- " + b), false);
        }
        return 1;
    }

    private static int deleteBox(CommandSourceStack src, String name) {
        if (!(src.getEntity() instanceof ServerPlayer player)) {
            src.sendFailure(Component.literal("此命令只能由玩家使用")); return 0;
        }
        SharedBackpackMod.database.deleteBox(player.getStringUUID(), name);
        src.sendSuccess(() -> Component.literal("§a盒子 '" + name + "' 已删除"), false);
        return 1;
    }

    private static int upgradeBox(CommandSourceStack src, String name) {
        if (!(src.getEntity() instanceof ServerPlayer player)) {
            src.sendFailure(Component.literal("此命令只能由玩家使用")); return 0;
        }
        SharedBackpackMod.database.upgradePages(name, 1);
        src.sendSuccess(() -> Component.literal("§a盒子 '" + name + "' 已升级"), false);
        return 1;
    }
}
