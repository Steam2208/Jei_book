package com.recipebookjei.mixin;

import com.recipebookjei.gui.CreativeTabBookButton;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.recipebook.RecipeBookTabButton;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RecipeBookTabButton.class)
public class RecipeBookTabButtonMixin {
    private static final ResourceLocation TAB_SPRITE = ResourceLocation.withDefaultNamespace("recipe_book/tab");
    private static final ResourceLocation TAB_SELECTED_SPRITE = ResourceLocation.withDefaultNamespace("recipe_book/tab_selected");

    @Inject(method = "renderWidget", at = @At("HEAD"), cancellable = true)
    private void onRenderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        if ((Object) this instanceof CreativeTabBookButton btn) {
            ci.cancel();
            try {
                ResourceLocation sprite = btn.isStateTriggered() ? TAB_SELECTED_SPRITE : TAB_SPRITE;
                graphics.blitSprite(sprite, btn.getX(), btn.getY(), btn.getWidth(), btn.getHeight());
                int offset = btn.isStateTriggered() ? -2 : 0;
                if (btn.getIconStack() != null && !btn.getIconStack().isEmpty()) {
                    graphics.renderFakeItem(btn.getIconStack(), btn.getX() + 9 + offset, btn.getY() + 5);
                }
            } catch (Throwable ignored) {}
        }
    }
}
