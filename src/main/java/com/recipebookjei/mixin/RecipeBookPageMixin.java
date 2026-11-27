package com.recipebookjei.mixin;

import com.recipebookjei.RecipeBookJeiMod;
import com.recipebookjei.gui.ICustomRecipeBookPage;
import com.recipebookjei.gui.ICustomRecipeButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.StateSwitchingButton;
import net.minecraft.client.gui.screens.recipebook.OverlayRecipeComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookPage;
import net.minecraft.client.gui.screens.recipebook.RecipeButton;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collections;
import java.util.List;

@Mixin(RecipeBookPage.class)
public class RecipeBookPageMixin implements ICustomRecipeBookPage {
    @Shadow @Final private List<RecipeButton> buttons;
    @Shadow @Final private OverlayRecipeComponent overlay;
    @Shadow private StateSwitchingButton forwardButton;
    @Shadow private StateSwitchingButton backButton;
    @Shadow private int totalPages;
    @Shadow private int currentPage;
    @Shadow private RecipeHolder<?> lastClickedRecipe;
    @Shadow private RecipeCollection lastClickedRecipeCollection;

    @Unique
    private List<ItemStack> recipebookjei$customItems = Collections.emptyList();
    @Unique
    private ItemStack recipebookjei$lastClickedItem = ItemStack.EMPTY;
    @Unique
    private int recipebookjei$lastClickedMouseButton = -1;
    @Unique
    private RecipeButton recipebookjei$lastClickedButtonWidget = null;

    @Override
    public void recipebookjei$setCustomItems(List<ItemStack> items, boolean resetPage) {
        this.recipebookjei$customItems = (items == null) ? Collections.emptyList() : items;
        this.overlay.setVisible(false);
        this.totalPages = (int) Math.ceil((double) this.recipebookjei$customItems.size() / 20.0);
        if (resetPage || this.currentPage >= this.totalPages) {
            this.currentPage = 0;
        }
        recipebookjei$updateCustomButtons();
    }

    @Override
    public List<ItemStack> recipebookjei$getCustomItems() {
        return this.recipebookjei$customItems;
    }

    @Override
    public List<RecipeButton> recipebookjei$getButtons() {
        return this.buttons;
    }

    @Override
    public ItemStack recipebookjei$getLastClickedItem() {
        return this.recipebookjei$lastClickedItem;
    }

    @Override
    public int recipebookjei$getLastClickedMouseButton() {
        return this.recipebookjei$lastClickedMouseButton;
    }

    @Override
    public RecipeButton recipebookjei$getLastClickedButtonWidget() {
        return this.recipebookjei$lastClickedButtonWidget;
    }

    @Override
    public void recipebookjei$openOverlay(RecipeCollection collection, RecipeButton button, int bookX, int bookY) {
        Minecraft mc = Minecraft.getInstance();
        this.overlay.init(
            mc,
            collection,
            button.getX(),
            button.getY(),
            bookX + 147 / 2,
            bookY + 13 + 166 / 2,
            (float) button.getWidth()
        );
    }

    @Override
    public void recipebookjei$updateCustomButtons() {
        int start = this.currentPage * 20;
        for (int i = 0; i < this.buttons.size(); i++) {
            RecipeButton button = this.buttons.get(i);
            int itemIndex = start + i;
            if (itemIndex < this.recipebookjei$customItems.size()) {
                ItemStack stack = this.recipebookjei$customItems.get(itemIndex);
                ((ICustomRecipeButton) button).recipebookjei$setItem(stack);
                button.visible = true;
                button.active = true;
            } else {
                ((ICustomRecipeButton) button).recipebookjei$setItem(ItemStack.EMPTY);
                button.visible = false;
                button.active = false;
            }
        }
        this.forwardButton.visible = (this.totalPages > 1 && this.currentPage < this.totalPages - 1);
        this.forwardButton.active = this.forwardButton.visible;
        this.backButton.visible = (this.totalPages > 1 && this.currentPage > 0);
        this.backButton.active = this.backButton.visible;
    }

    @Override
    public void recipebookjei$scrollPage(int delta) {
        if (this.totalPages <= 1) return;
        int newPage = this.currentPage + delta;
        if (newPage >= 0 && newPage < this.totalPages) {
            this.currentPage = newPage;
            this.overlay.setVisible(false);
            recipebookjei$updateCustomButtons();
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void onMouseClicked(double mouseX, double mouseY, int button, int x, int y, int width, int height, CallbackInfoReturnable<Boolean> cir) {
        this.lastClickedRecipe = null;
        this.lastClickedRecipeCollection = null;
        this.recipebookjei$lastClickedItem = ItemStack.EMPTY;
        this.recipebookjei$lastClickedMouseButton = -1;
        this.recipebookjei$lastClickedButtonWidget = null;

        if (this.overlay.isVisible()) {
            if (this.overlay.mouseClicked(mouseX, mouseY, button)) {
                this.lastClickedRecipe = this.overlay.getLastRecipeClicked();
                this.lastClickedRecipeCollection = this.overlay.getRecipeCollection();
            }
            this.overlay.setVisible(false);
            cir.setReturnValue(true);
            return;
        }

        if (!this.recipebookjei$customItems.isEmpty()) {
            if (this.forwardButton.visible && this.forwardButton.mouseClicked(mouseX, mouseY, button)) {
                this.currentPage++;
                recipebookjei$updateCustomButtons();
                cir.setReturnValue(true);
                return;
            }
            if (this.backButton.visible && this.backButton.mouseClicked(mouseX, mouseY, button)) {
                this.currentPage--;
                recipebookjei$updateCustomButtons();
                cir.setReturnValue(true);
                return;
            }
            if (button == 0 || button == 1) {
                for (RecipeButton btn : this.buttons) {
                    if (btn.visible && mouseX >= btn.getX() && mouseX < btn.getX() + btn.getWidth() &&
                                       mouseY >= btn.getY() && mouseY < btn.getY() + btn.getHeight()) {
                        ItemStack stack = ((ICustomRecipeButton) btn).recipebookjei$getItem();
                        if (stack != null && !stack.isEmpty()) {
                            RecipeBookJeiMod.LOGGER.info("Item button clicked: {}, mouse button: {}", stack.getHoverName().getString(), button);
                            this.recipebookjei$lastClickedItem = stack;
                            this.recipebookjei$lastClickedMouseButton = button;
                            this.recipebookjei$lastClickedButtonWidget = btn;
                            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                            cir.setReturnValue(true);
                            return;
                        }
                    }
                }
            }
        }

        cir.setReturnValue(false);
    }
}
