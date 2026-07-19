package com.xkball.x3dmap.api.client.gui;

import com.xkball.xklib.ui.css.property.value.CssLengthUnit;
import com.xkball.xklib.ui.render.IComponent;
import com.xkball.xklibmc.annotation.NonNullByDefault;

@NonNullByDefault
public record MapWindowSpec(
        IComponent title,
        boolean resizable,
        boolean blocking,
        float x,
        float y,
        CssLengthUnit width,
        CssLengthUnit height,
        boolean autoShrinkHeight
) {

    public static MapWindowSpec regular(IComponent title, boolean resizable, CssLengthUnit width, CssLengthUnit height) {
        return new MapWindowSpec(title, resizable, false, Float.NaN, Float.NaN, width, height, true);
    }

    public static MapWindowSpec regular(IComponent title, boolean resizable, float x, float y, CssLengthUnit width, CssLengthUnit height) {
        return new MapWindowSpec(title, resizable, false, x, y, width, height, true);
    }

    public static MapWindowSpec blocking(IComponent title, boolean resizable, CssLengthUnit width, CssLengthUnit height) {
        return new MapWindowSpec(title, resizable, true, Float.NaN, Float.NaN, width, height, true);
    }

    public static MapWindowSpec blocking(IComponent title, boolean resizable, float x, float y, CssLengthUnit width, CssLengthUnit height) {
        return new MapWindowSpec(title, resizable, true, x, y, width, height, true);
    }
}
