package com.recipebookjei.jei;

import com.mojang.blaze3d.platform.InputConstants;
import com.recipebookjei.RecipeBookJeiMod;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferManager;
import mezz.jei.api.runtime.IBookmarkOverlay;
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.common.Internal;
import mezz.jei.gui.bookmarks.BookmarkFactory;
import mezz.jei.gui.bookmarks.BookmarkList;
import mezz.jei.gui.bookmarks.IBookmark;
import mezz.jei.gui.overlay.bookmarks.BookmarkOverlay;
import mezz.jei.gui.overlay.elements.IElement;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.inventory.RecipeBookType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

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
            BookmarkList bookmarkList = getBookmarkList(jeiRuntime);
            if (bookmarkList != null) {
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

    private static BookmarkList getBookmarkList(IJeiRuntime r) {
        if (r == null) return null;
        try {
            IBookmarkOverlay overlay = r.getBookmarkOverlay();
            if (overlay instanceof BookmarkOverlay bo) {
                Field f = BookmarkOverlay.class.getDeclaredField("bookmarkList");
                f.setAccessible(true);
                if (f.get(bo) instanceof BookmarkList bl) {
                    return bl;
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static IBookmark createBookmark(BookmarkList bookmarkList, ITypedIngredient<ItemStack> typed) {
        if (bookmarkList == null || typed == null) return null;
        try {
            Field f = BookmarkList.class.getDeclaredField("bookmarkFactory");
            f.setAccessible(true);
            if (f.get(bookmarkList) instanceof BookmarkFactory factory) {
                return factory.create(typed);
            }
        } catch (Throwable ignored) {}
        return null;
    }

    public static List<ItemStack> getBookmarkedItems() {
        IJeiRuntime r = getRuntime();
        if (r == null) return Collections.emptyList();
        try {
            BookmarkList bookmarkList = getBookmarkList(r);
            if (bookmarkList != null) {
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
                BookmarkList bookmarkList = getBookmarkList(r);
                var typedOpt = r.getIngredientManager().createTypedIngredient(VanillaTypes.ITEM_STACK, stack);
                if (bookmarkList != null && typedOpt.isPresent()) {
                    IBookmark bookmark = createBookmark(bookmarkList, typedOpt.get());
                    if (bookmark != null) {
                        if (bookmarkList.contains(bookmark)) {
                            bookmarkList.remove(bookmark);
                            RecipeBookJeiMod.LOGGER.info("Removed bookmark for item: {}", stack.getHoverName().getString());
                            return false; // now removed
                        } else {
                            bookmarkList.add(bookmark);
                            RecipeBookJeiMod.LOGGER.info("Added bookmark for item: {}", stack.getHoverName().getString());
                            return true; // now added
                        }
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
                BookmarkList bookmarkList = getBookmarkList(r);
                var typedOpt = r.getIngredientManager().createTypedIngredient(VanillaTypes.ITEM_STACK, stack);
                if (bookmarkList != null && typedOpt.isPresent()) {
                    IBookmark bookmark = createBookmark(bookmarkList, typedOpt.get());
                    if (bookmark != null) {
                        boolean removed = bookmarkList.remove(bookmark);
                        RecipeBookJeiMod.LOGGER.info("Removed bookmark explicitly for item: {}", stack.getHoverName().getString());
                        return removed;
                    }
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
                BookmarkList bookmarkList = getBookmarkList(r);
                var typedOpt = r.getIngredientManager().createTypedIngredient(VanillaTypes.ITEM_STACK, stack);
                if (bookmarkList != null && typedOpt.isPresent()) {
                    IBookmark bookmark = createBookmark(bookmarkList, typedOpt.get());
                    if (bookmark != null) {
                        return bookmarkList.contains(bookmark);
                    }
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

    public static boolean isShowRecipeKey(int keyCode, int scanCode) {
        InputConstants.Key key = InputConstants.getKey(keyCode, scanCode);
        IJeiRuntime r = getRuntime();
        if (r != null) {
            try {
                return r.getKeyMappings().getShowRecipe().isActiveAndMatches(key);
            } catch (Throwable ignored) {}
        }
        return keyCode == GLFW.GLFW_KEY_R;
    }

    public static boolean isShowUsesKey(int keyCode, int scanCode) {
        InputConstants.Key key = InputConstants.getKey(keyCode, scanCode);
        IJeiRuntime r = getRuntime();
        if (r != null) {
            try {
                return r.getKeyMappings().getShowUses().isActiveAndMatches(key);
            } catch (Throwable ignored) {}
        }
        return keyCode == GLFW.GLFW_KEY_U;
    }

    public static boolean isBookmarkKey(int keyCode, int scanCode) {
        InputConstants.Key key = InputConstants.getKey(keyCode, scanCode);
        try {
            return Internal.getKeyMappings().getBookmark().isActiveAndMatches(key);
        } catch (Throwable ignored) {}
        return keyCode == GLFW.GLFW_KEY_A;
    }

    public enum TransferResult {
        TRANSFERRED,
        USER_ERROR,
        NO_SERVER,
        NO_RECIPE
    }

    public static boolean isJeiOnServer() {
        try {
            return Internal.getServerConnection().isJeiOnServer();
        } catch (Throwable t) {
            return false;
        }
    }

    public static TransferResult tryTransferRecipeForItem(ItemStack stack, RecipeBookMenu<?, ?> menu, Player player, boolean maxTransfer) {
        if (stack == null || stack.isEmpty() || menu == null || player == null) {
            return TransferResult.NO_RECIPE;
        }
        IJeiRuntime r = getRuntime();
        if (r == null) {
            RecipeBookJeiMod.LOGGER.warn("JEI runtime is null when transferring {}", stack.getHoverName().getString());
            return TransferResult.NO_RECIPE;
        }

        try {
            IFocus<ItemStack> outputFocus = r.getJeiHelpers().getFocusFactory().createFocus(
                RecipeIngredientRole.OUTPUT,
                VanillaTypes.ITEM_STACK,
                stack
            );
            IFocusGroup focusGroup = r.getJeiHelpers().getFocusFactory().createFocusGroup(List.of(outputFocus));
            IRecipeManager recipeManager = r.getRecipeManager();
            IRecipeTransferManager transferManager = r.getRecipeTransferManager();

            RecipeType<?> preferredType = getJeiRecipeType(menu.getRecipeBookType());
            List<IRecipeCategory<?>> categories = recipeManager.createRecipeCategoryLookup()
                .limitFocus(List.of(outputFocus))
                .get()
                .filter(cat -> transferManager.getRecipeTransferHandler(menu, cat).isPresent())
                .sorted((a, b) -> {
                    boolean aPref = a.getRecipeType().equals(preferredType);
                    boolean bPref = b.getRecipeType().equals(preferredType);
                    return Boolean.compare(bPref, aPref);
                })
                .toList();

            if (categories.isEmpty()) {
                RecipeBookJeiMod.LOGGER.info("No JEI category with transfer handler found for {} in {}", stack.getHoverName().getString(), menu.getClass().getSimpleName());
                return TransferResult.NO_RECIPE;
            }

            boolean sawNoServer = false;
            boolean sawUserError = false;
            for (IRecipeCategory<?> category : categories) {
                TransferResult res = tryTransferFromCategory(recipeManager, transferManager, category, outputFocus, focusGroup, menu, player, maxTransfer);
                if (res == TransferResult.TRANSFERRED) {
                    return TransferResult.TRANSFERRED;
                }
                if (res == TransferResult.NO_SERVER) {
                    sawNoServer = true;
                } else if (res == TransferResult.USER_ERROR) {
                    sawUserError = true;
                }
            }
            if (sawNoServer) return TransferResult.NO_SERVER;
            if (sawUserError) return TransferResult.USER_ERROR;
        } catch (Throwable t) {
            RecipeBookJeiMod.LOGGER.error("Failed to transfer JEI recipe for {}", stack, t);
        }
        return TransferResult.NO_RECIPE;
    }

    private static <T, C extends AbstractContainerMenu> TransferResult tryTransferFromCategory(
        IRecipeManager recipeManager,
        IRecipeTransferManager transferManager,
        IRecipeCategory<T> category,
        IFocus<ItemStack> outputFocus,
        IFocusGroup focusGroup,
        C menu,
        Player player,
        boolean maxTransfer
    ) {
        Optional<IRecipeTransferHandler<C, T>> handlerOpt = transferManager.getRecipeTransferHandler(menu, category);
        if (handlerOpt.isEmpty()) {
            return TransferResult.NO_RECIPE;
        }
        IRecipeTransferHandler<C, T> handler = handlerOpt.get();

        List<T> recipes = recipeManager.createRecipeLookup(category.getRecipeType())
            .limitFocus(List.of(outputFocus))
            .get()
            .toList();

        if (recipes.isEmpty()) {
            RecipeBookJeiMod.LOGGER.info("JEI lookup returned 0 recipes in {} for {}", category.getRecipeType().getUid(), stackNameForLog(outputFocus));
            return TransferResult.NO_RECIPE;
        }

        boolean sawNoServer = false;
        boolean sawUserError = false;

        for (T recipe : recipes) {
            try {
                Optional<IRecipeLayoutDrawable<T>> layoutOpt = recipeManager.createRecipeLayoutDrawable(category, recipe, focusGroup);
                if (layoutOpt.isEmpty()) continue;
                IRecipeLayoutDrawable<T> layout = layoutOpt.get();

                IRecipeTransferError error = handler.transferRecipe(
                    menu,
                    layout.getRecipe(),
                    layout.getRecipeSlotsView(),
                    player,
                    maxTransfer,
                    false
                );

                if (error == null || error.getType().allowsTransfer) {
                    IRecipeTransferError execError = handler.transferRecipe(
                        menu,
                        layout.getRecipe(),
                        layout.getRecipeSlotsView(),
                        player,
                        maxTransfer,
                        true
                    );
                    if (execError == null || execError.getType().allowsTransfer) {
                        return TransferResult.TRANSFERRED;
                    }
                    error = execError;
                }

                String tooltipText = formatTransferTooltip(error);
                RecipeBookJeiMod.LOGGER.info(
                    "JEI transfer declined for {} ({} / {}): type={}, tooltip=[{}]",
                    stackNameForLog(outputFocus),
                    category.getRecipeType().getUid(),
                    handler.getClass().getSimpleName(),
                    error.getType(),
                    tooltipText
                );
                if (isNoServerError(error)) {
                    sawNoServer = true;
                } else if (error.getType() == IRecipeTransferError.Type.USER_FACING) {
                    sawUserError = true;
                }
            } catch (Throwable t) {
                RecipeBookJeiMod.LOGGER.warn("Exception during JEI transfer in category {}", category.getRecipeType().getUid(), t);
            }
        }

        if (sawNoServer) return TransferResult.NO_SERVER;
        if (sawUserError) return TransferResult.USER_ERROR;
        return TransferResult.NO_RECIPE;
    }

    @SuppressWarnings("unchecked")
    public static TransferResult tryTransferRecipeHolder(RecipeHolder<?> holder, RecipeBookMenu<?, ?> menu, Player player, boolean maxTransfer) {
        if (holder == null || menu == null || player == null) {
            return TransferResult.NO_RECIPE;
        }
        IJeiRuntime r = getRuntime();
        if (r == null) {
            return TransferResult.NO_RECIPE;
        }

        try {
            IRecipeManager recipeManager = r.getRecipeManager();
            IRecipeTransferManager transferManager = r.getRecipeTransferManager();
            RecipeType<?> jeiType = getJeiRecipeType(menu.getRecipeBookType());
            if (jeiType == null) return TransferResult.NO_RECIPE;

            IRecipeCategory<Object> category = (IRecipeCategory<Object>) recipeManager.getRecipeCategory(jeiType);
            if (category == null) {
                return TransferResult.NO_RECIPE;
            }
            Optional<IRecipeTransferHandler<RecipeBookMenu<?, ?>, Object>> handlerOpt = transferManager.getRecipeTransferHandler(menu, category);
            if (handlerOpt.isEmpty()) {
                return TransferResult.NO_RECIPE;
            }
            IRecipeTransferHandler<RecipeBookMenu<?, ?>, Object> handler = handlerOpt.get();

            IFocusGroup emptyFocus = r.getJeiHelpers().getFocusFactory().getEmptyFocusGroup();
            Optional<IRecipeLayoutDrawable<Object>> layoutOpt = recipeManager.createRecipeLayoutDrawable(category, holder, emptyFocus);
            if (layoutOpt.isEmpty()) return TransferResult.NO_RECIPE;

            IRecipeLayoutDrawable<Object> layout = layoutOpt.get();
            IRecipeTransferError error = handler.transferRecipe(
                menu,
                layout.getRecipe(),
                layout.getRecipeSlotsView(),
                player,
                maxTransfer,
                false
            );
            if (error == null || error.getType().allowsTransfer) {
                IRecipeTransferError execError = handler.transferRecipe(
                    menu,
                    layout.getRecipe(),
                    layout.getRecipeSlotsView(),
                    player,
                    maxTransfer,
                    true
                );
                if (execError == null || execError.getType().allowsTransfer) {
                    return TransferResult.TRANSFERRED;
                }
                error = execError;
            }

            String tooltipText = formatTransferTooltip(error);
            RecipeBookJeiMod.LOGGER.info("JEI holder transfer declined for {}: type={}, tooltip=[{}]", holder.id(), error.getType(), tooltipText);
            if (isNoServerError(error)) {
                return TransferResult.NO_SERVER;
            }
            if (error.getType() == IRecipeTransferError.Type.USER_FACING) {
                return TransferResult.USER_ERROR;
            }
        } catch (Throwable t) {
            RecipeBookJeiMod.LOGGER.error("Failed to transfer specific recipe holder {}", holder.id(), t);
        }
        return TransferResult.NO_RECIPE;
    }

    private static boolean isNoServerError(IRecipeTransferError error) {
        if (error == null) return false;
        try {
            List<Component> lines = error.getTooltip();
            if (lines != null) {
                String expected = Component.translatable("jei.tooltip.error.recipe.transfer.no.server").getString();
                for (Component c : lines) {
                    if (c.getContents() instanceof TranslatableContents tc && "jei.tooltip.error.recipe.transfer.no.server".equals(tc.getKey())) {
                        return true;
                    }
                    if (!expected.isEmpty() && c.getString().equals(expected)) {
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private static String formatTransferTooltip(IRecipeTransferError error) {
        if (error == null) return "";
        StringBuilder sb = new StringBuilder();
        try {
            List<Component> lines = error.getTooltip();
            if (lines != null) {
                for (Component c : lines) {
                    if (!sb.isEmpty()) sb.append(" | ");
                    sb.append(c.getString());
                }
            }
        } catch (Throwable ignored) {}
        return sb.toString();
    }

    private static String stackNameForLog(IFocus<ItemStack> focus) {
        try {
            return focus.getTypedValue().getIngredient().getHoverName().getString();
        } catch (Throwable t) {
            return "?";
        }
    }

    private static RecipeType<?> getJeiRecipeType(RecipeBookType bookType) {
        if (bookType == null) return null;
        return switch (bookType) {
            case CRAFTING -> RecipeTypes.CRAFTING;
            case FURNACE -> RecipeTypes.SMELTING;
            case BLAST_FURNACE -> RecipeTypes.BLASTING;
            case SMOKER -> RecipeTypes.SMOKING;
        };
    }
}
