package com.recipebookjei.mixin;

import com.recipebookjei.gui.CreativeTabBookButton;
import com.recipebookjei.gui.ICustomRecipeBookComponent;
import com.recipebookjei.gui.ICustomRecipeBookPage;
import com.recipebookjei.gui.ICustomRecipeButton;
import com.recipebookjei.jei.RecipeBookJeiPlugin;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StateSwitchingButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.recipebook.GhostRecipe;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookPage;
import net.minecraft.client.gui.screens.recipebook.RecipeBookTabButton;
import net.minecraft.client.gui.screens.recipebook.RecipeButton;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.recipebook.PlaceRecipe;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.RecipeBook;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.inventory.RecipeBookType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackLinkedSet;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

@Mixin(RecipeBookComponent.class)
public abstract class RecipeBookComponentMixin implements ICustomRecipeBookComponent {
    @Shadow private int width;
    @Shadow private int height;
    @Shadow private int xOffset;
    @Shadow @Final private List<RecipeBookTabButton> tabButtons;
    @Shadow private RecipeBookTabButton selectedTab;
    @Shadow protected StateSwitchingButton filterButton;
    @Shadow @Final private RecipeBookPage recipeBookPage;
    @Shadow private EditBox searchBox;
    @Shadow protected Minecraft minecraft;
    @Shadow protected RecipeBookMenu<?, ?> menu;
    @Shadow private ClientRecipeBook book;
    @Shadow @Final private StackedContents stackedContents;
    @Shadow @Final protected GhostRecipe ghostRecipe;
    @Shadow public abstract boolean isVisible();
    @Shadow protected abstract void setVisible(boolean visible);
    @Shadow protected abstract void sendUpdateSettings();
    @Shadow public abstract void setupGhostRecipe(RecipeHolder<?> recipe, List<Slot> slots);

    @Invoker("toggleFiltering")
    protected abstract boolean recipebookjei$toggleFiltering();

    @Invoker("updateFilterButtonTooltip")
    protected abstract void recipebookjei$updateFilterButtonTooltip();

    @Invoker("isOffsetNextToMainGUI")
    protected abstract boolean recipebookjei$isOffsetNextToMainGUI();

    @Unique
    private final List<CreativeTabBookButton> recipebookjei$allTabs = new ArrayList<>();
    @Unique
    private final List<CreativeTabBookButton> recipebookjei$filteredTabs = new ArrayList<>();
    @Unique
    private final Map<Item, List<ItemStack>> recipebookjei$craftableResultsByItem = new HashMap<>();
    @Unique
    private int recipebookjei$tabScrollOffset = 0;
    @Unique
    private int recipebookjei$lastSelectedTabIndex = 0;
    @Unique
    private static final int VISIBLE_TABS_COUNT = 6;

    @Unique
    private static final RecipeBook RECIPEBOOKJEI$ALWAYS_KNOWN_BOOK = new RecipeBook() {
        @Override
        public boolean contains(RecipeHolder<?> recipe) {
            return recipe != null;
        }
    };

    @Inject(method = "initVisuals", at = @At("TAIL"))
    private void onInitVisuals(CallbackInfo ci) {
        if (this.filterButton != null) {
            this.filterButton.visible = true;
            this.filterButton.setStateTriggered(recipebookjei$isFilteringCraftable());
            this.recipebookjei$updateFilterButtonTooltip();
        }

        if (this.minecraft.level != null) {
            try {
                CreativeModeTabs.tryRebuildTabContents(
                    this.minecraft.level.enabledFeatures(),
                    this.minecraft.player != null && this.minecraft.player.canUseGameMasterBlocks(),
                    this.minecraft.level.registryAccess()
                );
            } catch (Exception ignored) {}
        }

        this.recipebookjei$allTabs.clear();

        // 1. Compass / Search tab (all items)
        CreativeTabBookButton searchTab = new CreativeTabBookButton(null);
        this.recipebookjei$allTabs.add(searchTab);

        // 2. Bookmarks tab (JEI bookmarks)
        CreativeTabBookButton bookmarksTab = CreativeTabBookButton.createBookmarksTab();
        this.recipebookjei$allTabs.add(bookmarksTab);

        // 3. Iterate ALL registered creative tabs from game and mods
        for (CreativeModeTab tab : BuiltInRegistries.CREATIVE_MODE_TAB) {
            if (tab.getType() == CreativeModeTab.Type.HOTBAR || tab.getType() == CreativeModeTab.Type.INVENTORY || tab.getType() == CreativeModeTab.Type.SEARCH) {
                continue;
            }

            if (tab.getDisplayItems().isEmpty() && this.minecraft.level != null) {
                try {
                    tab.buildContents(new CreativeModeTab.ItemDisplayParameters(
                        this.minecraft.level.enabledFeatures(),
                        this.minecraft.player != null && this.minecraft.player.canUseGameMasterBlocks(),
                        this.minecraft.level.registryAccess()
                    ));
                } catch (Exception ignored) {}
            }

            if (!tab.getDisplayItems().isEmpty() || tab.hasAnyItems()) {
                this.recipebookjei$allTabs.add(new CreativeTabBookButton(tab));
            }
        }

        recipebookjei$refreshCraftableCache();
        recipebookjei$updateFilteredTabs();

        // Restore selected tab or default to first
        int targetIndex = 0;
        if (this.recipebookjei$lastSelectedTabIndex >= 0 && this.recipebookjei$lastSelectedTabIndex < this.recipebookjei$filteredTabs.size()) {
            targetIndex = this.recipebookjei$lastSelectedTabIndex;
        }

        // Adjust scroll offset to keep selected tab in view
        if (targetIndex < this.recipebookjei$tabScrollOffset) {
            this.recipebookjei$tabScrollOffset = targetIndex;
        } else if (targetIndex >= this.recipebookjei$tabScrollOffset + VISIBLE_TABS_COUNT) {
            this.recipebookjei$tabScrollOffset = targetIndex - VISIBLE_TABS_COUNT + 1;
        }

        recipebookjei$refreshVisibleTabs();

        if (!this.recipebookjei$filteredTabs.isEmpty()) {
            this.selectedTab = this.recipebookjei$filteredTabs.get(targetIndex);
            this.selectedTab.setStateTriggered(true);
        }

        recipebookjei$updateDisplayItems(true);

        // Listen for live bookmark changes from JEI
        RecipeBookJeiPlugin.onBookmarksChanged = () -> {
            if (this.isVisible() && this.selectedTab instanceof CreativeTabBookButton btn && btn.isBookmarks()) {
                recipebookjei$updateDisplayItems(false);
            }
        };
    }

