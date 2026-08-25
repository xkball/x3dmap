package com.xkball.x3dmap.client.event;

import com.xkball.xklibmc.annotation.NonNullByDefault;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

@NonNullByDefault
public final class CancelWorldRenderingEvent extends Event implements ICancellableEvent {
}
