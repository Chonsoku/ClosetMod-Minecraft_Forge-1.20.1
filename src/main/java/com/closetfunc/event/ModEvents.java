package com.closetfunc.event;


import com.closetfunc.block.ModBlocks;
import com.closetfunc.block_entity.ModBlockEntities;


import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;


@SuppressWarnings("null")
public class ModEvents {    
    @SubscribeEvent
    public static void onMobTarget(LivingChangeTargetEvent event) {
        if (event.getNewTarget() instanceof Player player) {
            long currentTime = player.level().getGameTime();
            long cooldownUntil = player.getPersistentData().getLong("ClosetCooldownUntil");
            if (player.getPersistentData().getBoolean("IsInCloset") || currentTime < cooldownUntil) {
                event.setCanceled(true);
            }
        }
    }


    @SubscribeEvent
    public static void onLeftClickDefense(PlayerInteractEvent.LeftClickBlock event) {
        BlockState state = event.getLevel().getBlockState(event.getPos());
        if (state.is(ModBlocks.CLOSET_BLOCK.get()) || state.is(ModBlocks.CLOSET_BATIM_BLOCK.get()) || state.is(ModBlocks.CLOSET_BALDI_BLOCK.get())) {
            if (event.getEntity().getPersistentData().getBoolean("IsInCloset")) {
                event.setCanceled(true);
            }
        }
    }


