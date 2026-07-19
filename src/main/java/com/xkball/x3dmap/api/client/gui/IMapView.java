package com.xkball.x3dmap.api.client.gui;

import com.xkball.xklibmc.annotation.NonNullByDefault;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

@NonNullByDefault
public interface IMapView {

    ResourceKey<Level> dimension();

    @Nullable Vector3f screenToWorld(double screenX, double screenY);

    @Nullable Vector2f worldToScreen(Vector3f worldPosition);
}
