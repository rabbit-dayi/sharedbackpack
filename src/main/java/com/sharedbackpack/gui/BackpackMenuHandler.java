package com.sharedbackpack.gui;

import com.sharedbackpack.backpack.TeamResolver;
import com.sharedbackpack.commands.BindManager;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.TypedActionResult;

public final class BackpackMenuHandler {
    private BackpackMenuHandler() {
    }

    public static void register() {
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (!world.isClient && player instanceof ServerPlayerEntity && tryOpen((ServerPlayerEntity) player)) {
                return TypedActionResult.success(player.getStackInHand(hand));
            }
            return TypedActionResult.pass(player.getStackInHand(hand));
        });
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (!world.isClient && player instanceof ServerPlayerEntity && tryOpen((ServerPlayerEntity) player)) {
                return ActionResult.SUCCESS;
            }
            return ActionResult.PASS;
        });
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            if (!world.isClient && player instanceof ServerPlayerEntity && tryOpen((ServerPlayerEntity) player)) {
                return ActionResult.SUCCESS;
            }
            return ActionResult.PASS;
        });
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (!world.isClient && player instanceof ServerPlayerEntity && tryOpen((ServerPlayerEntity) player)) {
                return ActionResult.SUCCESS;
            }
            return ActionResult.PASS;
        });
    }

    private static boolean tryOpen(ServerPlayerEntity player) {
        if (!BindManager.matches(player.getUuidAsString(), player.getMainHandStack())) {
            return false;
        }
        String boxTarget = BindManager.getBoxTarget(player.getUuidAsString());
        if (boxTarget != null) {
            String team = TeamResolver.resolvePrimaryTeam(player);
            BackpackMenu.openForPlayer(player, "", 0, false, true, team + ":" + boxTarget);
        } else {
            BackpackMenu.openTeam(player, "");
        }
        return true;
    }
}
