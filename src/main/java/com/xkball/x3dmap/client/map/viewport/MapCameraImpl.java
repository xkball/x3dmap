package com.xkball.x3dmap.client.map.viewport;

import com.xkball.x3dmap.api.client.viewport.IMapCamera;
import com.xkball.x3dmap.api.client.viewport.IMapCameraControl;
import com.xkball.x3dmap.api.client.viewport.MapCameraState;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

@NonNullByDefault
public final class MapCameraImpl implements IMapCamera {
    
    private final Supplier<MapCameraState> stateGetter;
    private final Consumer<MapCameraState> stateSetter;
    private final Runnable invalidator;
    private final List<ControlImpl> controls = new ArrayList<>();
    private long nextSequence;
    
    public MapCameraImpl(Supplier<MapCameraState> stateGetter, Consumer<MapCameraState> stateSetter, Runnable invalidator) {
        this.stateGetter = stateGetter;
        this.stateSetter = stateSetter;
        this.invalidator = invalidator;
    }
    
    @Override
    public MapCameraState state() {
        return this.stateGetter.get();
    }
    
    @Override
    public boolean externallyControlled() {
        return !this.controls.isEmpty();
    }
    
    @Override
    public IMapCameraControl acquireControl(Identifier owner, int priority) {
        var control = new ControlImpl(owner, priority, this.nextSequence++);
        this.controls.add(control);
        return control;
    }
    
    public void setDefaultState(MapCameraState state) {
        if (!this.externallyControlled()) {
            this.apply(state);
        }
    }
    
    public void close() {
        for (var control : this.controls) {
            control.closed = true;
        }
        this.controls.clear();
    }
    
    private void apply(MapCameraState state) {
        this.stateSetter.accept(state);
        this.invalidator.run();
    }
    
    private ControlImpl activeControl() {
        return this.controls.stream()
                .max(Comparator.comparingInt((ControlImpl control) -> control.priority)
                        .thenComparingLong(control -> control.sequence))
                .orElseThrow();
    }
    
    private final class ControlImpl implements IMapCameraControl {
        
        private final Identifier owner;
        private final int priority;
        private final long sequence;
        private boolean closed;
        
        private ControlImpl(Identifier owner, int priority, long sequence) {
            this.owner = owner;
            this.priority = priority;
            this.sequence = sequence;
        }
        
        @Override
        public Identifier owner() {
            return this.owner;
        }
        
        @Override
        public int priority() {
            return this.priority;
        }
        
        @Override
        public boolean active() {
            return !this.closed && !MapCameraImpl.this.controls.isEmpty() && MapCameraImpl.this.activeControl() == this;
        }
        
        @Override
        public MapCameraState state() {
            return MapCameraImpl.this.state();
        }
        
        @Override
        public void setState(MapCameraState state) {
            if (this.active()) {
                MapCameraImpl.this.apply(state);
            }
        }
        
        @Override
        public void close() {
            if (!this.closed) {
                this.closed = true;
                MapCameraImpl.this.controls.remove(this);
                MapCameraImpl.this.invalidator.run();
            }
        }
    }
}
