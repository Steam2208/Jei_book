package com.recipebookjei.mixin;

import com.recipebookjei.gui.ICustomRecipeButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.recipebook.RecipeButton;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collections;
import java.util.List;

@Mixin(RecipeButton.class)
public abstract class RecipeButtonMixin implements ICustomRecipeButton {
    private static final ResourceLocation SLOT_SPRITE = ResourceLocation.withDefaultNamespace("recipe_book/slot_craftable");

    @Shadow private RecipeCollection collection;

    @Unique
    private ItemStack recipebookjei$customItem = ItemStack.EMPTY;

    @Override
    public void recipebookjei$setItem(ItemStack stack) {
        this.recipebookjei$customItem = (stack == null) ? ItemStack.EMPTY : stack;
    }

    @Override
    public ItemStack recipebookjei$getItem() {
        return this.recipebookjei$customItem;
    }

    @Inject(method = "renderWidget", at = @At("HEAD"), cancellable = true)
    private void onRenderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        // ALWAYS cancel if this.collection is null, so vanilla line 70 (hasCraftable) never crashes
        if (this.collection == null) {
            ci.cancel();
            if (this.recipebookjei$customItem != null && !this.recipebookjei$customItem.isEmpty()) {
                try {
                    RecipeButton btn = (RecipeButton) (Object) this;
                    graphics.blitSprite(SLOT_SPRITE, btn.getX(), btn.getY(), btn.getWidth(), btn.getHeight());
                    graphics.renderFakeItem(this.recipebookjei$customItem, btn.getX() + 4, btn.getY() + 4);
                    graphics.renderItemDecorations(Minecraft.getInstance().font, this.recipebookjei$customItem, btn.getX() + 4, btn.getY() + 4);
                } catch (Throwable ignored) {}
            }
        }
    }

    @Inject(method = "getTooltipText", at = @At("HEAD"), cancellable = true)
    private void onGetTooltip(CallbackInfoReturnable<List<Component>> cir) {
        if (this.recipebookjei$customItem != null && !this.recipebookjei$customItem.isEmpty()) {
            try {
                cir.setReturnValue(Screen.getTooltipFromItem(Minecraft.getInstance(), this.recipebookjei$customItem));
            } catch (Throwable t) {
                cir.setReturnValue(Collections.emptyList());
            }
        } else if (this.collection == null) {
            cir.setReturnValue(Collections.emptyList());
        }
    }

    @Inject(method = "updateWidgetNarration", at = @At("HEAD"), cancellable = true)
    private void onUpdateWidgetNarration(NarrationElementOutput output, CallbackInfo ci) {
        ci.cancel();
        if (this.recipebookjei$customItem != null && !this.recipebookjei$customItem.isEmpty()) {
            try {
                output.add(NarratedElementType.TITLE, Component.translatable("narration.recipe", this.recipebookjei$customItem.getHoverName()));
            } catch (Throwable ignored) {}
        }
    }

    @Inject(method = "getOrderedRecipes", at = @At("HEAD"), cancellable = true)
    private void onGetOrderedRecipes(CallbackInfoReturnable<List<RecipeHolder<?>>> cir) {
        if (this.collection == null) {
            cir.setReturnValue(Collections.emptyList());
        }
    }

    @Inject(method = "getRecipe", at = @At("HEAD"), cancellable = true)
    private void onGetRecipe(CallbackInfoReturnable<RecipeHolder<?>> cir) {
        if (this.collection == null) {
            cir.setReturnValue(null);
        }
    }

    @Inject(method = "isOnlyOption", at = @At("HEAD"), cancellable = true)
    private void onIsOnlyOption(CallbackInfoReturnable<Boolean> cir) {
        if (this.collection == null) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "isValidClickButton", at = @At("HEAD"), cancellable = true)
    private void onIsValidClickButton(int button, CallbackInfoReturnable<Boolean> cir) {
        if (!this.recipebookjei$customItem.isEmpty()) {
            cir.setReturnValue(button == 0 || button == 1);
        }
    }
}
