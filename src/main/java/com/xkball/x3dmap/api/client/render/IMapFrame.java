package com.xkball.x3dmap.api.client.render;

import com.xkball.x3dmap.api.client.viewport.IMapProjection;
import com.xkball.x3dmap.api.client.viewport.MapCameraState;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4fc;
import org.joml.Vector3fc;

@NonNullByDefault
public interface IMapFrame extends IMapProjection {

    ResourceKey<Level> dimension();

    Identifier preset();

    MapCameraState camera();

    Vector3fc cameraPosition();

    Vector3fc cameraDirection();

    Matrix4fc projectionMatrix();

    float viewportX();

    float viewportY();

    float viewportWidth();

    float viewportHeight();

    boolean cullNear();

    int lodDistance();

    int minimapHighDetailRange();

    int baseY();

    boolean isVisible(AABB bounds);
}
