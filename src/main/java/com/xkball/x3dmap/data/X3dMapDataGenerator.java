package com.xkball.x3dmap.data;

import com.xkball.x3dmap.X3dMap;
import com.xkball.x3dmap.block.X3dMapBlocks;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.ModelProvider;
import net.minecraft.core.Holder;
import net.minecraft.data.PackOutput;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.data.event.GatherDataEvent;

import java.util.stream.Stream;

@NonNullByDefault
@EventBusSubscriber(modid = X3dMap.MODID, value = Dist.CLIENT)
public final class X3dMapDataGenerator {

    private X3dMapDataGenerator() {
    }

    @SubscribeEvent
    public static void gatherData(GatherDataEvent.Client event) {
        event.createProvider(X3dMapModelProvider::new);
    }

    private static final class X3dMapModelProvider extends ModelProvider {

        private X3dMapModelProvider(PackOutput output) {
            super(output, X3dMap.MODID);
        }

        @Override
        protected void registerModels(BlockModelGenerators blockModels, ItemModelGenerators itemModels) {
            itemModels.declareCustomModelItem(X3dMapBlocks.TERRAIN_PROJECTOR_ITEM.get());
        }

        @Override
        protected Stream<? extends Holder<Block>> getKnownBlocks() {
            return Stream.empty();
        }

        @Override
        protected Stream<? extends Holder<Item>> getKnownItems() {
            return Stream.of(X3dMapBlocks.TERRAIN_PROJECTOR_ITEM);
        }
    }
}
