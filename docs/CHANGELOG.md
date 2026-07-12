## Minecraft 1.21.1 — Fabric + NeoForge

### Release: 0.6.0-dev3

---

**2026-07-12**

- **Edytor: bloki tintowane biomem nie renderują się już na biało w podglądach.**
  `PreviewWorld.getBlockTint` zwracał -1 (biały), gdy chunk nie miał przypisanego biomu —
  a podgląd terenu w BiomeEditorze i podgląd 3D w BO Store stawiają bloki bez danych
  biomów, więc trawa, liście, woda i winorośle były białe. Nowa klasa `PreviewBiomes`
  buduje tymczasowe, nierejestrowane biomy (`Holder.direct`), a `PreviewWorld.fillBiome`
  wypełnia nimi wszystkie quarty załadowanych chunków. Podgląd terenu składa biom
  z edytowanych propsów (`BiomeTemperature`, `BiomeWetness`, `FoliageColor`, `GrassColor`,
  `WaterColor`, `WaterFogColor`, `FogColor`, `SkyColor`, `GrassColorModifier`) z semantyką
  jak w `BiomeFactory`: kolor `0xFFFFFF` = użyj koloru wyliczonego z klimatu vanilla.
  Podgląd BO dostaje neutralny las umiarkowany, bo obiekty BO nie niosą biomu. Pełny
  world preview bez zmian — biomy bierze z `addChunkFromAccess`.

### Release: 0.6.0-dev2

---

**2026-06-30**

- **Edytor: nowy DimensionPreset z szablonu DefaultPreset nie jest już cicho gubiony.**
  Szablon DefaultPreset niesie legacy klucz `ShortPresetName: otg_default`, a kreator
  patchował tylko `RegistryName` (doklejał na końcu pliku). Loader renamuje `ShortPresetName`
  → `RegistryName` przy wczytaniu (`DimensionPresetConfig.renameOldSettings`), nadpisując
  doklejoną wartość → każdy nowy preset rozwiązywał się do `otg_default`, kolidował z
  DefaultPreset i był ignorowany w `loadDimensionPresetsFromDisk` (widoczny tylko jako
  szablon w kreatorze, którego lista skanuje dysk). `patchIniSettings` rozpoznaje teraz
  legacy aliasy (`LEGACY_KEY_ALIASES`) i podmienia linię w miejscu zamiast doklejać duplikat
  — naprawia ścieżkę kreatora i Clone. Poprzedni fix walidacji RegistryName (dev1) był
  niepełny: sprawdzał wartość z pola GUI, nie tę, którą config przyjmie po skopiowaniu
  szablonu z legacy aliasem.

### Release: 0.6.0-dev1

---

**2026-06-29**

- **Edytor: nowy DimensionPreset widoczny od razu, bez restartu gry.** Po stworzeniu
  presetu kreatorem (`DimensionPresetWizardScreen`) `PresetReloader.reload()` już
  aktualizował listę w pamięci, ale przepływ nawigacji nigdy nie wracał na ekran listy:
  kreator otwierał `WorldSettingsScreen`, a jego „Back" prowadził do `EditorHubScreen`,
  więc user nie widział nowego presetu i błędnie zakładał, że trzeba zrestartować grę.
  `WorldSettingsScreen` dostał opcjonalny `returnTo` — przy tworzeniu z kreatora „Back"
  wraca teraz na odświeżony `ManageDimensionPresetsScreen` z zaznaczonym nowym presetem
  (`selectByFolder`). Pozostałe wejścia (EditorHub, edycja z listy) bez zmian — fallback
  do EditorHub.
