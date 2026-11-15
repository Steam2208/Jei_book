package com.recipebookjei.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "mezz.jei.common.config.ClientToggleState", remap = false)
public class JeiToggleStateMixin {
    @Shadow private boolean overlayEnabled;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onInit(CallbackInfo ci) {
        // Automatically hide the ingredient list overlay so it doesn't clutter the screen by default
        this.overlayEnabled = false;
    }
}

