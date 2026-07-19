package com.xkball.x3dmap.ui.widget;

import com.xkball.xklib.api.gui.input.IMouseButtonEvent;
import com.xkball.xklib.api.gui.widget.IInputWidget;
import com.xkball.xklib.api.gui.widget.ILayoutVariable;
import com.xkball.xklib.resource.ResourceLocation;
import com.xkball.xklib.ui.css.property.value.CssLengthUnit;
import com.xkball.xklib.ui.render.IGUIGraphics;
import com.xkball.xklib.ui.widget.Widget;
import com.xkball.xklibmc.annotation.NonNullByDefault;

import java.util.ArrayList;
import java.util.List;

@NonNullByDefault
public class IntSliderWidget extends Widget implements IInputWidget<Integer> {
    
    private static final ResourceLocation TRACK = new ResourceLocation("minecraft", "widget/slider");
    public static final ResourceLocation HANDLE = new ResourceLocation("minecraft", "widget/slider_handle");
    private final int min;
    private final int max;
    private final List<ILayoutVariable<Integer>> bindings = new ArrayList<>();
    private int value;
    private boolean dragging;
    
    public IntSliderWidget(int min, int max, int value) {
        this.min = min;
        this.max = max;
        this.value = Math.clamp(value, min, max);
    }
    
    @Override
    public Integer getValue() {
        return this.value;
    }
    
    @Override
    public void setValue(Integer value) {
        var next = Math.clamp(value, this.min, this.max);
        if (this.value == next) return;
        this.value = next;
        for (var binding : this.bindings) {
            binding.set(next);
        }
    }
    
    @Override
    public IntSliderWidget bind(ILayoutVariable<Integer> variable) {
        this.setValue(variable.get());
        this.bindings.add(variable);
        variable.addCallback(this::setValueFromBinding);
        return this;
    }
    
    @Override
    protected boolean onMouseClicked(IMouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0) return false;
        this.dragging = true;
        this.updateFromMouse((float) event.x());
        return true;
    }
    
    @Override
    protected boolean onMouseDragged(IMouseButtonEvent event, double dx, double dy) {
        if (event.button() != 0 || !this.dragging) return false;
        this.updateFromMouse((float) (event.x() + dx));
        return true;
    }
    
    @Override
    protected boolean onMouseReleased(IMouseButtonEvent event) {
        if (event.button() != 0) return false;
        this.dragging = false;
        return true;
    }
    
    @Override
    public void onFocusChanged(boolean focused) {
        if (!focused) this.dragging = false;
    }
    
    @Override
    public boolean isFocusable() {
        return true;
    }
    
    @Override
    public void doRender(IGUIGraphics graphics, int mouseX, int mouseY, float a) {
        super.doRender(graphics, mouseX, mouseY, a);
        var padding = CssLengthUnit.rpxScaleWorkaround;
        graphics.blitSprite(TRACK, this.x, this.y, this.width, this.height, -1);
        var ratio = (this.value - this.min) / (float) (this.max - this.min);
        var handleWidth = 6 * padding;
        var handleX = this.x + ratio * (this.width - handleWidth);
        graphics.blitSprite(HANDLE, (int) handleX, (int) this.y, handleWidth, (int) this.height, -1);
        var textHeight = Math.min(8 * padding, this.height - 2 * padding);
        var textY = this.y + (this.height - textHeight) / 2 + 1;
        graphics.drawCenteredString(String.valueOf(this.value), this.x + this.width / 2, textY, -1, true, textHeight);
    }
    
    private void updateFromMouse(float mouseX) {
        var ratio = Math.clamp((mouseX - this.x) / Math.max(1, this.width), 0, 1);
        this.setValue(Math.round(this.min + ratio * (this.max - this.min)));
    }
    
    private void setValueFromBinding(int value) {
        this.value = Math.clamp(value, this.min, this.max);
    }
}
