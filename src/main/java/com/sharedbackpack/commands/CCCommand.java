package com.sharedbackpack.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.sharedbackpack.SharedBackpackMod;
import com.sharedbackpack.backpack.TeamResolver;
import com.sharedbackpack.compat.MinecraftCompat;
import com.sharedbackpack.database.DatabaseManager;
import com.sharedbackpack.gui.BackpackMenu;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.command.CommandManager;
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
            .then(DebugCommand.createCommand("debug"))
        );
    }

    private static int showHelp(ServerCommandSource src) {
        MinecraftCompat.sendFeedback(src, MinecraftCompat.text("§6=== Shared Backpack Help ==="));
        MinecraftCompat.sendFeedback(src, MinecraftCompat.text("§e/c §7- 打开共享背包"));
        MinecraftCompat.sendFeedback(src, MinecraftCompat.text("§e/c <搜索词> §7- 打开并搜索物品(拼音/英文/ID)"));
        MinecraftCompat.sendFeedback(src, MinecraftCompat.text("§e/cc help §7- 显示此帮助"));
        MinecraftCompat.sendFeedback(src, MinecraftCompat.text("§e/cc unload §7- 卸货模式(无按钮,纯格子)"));
        MinecraftCompat.sendFeedback(src, MinecraftCompat.text("§e/cc admin info <team> §7- 查看队伍背包信息"));
        MinecraftCompat.sendFeedback(src, MinecraftCompat.text("§e/cc admin clear <team> §7- 清空队伍背包"));
        MinecraftCompat.sendFeedback(src, MinecraftCompat.text("§e/cc admin upgrade <team> <pages> §7- 升级队伍背包页数"));
        MinecraftCompat.sendFeedback(src, MinecraftCompat.text("§e/cc admin backup §7- 强制备份数据库"));
        MinecraftCompat.sendFeedback(src, MinecraftCompat.text("§e/cc admin reload §7- 重新加载数据库"));
        MinecraftCompat.sendFeedback(src, MinecraftCompat.text("§e/cc search <query> §7- 搜索物品（支持拼音）"));
        MinecraftCompat.sendFeedback(src, MinecraftCompat.text("§e/cc box create <name> §7- 创建个人盒子"));
        MinecraftCompat.sendFeedback(src, MinecraftCompat.text("§e/cc box open <name> §7- 打开个人盒子"));
        MinecraftCompat.sendFeedback(src, MinecraftCompat.text("§e/cc box list §7- 列出个人盒子"));
        MinecraftCompat.sendFeedback(src, MinecraftCompat.text("§e/cc box delete <name> §7- 删除个人盒子"));
        MinecraftCompat.sendFeedback(src, MinecraftCompat.text("§e/cc bind §7- 将手持物品绑定为背包钥匙"));
        MinecraftCompat.sendFeedback(src, MinecraftCompat.text("§e/cc unbind §7- 解绑背包钥匙"));
        return 1;
    }

    private static int reloadDatabase(ServerCommandSource src) {
        if (SharedBackpackMod.database != null) {
            SharedBackpackMod.database.close();
        }
        SharedBackpackMod.database = new com.sharedbackpack.database.DatabaseManager(MinecraftCompat.getServer(src));
        SharedBackpackMod.database.init();
        MinecraftCompat.sendFeedback(src, MinecraftCompat.text("§a数据库已重新加载"));
        return 1;
    }

    private static int forceBackup(ServerCommandSource src) {
        try {
            java.io.File dbFile = new java.io.File(MinecraftCompat.getServer(src).getRunDirectory(), "config/sharedbackpack/backpack.db");
            if (dbFile.exists()) {
                String timestamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
                java.io.File backupDir = new java.io.File(dbFile.getParent(), "backups");
                backupDir.mkdirs();
                java.io.File backup = new java.io.File(backupDir, "backpack_backup_" + timestamp + ".db");
                java.nio.file.Files.copy(dbFile.toPath(), backup.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                MinecraftCompat.sendFeedback(src, MinecraftCompat.text("§a备份已创建: " + backup.getName()));
            } else {
                MinecraftCompat.sendFeedback(src, MinecraftCompat.text("§c数据库文件不存在"));
            }
        } catch (Exception e) {
            MinecraftCompat.sendFeedback(src, MinecraftCompat.text("§c备份失败: " + e.getMessage()));
        }
        return 1;
    }

    private static int showTeamInfo(ServerCommandSource src, String teamId) {
        String info = SharedBackpackMod.database.getBackpackInfo(teamId);
        MinecraftCompat.sendFeedback(src, MinecraftCompat.text("§6" + info));
        return 1;
    }

    private static int clearTeam(ServerCommandSource src, String teamId) {
        MinecraftCompat.sendFeedback(src, MinecraftCompat.text("§c清空功能开发中..."));
        return 1;
    }

    private static int upgradeTeam(ServerCommandSource src, String teamId, int pages) {
        if (SharedBackpackMod.database.upgradePages(teamId, pages)) {
            MinecraftCompat.sendFeedback(src, MinecraftCompat.text("§a已为队伍 " + teamId + " 添加 " + pages + " 页"));
        } else {
            src.sendError(MinecraftCompat.text("§c升级失败"));
        }
        return 1;
    }

    private static int listTeams(ServerCommandSource src) {
        MinecraftCompat.sendFeedback(src, MinecraftCompat.text("§6队伍列表功能开发中..."));
        return 1;
    }

    private static int searchItems(ServerCommandSource src, String query) {
        ServerPlayerEntity player = player(src);
        if (player != null) {
            List<DatabaseManager.BackpackItem> items = SharedBackpackMod.database.getItems(TeamResolver.resolvePrimaryTeam(player));
            List<DatabaseManager.BackpackItem> results = PinyinSearch.search(items, query);
            MinecraftCompat.sendFeedback(src, MinecraftCompat.text("§6找到 " + results.size() + " 个匹配物品"));
        }
        return 1;
    }

    private static int openUnload(ServerCommandSource src) {
        ServerPlayerEntity player = player(src);
        if (player != null) {
            BackpackMenu.openForPlayer(player, "", 0, true, false, null);
            MinecraftCompat.sendFeedback(src, MinecraftCompat.text("§a已打开卸货模式（无功能按钮）"));
        } else {
            src.sendError(MinecraftCompat.text("此命令只能由玩家使用"));
        }
        return 1;
    }

    private static int testPinyin(ServerCommandSource src, String text) {
        boolean match = PinyinUtil.matches("泥土", text);
        MinecraftCompat.sendFeedback(src, MinecraftCompat.text("§6" + text + " 匹配泥土: " + match));
        return 1;
    }

    private static int lookupItem(ServerCommandSource src, String id) {
        Item item = MinecraftCompat.getItem(new Identifier(id));
        if (item == null || item == Items.AIR) {
            MinecraftCompat.sendFeedback(src, MinecraftCompat.text("§c物品 " + id + " 未找到"));
        } else {
            String name = MinecraftCompat.getItemDisplayName(item);
            MinecraftCompat.sendFeedback(src, MinecraftCompat.text("§6" + id + " = " + name + " | py:" + PinyinUtil.toPinyin(name)));
        }
        return 1;
    }

    private static int createBox(ServerCommandSource src, String name) {
        ServerPlayerEntity player = player(src);
        if (player == null) {
            src.sendError(MinecraftCompat.text("此命令只能由玩家使用")); return 0;
        }
        String team = TeamResolver.resolvePrimaryTeam(player);
        SharedBackpackMod.database.createBox(team, name);
        MinecraftCompat.sendFeedback(src, MinecraftCompat.text("§a盒子 '" + name + "' 已创建"));
        return 1;
    }

    private static int openBox(ServerCommandSource src, String name) {
        ServerPlayerEntity player = player(src);
        if (player == null) {
            src.sendError(MinecraftCompat.text("此命令只能由玩家使用")); return 0;
        }
        String team = TeamResolver.resolvePrimaryTeam(player);
        BackpackMenu.openForPlayer(player, "", 0, false, true, team + ":" + name);
        return 1;
    }

    private static int listBoxes(ServerCommandSource src) {
        ServerPlayerEntity player = player(src);
        if (player == null) {
            src.sendError(MinecraftCompat.text("此命令只能由玩家使用")); return 0;
        }
        String team = TeamResolver.resolvePrimaryTeam(player);
        List<String> boxes = SharedBackpackMod.database.listBoxes(team);
        if (boxes.isEmpty()) MinecraftCompat.sendFeedback(src, MinecraftCompat.text("§7队伍没有盒子"));
        else {
            MinecraftCompat.sendFeedback(src, MinecraftCompat.text("§6盒子列表:"));
            for (String b : boxes) MinecraftCompat.sendFeedback(src, MinecraftCompat.text(" §e- " + b));
        }
        return 1;
    }

    private static int deleteBox(ServerCommandSource src, String name) {
        ServerPlayerEntity player = player(src);
        if (player == null) {
            src.sendError(MinecraftCompat.text("此命令只能由玩家使用")); return 0;
        }
        String team = TeamResolver.resolvePrimaryTeam(player);
        SharedBackpackMod.database.deleteBox(team, name);
        MinecraftCompat.sendFeedback(src, MinecraftCompat.text("§a盒子 '" + name + "' 已删除"));
        return 1;
    }

    private static int upgradeBox(ServerCommandSource src, String name) {
        ServerPlayerEntity player = player(src);
        if (player == null) {
            src.sendError(MinecraftCompat.text("此命令只能由玩家使用")); return 0;
        }
        SharedBackpackMod.database.upgradeBoxPages(TeamResolver.resolvePrimaryTeam(player), name, 1);
        MinecraftCompat.sendFeedback(src, MinecraftCompat.text("§a盒子 '" + name + "' 已升级"));
        return 1;
    }

    private static int bindItem(ServerCommandSource src) {
        ServerPlayerEntity player = player(src);
        if (player == null) {
            src.sendError(MinecraftCompat.text("此命令只能由玩家使用")); return 0;
        }
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) {
            src.sendError(MinecraftCompat.text("请手持一个物品")); return 0;
        }
        BindManager.bind(player.getUuidAsString(), stack);
        MinecraftCompat.sendFeedback(src, MinecraftCompat.text("§a已绑定 " + stack.getName().getString() + " 为背包钥匙"));
        return 1;
    }

    private static int bindToBox(ServerCommandSource src, String boxName) {
        ServerPlayerEntity player = player(src);
        if (player == null) {
            src.sendError(MinecraftCompat.text("此命令只能由玩家使用")); return 0;
        }
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) {
            src.sendError(MinecraftCompat.text("请手持一个物品")); return 0;
        }
        BindManager.bindToBox(player.getUuidAsString(), stack, boxName);
        MinecraftCompat.sendFeedback(src, MinecraftCompat.text("§a已绑定 " + stack.getName().getString() + " → 快速打开盒子: " + boxName));
        return 1;
    }

    private static int unbindItem(ServerCommandSource src) {
        ServerPlayerEntity player = player(src);
        if (player == null) {
            src.sendError(MinecraftCompat.text("此命令只能由玩家使用")); return 0;
        }
        BindManager.unbind(player.getUuidAsString());
        MinecraftCompat.sendFeedback(src, MinecraftCompat.text("§a已解绑。请使用 /cc bind 绑定手持物品作为背包钥匙"));
        return 1;
    }

    private static ServerPlayerEntity player(ServerCommandSource source) {
        Entity entity = source.getEntity();
        return entity instanceof ServerPlayerEntity ? (ServerPlayerEntity) entity : null;
    }
}