    @SubscribeEvent
    public static void onMobTick(LivingEvent.LivingTickEvent event) {
        if (event.getEntity().level().isClientSide) return;
        if (event.getEntity() instanceof Mob mob) {
            net.minecraft.world.entity.LivingEntity target = mob.getTarget();
            if (target instanceof Player player) {
                long currentTime = player.level().getGameTime();
                long cooldownUntil = player.getPersistentData().getLong("ClosetCooldownUntil");
                if (player.getPersistentData().getBoolean("IsInCloset") || currentTime < cooldownUntil) {
                    mob.setTarget(null);
                    if (mob instanceof Monster) {
                        mob.getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.ATTACK_TARGET);
                        mob.getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET);
                    }
                    mob.getNavigation().stop();
                }
            }
        }
    }


    @SubscribeEvent
    public static void onPlayerStartTracking(PlayerEvent.StartTracking event) {
        // УДАЛЕН неиспользуемый код
    }


    // ТЫ В ШКАФУ, КАК НЕУЯЗВИМЫЙ!!! ^.^
    @SubscribeEvent
    public static void InvinciblePlayer(net.minecraftforge.event.entity.living.LivingHurtEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (player.getPersistentData().getBoolean("IsInCloset")) {
                event.setCanceled(true);
            }
        }
    }
    
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) return;
        
        net.minecraft.server.level.ServerPlayer player = (net.minecraft.server.level.ServerPlayer) event.player;
        net.minecraft.server.level.ServerLevel level = player.serverLevel();


        long currentDayIndex = level.getDayTime() / 24000L;
        long timeOfDay = level.getDayTime() % 24000L;
        int ticksUntilNewDay = (int) (24000L - timeOfDay);
        if (ticksUntilNewDay <= 0) ticksUntilNewDay = 1;


        // БЛОК 1: Проверка expiryDay и cleanup
        if (player.getPersistentData().contains("TypewriterEffectsExpiryDay")) {
            long expiryDay = player.getPersistentData().getLong("TypewriterEffectsExpiryDay"); 


            if (currentDayIndex >= expiryDay) {
                int activeRewardId = player.getPersistentData().getInt("ActiveTypewriterRewardId");
                
                // Вызываем cleanup перед удалением флагов
                if (activeRewardId % 2 != 0) {
                    GoodRewards.cleanup(activeRewardId, player, level);
                } else {
                    BadRewards.cleanup(activeRewardId, player, level);
                    
                    if (activeRewardId == 4) {
                        com.closetfunc.network.ModMessages.sendToPlayer(
                            new com.closetfunc.network.ModMessages.ClientboundOpenTypewriterPacket(
                                player.blockPosition(), 0, 0, "HARDCORE_END"
                            ), 
                            player
                        );
                    }
                }
                
                // Удаляем флаги наград
                player.getPersistentData().remove("ActiveTypewriterRewardId");
                player.getPersistentData().remove("TypewriterEffectsExpiryDay");
            }
        }


        // БЛОК 2: execute() каждый тик (НО только если есть ActiveTypewriterRewardId и НЕ достигнута expiryDay)
        if (player.getPersistentData().contains("ActiveTypewriterRewardId")) {
            // ПРОВЕРКА: Не достигнута ли expiryDay (если достигнута - не вызываем execute())
            if (!player.getPersistentData().contains("TypewriterEffectsExpiryDay") || 
                level.getDayTime() / 24000L < player.getPersistentData().getLong("TypewriterEffectsExpiryDay")) {
                
                int activeRewardId = player.getPersistentData().getInt("ActiveTypewriterRewardId");
                
                if (activeRewardId % 2 != 0) {
                    GoodRewards.execute(activeRewardId, player, level, ticksUntilNewDay);
                } else {
                    BadRewards.execute(activeRewardId, player, level, ticksUntilNewDay);
                }


                if (activeRewardId == 4 && player.tickCount % 20 == 0) {
                    com.closetfunc.network.ModMessages.sendToPlayer(
                        new com.closetfunc.network.ModMessages.ClientboundOpenTypewriterPacket(player.blockPosition(), 0, 2, "HARDCORE_SYNC"), 
                        player
                    );
                }
            }
        }


        // БЛОК 3: Выдача наград при наличии typewriter с rewardType > 0
        if (player.tickCount % 20 == 0) {
            int radius = 16;
            for (BlockPos targetPos : BlockPos.betweenClosed(player.blockPosition().offset(-radius, -4, -radius), player.blockPosition().offset(radius, 4, radius))) {
                if (level.getBlockEntity(targetPos) instanceof ModBlockEntities.TypewriterBlockEntity typewriter && typewriter.rewardType > 0) {
                    if (!player.getPersistentData().contains("ActiveTypewriterRewardId")) {
                        SurveyManager.triggerReward(typewriter.rewardType, player, level, ticksUntilNewDay);
                        
                        typewriter.rewardType = 0;  
                        typewriter.setChanged();
                        level.sendBlockUpdated(targetPos, level.getBlockState(targetPos), level.getBlockState(targetPos), 3);
                    }
                }
            }
        }
        
        // Таймер пасхалки на "Death Note" X.X
        long currentGameTime = level.getGameTime();


        if (player.getPersistentData().contains("DeathNoteTimeTarget")) {
            long deathTimeTarget = player.getPersistentData().getLong("DeathNoteTimeTarget");


            if (currentGameTime >= deathTimeTarget) {
                player.getPersistentData().remove("DeathNoteTimeTarget");


                net.minecraft.world.damagesource.DamageSource heartAttackSource = 
                    new net.minecraft.world.damagesource.DamageSource(
                        level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE)
                            .getHolderOrThrow(net.minecraft.world.damagesource.DamageTypes.FELL_OUT_OF_WORLD)
                    ) {
                        @Override
                        public net.minecraft.network.chat.Component getLocalizedDeathMessage(net.minecraft.world.entity.LivingEntity entity) {
                            return net.minecraft.network.chat.Component.translatable("death.attack.heart_attack", entity.getDisplayName());
                        }
                    };


                player.hurt(heartAttackSource, Float.MAX_VALUE);
            }
        }
    }
    
    @SuppressWarnings("unused")
    private static void applyScreamerEffects(ServerPlayer player, net.minecraft.world.level.Level level, int duration) {
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
            net.minecraft.world.effect.MobEffects.DARKNESS, duration, 1, false, false
        ));
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
            net.minecraft.world.effect.MobEffects.BLINDNESS, duration, 0, false, false
        ));
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
            net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, duration, 3, false, false
        ));
    }


    @SubscribeEvent
    public static void onPlayerDayModifiers(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) return;
        
        ServerPlayer player = (ServerPlayer) event.player;
        net.minecraft.server.level.ServerLevel level = player.serverLevel();
        
        long currentWorldDayIndex = level.getDayTime() / 24000L;
        long timeOfDay = level.getDayTime() % 24000L;


        if (player.getPersistentData().contains("DeathNoteTimeTarget")) {
            long deathTimeTarget = player.getPersistentData().getLong("DeathNoteTimeTarget");
            if (level.getGameTime() >= deathTimeTarget) {
                player.getPersistentData().remove("DeathNoteTimeTarget");


                net.minecraft.world.damagesource.DamageSource heartAttackSource = 
                    new net.minecraft.world.damagesource.DamageSource(
                        level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE)
                            .getHolderOrThrow(net.minecraft.world.damagesource.DamageTypes.FELL_OUT_OF_WORLD)
                    ) {
                        @Override
                        public net.minecraft.network.chat.Component getLocalizedDeathMessage(net.minecraft.world.entity.LivingEntity entity) {
                            return net.minecraft.network.chat.Component.translatable("death.attack.heart_attack", entity.getDisplayName());
                        }
                    };


                player.hurt(heartAttackSource, Float.MAX_VALUE);
            }
        }


        if (player.getPersistentData().contains("TypewriterEffectsExpiryDay")) {
            long expiryDay = player.getPersistentData().getLong("TypewriterEffectsExpiryDay");
            if (currentWorldDayIndex >= expiryDay) {
                int activeRewardId = player.getPersistentData().getInt("ActiveTypewriterRewardId");
                if (activeRewardId % 2 != 0) {
                    GoodRewards.cleanup(activeRewardId, player, level);
                } else {
                    BadRewards.cleanup(activeRewardId, player, level);
                    if (activeRewardId == 4) {
                        com.closetfunc.network.ModMessages.sendToPlayer(
                            new com.closetfunc.network.ModMessages.ClientboundOpenTypewriterPacket(
                                player.blockPosition(), 0, 0, "HARDCORE_END"
                            ), 
                            player
                        );
                    }
                }
                player.getPersistentData().remove("ActiveTypewriterRewardId");
                player.getPersistentData().remove("TypewriterEffectsExpiryDay");
            }
        }


        int ticksUntilNewDay = (int) (24000L - timeOfDay);
        if (ticksUntilNewDay <= 0) ticksUntilNewDay = 1;
    }
}