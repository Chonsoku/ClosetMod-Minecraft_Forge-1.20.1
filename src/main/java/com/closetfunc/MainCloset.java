package com.closetfunc;

import com.closetfunc.block.ModBlocks;
import com.closetfunc.block_entity.ModBlockEntities;
import com.closetfunc.item.ModItems;
import com.closetfunc.sound.ModSounds;
import com.closetfunc.worldgen.ModFeature;
import com.closetfunc.event.BadRewards;
import com.closetfunc.event.GoodRewards;
import com.closetfunc.event.ModEvents;
import com.closetfunc.client.ClosetClient;
import com.closetfunc.client.SlanderRenderer;
import com.closetfunc.client.model.Slander;
import com.closetfunc.entity.ModEntities;
import com.closetfunc.network.ModMessages;
import com.closetfunc.event.SpecialEvents;

import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod(MainCloset.MOD_ID)
public class MainCloset {
    public static final String MOD_ID = "closet_mod";

    public MainCloset() {
        var bus = FMLJavaModLoadingContext.get().getModEventBus();

        // Регистрируем все компоненты мода из новых пакетов
        ModBlocks.BLOCKS.register(bus);
        ModItems.ITEMS.register(bus);
        ModBlockEntities.BLOCK_ENTITIES.register(bus);
        ModFeature.FEATURES.register(bus);
        ModSounds.SOUNDS.register(bus);
        ModEntities.ENTITY_TYPES.register(bus);
        ModMessages.register();

        bus.addListener(ModEntities::registerAttributes);

        MinecraftForge.EVENT_BUS.register(ModEvents.class);
        MinecraftForge.EVENT_BUS.register(GoodRewards.class);
        MinecraftForge.EVENT_BUS.register(BadRewards.class);
        MinecraftForge.EVENT_BUS.register(SpecialEvents.class);
        MinecraftForge.EVENT_BUS.register(ModBlockEntities.ClosetBlockEntity.class);
        
        // Пасхалка на "Death Note"
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.RegisterCommandsEvent event) -> {
            event.getDispatcher().register(com.mojang.brigadier.builder.LiteralArgumentBuilder.<net.minecraft.commands.CommandSourceStack>literal("closetmod_trigger_heartattack")
                .requires(source -> source.hasPermission(0))
                .executes(context -> {
                    net.minecraft.server.level.ServerPlayer serverPlayer = context.getSource().getPlayer();
                    if (serverPlayer != null && !serverPlayer.getPersistentData().contains("DeathNoteTimeTarget")) {
                        // Смерть через 1200 тиков (60 секунд)
                        long timeOfDeath = serverPlayer.level().getGameTime() + 1200L;
                        serverPlayer.getPersistentData().putLong("DeathNoteTimeTarget", timeOfDeath);
                    }
                    return 1;
                })
            );

            event.getDispatcher().register(
                com.mojang.brigadier.builder.LiteralArgumentBuilder.<net.minecraft.commands.CommandSourceStack>literal("closetmod_trigger")
                    .requires(source -> source.hasPermission(2))
                    .then(dayCommand("day1", 1, 2))
                    .then(dayCommand("day2", 3, 4))
                    .then(dayCommand("day3", 5, 6))
                    .then(dayCommand("day4", 7, 8))
                    .then(literal("day5").executes(context -> triggerRewardCommand(context.getSource(), com.closetfunc.event.SurveyManager.SPECIAL_EVENT_DAY_5)))
                    .then(literal("day10").executes(context -> triggerRewardCommand(context.getSource(), com.closetfunc.event.SurveyManager.SPECIAL_EVENT_DAY_10)))
            );
        });


        if (FMLEnvironment.dist.isClient()) {
            ClosetClient.init();

            bus.addListener((EntityRenderersEvent.RegisterRenderers event) ->
                    event.registerEntityRenderer(ModEntities.SLANDER.get(), SlanderRenderer::new));
            bus.addListener((EntityRenderersEvent.RegisterLayerDefinitions event) ->
                    event.registerLayerDefinition(Slander.LAYER_LOCATION, Slander::createBodyLayer));
        }

        bus.addListener(ModItems::addCreative);
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<net.minecraft.commands.CommandSourceStack> dayCommand(String day, int goodId, int badId) {
        return literal(day)
            .then(literal("good").executes(context -> triggerRewardCommand(context.getSource(), goodId)))
            .then(literal("bad").executes(context -> triggerRewardCommand(context.getSource(), badId)));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<net.minecraft.commands.CommandSourceStack> literal(String name) {
        return com.mojang.brigadier.builder.LiteralArgumentBuilder.literal(name);
    }

    private static int triggerRewardCommand(net.minecraft.commands.CommandSourceStack source, int rewardId) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        net.minecraft.server.level.ServerPlayer player = source.getPlayerOrException();
        net.minecraft.server.level.ServerLevel level = player.serverLevel();

        if (player.getPersistentData().contains("ActiveTypewriterRewardId")) {
            int currentId = player.getPersistentData().getInt("ActiveTypewriterRewardId");
            com.closetfunc.event.SurveyManager.cleanupReward(currentId, player, level);
            player.getPersistentData().remove("ActiveTypewriterRewardId");
            player.getPersistentData().remove("TypewriterEffectsExpiryDay");
            player.getPersistentData().remove("TypewriterEffectsTriggerDay");
            player.getPersistentData().remove("TypewriterEffectsExpiryTick");
        }

        int ticksUntilNewDay = (int) (24000L - (level.getDayTime() % 24000L));
        if (ticksUntilNewDay <= 0) ticksUntilNewDay = 1;
        com.closetfunc.event.SurveyManager.triggerReward(rewardId, player, level, ticksUntilNewDay);
        return 1;
    }
}
