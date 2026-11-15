package com.recipebookjei;

import com.recipebookjei.gui.ICustomRecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeUpdateListener;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(RecipeBookJeiMod.MODID)
public class RecipeBookJeiMod {
    public static final String MODID = "recipebookjei";
    public static final Logger LOGGER = LogManager.getLogger("RecipeBookJEI");

    public RecipeBookJeiMod() {
        LOGGER.info("Recipe Book JEI loaded successfully!");
        try {
            NeoForge.EVENT_BUS.addListener(ScreenEvent.MouseScrolled.Pre.class, this::onMouseScrolledPre);
            LOGGER.info("Registered ScreenEvent.MouseScrolled.Pre listener!");
        } catch (Throwable t) {
            LOGGER.error("Failed to register mouse scroll listener", t);
        }
    }

    private void onMouseScrolledPre(ScreenEvent.MouseScrolled.Pre event) {
        if (event.getScreen() instanceof RecipeUpdateListener listener) {
            RecipeBookComponent book = listener.getRecipeBookComponent();
            if (book != null && book.isVisible()) {
                if (((ICustomRecipeBookComponent) book).recipebookjei$mouseScrolled(
                    event.getMouseX(), event.getMouseY(),
                    event.getScrollDeltaX(), event.getScrollDeltaY()
                )) {
                    event.setCanceled(true);
                }
            }
        }
    }
}
