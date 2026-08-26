package com.xkball.x3dmap.config;

import com.mojang.logging.LogUtils;
import com.xkball.xklibmc.annotation.NonNullByDefault;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@NonNullByDefault
public record DimensionMapConfig(Identifier dimensionId, boolean enabled, boolean overrideSeaLevel, int seaLevel) {

    private static final Logger LOGGER = LogUtils.getLogger();

    public int resolveSeaLevel(int defaultSeaLevel) {
        return this.overrideSeaLevel ? this.seaLevel : defaultSeaLevel;
    }

    public static boolean isValidEntry(Object value) {
        return parseEntry(value) != null;
    }

    public static Map<Identifier, DimensionMapConfig> parse(List<? extends List<?>> values, String source) {
        var result = new LinkedHashMap<Identifier, DimensionMapConfig>();
        for (var value : values) {
            var entry = parseEntry(value);
            if (entry == null) {
                LOGGER.warn("Ignoring invalid dimension map config entry in {}: {}", source, value);
                continue;
            }
            if (result.put(entry.dimensionId(), entry) != null) {
                LOGGER.warn("Duplicate dimension map config for {} in {}; the last entry wins", entry.dimensionId(), source);
            }
        }
        return Map.copyOf(result);
    }

    private static @Nullable DimensionMapConfig parseEntry(Object value) {
        if (!(value instanceof List<?> entry) || entry.size() != 4) {
            return null;
        }
        if (!(entry.get(0) instanceof String dimensionValue)
                || !(entry.get(1) instanceof Boolean enabled)
                || !(entry.get(2) instanceof Boolean overrideSeaLevel)
                || !(entry.get(3) instanceof Number seaLevelValue)) {
            return null;
        }
        var dimensionId = Identifier.tryParse(dimensionValue);
        var seaLevel = seaLevelValue.longValue();
        if (dimensionId == null || seaLevel < Integer.MIN_VALUE || seaLevel > Integer.MAX_VALUE) {
            return null;
        }
        return new DimensionMapConfig(dimensionId, enabled, overrideSeaLevel, (int) seaLevel);
    }
}
