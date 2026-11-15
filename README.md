# Recipe Book JEI

Клиентский мод для Minecraft 1.21.1 (NeoForge), объединяющий ванильную книгу рецептов и Just Enough Items (JEI).

## Описание

Мод заменяет стандартную ванильную книгу рецептов на полнофункциональный каталог предметов из всех креативных вкладок (включая вкладки других модов), интегрированный с JEI. При этом громоздкий стандартный оверлей JEI скрывается, оставляя интерфейс инвентаря чистым и просторным.

## Возможности

* Просмотр рецептов в JEI:
  * ЛКМ по предмету — просмотр рецептов крафта (если предмет не крафтится, автоматически открывается его применение).
  * ПКМ по предмету — просмотр применения (Uses) в JEI.
  * Горячие клавиши при наведении на предмет: R (рецепты), U (применение), A (закладки).

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

* Seamless JEI Integration:
  * Left-click any item to view recipes (falls back to uses if uncraftable).
  * Right-click to view uses.
  * Standard keybinds: R (recipes), U (uses), A (bookmarks).
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

