package com.xkball.x3dmap.client.map.gui;

import com.mojang.logging.LogUtils;
import com.xkball.x3dmap.api.client.gui.IMapView;
import com.xkball.x3dmap.ui.widget.WorldTerrainWidget;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

@NonNullByDefault
public final class MapViewImpl implements IMapView {

    private static final Logger LOGGER = LogUtils.getLogger();
    private final WorldTerrainWidget widget;

    public MapViewImpl(WorldTerrainWidget widget) {
        this.widget = widget;
    }

    @Override
    public ResourceKey<Level> dimension() {
        var level = Minecraft.getInstance().level;
        if (level == null) {
            LOGGER.error("Map view dimension was requested without an active level");
            throw new IllegalStateException("Map view dimension was requested without an active level");
        }
        return level.dimension();
    }

    @Override
    public @Nullable Vector3f screenToWorld(double screenX, double screenY) {
        return this.widget.inner.projScreen2World(screenX, screenY);
    }

    @Override
    public @Nullable Vector2f worldToScreen(Vector3f worldPosition) {
        return this.widget.inner.projWorld2Screen(worldPosition);
    }
}
