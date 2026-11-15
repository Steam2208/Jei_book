package com.recipebookjei.jei;

import com.recipebookjei.RecipeBookJeiMod;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.common.Internal;
import mezz.jei.gui.bookmarks.BookmarkList;
import mezz.jei.gui.overlay.elements.IElement;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@JeiPlugin
public class RecipeBookJeiPlugin implements IModPlugin {
    public static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath("recipebookjei", "jei_plugin");
    public static IJeiRuntime runtime;
    public static Runnable onBookmarksChanged = null;

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
        RecipeBookJeiMod.LOGGER.info("JEI runtime available registered!");

        try {
            if (jeiRuntime.getBookmarkManager() instanceof BookmarkList bookmarkList) {
                bookmarkList.addSourceListChangedListener(() -> {
                    if (onBookmarksChanged != null) {
                        try {
                            Minecraft.getInstance().execute(onBookmarksChanged);
                        } catch (Throwable ignored) {}
                    }
                });
                RecipeBookJeiMod.LOGGER.info("Registered BookmarkList change listener!");
            }
        } catch (Throwable t) {
            RecipeBookJeiMod.LOGGER.warn("Failed to attach BookmarkList listener", t);
        }
    }

    @Override
    public void onRuntimeUnavailable() {
        runtime = null;
    }

    public static IJeiRuntime getRuntime() {
        if (runtime != null) {
            return runtime;
        }
        try {
            return Internal.getJeiRuntime();
        } catch (Throwable t) {
            return null;
        }
    }

    public static List<ItemStack> getBookmarkedItems() {
        IJeiRuntime r = getRuntime();
        if (r == null) return Collections.emptyList();
        try {
            if (r.getBookmarkManager() instanceof BookmarkList bookmarkList) {
                List<ItemStack> list = new ArrayList<>();
                for (IElement<?> element : bookmarkList.getElements()) {
                    if (element != null && element.isVisible()) {
                        ITypedIngredient<?> typed = element.getTypedIngredient();
                        if (typed != null) {
                            typed.getItemStack().ifPresent(stack -> {
                                if (!stack.isEmpty()) {
                                    list.add(stack);
                                }
                            });
                        }
                    }
                }
                return list;
            }
        } catch (Throwable t) {
            RecipeBookJeiMod.LOGGER.error("Failed to get bookmarked items", t);
        }
        return Collections.emptyList();
    }

    public static boolean toggleBookmark(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        IJeiRuntime r = getRuntime();
        if (r != null) {
            try {
                var typedOpt = r.getIngredientManager().createTypedIngredient(stack);
                if (typedOpt.isPresent()) {
                    var typed = typedOpt.get();
                    if (r.getBookmarkManager().contains(typed)) {
                        r.getBookmarkManager().remove(typed);
                        RecipeBookJeiMod.LOGGER.info("Removed bookmark for item: {}", stack.getHoverName().getString());
                        return false; // now removed
                    } else {
                        r.getBookmarkManager().add(typed);
                        RecipeBookJeiMod.LOGGER.info("Added bookmark for item: {}", stack.getHoverName().getString());
                        return true; // now added
                    }
                }
            } catch (Throwable t) {
                RecipeBookJeiMod.LOGGER.error("Failed to toggle bookmark for {}", stack, t);
            }
        }
        return false;
    }

    public static boolean removeBookmark(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        IJeiRuntime r = getRuntime();
        if (r != null) {
            try {
                var typedOpt = r.getIngredientManager().createTypedIngredient(stack);
                if (typedOpt.isPresent()) {
                    boolean removed = r.getBookmarkManager().remove(typedOpt.get());
                    RecipeBookJeiMod.LOGGER.info("Removed bookmark explicitly for item: {}", stack.getHoverName().getString());
                    return removed;
                }
            } catch (Throwable t) {
                RecipeBookJeiMod.LOGGER.error("Failed to remove bookmark for {}", stack, t);
            }
        }
        return false;
    }

    public static boolean isBookmarked(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        IJeiRuntime r = getRuntime();
        if (r != null) {
            try {
                var typedOpt = r.getIngredientManager().createTypedIngredient(stack);
                if (typedOpt.isPresent()) {
                    return r.getBookmarkManager().contains(typedOpt.get());
                }
            } catch (Throwable ignored) {}
        }
        return false;
    }

    public static void showRecipes(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        IJeiRuntime r = getRuntime();
        if (r != null) {
            try {
                RecipeBookJeiMod.LOGGER.info("Opening JEI recipes for item: {}", stack.getHoverName().getString());
                IFocus<ItemStack> outputFocus = r.getJeiHelpers().getFocusFactory().createFocus(
                    RecipeIngredientRole.OUTPUT,
                    VanillaTypes.ITEM_STACK,
                    stack
                );

                boolean hasOutput = false;
                try {
                    hasOutput = r.getRecipeManager().createRecipeCategoryLookup()
                        .limitFocus(List.of(outputFocus))
                        .get().findAny().isPresent();
                } catch (Throwable t) {
                    hasOutput = true;
                }

                if (hasOutput) {
                    r.getRecipesGui().show(List.of(outputFocus));
                } else {
                    showUses(stack);
                }
            } catch (Throwable t) {
                RecipeBookJeiMod.LOGGER.error("Failed to show JEI recipes for {}", stack, t);
            }
        } else {
            RecipeBookJeiMod.LOGGER.warn("JEI runtime is not available when showRecipes was called!");
        }
    }

    public static void showUses(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        IJeiRuntime r = getRuntime();
        if (r != null) {
            try {
                RecipeBookJeiMod.LOGGER.info("Opening JEI uses for item: {}", stack.getHoverName().getString());
                IFocus<ItemStack> focus = r.getJeiHelpers().getFocusFactory().createFocus(
                    RecipeIngredientRole.INPUT,
                    VanillaTypes.ITEM_STACK,
                    stack
                );
                r.getRecipesGui().show(List.of(focus));
            } catch (Throwable t) {
                RecipeBookJeiMod.LOGGER.error("Failed to show JEI uses for {}", stack, t);
            }
        } else {
            RecipeBookJeiMod.LOGGER.warn("JEI runtime is not available when showUses was called!");
        }
    }

    public static void addBookmark(ItemStack stack) {
        toggleBookmark(stack);
    }
}