    @Inject(method = "updateTabs", at = @At("HEAD"), cancellable = true)
    private void onUpdateTabs(CallbackInfo ci) {
        ci.cancel();
        recipebookjei$refreshCraftableCache();
        recipebookjei$updateFilteredTabs();
        recipebookjei$refreshVisibleTabs();
    }

    @Inject(method = "updateCollections", at = @At("HEAD"), cancellable = true)
    private void onUpdateCollections(boolean resetPage, CallbackInfo ci) {
        ci.cancel();
        recipebookjei$refreshCraftableCache();
        recipebookjei$updateFilteredTabs();
        recipebookjei$refreshVisibleTabs();
        recipebookjei$updateDisplayItems(resetPage);
    }

    @Unique
    private boolean recipebookjei$isFilteringCraftable() {
        return this.book != null && this.menu != null && this.book.isFiltering(this.menu);
    }

    @Unique
    private void recipebookjei$refreshStackedContents() {
        if (this.minecraft != null && this.minecraft.player != null && this.menu != null) {
            this.stackedContents.clear();
            this.minecraft.player.getInventory().fillStackedContents(this.stackedContents);
            this.menu.fillCraftSlotsStackedContents(this.stackedContents);
        }
    }

    @Unique
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void recipebookjei$refreshCraftableCache() {
        this.recipebookjei$craftableResultsByItem.clear();
        if (!recipebookjei$isFilteringCraftable()) {
            return;
        }
        if (this.minecraft == null || this.minecraft.level == null || this.menu == null) {
            return;
        }

        recipebookjei$refreshStackedContents();

        RecipeType<?> targetType = recipebookjei$getVanillaRecipeType(this.menu.getRecipeBookType());
        if (targetType == null) {
            return;
        }

        int gridW = this.menu.getGridWidth();
        int gridH = this.menu.getGridHeight();
        var registryAccess = this.minecraft.level.registryAccess();

        List<RecipeHolder<?>> holders = (List<RecipeHolder<?>>) (List<?>) this.minecraft.level.getRecipeManager().getAllRecipesFor((RecipeType) targetType);
        for (RecipeHolder<?> holder : holders) {
            Recipe<?> recipe = holder.value();
            if (recipe.isSpecial() || recipe.getIngredients().isEmpty()) {
                continue;
            }
            if (!recipe.canCraftInDimensions(gridW, gridH)) {
                continue;
            }
            if (!recipebookjei$quickHasIngredients(recipe)) {
                continue;
            }
            if (this.stackedContents.canCraft(recipe, null)) {
                ItemStack result = recipe.getResultItem(registryAccess);
                if (!result.isEmpty()) {
                    this.recipebookjei$craftableResultsByItem
                        .computeIfAbsent(result.getItem(), k -> new ArrayList<>())
                        .add(result);
                }
            }
        }
    }

    @Unique
    private boolean recipebookjei$quickHasIngredients(Recipe<?> recipe) {
        for (Ingredient ingredient : recipe.getIngredients()) {
            if (!ingredient.isEmpty()) {
                IntList ids = ingredient.getStackingIds();
                boolean hasAny = false;
                for (int i = 0; i < ids.size(); i++) {
                    if (this.stackedContents.contents.get(ids.getInt(i)) > 0) {
                        hasAny = true;
                        break;
                    }
                }
                if (!hasAny) {
                    return false;
                }
            }
        }
        return true;
    }

    @Unique
    private boolean recipebookjei$isItemCraftable(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        List<ItemStack> candidates = this.recipebookjei$craftableResultsByItem.get(stack.getItem());
        if (candidates == null) return false;
        for (ItemStack res : candidates) {
            if (ItemStack.isSameItemSameComponents(res, stack)) {
                return true;
            }
        }
        return false;
    }

