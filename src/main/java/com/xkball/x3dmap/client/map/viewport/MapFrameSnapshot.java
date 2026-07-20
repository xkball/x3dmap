package com.xkball.x3dmap.client.map.viewport;

import com.xkball.x3dmap.api.client.render.IMapFrame;
import com.xkball.x3dmap.api.client.viewport.MapCameraState;
import com.xkball.x3dmap.api.client.viewport.MapRay;
import com.xkball.x3dmap.client.terrain.LevelChunkStorage;
import com.xkball.x3dmap.utils.VanillaUtils;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;

@NonNullByDefault
public final class MapFrameSnapshot implements IMapFrame {

    private final ResourceKey<Level> dimension;
    private final Identifier preset;
    private final MapCameraState camera;
    private final float viewportX;
    private final float viewportY;
    private final float viewportWidth;
    private final float viewportHeight;
    private final boolean cullNear;
    private final int lodDistance;
    private final int minimapHighDetailRange;
    private final int baseY;
    private final @Nullable LevelChunkStorage terrainStorage;
    private final Vector3f cameraDirection;
    private final Vector3f cameraPosition;
    private final Matrix4f projectionMatrix;
    private final net.minecraft.client.renderer.culling.Frustum frustum;

    public MapFrameSnapshot(
            ResourceKey<Level> dimension,
            Identifier preset,
            MapCameraState camera,
            float viewportX,
            float viewportY,
            float viewportWidth,
            float viewportHeight,
            boolean cullNear,
            int lodDistance,
            int minimapHighDetailRange,
            int baseY,
            @Nullable LevelChunkStorage terrainStorage
    ) {
        this.dimension = dimension;
        this.preset = preset;
        this.camera = camera;
        this.viewportX = viewportX;
        this.viewportY = viewportY;
        this.viewportWidth = viewportWidth;
        this.viewportHeight = viewportHeight;
        this.cullNear = cullNear;
        this.lodDistance = lodDistance;
        this.minimapHighDetailRange = minimapHighDetailRange;
        this.baseY = baseY;
        this.terrainStorage = terrainStorage;
        this.cameraDirection = VanillaUtils.dirVec(camera.xRotation(), camera.yRotation());
        this.cameraPosition = new Vector3f(this.cameraDirection)
                .normalize(camera.distance() + 100)
                .add(camera.targetX(), camera.targetY(), camera.targetZ());
        var aspect = viewportWidth / Math.max(1, viewportHeight);
        this.projectionMatrix = new Matrix4f()
                .perspective(
                        (float) Math.toRadians(camera.fieldOfView()),
                        aspect,
                        Math.max(1, this.cameraPosition.y / 10),
                        Math.max(camera.distance() * 3, 16000)
                )
                .lookAt(
                        this.cameraPosition.x,
                        this.cameraPosition.y,
                        this.cameraPosition.z,
                        camera.targetX(),
                        camera.targetY(),
                        camera.targetZ(),
                        0,
                        1,
                        0
                );
        this.frustum = new net.minecraft.client.renderer.culling.Frustum(new Matrix4f(), this.projectionMatrix);
    }

    @Override
    public ResourceKey<Level> dimension() {
        return this.dimension;
    }

    @Override
    public Identifier preset() {
        return this.preset;
    }

    @Override
    public MapCameraState camera() {
        return this.camera;
    }

    @Override
    public Vector3fc cameraPosition() {
        return new Vector3f(this.cameraPosition);
    }

    @Override
    public Vector3fc cameraDirection() {
        return new Vector3f(this.cameraDirection);
    }

    @Override
    public Matrix4fc projectionMatrix() {
        return this.projectionMatrix;
    }

    @Override
    public float viewportX() {
        return this.viewportX;
    }

    @Override
    public float viewportY() {
        return this.viewportY;
    }

    @Override
    public float viewportWidth() {
        return this.viewportWidth;
    }

    @Override
    public float viewportHeight() {
        return this.viewportHeight;
    }

    @Override
    public boolean cullNear() {
        return this.cullNear;
    }

    @Override
    public int lodDistance() {
        return this.lodDistance;
    }

    @Override
    public int minimapHighDetailRange() {
        return this.minimapHighDetailRange;
    }

    @Override
    public int baseY() {
        return this.baseY;
    }

    @Override
    public boolean isVisible(AABB bounds) {
        return this.frustum.isVisible(bounds);
    }

