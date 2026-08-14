package com.sharedbackpack.backpack;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.scoreboard.Team;
import net.minecraft.scoreboard.Scoreboard;

import java.util.*;

public class TeamResolver {

    public static final String GLOBAL_TEAM = "__global__";

    /**
     * Resolve team IDs for a player.
     * If player is on teams, returns all team names (union).
     * Otherwise returns the global team.
     */
    public static List<String> resolveTeams(ServerPlayerEntity player) {
        Scoreboard scoreboard = player.getScoreboard();
        Team team = scoreboard.getPlayerTeam(player.getEntityName());
        if (team != null) {
            return Collections.singletonList(team.getName());
        }

        // Check if player is in multiple teams via scoreboard
        List<String> teams = new ArrayList<>();
        for (Team t : scoreboard.getTeams()) {
            if (t.getPlayerList().contains(player.getEntityName())) {
                teams.add(t.getName());
            }
        }
        if (!teams.isEmpty()) {
            return teams;
        }

        return Collections.singletonList(GLOBAL_TEAM);
    }

    /**
     * Get the primary team for adding items.
     * Always returns the first resolved team.
     */
    public static String resolvePrimaryTeam(ServerPlayerEntity player) {
        List<String> teams = resolveTeams(player);
        return teams.get(0);
    }
}