    @Unique
    private static RecipeType<?> recipebookjei$getVanillaRecipeType(RecipeBookType bookType) {
        if (bookType == null) return null;
        return switch (bookType) {
            case CRAFTING -> RecipeType.CRAFTING;
            case FURNACE -> RecipeType.SMELTING;
            case BLAST_FURNACE -> RecipeType.BLASTING;
            case SMOKER -> RecipeType.SMOKING;
        };
    }

    @Unique
    @SuppressWarnings({"unchecked", "rawtypes"})
    private List<RecipeHolder<?>> recipebookjei$findRecipesForItem(ItemStack stack) {
        List<RecipeHolder<?>> result = new ArrayList<>();
        if (stack == null || stack.isEmpty() || this.minecraft == null || this.minecraft.level == null || this.menu == null) {
            return result;
        }

        RecipeType<?> targetType = recipebookjei$getVanillaRecipeType(this.menu.getRecipeBookType());
        if (targetType == null) {
            return result;
        }

        int gridW = this.menu.getGridWidth();
        int gridH = this.menu.getGridHeight();
        var registryAccess = this.minecraft.level.registryAccess();

        List<RecipeHolder<?>> holders = (List<RecipeHolder<?>>) (List<?>) this.minecraft.level.getRecipeManager().getAllRecipesFor((RecipeType) targetType);
        for (RecipeHolder<?> holder : holders) {
            Recipe<?> recipe = holder.value();
            if (recipe.isSpecial() || recipe.getIngredients().isEmpty()) {
                continue;
            }
            if (!recipe.canCraftInDimensions(gridW, gridH)) {
                continue;
            }
            ItemStack out = recipe.getResultItem(registryAccess);
            if (!out.isEmpty() && ItemStack.isSameItemSameComponents(out, stack)) {
                result.add(holder);
            }
        }
        return result;
    }

    @Unique
    private void recipebookjei$placeItemRecipe(ItemStack stack, boolean maxTransfer) {
        if (this.minecraft == null || this.minecraft.player == null || this.minecraft.player.isSpectator() || this.menu == null) {
            return;
        }

        this.ghostRecipe.clear();

        RecipeBookJeiPlugin.TransferResult jeiResult = RecipeBookJeiPlugin.tryTransferRecipeForItem(stack, this.menu, this.minecraft.player, maxTransfer);
        if (jeiResult == RecipeBookJeiPlugin.TransferResult.TRANSFERRED) {
            if (!this.recipebookjei$isOffsetNextToMainGUI()) {
                this.setVisible(false);
            }
            return;
        }

        List<RecipeHolder<?>> candidates = recipebookjei$findRecipesForItem(stack);
        if (candidates.isEmpty()) {
            return;
        }

        recipebookjei$refreshStackedContents();

        RecipeHolder<?> bestCraftableUnlocked = null;
        RecipeHolder<?> bestCraftableAny = null;
        for (RecipeHolder<?> holder : candidates) {
            if (recipebookjei$quickHasIngredients(holder.value()) && this.stackedContents.canCraft(holder.value(), null)) {
                if (this.book != null && this.book.contains(holder)) {
                    bestCraftableUnlocked = holder;
                    break;
                } else if (bestCraftableAny == null) {
                    bestCraftableAny = holder;
                }
            }
        }

        RecipeHolder<?> craftable = (bestCraftableUnlocked != null) ? bestCraftableUnlocked : bestCraftableAny;
        if (craftable != null && jeiResult != RecipeBookJeiPlugin.TransferResult.USER_ERROR) {
            boolean placed = false;
            if (this.book != null && this.book.contains(craftable) && this.minecraft.gameMode != null) {
                this.minecraft.gameMode.handlePlaceRecipe(this.menu.containerId, craftable, maxTransfer);
                placed = true;
            }
            if (!placed && jeiResult == RecipeBookJeiPlugin.TransferResult.NO_SERVER) {
                recipebookjei$clientPlaceRecipe(craftable, maxTransfer);
                placed = true;
            }
            if (!placed) {
                this.setupGhostRecipe(craftable, this.menu.slots);
            }
        } else {
            this.setupGhostRecipe(candidates.get(0), this.menu.slots);
        }

        if (!this.recipebookjei$isOffsetNextToMainGUI()) {
            this.setVisible(false);
        }
    }

    @Unique
    private void recipebookjei$placeRecipeHolder(RecipeHolder<?> holder, boolean maxTransfer) {
        if (holder == null || this.minecraft == null || this.minecraft.player == null || this.minecraft.player.isSpectator() || this.menu == null) {
            return;
        }

        this.ghostRecipe.clear();

        RecipeBookJeiPlugin.TransferResult jeiResult = RecipeBookJeiPlugin.tryTransferRecipeHolder(holder, this.menu, this.minecraft.player, maxTransfer);
        if (jeiResult == RecipeBookJeiPlugin.TransferResult.TRANSFERRED) {
            if (!this.recipebookjei$isOffsetNextToMainGUI()) {
                this.setVisible(false);
            }
            return;
        }

        recipebookjei$refreshStackedContents();
        boolean canCraft = recipebookjei$quickHasIngredients(holder.value()) && this.stackedContents.canCraft(holder.value(), null);
        if (canCraft && jeiResult != RecipeBookJeiPlugin.TransferResult.USER_ERROR) {
            boolean placed = false;
            if (this.book != null && this.book.contains(holder) && this.minecraft.gameMode != null) {
                this.minecraft.gameMode.handlePlaceRecipe(this.menu.containerId, holder, maxTransfer);
                placed = true;
            }
            if (!placed && jeiResult == RecipeBookJeiPlugin.TransferResult.NO_SERVER) {
                recipebookjei$clientPlaceRecipe(holder, maxTransfer);
                placed = true;
            }
            if (!placed) {
                this.setupGhostRecipe(holder, this.menu.slots);
            }
        } else {
            this.setupGhostRecipe(holder, this.menu.slots);
        }

        if (!this.recipebookjei$isOffsetNextToMainGUI()) {
            this.setVisible(false);
        }
    }

