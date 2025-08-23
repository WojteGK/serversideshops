package com.nevarious.serversideshops;

import net.minecraft.scoreboard.ScoreAccess;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardCriterion;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public class EconomyService {

    private static Scoreboard getScoreboard(ServerPlayerEntity p) {
        return p.getWorld().getScoreboard();
    }

    private static ScoreboardObjective ensureObjective(ServerPlayerEntity p) {
        Scoreboard sb = getScoreboard(p);
        ScoreboardObjective obj = sb.getNullableObjective(ServersideShopsMod.OBJECTIVE);
        if (obj == null) {
            // numberFormat = null -> client default (integer) rendering
            obj = sb.addObjective(
                ServersideShopsMod.OBJECTIVE,
                ScoreboardCriterion.DUMMY,
                Text.literal("Money"),
                ScoreboardCriterion.RenderType.INTEGER,
                false,
                null
            );
        }
        return obj;
    }

    public static long getBalance(ServerPlayerEntity p) {
        ScoreboardObjective obj = ensureObjective(p);
        ScoreAccess score = getScoreboard(p).getOrCreateScore(p, obj); // ServerPlayerEntity implements ScoreHolder
        return score.getScore();
    }

    private static void setBalance(ServerPlayerEntity p, long value) {
        if (value < 0) value = 0;
        ScoreboardObjective obj = ensureObjective(p);
        ScoreAccess score = getScoreboard(p).getOrCreateScore(p, obj);
        score.setScore((int)Math.min(Integer.MAX_VALUE, value));
    }

    public static boolean tryWithdraw(ServerPlayerEntity p, long amount) {
        long bal = getBalance(p);
        if (bal < amount) return false;
        setBalance(p, bal - amount);
        return true;
    }

    public static void deposit(ServerPlayerEntity p, long amount) {
        long bal = getBalance(p);
        setBalance(p, bal + amount);
    }

    public static boolean tryTransfer(ServerPlayerEntity from, ServerPlayerEntity to, long amount) {
        if (amount <= 0) return false;
        if (!tryWithdraw(from, amount)) return false;
        deposit(to, amount);
        return true;
    }
}
