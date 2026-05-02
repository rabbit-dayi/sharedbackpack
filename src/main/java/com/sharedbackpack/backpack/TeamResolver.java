package com.sharedbackpack.backpack;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.util.*;

public class TeamResolver {

    public static final String GLOBAL_TEAM = "__global__";

    /**
     * Resolve team IDs for a player.
     * If player is on teams, returns all team names (union).
     * Otherwise returns the global team.
     */
    public static List<String> resolveTeams(ServerPlayer player) {
        Scoreboard scoreboard = player.getScoreboard();
        PlayerTeam team = scoreboard.getPlayersTeam(player.getScoreboardName());
        if (team != null) {
            return List.of(team.getName());
        }

        // Check if player is in multiple teams via scoreboard
        List<String> teams = new ArrayList<>();
        for (PlayerTeam t : scoreboard.getPlayerTeams()) {
            if (t.getPlayers().contains(player.getScoreboardName())) {
                teams.add(t.getName());
            }
        }
        if (!teams.isEmpty()) {
            return teams;
        }

        return List.of(GLOBAL_TEAM);
    }

    /**
     * Get the primary team for adding items.
     * Always returns the first resolved team.
     */
    public static String resolvePrimaryTeam(ServerPlayer player) {
        List<String> teams = resolveTeams(player);
        return teams.get(0);
    }
}
