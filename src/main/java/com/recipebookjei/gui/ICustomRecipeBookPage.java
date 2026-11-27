package com.recipebookjei.gui;

import net.minecraft.client.gui.screens.recipebook.RecipeButton;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.world.item.ItemStack;
import java.util.List;

public interface ICustomRecipeBookPage {
    void recipebookjei$setCustomItems(List<ItemStack> items, boolean resetPage);
    List<ItemStack> recipebookjei$getCustomItems();
    void recipebookjei$updateCustomButtons();
    void recipebookjei$scrollPage(int delta);
    List<RecipeButton> recipebookjei$getButtons();
    ItemStack recipebookjei$getLastClickedItem();
    int recipebookjei$getLastClickedMouseButton();
    RecipeButton recipebookjei$getLastClickedButtonWidget();
    void recipebookjei$openOverlay(RecipeCollection collection, RecipeButton button, int bookX, int bookY);
}

