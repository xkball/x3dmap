package com.xkball.x3dmap.client.b3d.pipeline;

import com.xkball.x3dmap.X3dMap;
import com.xkball.x3dmap.utils.VanillaUtils;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import net.minecraft.resources.ResourceKey;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.pipeline.PipelineModifier;
import net.neoforged.neoforge.client.pipeline.RegisterPipelineModifiersEvent;

import java.util.Optional;

@NonNullByDefault
@EventBusSubscriber(modid = X3dMap.MODID, value = Dist.CLIENT)
public final class X3dMapPipelineModifiers {

    public static final ResourceKey<PipelineModifier> NO_DEPTH_TEST = ResourceKey.create(
            PipelineModifier.MODIFIERS_KEY,
            VanillaUtils.modRL("no_depth_test")
    );

    private X3dMapPipelineModifiers() {
    }

    @SubscribeEvent
    public static void register(RegisterPipelineModifiersEvent event) {
        event.register(NO_DEPTH_TEST, (pipeline, name) -> pipeline.toBuilder()
                .withLocation(name)
                .withDepthStencilState(Optional.empty())
                .build());
    }
}
