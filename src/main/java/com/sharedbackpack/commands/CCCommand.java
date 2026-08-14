package com.sharedbackpack.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.sharedbackpack.SharedBackpackMod;
import com.sharedbackpack.backpack.TeamResolver;
import com.sharedbackpack.database.DatabaseManager;
import com.sharedbackpack.gui.BackpackMenu;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.command.CommandManager;
import net.minecraft.util.registry.Registry;
import net.minecraft.text.LiteralText;
import net.minecraft.util.Identifier;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.util.List;

public class CCCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("cc")
            .then(CommandManager.literal("help")
                .executes(ctx -> showHelp(ctx.getSource())))
            .then(CommandManager.literal("admin")
                .then(CommandManager.literal("reload")
                    .requires(src -> src.hasPermissionLevel(2))
                    .executes(ctx -> reloadDatabase(ctx.getSource())))
                .then(CommandManager.literal("backup")
                    .requires(src -> src.hasPermissionLevel(2))
                    .executes(ctx -> forceBackup(ctx.getSource())))
                .then(CommandManager.literal("info")
                    .requires(src -> src.hasPermissionLevel(2))
                    .then(CommandManager.argument("team", StringArgumentType.string())
                        .executes(ctx -> showTeamInfo(ctx.getSource(), StringArgumentType.getString(ctx, "team")))))
                .then(CommandManager.literal("clear")
                    .requires(src -> src.hasPermissionLevel(2))
                    .then(CommandManager.argument("team", StringArgumentType.string())
                        .executes(ctx -> clearTeam(ctx.getSource(), StringArgumentType.getString(ctx, "team")))))
                .then(CommandManager.literal("upgrade")
                    .requires(src -> src.hasPermissionLevel(2))
                    .then(CommandManager.argument("team", StringArgumentType.string())
                        .then(CommandManager.argument("pages", IntegerArgumentType.integer(1))
                            .executes(ctx -> upgradeTeam(ctx.getSource(),
                                StringArgumentType.getString(ctx, "team"),
                                IntegerArgumentType.getInteger(ctx, "pages"))))))
                .then(CommandManager.literal("listteams")
                    .requires(src -> src.hasPermissionLevel(2))
                    .executes(ctx -> listTeams(ctx.getSource()))))
            .then(CommandManager.literal("search")
                .then(CommandManager.argument("query", StringArgumentType.greedyString())
                    .executes(ctx -> searchItems(ctx.getSource(), StringArgumentType.getString(ctx, "query")))))
            .then(CommandManager.literal("unload")
                .executes(ctx -> openUnload(ctx.getSource())))
            .then(CommandManager.literal("pinyin")
                .then(CommandManager.argument("text", StringArgumentType.greedyString())
                    .executes(ctx -> testPinyin(ctx.getSource(), StringArgumentType.getString(ctx, "text")))))
            .then(CommandManager.literal("lookup")
                .then(CommandManager.argument("id", StringArgumentType.greedyString())
                    .executes(ctx -> lookupItem(ctx.getSource(), StringArgumentType.getString(ctx, "id")))))
            .then(CommandManager.literal("box")
                .then(CommandManager.literal("create")
                    .then(CommandManager.argument("name", StringArgumentType.string())
                        .executes(ctx -> createBox(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
                .then(CommandManager.literal("open")
                    .then(CommandManager.argument("name", StringArgumentType.string())
                        .executes(ctx -> openBox(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
                .then(CommandManager.literal("list")
                    .executes(ctx -> listBoxes(ctx.getSource())))
                .then(CommandManager.literal("delete")
                    .then(CommandManager.argument("name", StringArgumentType.string())
                        .executes(ctx -> deleteBox(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
                .then(CommandManager.literal("upgrade")
                    .then(CommandManager.argument("name", StringArgumentType.string())
                        .executes(ctx -> upgradeBox(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
                // Quick open: /cc box <name> directly
                .then(CommandManager.argument("quickname", StringArgumentType.string())
                    .executes(ctx -> openBox(ctx.getSource(), StringArgumentType.getString(ctx, "quickname")))))
            .then(CommandManager.literal("bind")
                .executes(ctx -> bindItem(ctx.getSource()))
                .then(CommandManager.literal("box")
                    .then(CommandManager.argument("name", StringArgumentType.string())
                        .executes(ctx -> bindToBox(ctx.getSource(), StringArgumentType.getString(ctx, "name"))))))
            .then(CommandManager.literal("unbind")
                .executes(ctx -> unbindItem(ctx.getSource())))
        );
    }

    private static int showHelp(ServerCommandSource src) {
        src.sendFeedback(new LiteralText("§6=== Shared Backpack Help ==="), false);
        src.sendFeedback(new LiteralText("§e/c §7- 打开共享背包"), false);
        src.sendFeedback(new LiteralText("§e/c <搜索词> §7- 打开并搜索物品(拼音/英文/ID)"), false);
        src.sendFeedback(new LiteralText("§e/cc help §7- 显示此帮助"), false);
        src.sendFeedback(new LiteralText("§e/cc unload §7- 卸货模式(无按钮,纯格子)"), false);
        src.sendFeedback(new LiteralText("§e/cc admin info <team> §7- 查看队伍背包信息"), false);
        src.sendFeedback(new LiteralText("§e/cc admin clear <team> §7- 清空队伍背包"), false);
        src.sendFeedback(new LiteralText("§e/cc admin upgrade <team> <pages> §7- 升级队伍背包页数"), false);
        src.sendFeedback(new LiteralText("§e/cc admin backup §7- 强制备份数据库"), false);
        src.sendFeedback(new LiteralText("§e/cc admin reload §7- 重新加载数据库"), false);
        src.sendFeedback(new LiteralText("§e/cc search <query> §7- 搜索物品（支持拼音）"), false);
        src.sendFeedback(new LiteralText("§e/cc box create <name> §7- 创建个人盒子"), false);
        src.sendFeedback(new LiteralText("§e/cc box open <name> §7- 打开个人盒子"), false);
        src.sendFeedback(new LiteralText("§e/cc box list §7- 列出个人盒子"), false);
        src.sendFeedback(new LiteralText("§e/cc box delete <name> §7- 删除个人盒子"), false);
        return 1;
    }

    private static int reloadDatabase(ServerCommandSource src) {
        if (SharedBackpackMod.database != null) {
            SharedBackpackMod.database.close();
        }
        SharedBackpackMod.database = new com.sharedbackpack.database.DatabaseManager(src.getMinecraftServer());
        SharedBackpackMod.database.init();
        src.sendFeedback(new LiteralText("§a数据库已重新加载"), false);
        return 1;
    }

    private static int forceBackup(ServerCommandSource src) {
        try {
            java.io.File dbFile = new java.io.File(src.getMinecraftServer().getRunDirectory(), "config/sharedbackpack/backpack.db");
            if (dbFile.exists()) {
                String timestamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
                java.io.File backupDir = new java.io.File(dbFile.getParent(), "backups");
                backupDir.mkdirs();
                java.io.File backup = new java.io.File(backupDir, "backpack_backup_" + timestamp + ".db");
                java.nio.file.Files.copy(dbFile.toPath(), backup.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                src.sendFeedback(new LiteralText("§a备份已创建: " + backup.getName()), false);
            } else {
                src.sendFeedback(new LiteralText("§c数据库文件不存在"), false);
            }
        } catch (Exception e) {
            src.sendFeedback(new LiteralText("§c备份失败: " + e.getMessage()), false);
        }
        return 1;
    }

    private static int showTeamInfo(ServerCommandSource src, String teamId) {
        String info = SharedBackpackMod.database.getBackpackInfo(teamId);
        src.sendFeedback(new LiteralText("§6" + info), false);
        return 1;
    }

    private static int clearTeam(ServerCommandSource src, String teamId) {
        src.sendFeedback(new LiteralText("§c清空功能开发中..."), false);
        return 1;
    }

    private static int upgradeTeam(ServerCommandSource src, String teamId, int pages) {
        if (SharedBackpackMod.database.upgradePages(teamId, pages)) {
            src.sendFeedback(new LiteralText("§a已为队伍 " + teamId + " 添加 " + pages + " 页"), false);
        } else {
            src.sendError(new LiteralText("§c升级失败"));
        }
        return 1;
    }

    private static int listTeams(ServerCommandSource src) {
        src.sendFeedback(new LiteralText("§6队伍列表功能开发中..."), false);
        return 1;
    }

    private static int searchItems(ServerCommandSource src, String query) {
        ServerPlayerEntity player = player(src);
        if (player != null) {
            List<DatabaseManager.BackpackItem> items = SharedBackpackMod.database.getItems(TeamResolver.resolvePrimaryTeam(player));
            List<DatabaseManager.BackpackItem> results = PinyinSearch.search(items, query);
            src.sendFeedback(new LiteralText("§6找到 " + results.size() + " 个匹配物品"), false);
        }
        return 1;
    }

    private static int openUnload(ServerCommandSource src) {
        ServerPlayerEntity player = player(src);
        if (player != null) {
            BackpackMenu.openForPlayer(player, "", 0, true, false, null);
            src.sendFeedback(new LiteralText("§a已打开卸货模式（无功能按钮）"), false);
        } else {
            src.sendError(new LiteralText("此命令只能由玩家使用"));
        }
        return 1;
    }

    private static int testPinyin(ServerCommandSource src, String text) {
        boolean match = PinyinUtil.matches("泥土", text);
        src.sendFeedback(new LiteralText("§6" + text + " 匹配泥土: " + match), false);
        return 1;
    }

    private static int lookupItem(ServerCommandSource src, String id) {
        Item item = Registry.ITEM.get(new Identifier(id));
        if (item == null || item == Items.AIR) {
            src.sendFeedback(new LiteralText("§c物品 " + id + " 未找到"), false);
        } else {
            String name = item.getName().getString();
            src.sendFeedback(new LiteralText("§6" + id + " = " + name + " | py:" + PinyinUtil.toPinyin(name)), false);
        }
        return 1;
    }

    private static int createBox(ServerCommandSource src, String name) {
        ServerPlayerEntity player = player(src);
        if (player == null) {
            src.sendError(new LiteralText("此命令只能由玩家使用")); return 0;
        }
        String team = TeamResolver.resolvePrimaryTeam(player);
        SharedBackpackMod.database.createBox(team, name);
        src.sendFeedback(new LiteralText("§a盒子 '" + name + "' 已创建"), false);
        return 1;
    }

    private static int openBox(ServerCommandSource src, String name) {
        ServerPlayerEntity player = player(src);
        if (player == null) {
            src.sendError(new LiteralText("此命令只能由玩家使用")); return 0;
        }
        String team = TeamResolver.resolvePrimaryTeam(player);
        BackpackMenu.openForPlayer(player, "", 0, false, true, team + ":" + name);
        return 1;
    }

    private static int listBoxes(ServerCommandSource src) {
        ServerPlayerEntity player = player(src);
        if (player == null) {
            src.sendError(new LiteralText("此命令只能由玩家使用")); return 0;
        }
        String team = TeamResolver.resolvePrimaryTeam(player);
        List<String> boxes = SharedBackpackMod.database.listBoxes(team);
        if (boxes.isEmpty()) src.sendFeedback(new LiteralText("§7队伍没有盒子"), false);
        else {
            src.sendFeedback(new LiteralText("§6盒子列表:"), false);
            for (String b : boxes) src.sendFeedback(new LiteralText(" §e- " + b), false);
        }
        return 1;
    }

    private static int deleteBox(ServerCommandSource src, String name) {
        ServerPlayerEntity player = player(src);
        if (player == null) {
            src.sendError(new LiteralText("此命令只能由玩家使用")); return 0;
        }
        String team = TeamResolver.resolvePrimaryTeam(player);
        SharedBackpackMod.database.deleteBox(team, name);
        src.sendFeedback(new LiteralText("§a盒子 '" + name + "' 已删除"), false);
        return 1;
    }

    private static int upgradeBox(ServerCommandSource src, String name) {
        ServerPlayerEntity player = player(src);
        if (player == null) {
            src.sendError(new LiteralText("此命令只能由玩家使用")); return 0;
        }
        SharedBackpackMod.database.upgradeBoxPages(TeamResolver.resolvePrimaryTeam(player), name, 1);
        src.sendFeedback(new LiteralText("§a盒子 '" + name + "' 已升级"), false);
        return 1;
    }

    private static int bindItem(ServerCommandSource src) {
        ServerPlayerEntity player = player(src);
        if (player == null) {
            src.sendError(new LiteralText("此命令只能由玩家使用")); return 0;
        }
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) {
            src.sendError(new LiteralText("请手持一个物品")); return 0;
        }
        BindManager.bind(player.getUuidAsString(), stack);
        src.sendFeedback(new LiteralText("§a已绑定 " + stack.getName().getString() + " 为背包钥匙"), false);
        return 1;
    }

    private static int bindToBox(ServerCommandSource src, String boxName) {
        ServerPlayerEntity player = player(src);
        if (player == null) {
            src.sendError(new LiteralText("此命令只能由玩家使用")); return 0;
        }
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) {
            src.sendError(new LiteralText("请手持一个物品")); return 0;
        }
        BindManager.bindToBox(player.getUuidAsString(), stack, boxName);
        src.sendFeedback(new LiteralText("§a已绑定 " + stack.getName().getString() + " → 快速打开盒子: " + boxName), false);
        return 1;
    }

    private static int unbindItem(ServerCommandSource src) {
        ServerPlayerEntity player = player(src);
        if (player == null) {
            src.sendError(new LiteralText("此命令只能由玩家使用")); return 0;
        }
        BindManager.unbind(player.getUuidAsString());
        src.sendFeedback(new LiteralText("§a已解绑，恢复默认胡萝卜"), false);
        return 1;
    }

    private static ServerPlayerEntity player(ServerCommandSource source) {
        Entity entity = source.getEntity();
        return entity instanceof ServerPlayerEntity ? (ServerPlayerEntity) entity : null;
    }
}