    @Override
    public @Nullable Vector2f worldToScreen(Vector3fc worldPosition) {
        if (!this.isVisible(new AABB(worldPosition.x(), worldPosition.y(), worldPosition.z(), worldPosition.x() + 1, worldPosition.y() + 1, worldPosition.z() + 1))) {
            return null;
        }
        var projected = this.projectionMatrix.transform(new Vector4f(worldPosition.x(), worldPosition.y(), worldPosition.z(), 1));
        if (projected.w == 0) {
            return null;
        }
        var normalizedX = projected.x / projected.w;
        var normalizedY = projected.y / projected.w;
        return new Vector2f(
                (1 + normalizedX) / 2 * this.viewportWidth + this.viewportX,
                (1 - normalizedY) / 2 * this.viewportHeight + this.viewportY
        );
    }

    @Override
    public @Nullable MapRay screenRay(double screenX, double screenY) {
        if (this.viewportWidth <= 0 || this.viewportHeight <= 0) {
            return null;
        }
        var normalizedX = (float) ((screenX - this.viewportX) / this.viewportWidth * 2 - 1);
        var normalizedY = (float) (1 - (screenY - this.viewportY) / this.viewportHeight * 2);
        var inverse = this.projectionMatrix.invert(new Matrix4f());
        var near = inverse.transform(new Vector4f(normalizedX, normalizedY, -1, 1));
        var far = inverse.transform(new Vector4f(normalizedX, normalizedY, 1, 1));
        near.div(near.w);
        far.div(far.w);
        var direction = new Vector3f(far.x, far.y, far.z).sub(near.x, near.y, near.z).normalize();
        return new MapRay(
                this.cameraPosition.x,
                this.cameraPosition.y,
                this.cameraPosition.z,
                direction.x,
                direction.y,
                direction.z
        );
    }

    @Override
    public @Nullable Vector3f screenToTerrain(double screenX, double screenY) {
        var ray = this.screenRay(screenX, screenY);
        if (ray == null || this.terrainStorage == null) {
            return null;
        }
        var origin = ray.origin();
        var direction = ray.direction();
        var baseDistance = Math.max(this.camera.distance() + 100, 256);
        var maxDistance = Math.max(Math.max(this.camera.distance() * 2, 8000), baseDistance * 4);
        var step = Math.max(baseDistance / 8, 16);
        var previousDistance = 0.0f;
        var previousDelta = this.rayTerrainDelta(origin);
        for (var distance = step; distance <= maxDistance; distance += step) {
            var delta = this.rayTerrainDelta(this.rayPoint(origin, direction, distance));
            if (previousDelta * delta <= 0) {
                return this.searchRayTerrainHit(origin, direction, previousDistance, distance);
            }
            previousDistance = distance;
            previousDelta = delta;
        }
        return null;
    }

    public Matrix4f mutableProjectionMatrix() {
        return this.projectionMatrix;
    }

    public Vector3f mutableCameraPosition() {
        return this.cameraPosition;
    }

    public Vector3f mutableCameraDirection() {
        return this.cameraDirection;
    }

    private Vector3f searchRayTerrainHit(Vector3f origin, Vector3f direction, float nearDistance, float farDistance) {
        var low = nearDistance;
        var high = farDistance;
        var lowDelta = this.rayTerrainDelta(this.rayPoint(origin, direction, low));
        for (var i = 0; i < 32; i++) {
            var middle = (low + high) * 0.5f;
            var middleDelta = this.rayTerrainDelta(this.rayPoint(origin, direction, middle));
            if (lowDelta * middleDelta <= 0) {
                high = middle;
            } else {
                low = middle;
                lowDelta = middleDelta;
            }
        }
        return this.rayPoint(origin, direction, high);
    }

    private Vector3f rayPoint(Vector3f origin, Vector3f direction, float distance) {
        return new Vector3f(direction).mul(distance).add(origin);
    }

    private float rayTerrainDelta(Vector3f point) {
        if (this.terrainStorage == null) {
            return point.y - this.baseY;
        }
        var x = (int) Math.floor(point.x);
        var z = (int) Math.floor(point.z);
        if (this.terrainStorage.getChunk(new ChunkPos(x >> 4, z >> 4)) == null) {
            return point.y - this.baseY;
        }
        return point.y - this.terrainStorage.getHeight(x, z);
    }
}
