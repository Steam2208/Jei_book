package com.recipebookjei.gui;

import com.recipebookjei.jei.RecipeBookJeiPlugin;
import net.minecraft.client.RecipeBookCategories;
import net.minecraft.client.gui.screens.recipebook.RecipeBookTabButton;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

public class CreativeTabBookButton extends RecipeBookTabButton {
    private final CreativeModeTab creativeTab;
    private final ItemStack iconStack;
    private final Component tabTooltip;
    private final boolean isBookmarks;

    public CreativeTabBookButton(CreativeModeTab creativeTab) {
        super(RecipeBookCategories.CRAFTING_SEARCH);
        this.creativeTab = creativeTab;
        this.isBookmarks = false;

        if (creativeTab != null) {
            ItemStack icon = creativeTab.getIconItem();
            if (icon == null || icon.isEmpty()) {
                if (!creativeTab.getDisplayItems().isEmpty()) {
                    icon = creativeTab.getDisplayItems().iterator().next();
                }
            }
            if (icon == null || icon.isEmpty()) {
                icon = new ItemStack(Items.CHEST);
            }
            this.iconStack = icon;
            this.tabTooltip = creativeTab.getDisplayName();
        } else {
            this.iconStack = new ItemStack(Items.COMPASS);
            this.tabTooltip = Component.translatable("gui.recipebook.search_hint");
        }
    }

    public static CreativeTabBookButton createBookmarksTab() {
        return new CreativeTabBookButton(true);
    }

    private CreativeTabBookButton(boolean isBookmarks) {
        super(RecipeBookCategories.CRAFTING_SEARCH);
        this.creativeTab = null;
        this.isBookmarks = true;
        this.iconStack = new ItemStack(Items.ENCHANTED_BOOK);
        this.tabTooltip = Component.translatable("jei.tooltip.bookmarks");
    }

    public boolean isBookmarks() {
        return this.isBookmarks;
    }

    public boolean isSearch() {
        return this.creativeTab == null && !this.isBookmarks;
    }

    public CreativeModeTab getCreativeTab() {
        return this.creativeTab;
    }

    public ItemStack getIconStack() {
        return this.iconStack;
    }

    public Component getTabTooltip() {
        return this.tabTooltip;
    }

    public boolean hasMatchingItems(String modFilter, String itemFilter) {
        return hasMatchingItems(modFilter, itemFilter, null);
    }

    public boolean hasMatchingItems(String modFilter, String itemFilter, Predicate<ItemStack> extraFilter) {
        if (this.isSearch()) {
            return true; // Search tab always visible
        }

        if (this.isBookmarks()) {
            List<ItemStack> bookmarks = RecipeBookJeiPlugin.getBookmarkedItems();
            for (ItemStack stack : bookmarks) {
                if (matchesStack(stack, modFilter, itemFilter) && (extraFilter == null || extraFilter.test(stack))) {
                    return true;
                }
            }
            return false;
        }

        if (this.creativeTab == null) return false;

        // If searching @mod without item query (and no craftability filter), check tab registry namespace / title first
        if (extraFilter == null && !modFilter.isEmpty() && itemFilter.isEmpty()) {
            ResourceLocation key = BuiltInRegistries.CREATIVE_MODE_TAB.getKey(this.creativeTab);
            if (key != null) {
                if (key.getNamespace().toLowerCase(Locale.ROOT).contains(modFilter) ||
                    key.getPath().toLowerCase(Locale.ROOT).contains(modFilter)) {
                    return true;
                }
            }
            String displayName = this.creativeTab.getDisplayName().getString().toLowerCase(Locale.ROOT);
            if (displayName.contains(modFilter)) {
                return true;
            }
        }

        // Check if any item in the tab matches
        try {
            for (ItemStack stack : this.creativeTab.getDisplayItems()) {
                if (matchesStack(stack, modFilter, itemFilter) && (extraFilter == null || extraFilter.test(stack))) {
                    return true;
                }
            }
        } catch (Throwable ignored) {}

        return false;
    }

    public static boolean matchesStack(ItemStack stack, String modFilter, String itemFilter) {
        if (stack == null || stack.isEmpty()) return false;
        try {
            Item item = stack.getItem();
            ResourceLocation key = BuiltInRegistries.ITEM.getKey(item);

            // Check mod filter (@mod)
            if (!modFilter.isEmpty()) {
                if (key == null || !key.getNamespace().toLowerCase(Locale.ROOT).contains(modFilter)) {
                    return false;
                }
            }

            // Check item text filter
            if (!itemFilter.isEmpty()) {
                String name = stack.getHoverName().getString().toLowerCase(Locale.ROOT);
                if (name.contains(itemFilter)) {
                    return true;
                }
                if (key != null && (key.getPath().toLowerCase(Locale.ROOT).contains(itemFilter) ||
                                    key.getNamespace().toLowerCase(Locale.ROOT).contains(itemFilter))) {
                    return true;
                }
                return false;
            }

            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
