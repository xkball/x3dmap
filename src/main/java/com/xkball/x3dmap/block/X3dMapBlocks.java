package com.xkball.x3dmap.block;

import com.xkball.x3dmap.X3dMap;
import com.xkball.x3dmap.block.entity.TerrainProjectorBlockEntity;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@NonNullByDefault
public final class X3dMapBlocks {

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(X3dMap.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(X3dMap.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, X3dMap.MODID);
    public static final DeferredHolder<Block, TerrainProjectorBlock> TERRAIN_PROJECTOR = BLOCKS.registerBlock(
            "terrain_projector", TerrainProjectorBlock::new,() -> Block.Properties.of().strength(1.0F).noOcclusion()
    );
    public static final DeferredHolder<Item, BlockItem> TERRAIN_PROJECTOR_ITEM = ITEMS.registerSimpleBlockItem(TERRAIN_PROJECTOR);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TerrainProjectorBlockEntity>> TERRAIN_PROJECTOR_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register(
            "terrain_projector", () -> new BlockEntityType<>(TerrainProjectorBlockEntity::new, TERRAIN_PROJECTOR.get())
    );

    private X3dMapBlocks() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITY_TYPES.register(modEventBus);
        modEventBus.addListener(X3dMapBlocks::addCreativeModeTabContents);
    }

    private static void addCreativeModeTabContents(BuildCreativeModeTabContentsEvent event) {
        if (CreativeModeTabs.FUNCTIONAL_BLOCKS.equals(event.getTabKey())) {
            event.accept(TERRAIN_PROJECTOR_ITEM.get());
        }
    }
}
