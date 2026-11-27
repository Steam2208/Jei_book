# Recipe Book JEI

Клиентский мод для Minecraft 1.21.1 (NeoForge), объединяющий ванильную книгу рецептов и Just Enough Items (JEI).

## Описание

Мод заменяет стандартную ванильную книгу рецептов на полнофункциональный каталог предметов из всех креативных вкладок (включая вкладки других модов), интегрированный с JEI. При этом громоздкий стандартный оверлей JEI скрывается, оставляя интерфейс инвентаря чистым и просторным.

## Возможности

* Крафт и просмотр рецептов в JEI:
  * ЛКМ по предмету — выложить рецепт на верстаке / сетке крафта (с Shift — максимальный стак; при нехватке ресурсов показывается призрачный рецепт).
  * ПКМ по предмету — выбор альтернативного рецепта, если их несколько.
  * Горячие клавиши JEI при наведении на предмет: R (рецепты, по умолчанию), U (применение, по умолчанию), A (закладки) — с поддержкой переназначения в настройках управления JEI.

* Фильтр доступных рецептов:
  * Кнопка-галочка справа от поиска позволяет отображать только те предметы, для крафта которых сейчас хватает ресурсов в инвентаре.
  * Пустые вкладки при включённом фильтре автоматически скрываются.

* Прокрутка колесом мыши:
  * Колонку вкладок слева можно плавно листать колесом мыши вверх и вниз.
  * Основную сетку предметов и страницы можно переключать колесом мыши.

* Вкладка закладок JEI:
  * Отдельная вкладка закладок (с иконкой зачарованной книги) рядом с поиском.
  * Быстрое добавление и удаление закладок по клавише A или Delete.
  * Автоматическая синхронизация с закладками JEI.

* Умный поиск и динамическая фильтрация вкладок:
  * Поддержка поиска по тегу мода (@mod, например, @create или @create gear).
  * При поиске пустые вкладки (без совпадений) автоматически скрываются, отображаются только вкладки с найденными предметами.
  * Если выбранная вкладка оказывается пустой, интерфейс автоматически переключается на глобальный поиск.
  * Клик ПКМ по строке поиска или иконке лупы мгновенно очищает поиск.

## Требования

* Minecraft 1.21.1
* NeoForge (21.1.x)
* Just Enough Items (JEI) 19.x

---

# English Description

A client-side mod for Minecraft 1.21.1 (NeoForge) that integrates the vanilla Recipe Book with Just Enough Items (JEI).

## Features

* Seamless Crafting & JEI Integration:
  * Left-click any item to place its recipe into the crafting grid (Shift+click for max stack; shows a ghost recipe if ingredients are missing).
  * Right-click to choose an alternative recipe if multiple exist.
  * JEI keybinds when hovering an item: Show Recipes (default `R`), Show Uses (default `U`), Bookmark (default `A`) — respects your JEI keybinding settings.
  * Craftable-only toggle button next to the search bar to filter for recipes you currently have ingredients for.
  * Default side JEI overlay is hidden to keep the UI clean.

* Mouse Wheel Scrolling:
  * Smooth wheel scrolling for the side creative tabs list.
  * Wheel scrolling to navigate pages in the main item grid.

* JEI Bookmarks Tab:
  * Dedicated bookmarks tab with instant sync with JEI bookmarks.
  * Quick add/remove with key A or Delete.

* Smart Search & Dynamic Tab Filtering:
  * Mod namespace search support (@mod, e.g. @create or @create gear).
  * Automatically hides empty tabs that contain no matching items during search.
  * Right-click on the search bar or magnifying glass to clear search instantly.

