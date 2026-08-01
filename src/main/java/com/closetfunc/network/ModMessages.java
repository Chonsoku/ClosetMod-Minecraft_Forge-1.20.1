package com.closetfunc.network;

import com.closetfunc.MainCloset;
import com.closetfunc.block_entity.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import java.util.function.Supplier;

@SuppressWarnings("null")
public class ModMessages {
    private static SimpleChannel INSTANCE;
    private static int packetId = 0;

    private static int id() { return packetId++; }

    public static void register() {
        SimpleChannel net = NetworkRegistry.ChannelBuilder
                .named(new ResourceLocation(MainCloset.MOD_ID, "messages"))
                .networkProtocolVersion(() -> "1.0")
                .clientAcceptedVersions(s -> true)
                .serverAcceptedVersions(s -> true)
                .simpleChannel();

        INSTANCE = net;

        net.messageBuilder(ServerboundTypewriterTextPacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(ServerboundTypewriterTextPacket::new)
                .encoder(ServerboundTypewriterTextPacket::toBytes)
                .consumerMainThread(ServerboundTypewriterTextPacket::handle)
                .add();

        net.messageBuilder(ClientboundOpenTypewriterPacket.class, id(), NetworkDirection.PLAY_TO_CLIENT)
        .decoder(ClientboundOpenTypewriterPacket::new)
        .encoder(ClientboundOpenTypewriterPacket::toBytes)
        .consumerMainThread(ClientboundOpenTypewriterPacket::handle)
        .add();

        net.messageBuilder(ClientboundUpdateHardcoreVisualsPacket.class, id(), NetworkDirection.PLAY_TO_CLIENT)
            .decoder(ClientboundUpdateHardcoreVisualsPacket::new)
            .encoder(ClientboundUpdateHardcoreVisualsPacket::toBytes)
            .consumerMainThread(ClientboundUpdateHardcoreVisualsPacket::handle)
            .add();
    }

    public static <MSG> void sendToServer(MSG message) {
        INSTANCE.sendToServer(message);
    }

    public static <MSG> void sendToPlayer(MSG message, ServerPlayer player) {
    INSTANCE.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player), message);
    }

    public static class ClientboundUpdateHardcoreVisualsPacket {
        private final boolean active;
        public ClientboundUpdateHardcoreVisualsPacket(boolean active) { this.active = active; }
        public ClientboundUpdateHardcoreVisualsPacket(FriendlyByteBuf buf) { this.active = buf.readBoolean(); }
        public void toBytes(FriendlyByteBuf buf) { buf.writeBoolean(active); }
        public boolean handle(Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> {
                com.closetfunc.client.ClosetClient.isTypewriterHardcoreActive = this.active;
            });
            return true;
        }
    }

    public static class ServerboundTypewriterTextPacket {
        private final BlockPos pos;
        private final String[] pagesText;
        private final int dialogueStep;
        private final int rewardType;
        private final int currentEventId;
        private final boolean firstAnswerWasBad;

        public ServerboundTypewriterTextPacket(BlockPos pos, String[] pagesText, int dialogueStep, int rewardType, int currentEventId, boolean firstAnswerWasBad) {
            this.pos = pos;
            this.pagesText = pagesText;
            this.dialogueStep = dialogueStep;
            this.rewardType = rewardType;
            this.currentEventId = currentEventId;
            this.firstAnswerWasBad = firstAnswerWasBad;
        }

        public ServerboundTypewriterTextPacket(FriendlyByteBuf buf) {
            this.pos = buf.readBlockPos();
            this.dialogueStep = buf.readInt();
            this.rewardType = buf.readInt();
            this.currentEventId = buf.readInt();
            this.firstAnswerWasBad = buf.readBoolean();
            this.pagesText = new String[128];
            for (int i = 0; i < 128; i++) {
                this.pagesText[i] = buf.readUtf();
            }
        }

        public void toBytes(FriendlyByteBuf buf) {
            buf.writeBlockPos(pos);
            buf.writeInt(dialogueStep);
            buf.writeInt(rewardType);
            buf.writeInt(currentEventId);
            buf.writeBoolean(firstAnswerWasBad);
            for (String text : pagesText) {
                buf.writeUtf(text != null ? text : "");
            }
        }

        public boolean handle(Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> {
                net.minecraft.server.level.ServerPlayer sender = context.getSender();
                if (sender == null) return;
                
                ServerLevel level = sender.serverLevel();
                var playerNBT = sender.getPersistentData();

                playerNBT.putInt("TypewriterDialogueStep", this.dialogueStep);
                playerNBT.putInt("TypewriterRewardType", this.rewardType);
                playerNBT.putInt("TypewriterCurrentEventId", this.currentEventId);
                playerNBT.putBoolean("TypewriterFirstAnswerWasBad", this.firstAnswerWasBad);
                
                if (level.hasChunkAt(pos) && level.getBlockEntity(pos) instanceof ModBlockEntities.TypewriterBlockEntity be) {
                    be.updateTextFromServer(this.pagesText);
                    be.dialogueStep = this.dialogueStep;
                    be.rewardType = this.rewardType;
                    be.currentEventId = this.currentEventId;
                    be.firstAnswerWasBad = this.firstAnswerWasBad;
                    
                    if (this.pagesText != null && this.pagesText.length > 0) {
                        int surveyPage = Math.max(0, Math.min(be.currentSurveyPage, this.pagesText.length - 1));
                        playerNBT.putString("TypewriterFirstPageText", this.pagesText[surveyPage] != null ? this.pagesText[surveyPage] : " ");
                    }
                    
                    if (be.dialogueStep == 3) {
                        long currentDayIndex = level.getDayTime() / 24000L;

                        if (playerNBT.getInt("TypewriterRewardTriggeredDay") != be.surveyDay) {
                            playerNBT.putInt("TypewriterRewardTriggeredDay", be.surveyDay);

                            be.lastSurveyDay = currentDayIndex;
                            playerNBT.putLong("TypewriterLastCompletedDay", currentDayIndex);

                            long timeOfDay = level.getDayTime() % 24000L;
                            int ticksUntilNewDay = (int) (24000L - timeOfDay);
                            if (ticksUntilNewDay <= 0) ticksUntilNewDay = 1;

                            com.closetfunc.event.SurveyManager.triggerReward(be.rewardType, sender, level, ticksUntilNewDay);

                            be.rewardType = 0;
                        }
                    }
                    
                    be.setChanged();
                    level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), 3);
                }
            });
            return true;
        }
    }

        public static class ClientboundOpenTypewriterPacket {
        private final BlockPos pos;
        private final int paperCount;
        private final int surveyDay;
        private final String firstPageText;
        private final int surveyPage;
        private final int dialogueStep;
        private final int rewardType;
        private final int currentEventId;
        private final boolean firstAnswerWasBad;

        public ClientboundOpenTypewriterPacket(BlockPos pos, int paperCount, int surveyDay, String firstPageText, int surveyPage, int dialogueStep, int rewardType, int currentEventId, boolean firstAnswerWasBad) {
            this.pos = pos;
            this.paperCount = paperCount;
            this.surveyDay = surveyDay;
            this.firstPageText = firstPageText != null ? firstPageText : "";
            this.surveyPage = surveyPage;
            this.dialogueStep = dialogueStep;
            this.rewardType = rewardType;
            this.currentEventId = currentEventId;
            this.firstAnswerWasBad = firstAnswerWasBad;
        }

        public ClientboundOpenTypewriterPacket(FriendlyByteBuf buf) {
            this.pos = buf.readBlockPos();
            this.paperCount = buf.readInt();
            this.surveyDay = buf.readInt();
            this.firstPageText = buf.readUtf();
            this.surveyPage = buf.readInt();
            this.dialogueStep = buf.readInt();
            this.rewardType = buf.readInt();
            this.currentEventId = buf.readInt();
            this.firstAnswerWasBad = buf.readBoolean();
        }

        public void toBytes(FriendlyByteBuf buf) {
            buf.writeBlockPos(pos);
            buf.writeInt(paperCount);
            buf.writeInt(surveyDay);
            buf.writeUtf(firstPageText);
            buf.writeInt(surveyPage);
            buf.writeInt(dialogueStep);
            buf.writeInt(rewardType);
            buf.writeInt(currentEventId);
            buf.writeBoolean(firstAnswerWasBad);
        }

        public boolean handle(Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> {
                com.closetfunc.client.ClosetClient.openCustomTypewriterScreen(this.pos, this.paperCount, this.surveyDay, this.firstPageText, this.surveyPage, this.dialogueStep, this.rewardType, this.currentEventId, this.firstAnswerWasBad);
            });
            return true;
        }
    }
}