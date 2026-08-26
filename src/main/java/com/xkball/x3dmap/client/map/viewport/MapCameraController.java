package com.xkball.x3dmap.client.map.viewport;

import com.xkball.x3dmap.api.client.viewport.MapCameraState;
import com.xkball.x3dmap.utils.VanillaUtils;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import org.joml.Matrix2f;
import org.joml.Vector2f;
import org.joml.Vector3f;

@NonNullByDefault
public final class MapCameraController {

    private final Vector3f target = new Vector3f();
    private float xRotation;
    private float yRotation;
    private float distance;
    private float fieldOfView;

    public MapCameraController(MapCameraState initialState) {
        this.apply(initialState);
    }

    public MapCameraState state() {
        return new MapCameraState(
                this.target.x,
                this.target.y,
                this.target.z,
                this.xRotation,
                this.yRotation,
                this.distance,
                this.fieldOfView
        );
    }

    public void apply(MapCameraState state) {
        this.target.set(state.targetX(), state.targetY(), state.targetZ());
        this.xRotation = Math.clamp(state.xRotation(), -89.9F, 89.9F);
        this.yRotation = normalizeRotation(state.yRotation());
        this.distance = Math.max(0.1F, state.distance());
        this.fieldOfView = Math.clamp(state.fieldOfView(), 1, 179);
    }

    public Vector3f target() {
        return this.target;
    }

    public Vector3f direction() {
        return VanillaUtils.dirVec(this.xRotation, this.yRotation);
    }

    public Vector3f position() {
        return this.direction().normalize(this.distance).add(this.target);
    }

    public float xRotation() {
        return this.xRotation;
    }

    public float yRotation() {
        return this.yRotation;
    }

    public float distance() {
        return this.distance;
    }

    public float fieldOfView() {
        return this.fieldOfView;
    }

    public void setRotation(float xRotation, float yRotation) {
        this.xRotation = Math.clamp(xRotation, -89.9F, 89.9F);
        this.yRotation = normalizeRotation(yRotation);
    }

    public void setDistance(float distance) {
        this.distance = Math.max(0.1F, distance);
    }

    public void setFieldOfView(float fieldOfView) {
        this.fieldOfView = Math.clamp(fieldOfView, 1, 179);
    }

    public void rotate(double dx, double dy) {
        var sensitivity = 0.25F * Math.max(0.4F, this.fieldOfView / 100);
        this.setRotation(this.xRotation + (float) dy * sensitivity, this.yRotation - (float) dx * sensitivity);
    }

    public void rotateYaw(double dx) {
        var sensitivity = 0.25F * Math.max(0.4F, this.fieldOfView / 100);
        this.setRotation(this.xRotation, this.yRotation - (float) dx * sensitivity);
    }

    public void rotateYawBy(float degrees) {
        this.setRotation(this.xRotation, this.yRotation + degrees);
    }

    public void zoom(double scrollY) {
        this.distance -= (float) (scrollY * Math.log10(this.distance + 10F));
        this.distance = Math.max(this.distance, 0.1F);
    }

    public void move(float dx, float dy, float dz, boolean allowVertical) {
        var speed = 0.75F * (1 + this.distance / 100);
        var direction = new Vector2f(dx, dz).mul(speed);
        direction.mul(new Matrix2f().rotate((float) Math.toRadians(-this.yRotation)));
        this.target.add(direction.x, allowVertical ? dy * speed : 0, direction.y);
    }

    private static float normalizeRotation(float rotation) {
        return (rotation % 360 + 360) % 360;
    }
}
