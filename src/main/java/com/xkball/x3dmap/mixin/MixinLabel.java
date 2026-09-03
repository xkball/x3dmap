package com.xkball.x3dmap.mixin;

import com.xkball.xklib.api.gui.widget.IGuiWidget;
import com.xkball.xklib.ui.widget.Label;
import com.xkball.xklib.ui.widget.Widget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Label.class)
public class MixinLabel extends Widget {

    @Inject(method = "createTooltip",at = @At("RETURN"),cancellable = true)
    public void onCreateTooltip(int mouseX, int mouseY, CallbackInfoReturnable<IGuiWidget> cir){
        var result = cir.getReturnValue();
        if(result == null){
            cir.setReturnValue(super.createTooltip(mouseX, mouseY));
        }
    }
}
