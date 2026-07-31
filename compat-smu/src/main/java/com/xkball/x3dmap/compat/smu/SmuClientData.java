package com.xkball.x3dmap.compat.smu;

import com.mojang.logging.LogUtils;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.teacon.exhibition_portal.client.EPClient;
import org.teacon.exhibition_portal.client.framework.binding.LayoutParameter;
import org.teacon.exhibition_portal.components.ExhibitionMetadata;
import org.teacon.exhibition_portal.components.ExhibitionWaypoint;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;

@NonNullByDefault
public final class SmuClientData {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final @Nullable MethodHandle GET_LAYOUT_VALUE = createLayoutValueGetter();
    private static boolean readFailed;

    private SmuClientData() {
    }

    public static List<ExhibitionMetadata> labels() {
        var getter = GET_LAYOUT_VALUE;
        if (getter == null || readFailed) {
            return List.of();
        }
        try {
            var lookup = read(getter, EPClient.GALLERY_LOOKUP);
            var order = read(getter, EPClient.GALLERIES);
            var result = new ArrayList<ExhibitionMetadata>();
            for (var id : order) {
                var exhibition = lookup.get(id);
                if (exhibition != null) {
                    var metadata = exhibition.metadata();
                    if (!ExhibitionWaypoint.EMPTY.equals(metadata.waypoint())) {
                        result.add(metadata);
                    }
                }
            }
            return result;
        } catch (Exception e) {
            readFailed = true;
            LOGGER.error("Failed to read Sign Me Up exhibition data", e);
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T read(MethodHandle getter, LayoutParameter<T> parameter) {
        try {
            return (T) getter.invokeExact(parameter);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw new IllegalStateException("Failed to invoke Sign Me Up layout value accessor", e);
        }
    }

    private static @Nullable MethodHandle createLayoutValueGetter() {
        try {
            var lookup = MethodHandles.privateLookupIn(LayoutParameter.class, MethodHandles.lookup());
            return lookup.findVirtual(LayoutParameter.class, "getValue", MethodType.methodType(Object.class));
        } catch (ReflectiveOperationException e) {
            LOGGER.error("Failed to initialize Sign Me Up client data access", e);
            return null;
        }
    }
}
