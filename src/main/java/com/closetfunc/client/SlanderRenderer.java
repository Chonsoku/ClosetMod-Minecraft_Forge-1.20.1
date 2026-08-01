package com.closetfunc.client;

import com.closetfunc.MainCloset;
import com.closetfunc.client.model.Slander;
import com.closetfunc.entity.SlanderEntity;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class SlanderRenderer extends MobRenderer<SlanderEntity, Slander> {
    public SlanderRenderer(EntityRendererProvider.Context context) {
        super(context, new Slander(context.bakeLayer(Slander.LAYER_LOCATION)), 0.6F);
    }

    @Override
    public ResourceLocation getTextureLocation(SlanderEntity entity) {
        return ResourceLocation.fromNamespaceAndPath(MainCloset.MOD_ID, "textures/entity/slander.png");
    }
}
