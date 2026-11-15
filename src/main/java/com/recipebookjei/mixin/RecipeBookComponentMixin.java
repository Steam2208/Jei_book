package com.recipebookjei.mixin;

import com.recipebookjei.gui.CreativeTabBookButton;
import com.recipebookjei.gui.ICustomRecipeBookComponent;
import com.recipebookjei.gui.ICustomRecipeBookPage;
import com.recipebookjei.gui.ICustomRecipeButton;
import com.recipebookjei.jei.RecipeBookJeiPlugin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StateSwitchingButton;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookPage;
import net.minecraft.client.gui.screens.recipebook.RecipeBookTabButton;
import net.minecraft.client.gui.screens.recipebook.RecipeButton;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

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
    @Shadow public abstract boolean isVisible();

    @Unique
    private final List<CreativeTabBookButton> recipebookjei$allTabs = new ArrayList<>();
    @Unique
    private final List<CreativeTabBookButton> recipebookjei$filteredTabs = new ArrayList<>();
    @Unique
    private int recipebookjei$tabScrollOffset = 0;
    @Unique
    private int recipebookjei$lastSelectedTabIndex = 0;
    @Unique
    private static final int VISIBLE_TABS_COUNT = 6;

    @Inject(method = "initVisuals", at = @At("TAIL"))
    private void onInitVisuals(CallbackInfo ci) {
        if (this.filterButton != null) {
            this.filterButton.visible = false;
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
        recipebookjei$updateFilteredTabs();
        recipebookjei$refreshVisibleTabs();
    }

    @Inject(method = "updateCollections", at = @At("HEAD"), cancellable = true)
    private void onUpdateCollections(boolean resetPage, CallbackInfo ci) {
        ci.cancel();
        recipebookjei$updateFilteredTabs();
        recipebookjei$refreshVisibleTabs();
        recipebookjei$updateDisplayItems(resetPage);
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

        this.recipebookjei$filteredTabs.clear();
        if (search.isEmpty()) {
            this.recipebookjei$filteredTabs.addAll(this.recipebookjei$allTabs);
        } else {
            for (CreativeTabBookButton tab : this.recipebookjei$allTabs) {
                if (tab.hasMatchingItems(modFilter, itemFilter)) {
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
        List<ItemStack> filtered = new ArrayList<>();

        if (search.isEmpty()) {
            for (ItemStack stack : rawItems) {
                if (stack != null && !stack.isEmpty()) {
                    filtered.add(stack);
                }
            }
        } else {
            String modQuery = "";
            String itemQuery = search;
            if (search.startsWith("@")) {
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

            Set<String> seen = new HashSet<>();
            for (ItemStack stack : rawItems) {
                if (stack == null || stack.isEmpty()) continue;
                if (CreativeTabBookButton.matchesStack(stack, modQuery, itemQuery)) {
                    if (cTab.isSearch()) {
                        String key = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                        if (!seen.add(key)) continue;
                    }
                    filtered.add(stack);
                }
            }
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
                                 mouseX < this.searchBox.getX() + this.searchBox.getWidth() + 10 &&
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
            }
        }

        RecipeButton hovered = null;
        for (RecipeButton btn : ((ICustomRecipeBookPage) this.recipeBookPage).recipebookjei$getButtons()) {
            if (btn.isHoveredOrFocused()) {
                hovered = btn;
                break;
            }
        }

        if (hovered != null) {
            ItemStack stack = ((ICustomRecipeButton) hovered).recipebookjei$getItem();
            if (stack != null && !stack.isEmpty()) {
                if (keyCode == GLFW.GLFW_KEY_R) {
                    RecipeBookJeiPlugin.showRecipes(stack);
                    cir.setReturnValue(true);
                    return;
                } else if (keyCode == GLFW.GLFW_KEY_U) {
                    RecipeBookJeiPlugin.showUses(stack);
                    cir.setReturnValue(true);
                    return;
                } else if (keyCode == GLFW.GLFW_KEY_A) {
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