    @Unique
    private boolean recipebookjei$clientPlaceRecipe(RecipeHolder<?> holder, boolean maxTransfer) {
        if (this.minecraft == null || this.minecraft.player == null || this.minecraft.gameMode == null || this.menu == null) {
            return false;
        }
        if (!this.menu.getCarried().isEmpty()) {
            return false;
        }

        int resultSlot = this.menu.getResultSlotIndex();
        int gridW = this.menu.getGridWidth();
        int gridH = this.menu.getGridHeight();

        Map<Integer, Ingredient> slotIngredients = new LinkedHashMap<>();
        PlaceRecipe<Ingredient> placer = (ingredient, slotIndex, maxAmount, gridX, gridY) -> {
            if (!ingredient.isEmpty() && slotIndex != resultSlot) {
                slotIngredients.put(slotIndex, ingredient);
            }
        };
        placer.placeRecipe(gridW, gridH, resultSlot, holder, holder.value().getIngredients().iterator(), 0);

        if (slotIngredients.isEmpty()) {
            return false;
        }

        int setsToPlace = 1;
        if (maxTransfer) {
            recipebookjei$refreshStackedContents();
            int maxCraftable = this.stackedContents.getBiggestCraftableStack(holder, null);
            if (maxCraftable > 1) {
                int cap = maxCraftable;
                for (Ingredient ing : slotIngredients.values()) {
                    int ingCap = 1;
                    for (Slot s : this.menu.slots) {
                        if (s.container instanceof Inventory && s.hasItem() && ing.test(s.getItem())) {
                            ingCap = Math.max(ingCap, s.getItem().getMaxStackSize());
                        }
                    }
                    cap = Math.min(cap, ingCap);
                }
                setsToPlace = Math.max(1, cap);
            }
        }

        // Only return crafting grid slots that do not match the target recipe or exceed setsToPlace
        for (int i = 0; i < this.menu.getSize(); i++) {
            if (i != resultSlot && this.menu.shouldMoveToInventory(i)) {
                Slot slot = this.menu.getSlot(i);
                if (slot.hasItem()) {
                    ItemStack cur = slot.getItem();
                    Ingredient wanted = slotIngredients.get(i);
                    if (wanted != null && wanted.test(cur) && cur.getCount() <= setsToPlace) {
                        continue;
                    }
                    this.minecraft.gameMode.handleInventoryMouseClick(this.menu.containerId, slot.index, 0, ClickType.QUICK_MOVE, this.minecraft.player);
                    if (slot.hasItem()) {
                        return false;
                    }
                }
            }
        }

        Map<Integer, Integer> remainingNeeds = new LinkedHashMap<>();
        for (Map.Entry<Integer, Ingredient> entry : slotIngredients.entrySet()) {
            int slotIdx = entry.getKey();
            Slot slotObj = this.menu.getSlot(slotIdx);
            int curCount = slotObj.hasItem() ? slotObj.getItem().getCount() : 0;
            int need = setsToPlace - curCount;
            if (need > 0) {
                remainingNeeds.put(slotIdx, need);
            }
        }

        if (remainingNeeds.isEmpty()) {
            return true;
        }

        int safety = 0;
        while (!remainingNeeds.isEmpty() && safety++ < 64) {
            Map.Entry<Integer, Integer> first = remainingNeeds.entrySet().iterator().next();
            int firstSlotIdx = first.getKey();
            Ingredient firstIng = slotIngredients.get(firstSlotIdx);
            ItemStack existingInFirst = this.menu.getSlot(firstSlotIdx).getItem();

            Slot srcSlot = null;
            for (Slot s : this.menu.slots) {
                if (s.container instanceof Inventory && s.hasItem() && s.allowModification(this.minecraft.player) && firstIng.test(s.getItem())) {
                    if (existingInFirst.isEmpty() || ItemStack.isSameItemSameComponents(existingInFirst, s.getItem())) {
                        srcSlot = s;
                        break;
                    }
                }
            }
            if (srcSlot == null) {
                break;
            }

            ItemStack srcProto = srcSlot.getItem().copy();
            int available = srcProto.getCount();

            List<Integer> matchingTargets = new ArrayList<>();
            for (Map.Entry<Integer, Integer> entry : remainingNeeds.entrySet()) {
                int ts = entry.getKey();
                Slot targetSlotObj = this.menu.getSlot(ts);
                ItemStack existing = targetSlotObj.getItem();
                if (slotIngredients.get(ts).test(srcProto)
                    && (existing.isEmpty() || ItemStack.isSameItemSameComponents(existing, srcProto))
                    && targetSlotObj.getMaxStackSize(srcProto) > existing.getCount()) {
                    matchingTargets.add(ts);
                }
            }

            if (matchingTargets.isEmpty()) {
                break;
            }

            // Single slot exact half-split shortcut
            if (matchingTargets.size() == 1) {
                int ts = matchingTargets.get(0);
                int need = remainingNeeds.get(ts);
                int half = (available + 1) / 2;
                if (available > need && half > 1 && half <= need) {
                    this.minecraft.gameMode.handleInventoryMouseClick(this.menu.containerId, srcSlot.index, 1, ClickType.PICKUP, this.minecraft.player);
                    this.minecraft.gameMode.handleInventoryMouseClick(this.menu.containerId, ts, 0, ClickType.PICKUP, this.minecraft.player);
                    int rem = need - half;
                    if (rem <= 0) {
                        remainingNeeds.remove(ts);
                    } else {
                        remainingNeeds.put(ts, rem);
                    }
                    continue;
                }
            }

            // Keep stack on cursor while filling matching targets, return remainder once
            this.minecraft.gameMode.handleInventoryMouseClick(this.menu.containerId, srcSlot.index, 0, ClickType.PICKUP, this.minecraft.player);
            for (int ts : matchingTargets) {
                int carriedCount = this.menu.getCarried().getCount();
                if (carriedCount <= 0) {
                    break;
                }
                int need = remainingNeeds.get(ts);
                int take = Math.min(need, carriedCount);
                if (take == carriedCount) {
                    this.minecraft.gameMode.handleInventoryMouseClick(this.menu.containerId, ts, 0, ClickType.PICKUP, this.minecraft.player);
                    int rem = need - take;
                    if (rem <= 0) {
                        remainingNeeds.remove(ts);
                    } else {
                        remainingNeeds.put(ts, rem);
                    }
                    break;
                } else {
                    for (int c = 0; c < take; c++) {
                        this.minecraft.gameMode.handleInventoryMouseClick(this.menu.containerId, ts, 1, ClickType.PICKUP, this.minecraft.player);
                    }
                    int rem = need - take;
                    if (rem <= 0) {
                        remainingNeeds.remove(ts);
                    } else {
                        remainingNeeds.put(ts, rem);
                    }
                }
            }
            if (!this.menu.getCarried().isEmpty()) {
                this.minecraft.gameMode.handleInventoryMouseClick(this.menu.containerId, srcSlot.index, 0, ClickType.PICKUP, this.minecraft.player);
                if (!this.menu.getCarried().isEmpty()) {
                    break;
                }
            }
        }

        return true;
    }

