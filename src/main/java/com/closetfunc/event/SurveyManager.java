package com.closetfunc.event;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public class SurveyManager {
    public static final int MAX_DAYS = 10;

    public static final int SPECIAL_EVENT_DAY_5 = 99;
    public static final int SPECIAL_EVENT_DAY_10 = 100;

    public static int calculateRewardType(int currentDay, boolean totalNegative) {
        if (currentDay == 5) return SPECIAL_EVENT_DAY_5;
        if (currentDay == 10) return SPECIAL_EVENT_DAY_10;

        if (totalNegative) {
            return currentDay * 2;
        } else {
            return (currentDay * 2) - 1;
        }
    }

    public static void triggerReward(int rewardType, ServerPlayer player, ServerLevel level, int duration) {
        if (player.getPersistentData().contains("ActiveTypewriterRewardId")) {
            int oldRewardId = player.getPersistentData().getInt("ActiveTypewriterRewardId");
            cleanupReward(oldRewardId, player, level);
        }

        player.getPersistentData().putInt("ActiveTypewriterRewardId", rewardType);
        player.getPersistentData().remove("TypewriterEffectsCleanedUp");
        
        long currentWorldDayIndex = level.getDayTime() / 24000L;
        player.getPersistentData().putLong("TypewriterEffectsExpiryDay", currentWorldDayIndex + 1);
        player.getPersistentData().putLong("TypewriterEffectsTriggerDay", currentWorldDayIndex);
        player.getPersistentData().putLong("TypewriterEffectsExpiryTick", level.getGameTime() + 24000L);

        if (rewardType == SPECIAL_EVENT_DAY_5 || rewardType == SPECIAL_EVENT_DAY_10) {
            SpecialEvents.execute(rewardType, player, level, duration);
        } else if (rewardType % 2 != 0) {
            GoodRewards.execute(rewardType, player, level, duration);
        } else {
            BadRewards.execute(rewardType, player, level, duration);
        }
    }

    public static void cleanupReward(int rewardId, ServerPlayer player, ServerLevel level) {
        if (rewardId == SPECIAL_EVENT_DAY_5 || rewardId == SPECIAL_EVENT_DAY_10) {
            SpecialEvents.cleanup(rewardId, player, level);
        } else if (rewardId % 2 != 0) {
            GoodRewards.cleanup(rewardId, player, level);
        } else {
            BadRewards.cleanup(rewardId, player, level);

            if (rewardId == 4) {
                com.closetfunc.network.ModMessages.sendToPlayer(
                    new com.closetfunc.network.ModMessages.ClientboundOpenTypewriterPacket(
                        player.blockPosition(), 0, 0, "HARDCORE_END", 0, 0, 0, 0, false
                    ),
                    player
                );
            }
        }
    }
}
