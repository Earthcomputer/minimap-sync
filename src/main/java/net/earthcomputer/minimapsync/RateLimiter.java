package net.earthcomputer.minimapsync;

import net.minecraft.Util;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class RateLimiter {
    private static final long MIN_TIME_BETWEEN_WARNINGS = 2000;

    private final long minTimeBetweenActions;
    private final Map<UUID, PlayerRateData> perPlayerData = new HashMap<>();
    private final Component warningMessage;

    public RateLimiter(long minTimeBetweenActions, Component warningMessage) {
        this.minTimeBetweenActions = minTimeBetweenActions;
        this.warningMessage = warningMessage;
    }

    public boolean checkRateLimit(ServerPlayer player, Runnable rollback) {
        PlayerRateData perPlayerData = this.perPlayerData.get(player.getUUID());
        if (perPlayerData == null) {
            this.perPlayerData.put(player.getUUID(), new PlayerRateData(rollback));
            return true;
        }

        long now = System.currentTimeMillis();
        boolean actionAllowed = now - perPlayerData.lastActionTime >= minTimeBetweenActions;
        perPlayerData.lastActionTime = now;
        if (actionAllowed) {
            perPlayerData.lastActionRollback = rollback;
        } else {
            if (now - perPlayerData.lastWarningTime >= MIN_TIME_BETWEEN_WARNINGS) {
                perPlayerData.lastWarningTime = now;
                player.sendMessage(warningMessage, Util.NIL_UUID);
            }
            if (perPlayerData.lastActionRollback != null) {
                perPlayerData.lastActionRollback.run();
                perPlayerData.lastActionRollback = null;
            }
        }

        return actionAllowed;
    }

    private static final class PlayerRateData {
        private long lastActionTime;
        private long lastWarningTime;
        @Nullable
        private Runnable lastActionRollback;

        PlayerRateData(@Nullable Runnable lastActionRollback) {
            this.lastActionTime = System.currentTimeMillis();
            this.lastActionRollback = lastActionRollback;
        }
    }
}