    @Unique
    private void recipebookjei$tryOpenVariantOverlay(ItemStack stack, RecipeButton widget, int bookX, int bookY) {
        if (this.minecraft == null || this.minecraft.level == null || this.menu == null) {
            return;
        }
        List<RecipeHolder<?>> holders = recipebookjei$findRecipesForItem(stack);
        if (holders.size() <= 1) {
            return;
        }

        recipebookjei$refreshStackedContents();
        RecipeCollection collection = new RecipeCollection(this.minecraft.level.registryAccess(), holders);
        collection.canCraft(this.stackedContents, this.menu.getGridWidth(), this.menu.getGridHeight(), RECIPEBOOKJEI$ALWAYS_KNOWN_BOOK);
        boolean filtering = recipebookjei$isFilteringCraftable();
        if (filtering && collection.getDisplayRecipes(true).size() <= 1) {
            return;
        }

        ((ICustomRecipeBookPage) this.recipeBookPage).recipebookjei$openOverlay(collection, widget, bookX, bookY);
    }

    @Unique
    private void recipebookjei$updateFilteredTabs() {
        String search = (this.searchBox != null) ? this.searchBox.getValue().trim().toLowerCase(Locale.ROOT) : "";
        String modFilter = "";
        String itemFilter = search;

        if (search.startsWith("@")) {
            String afterAt = search.substring(1).trim();
            int spaceIdx = afterAt.indexOf(' ');
            if (spaceIdx != -1) {
                modFilter = afterAt.substring(0, spaceIdx).trim();
                itemFilter = afterAt.substring(spaceIdx + 1).trim();
            } else {
                modFilter = afterAt;
                itemFilter = "";
            }
        }

        boolean onlyCraftable = recipebookjei$isFilteringCraftable();
        Predicate<ItemStack> craftFilter = onlyCraftable ? this::recipebookjei$isItemCraftable : null;

        this.recipebookjei$filteredTabs.clear();
        if (search.isEmpty() && !onlyCraftable) {
            this.recipebookjei$filteredTabs.addAll(this.recipebookjei$allTabs);
        } else {
            for (CreativeTabBookButton tab : this.recipebookjei$allTabs) {
                if (tab.hasMatchingItems(modFilter, itemFilter, craftFilter)) {
                    this.recipebookjei$filteredTabs.add(tab);
                }
            }
        }

        // If currently selected tab was filtered out, switch selection to Search tab (Compass)
        if (this.selectedTab != null && !this.recipebookjei$filteredTabs.contains(this.selectedTab)) {
            this.selectedTab.setStateTriggered(false);
            if (!this.recipebookjei$filteredTabs.isEmpty()) {
                this.selectedTab = this.recipebookjei$filteredTabs.get(0);
                this.selectedTab.setStateTriggered(true);
            } else {
                this.selectedTab = null;
            }
            this.recipebookjei$tabScrollOffset = 0;
        }
    }