- **Edytor: koniec cichego gubienia presetów przez duplikat RegistryName.**
  `loadDimensionPresetsFromDisk()` po cichu odrzuca preset, którego RegistryName już
  istnieje (mapa aliasów jest kluczowana po nim). Kreator walidował tylko FolderName
  i DisplayName, więc dało się stworzyć „cichego trupa". Dodano walidację RegistryName
  w `DimensionPresetWizardScreen` (komunikat błędu + blokada „Next/Finish"). Ścieżka
  Clone (która dwukrotnym klonowaniem tego samego presetu generowała identyczny
  RegistryName) używa teraz `resolveUniqueRegistryName` — dokleja `_1`, `_2`… przy
  kolizji, analogicznie do unikalności nazwy folderu.
- **Edytor: RegistryName zawsze normalizowany do poprawnego MC id.** Ręcznie wpisany
  RegistryName (spacje, wielkie litery, znaki specjalne) był zapisywany dosłownie,
  dając nieprawidłowy resource id. `effectiveRegistryName()` przepuszcza teraz wejście
  przez `normalizeId` (z fallbackiem na nazwę folderu, gdy znormalizuje się do pustego),
  ekran potwierdzenia pokazuje finalną wartość, a krok metadanych podgląda
  „RegistryName saved as: …", gdy wpisana wartość zostanie wyczyszczona.

**2026-06-20**

- **3D underground biomes — Phase 2 (per-region cave shaping):** underground biomes can
  now scale the density of each cave type inside their region via `CaveCheeseScale`,
  `CaveSpaghetti3dScale`, `CaveSpaghetti2dScale`, `CaveNoodleScale`, `CavePillarScale`
  (`.bc`, default 1.0 = unchanged). Multipliers cross-fade between adjacent regions and
  into normal stone (`resolveCaveScales` + cross-faded membership weights). Carving applies
  them through `RegionScaleFunction` inside the cave-density graph, bound per-chunk via a
  thread-local. With all scales at 1.0 the carve graph falls back to constant scaling
  (`hasUndergroundCaveScaling` guard) — output is identical to before.

**2026-06-17 — Przeprojektowanie biomów podziemnych 3D (prawdziwe regiony 3D + pełna kontrola OTG)**

- **Rozmieszczanie szumem 3D**: biomy podziemne tworzą organiczne, spójne regiony 3D zamiast siatki 64×64. Nowa klasa `UndergroundRegionNoise` (3D Perlin, seed per biom) + ustawienia `UndergroundRegionSize` (wielkość blobów) i `UndergroundVerticalScale` (rozciągnięcie w pionie).
- **`UndergroundBiomeRarity` → pokrycie %**: reinterpretacja jako ułamek objętości. Mapowanie zlinearyzowane empiryczną kwantylą rozkładu szumu (`coverage 20` ≈ realnie 20% objętości), bo surowy Perlin kumuluje się koło 0.5.
- **Niezależność od powierzchni**: placement steruje szum 3D + głębokość; `MinSurfaceTemperature/Wetness` zostają jako opcjonalny miękki filtr (domyślnie pełny zakres = brak sprzężenia). Płynne wyciszenie blisko powierzchni zamiast twardego cięcia.
- **Usunięty cheese-air gate**: biom zajmuje całą komórkę 3D regionu (nie tylko powietrze), więc vanilla `applyBiomeDecoration` wypełnia regiony feature'ami (mech/dripstone/sculk) — naprawia "małe losowe płaty".
- **Pełna kontrola OTG (Level 2)**:
  - `OTGChunkGenerator.populateNoise` konwertuje `StoneBlock` per biom podziemny w całym regionie (kształt terenu bez zmian) — mapa `UndergroundBiomeMap` (quart res) liczona raz na chunk.
  - Kolejki resource OTG (`Ore`/`Liquid`/`Dungeon`/…) biomów podziemnych wykonują się **zmaskowane do regionu** (`IWorldGenRegion.begin/endUndergroundBiomeMask` + guard na `setBlock`). Resource na granicy regionu mogą być przycięte (strategia C1).
- **Spójność seeda**: resolver biome source przebudowywany w `setSeed`, żeby F3/vanilla feature'y zgadzały się z konwersją bloków i resource OTG.
- **DefaultPreset**: lush/dripstone/deep_dark odsprzężone i dostrojone; `MinorVersion` 0.1→0.2 wymusza ponowne wypakowanie presetu u istniejących userów.

**2026-05-15 — Fix drzew w nowych biomach**

- `Registry(minecraft:trees_*)` zamienione na natywne `Tree()` syntax — pewniejsze mapowanie przez `TreeType` enum
- Cherry Grove: `Tree(10,Cherry,100)` (TreeFeatures.CHERRY)
- Mangrove Swamp: `Tree(8,TallMangrove,30,Mangrove,100)` (TreeFeatures.TALL_MANGROVE / MANGROVE)
- Grove: `Tree(10,Taiga2,80,Taiga1,100)` (spruce + pine)
- Meadow: `Tree(1,Tree,30,Birch,100)` (rzadki oak + birch)
- Flower features (`flower_meadow`, `flower_cherry`) zostawione jako `Registry()` — brak natywnego OTG mappingu

**2026-05-15 — Fix BiomeHeight nowych biomów**

- Wartości BiomeHeight 7.0/7.5 dla Frozen/Jagged Peaks generowały biomy poza build limitem — odwzorowane były z konwencji Biome Bundle, nie DefaultPreset
- Skala DefaultPreset: Plains 0.25 / Hills 0.45 / Mountain Edge 0.8 / Mountains 1.0 (cap)
- Nowe wartości: Meadow 0.5, Grove 0.5, Cherry Grove 0.6, Snowy Slopes 1.3, Stony Peaks 1.5, Frozen Peaks 1.8, Jagged Peaks 2.0
- BiomeVolatility też okrojone: Jagged Peaks z 0.7 na 0.6, reszta dopasowana do biomów hills/mountains z istniejącego presetu

**2026-05-15 — DefaultPreset: 8 nowych waniliowych biomów (1.18-1.20)**

- **Biomy 1.18 mountains**: Meadow, Grove, Snowy Slopes, Frozen Peaks, Jagged Peaks, Stony Peaks
- **Biome 1.19**: Mangrove Swamp (mud ground, swamp grass modifier, foliage 0x8DB127, water 0x3A7A6A)
- **Biome 1.20**: Cherry Grove (grass+foliage 0xB6DB61, water 0x5DB7EF, pink_petals)
- **BiomeGroups**: dodane do NormalBiomes (Meadow, Cherry Grove, Mangrove Swamp), ColdBiomes (Grove), HotBiomes (Stony Peaks), IceBiomes (Snowy Slopes)
- **IsleBiomes**: Frozen Peaks i Jagged Peaks jako wyspy w Snowy Slopes (rarity 80)
- **Surface bloki specjalne**: Stony Peaks → stone, Frozen Peaks → packed_ice, Snowy Slopes/Jagged Peaks → snow_block over stone, Grove → snow_block, Mangrove Swamp → grass_block over mud
- **Vegetation via vanilla feature Registry**: trees_meadow/flower_meadow, trees_grove, trees_mangrove/mangrove_vegetation/seagrass_swamp, trees_cherry_grove/flower_cherry
- Music tracks i mob spawning dziedziczone przez InheritMobsBiomeName z odpowiednich vanilla ID

**2026-05-15 — In-game editor: DimensionPreset creation**

- **ManageDimensionPresetsScreen**: lists all DimensionPresets with summary (folder, display name, registry name, author, description, biome count); CRUD buttons (New/Clone/Edit/Delete). Edit opens existing `WorldSettingsScreen`. DefaultPreset is delete-disabled. Delete warns when WorldPreset YAMLs reference the preset by `PresetFolderName`.
- **DimensionPresetWizardScreen**: 4-step creation wizard — template picker ("Blank Minimal" = DefaultPreset baseline + every preset on disk), metadata (DisplayName/FolderName/RegistryName/Author/Description with auto-suggest), biome strategy info, confirm. Finish copies the template folder, patches identity fields, reloads presets, and opens `WorldSettingsScreen` on the new preset.
- **Data layer**: `DimensionPresetOperations` (newFromTemplate / cloneFrom / delete / findWorldPresetsReferencing / suggestFolderName / patchIniSettings — preserves comments and structure when rewriting identity fields), `DimensionPresetTemplates` (lists DefaultPreset as Blank Minimal + every other preset on disk).
- **EditorHubScreen**: "Manage DimensionPresets" button stacked above "Manage WorldPresets" near preset selector; main cards shifted down by 22px to make room.

**2026-04-13 — In-game editor Phase 5: WorldPreset YAML editor**

- **ManageWorldPresetsScreen**: lists all `WorldPresets/*.yaml` with summary panel; CRUD buttons (New/Clone/Edit/Delete)
- **WorldPresetEditorScreen**: tabs-based editor for single WorldPreset YAML — Metadata, Overworld, Nether, End, Dimensions, Settings, GameRules (50 rules with tri-state editing)
- **WorldPresetWizardScreen**: 4-step creation wizard — template picker (Blank + shipped resource YAMLs), metadata, dimensions placeholder, confirm
- **GameRulesEditorScreen**: standalone screen for per-dimension GameRules overrides
- **New widgets**: `DimensionSlotWidget` (OTG/Non-OTG toggle + preset cycle + portal config), `DimensionAccordionCard` (expandable card per custom dimension), `GameRuleTriStateWidget` (null/true/false radios, null/int), `GameRulesListWidget` (scrollable list with search, reflection-based)
- **Data layer**: `WorldPresetYamlIO`, `WorldPresetFileScanner`, `WorldPresetOperations`, `WorldPresetTemplates`
- **DimensionManager**: now tracks `activeWorldPresetConfigPath` for file-path-based matching
- **WorldPresetRegistrar.normalizeId()**: widened to public for editor reuse
- **EditorHubScreen**: new "Manage WorldPresets" button near preset selector

**2026-04-02 — Resource queue editing**

- **ResourceQueueScreen**: Full-screen editor for resource queue entries (Ore, Tree, CustomObject, etc.) — inline text editing per entry, add/delete/reorder with ▲/▼ buttons, colored function type indicators
- **ResourceEntry**: Mutable wrapper for ConfigFunction lines with dirty/deleted tracking
- **ConfigWriter.saveWithResources()**: Saves properties + resource queue entries to .bc files, replacing original resource lines in-place
- **BiomeEditorScreen**: "Resources (N)" button opens ResourceQueueScreen, save writes modified resources back to .bc

### Release: 0.5.0-dev2

---

**2026-04-02**

- feat: NonOTGWorldType now supports all vanilla world types (`flat`, `amplified`, `large_biomes`) and modded types (`modid:name`). Looks up WorldPreset in MC's registry instead of always creating standard vanilla overworld. Falls back to normal if preset not found.

---

### Release: 0.5.0-dev1

**2026-03-30 — Editor codebase refactoring**

- **Viewport3DRenderer**: Extract shared 3D viewport GL code (scissor, viewport calc, depth clear, 4 RenderType draws, state restore) from PreviewScreen, BOBrowserScreen, and BiomeTerrainPreviewScreen into single static utility
- **ScrollablePanel**: Abstract base class for ScrollableListWidget and TreeListWidget — shared scrollbar rendering, scroll clamping, drag handling, mouse wheel
- **PresetReloader**: Static `reload()` utility replaces 3 identical `reloadPresets()` methods across editor screens
- **PropertyGridMode**: Enum (PRESET_EDITOR, BIOME_EDITOR, GROUP_EDITOR) replaces 3-boolean constructor args on PropertyGridWidget
- **BiomeHeightmapGenerator**: Split 155-line `generate()` into `readTerrainParams()` + `computeNoiseColumns()` + `interpolateAndPlaceBlocks()` + `findSurfaceY()` + `placeColumnBlocks()`, added TerrainParams record; generic `getProperty()` replaces 3 near-identical typed helpers
- **BiomeEditorScreen/GroupSettingsScreen**: Split long `init()` methods into focused helpers (initBiomeList, initPropertyGrid, initBottomBar, etc.)
- **SharedWorldGenRegion**: Move identical `fromBlockState`/`toBlockState`/`convertNBT` from Fabric/NeoForge into concrete shared implementations
- **SharedNBTHelper**: Move identical `getNBTFromLocation` into shared base — both platform NBTHelper subclasses now empty
- **Error logging**: Add LOG.warn/error for previously silent failures in BiomeEditorScreen (ini read), WorldSettingsScreen (config fallback), BiomeTerrainPreviewScreen (buffer release), PreviewScreen (preset loading)
- **DRY EditBox registration**: Unify init-time PropertyGrid EditBox registration via `refreshPropertyEditBoxes()` across all 3 editor screens

**2026-03-21 — In-game editor Phase 3: Group Settings**

- **GroupSettingsScreen**: Three-column BiomeGroup editor — group list (left), group params + biome assignment (center), PropertyGridWidget with Override/Merge/OPV flags (right)
- **Group CRUD**: New/Delete groups, edit depth/rarity/temperature range, assign/remove biomes via dual-list with arrow buttons
- **Property overrides**: Per-group biome property overrides stored in `.otg-editor.json` via GroupOverrideStore, loaded into PropertyGrid with Override/Merge/OPV toggles
- **Save**: Writes group lines to .ini via `ConfigWriter.saveBiomeGroups()` + overrides to JSON via `GroupOverrideStore.save()`
- **EditorHubScreen**: Group Settings button now active
- **BiomeEditorScreen override integration**: Save resolves group overrides via `OverrideResolver` before writing .bc — Override/Merge/OPV flags from GroupSettings flow through to biome files

**2026-03-20 — In-game editor Phase 4: BO Store with 3D Preview**

- **BOBrowserScreen 3D viewport**: Embedded 3D BO2/BO3/BO4 preview — select object in tree, renders in right panel via PreviewRenderer/PreviewWorld/OrbitCamera. Drag to orbit, scroll to zoom.
- **Direction buttons**: N/S/E/W snap buttons for camera orientation in BO preview
- **Metadata panel**: Object name, type, size (XxYxZ), block count displayed below viewport
- **Assign to Biome**: BiomeSelectDialog picker + appends `CustomObject(100, name)` to selected biome's .bc file
- **BOBounds expanded**: Now includes blockCount, sizeX/Y/Z for metadata display
- **OrbitCamera setters**: `setTheta()`/`setPhi()` for direction snapping
- **BO Store button**: Enabled in EditorHubScreen, opens BOBrowserScreen from hub
- **BOBrowserScreen parent navigation**: Constructor takes `Screen parent` for correct back-nav from both Hub and BiomeEditor

**2026-03-20 — Editor polish & review fixes**

- **TreeListWidget**: Collapsible folder tree for BO browser — folders expand/collapse on click (▶/▼), sorted folders-first, search auto-expands matching branches
- **Scrollbar**: Visual scrollbar with click-to-jump and drag support on ScrollableListWidget (biome list) and TreeListWidget (BO browser)
- **PropertyGridWidget.syncEditBoxes()**: DRY helper eliminates duplicate `refreshPropertyEditBoxes()` in WorldSettingsScreen and BiomeEditorScreen
- **Resource queue display**: BiomeEditorScreen shows collected ConfigFunction lines (Ore, Tree, etc.) read-only below property grid with scroll
- **PropertyCategory**: Unique display names — `BIOME_TERRAIN` → "Biome Terrain", `BIOME_STRUCTURES` → "Biome Structures"
- **BiomeEditorScreen**: Index-based biome lookup via `filteredBiomeEntries` — fixes wrong file loaded/deleted when subdirectory biomes share names
- **BiomeEditorScreen**: Delete confirmation — double-click required, resets on biome select/new/clone/save
- **PropertyExtractor**: DRY — `extractPresetDefinitions()`/`extractBiomeDefinitions()` delegate to shared `extractDefinitions()` helper
- **BOBrowserScreen**: Rescan guard + legacy `WorldObjects/` folder fallback
- **PropertyGridWidget**: Null guard for uninitialized tabs (no biome selected crash fix)

**2026-03-19 — In-game editor Phase 2: Biome Editor**

- **BiomeEditorScreen**: Split-pane biome editor — left panel with searchable biome list, right panel with PropertyGridWidget for .bc file editing (category tabs, search, Override/Merge toggles)
- **Biome CRUD**: New (generates .bc from defaults), Clone (file copy), Delete — all update biome list immediately
- **BiomeFileScanner**: Discovers .bc/.biome files in preset's Biomes/ folder with legacy WorldBiomes/ fallback
- **Biome property extraction**: `PropertyExtractor.extractBiomeDefinitions()` scans 10 biome ConfigSection subclasses including annotation-generated BiomePlacementSettings/BiomeStructureTagSettings
- **ConfigLoader resource queue**: Collects ConfigFunction lines (Ore, Tree, CustomObject) for read-only display
- **ConfigWriter.createFromDefaults()**: Generates fresh .bc files grouped by category for new biome creation
- **BOBrowserScreen**: Simple BO3/BO4 file browser with search and scrollable list
- **Enum dropdowns**: Click on enum property opens overlay dropdown with value list instead of cycling
- **EditorHubScreen**: Biome Editor button now active

**2026-03-19 — In-game editor Phase 1: UI framework + World Settings**

- **EditorHubScreen**: Hub screen with DimensionPreset selector (◀/▶ cycling), 4 navigation cards (World Settings active, Biome/Group/BO Store disabled for future phases), Preview World button
- **WorldSettingsScreen**: Full property grid editor for `DimensionPresetConfig.ini` — category tabs, search filter, type-dependent editors (boolean toggle, enum cycler, text input), save to disk with comment/structure preservation
- **Data layer**: `PropertyType`/`PropertyCategory` enums, `PropertyDefinition` record, `PropertyValue` with dirty tracking, `PropertyExtractor` (reflects OTG Setting classes), `ConfigLoader`/`ConfigWriter` (round-trip .ini parsing)
- **Widget layer**: `ScrollableListWidget`, `CategoryTabsWidget`, `SearchBoxWidget`, `PropertyRowWidget`, `PropertyGridWidget` — reusable components for future editor phases
- **TitleScreenMixin**: "OTG Editor" button now opens EditorHubScreen instead of PreviewScreen

---

### Release: 0.4.0-dev2

**2026-03-05 — In-game editor & preview system (WIP)**

- **Client source sets**: Added `splitEnvironmentSourceSets()` to shared and fabric modules; client-only code compiles separately from server code
- **OTG Editor button**: TitleScreenMixin injects "OTG Editor" button on the title screen, opens empty PreviewScreen
- **OrbitCamera**: Spherical coordinate camera with rotate/zoom/fitTo for 3D terrain preview
- **PreviewWorld**: `BlockAndTintGetter` implementation backed by `PreviewChunk` array — stores blocks + biomes copied from generated chunks, full brightness fake lighting, biome tint support
- **TempServerManager**: Uses MC's native `createFreshLevel` to spin up IntegratedServer with selected OTG preset; auto-cleans temp saves on stop
- **ChunkGenerationManager**: Spiral-order chunk generation from ServerLevel, feeds chunks into PreviewWorld with progress callbacks
- **PreviewRenderer**: Compiles block meshes via MC's `BlockRenderDispatcher.renderBatched()` into per-section VBOs, renders using `CHUNK_OFFSET` uniform pattern matching MC's `LevelRenderer.renderSectionLayer`
- **PreviewState**: Static state machine (IDLE → WAITING → GENERATING → COMPILING → DONE) that survives screen transitions during `createFreshLevel` flow
- **PreviewScreen**: Full UI with seed input, size/generation level selectors, 3D viewport with orbit camera, generate/clear/back controls
- **ClientTickMixin**: Polls `PreviewState.tick()` to detect when server is ready for chunk generation
- **BO3/BO4 preview**: `BOPreviewHelper` loads custom objects via `OTG.getEngine()` managers, iterates block functions, resolves `IBlockStateMaterial` → `BlockState`, places into PreviewWorld with direct `setBlockState`. Camera auto-fits to object bounds.
- **Preset selector**: Cycles through available DimensionPreset folder names in PreviewScreen
- **Server lifecycle**: TempServerManager stays alive after terrain generation — reused between preview modes, only stops on reset()/screen close
- **Viewport fixes**: `glViewport` set to panel area (was projecting on full window), depth buffer cleared before 3D render, depth test restored after pass, `onClose()` calls `PreviewState.reset()`, `compileSection()` wrapped in try-finally for ByteBufferBuilder leak prevention, `phase`/`statusText` marked volatile
- **Progress bar**: Visual progress bar in viewport during chunk generation
- **Error handling**: OOM catch with cleanup, 60s server start timeout, JVM shutdown hook cleans temp world saves on crash

**2026-03-03–04 — Portal overrides & GameRule fixes**

- **SharedMaterialData interface**: Now implements IBlockStateMaterial for cross-module material comparison
- **WorldPreset portal overrides**: Portal configuration (frame block, ignition item, color) can be overridden per-dimension in WorldPreset YAMLs
- **Portal gating**: Dimensions can disable portal creation/travel entirely via YAML config
- **Respawn-in-dimension mixin**: Players respawn in the OTG dimension they died in (if configured) instead of always respawning in the overworld
- **GameRules YAML override fix**: Fixed bug where world-level GameRule overrides in YAML weren't being applied
- **Default.yaml**: Added default WorldPreset YAML that ships with the mod

**2026-03-03 — Compatibility & display names**

- **Legacy custom objects**: UseWorld/UseBiome custom objects now handled gracefully instead of crashing
- **FeatureSorter cycle crash**: Deduplicated Registry() features to prevent cycle in MC's FeatureSorter (caused infinite loop during biome feature ordering)
- **Portal ClassCastException**: Fixed crash when portal frame block resolution encounters non-block materials
- **WorldPreset display names**: Dynamic display names via Language mixin — WorldPreset names show localized in the MC world creation GUI instead of raw YAML filenames

**2026-03-02 — Code review fixes**

- Nullable DimensionConfig overrides, error handling improvements
- Various correctness fixes from two rounds of code review

**2026-03-01 — WorldPreset system**

Renamed Preset → DimensionPreset, DimensionConfig → WorldPresetConfig across 188 files, 13 classes.

- **WorldPreset YAML**: New config format that composes a full world from multiple DimensionPresets. Defines which presets go in which dimensions, with per-dimension overrides.
- **WorldPresetRegistrar**: YAML configs registered as Minecraft WorldPresets — appear in the world creation GUI alongside vanilla presets.
- **WorldPresetConfigLoader**: Loads all YAMLs from `WorldPresets/` folder.
- **3-layer GameRules**: DimensionPresetConfig.ini → YAML world-level → YAML per-dimension. Each layer can override individual rules.
- **Folder renames**: `Presets/` → `DimensionPresets/`, `DimensionConfigs/` → `WorldPresets/`, `PresetConfig.ini` → `DimensionPresetConfig.ini`

---

### Release: 0.4.0-dev1

**2026-03-01 — GameRules per-dimension**

Full per-dimension GameRules system:

- **LevelGameRulesMixin**: Intercepts `Level.getGameRules()` to return dimension-specific rules. Works on both Fabric and NeoForge via shared mixin.
- **GameRuleManager**: Static map of dimension → GameRules, populated during dimension creation, cleared on server stop.
- **GameRuleApplier**: Merges GameRules from 3 layers — DimensionPresetConfig.ini (base) → WorldPreset YAML world-level → WorldPreset YAML per-dimension.
- **52 GameRule settings**: 32 original + 20 new 1.21.1 rules (ENDER_PEARLS_VANISH_ON_DEATH, DO_VINES_SPREAD, PLAYERS_SLEEPING_PERCENTAGE, etc.)
- **OTGWorldStorage**: Renamed from DimensionStorage, v2 format persists dimensions + GameRules to `otg_world_data.json`.
- Fixed copy-paste bug where DO_MOB_SPAWNING getter returned DO_MOB_LOOT value.

**2026-02-19 — BO4Config split**

Split monolithic BO4Config.java (1769 LOC) into focused components:

- **BO4BlockStorage** (326 LOC): Block data management and material resolution
- **BO4DataSerializer** (485 LOC): Binary serialization/deserialization
- **BO4ConfigWriter** (292 LOC): INI file writing
- BO4Config reduced to 733 LOC — just config loading and field access

**2026-02-18 — Logger refactor & BO4Config cleanup**

- **Unified logger**: Merged 3 separate logger implementations (Logger, OTGLogger, BasicLogger) into single OTGLogger. Added LogFormatter with `{}` placeholder support and lazy evaluation. ILogger.init() changed from 8-boolean to EnumSet<LogCategory>. ~416 call sites migrated.
- **BO4Config cleanup**: Extracted helpers, added try-with-resources, removed dead code. Preparation for BO4Config split.
- **BLANK material null-guard**: Fixed NPE when SharedWorldGenRegion.setBlock encounters BLANK material data.

**2026-02-17 — Mixin deduplication & more shared extraction**

- Moved 6 shared mixins to `platforms/shared/` (BiomeDataMixin, WorldPresetTagsMixin, LevelGameRulesMixin, ChunkAccessAccessor, MappedRegistryAccessor, MinecraftServerAccessor)
- Extracted SharedNBTHelper (~270 lines deduplicated), SharedOTGBiomeProvider (~160 lines)
- Collapsed BiomePlatformAdapter into shared — both platform implementations were identical
- Deleted dead code: OTGTemplateHandler, ShowWorldPresetsCommand, BiomeSyncWrapper
- Swamp biome flattened to 50/50 water/land ratio

**2026-02-16 — Biome loading redesign**

Decomposed the monolithic SharedLegacyBiomeLoader into clean, testable components:

- **BiomePlanResolver**: Pure logic for resolving biome assignments from preset config. 7 unit tests.
- **BiomePlan**: Immutable data class for biome resolution output.
- **BiomeFactory**: Creates MC Biome objects from OTG biome configs.
- **BiomeRegistrar**: Isolates MC registry mutation.
- **SharedDimensionPresetBiomeLoader**: Orchestrates the pipeline via composition.
- Deleted 430-line BiomeRegistryNames class frozen at 1.16.5 biome names.
- Fixed bit packing validation, ocean temperature index inversion, dynamic biome array sizing.

**2026-02-16 — Platform deduplication & cleanup**

Massive refactoring day — extracted most runtime logic from Fabric/NeoForge into `platforms/shared/`:

- SharedOTGChunkGenerator, SharedWorldGenRegion, SharedLegacyBiomeLoader
- Portal system, material classes (MaterialData, MaterialReader, Materials, MaterialTag, LegacyMaterials)
- ShadowChunkGenerator, ChunkBuffer, Biome, DimensionHelper
- **Deleted legacy Forge platform**: 74 files, ~17k lines of dead code targeting old Forge (not NeoForge). Fabric + NeoForge only going forward.
- Cleaned up dead interfaces, abandoned event hooks, dead EntityCategory enum, unused biomeColorMap, debug code

**Bug fixes**

- **Dynamic world bounds**: Replaced remaining deprecated WORLD_DEPTH/WORLD_HEIGHT with runtime world bounds
- **printStackTrace cleanup**: Replaced all bare `printStackTrace()` calls with structured OTGLog logging
- **NeoForge backports**: 3 bugfixes that were Fabric-only ported to NeoForge, removed duplicate NoiseParamRegistry
- **RuntimeException bombs**: Replaced 4 RuntimeException throws in BO4CustomStructure with OTGLog.error (mod no longer crashes server on recoverable BO4 errors)
- **River generation**: Fixed rivers not generating when RandomRivers=false
- **Structure tag injection disabled**: Temporarily disabled biome→structure tag injection due to bindTags() performance bug on NeoForge (~60s stall)

---

### Release: 0.2.0-dev5

**2026-02-15 (cont.) — FromImage fix, BO connection states**

- **FromImage OOM**: Fixed OutOfMemoryError when using FromImage biome mode — biome feature ordering cycle caused exponential memory growth.
- **BO connection states**: Updated block connection states for glass panes, iron bars, fences, and walls in BO objects. Objects placed in-world now correctly connect to adjacent blocks instead of floating as standalone pillars.

---

### Release: 0.2.0-dev4

**2026-02-15 — Commands, structures, terrain fixes**

- **UndergroundBiomeRarity**: New config setting to control underground biome spawn frequency.

**BO2/3/4 Commands**

- `/otg flushcache` — clear all cached custom objects
- `/otg spawn <object>` — spawn a BO2/BO3/BO4 at player position
- `/otg structure <object>` — start BO4 structure from branch
- `/otg export [template] [-e excludes] [-t tileentities]` — export selection to BO3 (WorldEdit integration)
- `/otg exportbo4data` — export BO4 data files for all custom objects
- Command infrastructure with CommandWorldAccessor for cross-platform world access

**Vanilla structures in OTG biomes**

- Inject OTG biomes into vanilla structure biome tags on both Fabric and NeoForge
- Villages, strongholds, witch huts, etc. now generate in OTG biomes that match the right temperature/category
- BiomeStructureTagConfig stored during biome registration, StructureTagMapper maps biomes to structure tags

**Bug fixes**

- **Village buildings missing**: JigsawStructureData delta parameter was using bounding box maxY instead of ground level delta, causing massively wrong terrain density around structures — villages only generated paths/farmland, no buildings
- **Steep biome borders**: Implemented vanilla weight halving for biome height blending. Neighbors with higher BiomeHeight get blending weight halved, creating softer transitions instead of cliffs
- **BO3 spawn cache**: spawnForced now properly registers objects in structure cache
- **BO4 structure plotting**: Fixed spiral chunk search and plotBo4Structure() cache integration
- **Beard fill (structure terrain)**: Fixed terrain adaptation around structures — ground under buildings was being carved instead of filled
- **Swamp flattening**: Swamp biome terrain now properly flattened

---

### Release: 0.2.0-dev3

**2026-02-13–14 — 3D underground biomes & cave improvements**

- **3D underground biomes**: Full system for assigning different biomes underground based on Y level and cheese cave noise. Biomes like Lush Caves, Dripstone Caves, Deep Dark appear only inside actual cave voids, not in solid rock.
  - UndergroundBiomeResolver with pre-computed lookup tables
  - Surface height estimation to determine underground threshold
  - Underground biomes excluded from 2D surface layer system
  - Example configs included (LushCaves, DripstoneCaves, DeepDark)
- **Surface-relative cave suppression**: Caves now thin out gradually near the surface using quadratic falloff instead of a hard cutoff. Prevents cheese holes in mountain tops while still allowing cave breakthroughs in valleys.
- **NeoForge carver unification**: Both platforms now use identical noise evaluation for cave carving.

**2026-02-12 — Noise cave carving**

- **1.18+ noise caves**: Added configurable noise cave density carving. Caves now use 3D noise sampling instead of just legacy carvers.
- **Thread-safe CustomObjectResource**: Fixed lazy init race condition that caused crashes with C2ME parallel chunk loading.

---

### Release: 0.2.0-dev2

**2026-02-08 — NeoForge platform & compatibility fixes**

- **NeoForge 1.21.1 support**: Multi-loader build from single codebase via Architectury. NeoForge platform with DeferredRegister, Data Attachments (replacing CCA), NeoForge event bus wiring.
- **C2ME compatibility**: Replaced FifoMap with ThreadSafeLRUCache to fix ConcurrentModificationException when C2ME workers access CustomStructureCache concurrently.
- **RegistryLoaderMixin fix**: Added @Local(ordinal=1) to disambiguate List type erasure, guard against client-side registry sync.

---

### Release: 0.2.0-dev1

**2026-02-07 — MC 1.21.1 port**

Full port from 1.20.1 to 1.21.1 (Java 21, Fabric API 0.116.7, Architectury 13.0.8):

- Migrated ResourceLocation constructors to static factory methods (MC 1.21 change)
- Migrated ChunkGenerator/BiomeSource codecs to MapCodec (MC 1.20.5+ change)
- Removed Executor parameter from fillFromNoise (MC 1.21 change)
- Rewrote RegistryLoaderMixin for 1.21.1 RegistryDataLoader changes — now uses @Local from MixinExtras instead of LocalCapture
- Updated WorldPresetTagsMixin for updateRegistryTags() signature change
- Fixed BootstapContext → BootstrapContext typo (MC fixed their own typo in 1.20.5)

---

## Minecraft 1.20.1

### Release: 0.2.0-dev10

**2026-02-06 — Thread-safe chunk generation**

- **Caffeine caching**: Replaced all FifoMap/LinkedHashMap caches with Caffeine-backed ThreadSafeLRUCache. Fixes ConcurrentModificationException with C2ME.
- **Synchronous fillFromNoise**: Removed ShadowChunkGenerator worker threads. Vanilla's ForkJoinPool already parallelizes chunk generation — same approach C2ME uses. Shadow gen kept only for BO4 objects.
- **C2ME compatibility**: OTG now works alongside C2ME for multiplayer chunk generation scaling.

**2026-02-04 — Performance optimization (benchmark-driven)**

Built a headless terrain snapshot testing system with benchmark command for measuring generation throughput without running Minecraft.

Optimizations applied (with before/after measurements):

- **Flatten GRAD array**: Converted 2D int[][] to flat int[] in SimplexNoiseSampler for better CPU cache locality.
- **BiomeSettings cache**: Cache per-chunk instead of per-block — reduces getBiomeSettings() calls from 98,304 to 256 per chunk (384x reduction).
- **Noise buffer reuse**: Pre-allocate double[][][] via ThreadLocal instead of 4KB allocation per chunk.
- **Bit shifts in ChunkCoordinate**: Replace `* 16` with `<< 4`, use Math.floorDiv for region coords. (Credit: Meldexun/OTG)
- **LRU cache**: Replaced FifoMap with LRUCache that evicts least-recently-used instead of oldest entries. Better hit rates for temporal locality patterns. (Credit: Meldexun/OTG)
- **O(log n) biome selection**: TreeMap.higherEntry() instead of linear iteration. (Credit: Meldexun/OTG)
- **SurfaceSettings cache**: Avoid 3 redundant method calls per block in populateNoise inner loop.
- **Hoist MutableBoolean**: Move allocation outside carver loops — eliminates 400-900 allocations per carve call.
- **Eliminate ThreadLocal boxing**: Replace ThreadLocal<Integer>/ThreadLocal<Double> with single ThreadLocal holding primitive fields.

**2026-02-03 — Portals, CI/CD, mob spawning**

- **Nether-style portals**: Implemented portal system for OTG dimensions — build a portal frame, light it, teleport between dimensions. Portal frame blocks and linking logic are configurable per preset.
- **Portal refactors**: Extracted PortalConfigResolver, PortalConfigLookup, DimensionKeys, DimensionNameUtils to shared modules for future NeoForge reuse.
- **CI/CD**: GitHub Actions workflow with auto-release on push. Builds both platforms, uploads artifacts.
- **Mob spawning**: Implemented mob spawning during chunk generation on Fabric (was previously missing).

**2026-02-02 — Dynamic dimensions system**

- **Runtime dimension creation**: Full system for creating OTG dimensions at runtime via datapack generation. Core types, platform interfaces, Fabric dimension manager, safe spawn logic, teleport command.
- **Dimension JSON handling**: Streamlined dimension type and noise settings JSON generation.

**2026-02-01 — Terrain tuning & deepslate**

- **Terrain constant tuning**: Adjusted BiomeHeight, BiomeVolatility, and noise parameters for 1.18+ terrain that no longer looks like a jagged mess.
- **VolatilityWeight fix**: Increased VolatilityWeight values from 0-1 range to ~48 range — the low values caused horrible jagged terrain.
- **DEEPSLATE material**: Added deepslate to the material registry for proper resource generation below Y=0.

**2026-01-24 — Terrain smoothing**

- **SmoothRadius increase**: Bumped smoothing radius for better terrain transitions between biomes.
- **Biome tag mapping**: Added FabricBiomeTagMapper to map OTG biomes to vanilla structure tags, enabling villages/strongholds/etc. in OTG biomes.

**2026-01-23 — World height & crash fixes**

- **Dynamic world height**: Replaced hardcoded WORLD_DEPTH/WORLD_HEIGHT constants with runtime MinY/MaxY from the world. Fixes terrain generation on custom-height worlds (1.18+ changed overworld to Y -64..319).
- **Crash safeguards**: Added null-checks and bounds validation throughout chunk generation to prevent empty chunks and NPEs during terrain population.
- **Tree spawning materials**: Added MaterialSet support so tree objects can validate block placement against a set of allowed materials.
- **OTG dimension type**: Expanded PresetSettings with dimension type fields (fixed time, respawn anchor, etc.) for custom OTG dimensions.
