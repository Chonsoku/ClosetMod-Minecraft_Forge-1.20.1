package com.closetfunc.event;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public class SpecialEvents {
    private static final Map<Integer, SpecialEventBase> EVENTS = new HashMap<>();

    static {
        EVENTS.put(SurveyManager.SPECIAL_EVENT_DAY_5, new SpecialEventBase() {
            @Override
            public void tick(ServerPlayer player, ServerLevel level, int duration) {
                if (!player.getPersistentData().getBoolean("SpecialEvent5Started")) {
                    player.getPersistentData().putBoolean("SpecialEvent5Started", true);

                    player.sendSystemMessage(
                        net.minecraft.network.chat.Component.literal("§4§l???§r §cТы чувствуешь, как пространство искажается...")
                    );
                }

                if (player.tickCount % 100 == 0) {
                    player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        net.minecraft.world.effect.MobEffects.DARKNESS, 120, 1, false, false
                    ));
                }
            }

            @Override
            public void cleanup(ServerPlayer player, ServerLevel level) {
                player.getPersistentData().remove("SpecialEvent5Started");
                player.removeEffect(net.minecraft.world.effect.MobEffects.DARKNESS);
            }
        });

        EVENTS.put(SurveyManager.SPECIAL_EVENT_DAY_10, new SpecialEventBase() {
            @Override
            public void tick(ServerPlayer player, ServerLevel level, int duration) {
                if (!player.getPersistentData().getBoolean("SpecialEvent10Started")) {
                    player.getPersistentData().putBoolean("SpecialEvent10Started", true);

                    player.sendSystemMessage(
                        net.minecraft.network.chat.Component.literal("§4§lФИНАЛ§r §cВсё подходит к концу...")
                    );
                }
            }

            @Override
            public void cleanup(ServerPlayer player, ServerLevel level) {
                player.getPersistentData().remove("SpecialEvent10Started");
            }
        });
    }

    public static void execute(int rewardId, ServerPlayer player, ServerLevel level, int duration) {
        SpecialEventBase event = EVENTS.get(rewardId);
        if (event != null) {
            event.tick(player, level, duration);
        }
    }

    public static void cleanup(int rewardId, ServerPlayer player, ServerLevel level) {
        SpecialEventBase event = EVENTS.get(rewardId);
        if (event != null) {
            event.cleanup(player, level);
        }
    }

    public interface SpecialEventBase {
        void tick(ServerPlayer player, ServerLevel level, int duration);
        void cleanup(ServerPlayer player, ServerLevel level);
    }
}