    @Unique
    private void recipebookjei$refreshVisibleTabs() {
        this.tabButtons.clear();
        int bookX = (this.width - 147) / 2 - this.xOffset;
        int bookY = (this.height - 166) / 2;
        int tabX = bookX - 30;
        int tabY = bookY + 3;

        int total = this.recipebookjei$filteredTabs.size();
        int maxOffset = Math.max(0, total - VISIBLE_TABS_COUNT);
        if (this.recipebookjei$tabScrollOffset > maxOffset) {
            this.recipebookjei$tabScrollOffset = maxOffset;
        }
        if (this.recipebookjei$tabScrollOffset < 0) {
            this.recipebookjei$tabScrollOffset = 0;
        }

        int end = Math.min(total, this.recipebookjei$tabScrollOffset + VISIBLE_TABS_COUNT);
        for (int i = this.recipebookjei$tabScrollOffset; i < end; i++) {
            CreativeTabBookButton tab = this.recipebookjei$filteredTabs.get(i);
            int slotIndex = i - this.recipebookjei$tabScrollOffset;
            tab.setPosition(tabX, tabY + slotIndex * 27);
            tab.visible = true;
            this.tabButtons.add(tab);
        }
    }

    @Unique
    private void recipebookjei$updateDisplayItems(boolean resetPage) {
        if (this.selectedTab == null || !(this.selectedTab instanceof CreativeTabBookButton cTab)) {
            return;
        }

        List<ItemStack> rawItems = new ArrayList<>();
        if (cTab.isBookmarks()) {
            rawItems.addAll(RecipeBookJeiPlugin.getBookmarkedItems());
        } else if (cTab.isSearch()) {
            for (CreativeTabBookButton btn : this.recipebookjei$filteredTabs) {
                if (btn.getCreativeTab() != null) {
                    try {
                        rawItems.addAll(btn.getCreativeTab().getDisplayItems());
                    } catch (Throwable ignored) {}
                }
            }
        } else if (cTab.getCreativeTab() != null) {
            try {
                rawItems.addAll(cTab.getCreativeTab().getDisplayItems());
            } catch (Throwable ignored) {}
        }

        String search = (this.searchBox != null) ? this.searchBox.getValue().trim().toLowerCase(Locale.ROOT) : "";
        String modQuery = "";
        String itemQuery = search;
        if (!search.isEmpty() && search.startsWith("@")) {
            String afterAt = search.substring(1).trim();
            int spaceIdx = afterAt.indexOf(' ');
            if (spaceIdx != -1) {
                modQuery = afterAt.substring(0, spaceIdx).trim();
                itemQuery = afterAt.substring(spaceIdx + 1).trim();
            } else {
                modQuery = afterAt;
                itemQuery = "";
            }
        }

        boolean onlyCraftable = recipebookjei$isFilteringCraftable();
        Set<ItemStack> seen = cTab.isSearch() ? ItemStackLinkedSet.createTypeAndComponentsSet() : null;
        List<ItemStack> filtered = new ArrayList<>();

        for (ItemStack stack : rawItems) {
            if (stack == null || stack.isEmpty()) continue;
            if (!search.isEmpty() && !CreativeTabBookButton.matchesStack(stack, modQuery, itemQuery)) {
                continue;
            }
            if (onlyCraftable && !recipebookjei$isItemCraftable(stack)) {
                continue;
            }
            if (seen != null && !seen.add(stack)) {
                continue;
            }
            filtered.add(stack);
        }

        ((ICustomRecipeBookPage) this.recipeBookPage).recipebookjei$setCustomItems(filtered, resetPage);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void onRender(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        if (!this.isVisible()) return;

        int total = this.recipebookjei$filteredTabs.size();
        if (total > VISIBLE_TABS_COUNT) {
            int bookX = (this.width - 147) / 2 - this.xOffset;
            int bookY = (this.height - 166) / 2;
            int tabX = bookX - 30;
            int tabY = bookY + 3;
            Font font = this.minecraft.font;

            if (this.recipebookjei$tabScrollOffset > 0) {
                int arrowColor = (mouseX >= tabX && mouseX <= tabX + 30 && mouseY >= tabY - 11 && mouseY < tabY) ? 0xFFFFA0 : 0xFFFFFF;
                graphics.drawCenteredString(font, "▲", tabX + 15, tabY - 10, arrowColor);
            }

            int maxOffset = total - VISIBLE_TABS_COUNT;
            if (this.recipebookjei$tabScrollOffset < maxOffset) {
                int arrowColor = (mouseX >= tabX && mouseX <= tabX + 30 && mouseY >= tabY + VISIBLE_TABS_COUNT * 27 && mouseY <= tabY + VISIBLE_TABS_COUNT * 27 + 12) ? 0xFFFFA0 : 0xFFFFFF;
                graphics.drawCenteredString(font, "▼", tabX + 15, tabY + VISIBLE_TABS_COUNT * 27 + 2, arrowColor);
            }
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void onMouseClicked(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (!this.isVisible()) return;

        int bookX = (this.width - 147) / 2 - this.xOffset;
        int bookY = (this.height - 166) / 2;
        int tabX = bookX - 30;
        int tabY = bookY + 3;

        int total = this.recipebookjei$filteredTabs.size();
        if (total > VISIBLE_TABS_COUNT) {
            if (this.recipebookjei$tabScrollOffset > 0 && mouseX >= tabX && mouseX <= tabX + 30 && mouseY >= tabY - 12 && mouseY < tabY) {
                this.recipebookjei$tabScrollOffset--;
                recipebookjei$refreshVisibleTabs();
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                cir.setReturnValue(true);
                return;
            }
            int maxOffset = total - VISIBLE_TABS_COUNT;
            if (this.recipebookjei$tabScrollOffset < maxOffset && mouseX >= tabX && mouseX <= tabX + 30 && mouseY >= tabY + VISIBLE_TABS_COUNT * 27 && mouseY <= tabY + VISIBLE_TABS_COUNT * 27 + 14) {
                this.recipebookjei$tabScrollOffset++;
                recipebookjei$refreshVisibleTabs();
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                cir.setReturnValue(true);
                return;
            }
        }

        if (this.filterButton != null && this.filterButton.visible && this.filterButton.mouseClicked(mouseX, mouseY, button)) {
            boolean filtering = this.recipebookjei$toggleFiltering();
            this.filterButton.setStateTriggered(filtering);
            this.recipebookjei$updateFilterButtonTooltip();
            this.sendUpdateSettings();
            recipebookjei$refreshCraftableCache();
            recipebookjei$updateFilteredTabs();
            recipebookjei$refreshVisibleTabs();
            recipebookjei$updateDisplayItems(true);
            cir.setReturnValue(true);
            return;
        }

        for (RecipeBookTabButton tab : this.tabButtons) {
            if (tab.mouseClicked(mouseX, mouseY, button)) {
                if (this.selectedTab != tab) {
                    if (this.selectedTab != null) {
                        this.selectedTab.setStateTriggered(false);
                    }
                    this.selectedTab = tab;
                    this.selectedTab.setStateTriggered(true);
                    if (tab instanceof CreativeTabBookButton cTab) {
                        this.recipebookjei$lastSelectedTabIndex = this.recipebookjei$filteredTabs.indexOf(cTab);
                    }
                    recipebookjei$updateDisplayItems(true);
                }
                cir.setReturnValue(true);
                return;
            }
        }

        if (this.searchBox != null) {
            boolean overSearch = mouseX >= this.searchBox.getX() - 16 &&
                                 mouseX < this.searchBox.getX() + this.searchBox.getWidth() + 2 &&
                                 mouseY >= this.searchBox.getY() - 2 &&
                                 mouseY < this.searchBox.getY() + this.searchBox.getHeight() + 2;

            if (overSearch) {
                if (button == 1) { // RMB -> Clear search!
                    if (!this.searchBox.getValue().isEmpty()) {
                        this.searchBox.setValue("");
                        this.searchBox.setFocused(true);
                        recipebookjei$updateFilteredTabs();
                        recipebookjei$refreshVisibleTabs();
                        recipebookjei$updateDisplayItems(true);
                        this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    }
                    cir.setReturnValue(true);
                    return;
                } else if (button == 0) { // LMB
                    this.searchBox.setFocused(true);
                    this.searchBox.mouseClicked(mouseX, mouseY, button);
                    cir.setReturnValue(true);
                    return;
                }
            } else if (button == 0) {
                this.searchBox.setFocused(false);
            }
        }

        if (this.recipeBookPage.mouseClicked(mouseX, mouseY, button, bookX, bookY, 147, 166)) {
            RecipeHolder<?> overlayRecipe = this.recipeBookPage.getLastClickedRecipe();
            if (overlayRecipe != null) {
                recipebookjei$placeRecipeHolder(overlayRecipe, Screen.hasShiftDown());
            } else {
                ICustomRecipeBookPage customPage = (ICustomRecipeBookPage) this.recipeBookPage;
                ItemStack clickedItem = customPage.recipebookjei$getLastClickedItem();
                int clickedBtn = customPage.recipebookjei$getLastClickedMouseButton();
                RecipeButton widget = customPage.recipebookjei$getLastClickedButtonWidget();
                if (clickedItem != null && !clickedItem.isEmpty()) {
                    if (clickedBtn == 0) {
                        recipebookjei$placeItemRecipe(clickedItem, Screen.hasShiftDown());
                    } else if (clickedBtn == 1 && widget != null) {
                        recipebookjei$tryOpenVariantOverlay(clickedItem, widget, bookX, bookY);
                    }
                }
            }
            cir.setReturnValue(true);
            return;
        }

        if (mouseX >= bookX - 35 && mouseX <= bookX + 147 && mouseY >= bookY - 15 && mouseY <= bookY + 180) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "isMouseOver", at = @At("HEAD"), cancellable = true)
    private void onIsMouseOver(double mouseX, double mouseY, CallbackInfoReturnable<Boolean> cir) {
        if (this.isVisible()) {
            int bookX = (this.width - 147) / 2 - this.xOffset;
            int bookY = (this.height - 166) / 2;
            if (mouseX >= bookX - 40 && mouseX <= bookX + 147 && mouseY >= bookY - 18 && mouseY <= bookY + 185) {
                cir.setReturnValue(true);
            }
        }
    }

    @Override
    public boolean recipebookjei$mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!this.isVisible()) return false;

        int bookX = (this.width - 147) / 2 - this.xOffset;
        int bookY = (this.height - 166) / 2;

        // 1. Tabs area (left column)
        if (mouseX >= bookX - 40 && mouseX < bookX && mouseY >= bookY - 18 && mouseY <= bookY + 185) {
            int delta = (scrollY > 0) ? -1 : (scrollY < 0 ? 1 : 0);
            if (delta != 0) {
                int total = this.recipebookjei$filteredTabs.size();
                int maxOffset = Math.max(0, total - VISIBLE_TABS_COUNT);
                int oldOffset = this.recipebookjei$tabScrollOffset;
                this.recipebookjei$tabScrollOffset = Math.max(0, Math.min(maxOffset, this.recipebookjei$tabScrollOffset + delta));
                if (this.recipebookjei$tabScrollOffset != oldOffset) {
                    recipebookjei$refreshVisibleTabs();
                }
                return true;
            }
        }

        // 2. Main Book area (item grid & search)
        if (mouseX >= bookX && mouseX <= bookX + 147 && mouseY >= bookY && mouseY <= bookY + 166) {
            int delta = (scrollY > 0) ? -1 : (scrollY < 0 ? 1 : 0);
            if (delta != 0) {
                ((ICustomRecipeBookPage) this.recipeBookPage).recipebookjei$scrollPage(delta);
                return true;
            }
        }

        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return recipebookjei$mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void onKeyPressed(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (!this.isVisible()) return;

        if (this.searchBox != null && this.searchBox.isFocused()) {
            if (this.searchBox.keyPressed(keyCode, scanCode, modifiers)) {
                recipebookjei$updateFilteredTabs();
                recipebookjei$refreshVisibleTabs();
                recipebookjei$updateDisplayItems(true);
                cir.setReturnValue(true);
                return;
            } else if (this.searchBox.isVisible() && keyCode != GLFW.GLFW_KEY_ESCAPE) {
                cir.setReturnValue(true);
                return;
            }
        }

        RecipeButton hovered = null;
        for (RecipeButton btn : ((ICustomRecipeBookPage) this.recipeBookPage).recipebookjei$getButtons()) {
            if (btn.visible && btn.isHovered()) {
                hovered = btn;
                break;
            }
        }
        if (hovered == null) {
            for (RecipeButton btn : ((ICustomRecipeBookPage) this.recipeBookPage).recipebookjei$getButtons()) {
                if (btn.visible && btn.isFocused()) {
                    hovered = btn;
                    break;
                }
            }
        }

        if (hovered != null) {
            ItemStack stack = ((ICustomRecipeButton) hovered).recipebookjei$getItem();
            if (stack != null && !stack.isEmpty()) {
                if (RecipeBookJeiPlugin.isShowRecipeKey(keyCode, scanCode)) {
                    RecipeBookJeiPlugin.showRecipes(stack);
                    cir.setReturnValue(true);
                    return;
                } else if (RecipeBookJeiPlugin.isShowUsesKey(keyCode, scanCode)) {
                    RecipeBookJeiPlugin.showUses(stack);
                    cir.setReturnValue(true);
                    return;
                } else if (RecipeBookJeiPlugin.isBookmarkKey(keyCode, scanCode)) {
                    boolean added = RecipeBookJeiPlugin.toggleBookmark(stack);
                    float pitch = added ? 1.5F : 0.8F;
                    this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, pitch));
                    if (this.selectedTab instanceof CreativeTabBookButton cTab && cTab.isBookmarks()) {
                        recipebookjei$updateDisplayItems(false);
                    }
                    cir.setReturnValue(true);
                    return;
                } else if (keyCode == GLFW.GLFW_KEY_DELETE) {
                    if (this.selectedTab instanceof CreativeTabBookButton cTab && cTab.isBookmarks()) {
                        RecipeBookJeiPlugin.removeBookmark(stack);
                        this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 0.8F));
                        recipebookjei$updateDisplayItems(false);
                        cir.setReturnValue(true);
                        return;
                    }
                }
            }
        }
    }

    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
    private void onCharTyped(char codePoint, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (!this.isVisible()) return;

        if (this.searchBox != null && this.searchBox.isFocused()) {
            if (this.searchBox.charTyped(codePoint, modifiers)) {
                recipebookjei$updateFilteredTabs();
                recipebookjei$refreshVisibleTabs();
                recipebookjei$updateDisplayItems(true);
                cir.setReturnValue(true);
                return;
            }
        }
    }

    @Inject(method = "renderTooltip", at = @At("TAIL"))
    private void onRenderTooltip(GuiGraphics graphics, int x, int y, int mouseX, int mouseY, CallbackInfo ci) {
        if (!this.isVisible()) return;

        for (RecipeBookTabButton tab : this.tabButtons) {
            if (tab.isHoveredOrFocused() && tab instanceof CreativeTabBookButton cTab) {
                graphics.renderTooltip(this.minecraft.font, cTab.getTabTooltip(), mouseX, mouseY);
                break;
            }
        }
    }
}
