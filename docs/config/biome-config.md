# Biome Config (.bc)

A `.bc` file defines a single biome: its identity, where it spawns, terrain shape, surface and stone blocks, colours and sounds, mob spawns, vanilla structures, underground-biome behaviour, and its resource queue.

Biome files live in the preset's biome folders, e.g. `DimensionPresets/<PresetName>/Biomes/<BiomeName>.bc` (subfolders such as `Biomes/Underground/` are allowed and purely organisational).

For preset-wide (dimension-level) settings — biome distribution, world cave settings, dimension properties — see [DimensionPresetConfig.ini](dimension-preset-config.md). Resource spawning (ores, plants, trees, BO objects, dungeons, lakes) uses a separate syntax documented on the [Resource Queue](resource-queue.md) page.

!!! tip "In-game editor"
    Every setting on this page is also editable in the in-game **Biome Editor**, which reflects over the same config definitions — newly added settings appear automatically under the relevant tab.

## Materials

Block settings accept either modern resource locations (`minecraft:grass_block`, `modid:block`) or legacy OTG names (`GRASS_BLOCK`, `STONE`). Colours are hex, written as `0xRRGGBB`.

---

## Identity

Settings that define a biome's name and how it appears in the game UI.

### DisplayName

Controls what name appears in the F3 overlay and similar info screens. Defaults to the biome file name (with spaces inserted before capital letters) if left blank.

| Property | Value |
|----------|-------|
| Type | `string` |
| Default | `""` |

---

## Biome Tags

Settings that classify a biome for use by tag generation and other mods.

### BiomeType

Determines the base terrain block used for this biome and which dimension it belongs to. `OVERWORLD` uses grass blocks, `NETHER` uses netherrack, `END` uses end stone.

| Property | Value |
|----------|-------|
| Type | `enum` |
| Default | `OVERWORLD` |
| Options | `OVERWORLD`, `NETHER`, `END` |

### BiomeTags

A list of tag strings used by other mods to identify this biome and place modded blocks, items, and mobs. Tags also drive OTG's internal biome-property flags (e.g. `HOT`, `DRY`, `SANDY`).

At world load, these are bound to the Minecraft biome tag registry as the matching vanilla tags (`minecraft:is_forest`, `minecraft:is_taiga`, …) and `c:` convention tags (`c:is_snowy`, `c:is_cold/overworld`, …). Together with the dimension tag derived from `BiomeType` (`minecraft:is_overworld`/`is_nether`/`is_end` plus the `c:` counterpart), this is what makes tag-targeted biome modifiers from other mods (modded ores, mob spawns) apply to OTG biomes. Biomes with `TemplateForBiome: true` are unaffected — they reuse an existing registered biome that already carries its own tags.

| Property | Value |
|----------|-------|
| Type | `string list` |
| Default | `""` (empty) |

```properties
BiomeTags: HOT, DRY, SANDY, OVERWORLD
```

---

## Placement

Settings that control where and how often a biome spawns, its map colour, and its river/isle/border relationships.

### BiomeSize

Determines which biome layer this biome generates in, relative to `GenerationDepth`. Higher numbers produce smaller biomes; lower numbers produce larger ones. Isle and border biomes use their own size overrides.

| Property | Value |
|----------|-------|
| Type | `int` |
| Default | `4` |
| Range | `0` – `20` |

### BiomeRarity

Spawn weight for this biome relative to others in the same layer. A value of 100 gives roughly a 1-in-6 chance when six normal biomes exist; 50 gives roughly 1-in-11. Has no effect on Ocean or River biomes unless they are also added as normal biomes.

| Property | Value |
|----------|-------|
| Type | `int` |
| Default | `100` |
| Range | `0` – `2147483647` |

### BiomeMapColor

Hex colour used in the output of `/otg map` and as the pixel colour when `BiomeMode` is set to `FromImage`.

| Property | Value |
|----------|-------|
| Type | `color` |
| Default | `0xFFFFFF` |

```properties
BiomeMapColor: 0x3F76E4
```

### RiverBiome

The biome that replaces rivers passing through this biome.

| Property | Value |
|----------|-------|
| Type | `string` |
| Default | `River` |

### IsleInBiomes

List of biomes inside which this biome may spawn as an island. The biome must also be listed in `IsleBiomes` in `DimensionPresetConfig.ini`.

| Property | Value |
|----------|-------|
| Type | `string list` |
| Default | `Ocean` |

```properties
IsleInBiomes: Ocean, DeepOcean
```

### BiomeSizeWhenIsle

Biome layer size used when this biome spawns as an isle (in `BiomeMode: Normal`). Must be a larger number than the host biome's `BiomeSize` so the island is physically smaller.

| Property | Value |
|----------|-------|
| Type | `int` |
| Default | `6` |
| Range | `0` – `20` |

### BiomeRarityWhenIsle

Spawn rarity used when this biome spawns as an isle in `BiomeMode: Normal`.

| Property | Value |
|----------|-------|
| Type | `int` |
| Default | `97` |
| Range | `0` – `2147483647` |

### BorderInBiomes

List of biomes that this biome can border. The biome must also be listed in `BorderBiomes` in `DimensionPresetConfig.ini`.

| Property | Value |
|----------|-------|
| Type | `string list` |
| Default | *(empty)* |

```properties
BorderInBiomes: Ocean
```

### OnlyBorderNear

Whitelist of neighbouring biomes that must be adjacent for this border biome to spawn. When non-empty, `NotBorderNear` is ignored.

| Property | Value |
|----------|-------|
| Type | `string list` |
| Default | *(empty)* |

```properties
OnlyBorderNear: Plains, Forest
```

### NotBorderNear

Blacklist of neighbouring biomes that prevent this border biome from spawning. Only consulted when `OnlyBorderNear` is empty.

| Property | Value |
|----------|-------|
| Type | `string list` |
| Default | *(empty)* |

```properties
NotBorderNear: ExtremeHills
```

### BiomeSizeWhenBorder

Biome layer size used when this biome spawns as a border (in `BiomeMode: Normal`). Must be a larger number than the host biome's `BiomeSize` so the border is physically narrower.

| Property | Value |
|----------|-------|
| Type | `int` |
| Default | `8` |
| Range | `0` – `20` |

---

## Terrain Shape

Controls how the heightmap is shaped for this biome — base elevation, noise layers, smoothing at biome borders, and per-height density overrides.

### BiomeHeight

Controls the base elevation added during terrain generation. Value `0.0` is roughly half map height with all other settings at defaults. Negative values push terrain below sea level.

| Property | Value |
|----------|-------|
| Type | `float` |
| Default | `0.1` |
| Range | `-10.0` – `10.0` |

### BiomeVolatility

Scales the amplitude of the base terrain noise for this biome. Higher values produce more dramatic hills and valleys.

| Property | Value |
|----------|-------|
| Type | `float` |
| Default | `0.3` |
| Range | `-1000.0` – `1000.0` |

### SmoothRadius

Width of the blending zone between this biome and its neighbours. The actual smooth area in blocks is approximately `(thisSmoothRadius + 1 + neighbourSmoothRadius) × 4`.

| Property | Value |
|----------|-------|
| Type | `int` |
| Default | `2` |
| Range | `0` – `32` |

### CustomHeightControlSmoothRadius

Same blending logic as `SmoothRadius` but applies only to the `CustomHeightControl` noise layer.

| Property | Value |
|----------|-------|
| Type | `int` |
| Default | `2` |
| Range | `0` – `32` |

### PeakFactor

Multiplier for how strongly this biome responds to continental peaks — the large-scale upward undulation of the continental noise (see `ContinentalScale`/`ContinentalBias` in the DimensionPreset config). `1.0` = full response, values between `0` and `1` = weaker peaks, `0` = this biome ignores continental peaks entirely (flat baseline). Negative values invert the response, so continental peaks *lower* the terrain instead of raising it. Use high values (e.g. `2.0`) for mountain/peak biomes.

| Property | Value |
|----------|-------|
| Type | `double` |
| Default | `1.0` |
| Range | `-1000.0` – `1000.0` |

> **Migration:** This setting replaces the old `MaxAverageHeight`. Older configs (before `ConfigVersion: 3`) are converted automatically — the key is renamed and the 0-centered additive value is shifted to the new multiplier scale (`value + 1`).

### ValleyFactor

Multiplier for how strongly this biome responds to continental valleys — the downward undulation of the continental noise, typically the ocean floor. `1.0` = full response, values between `0` and `1` = shallower valleys, `0` = none. Negative values invert the response, so continental valleys *raise* the terrain instead of lowering it. Use high values (e.g. `2.0`) for deep-ocean biomes.

| Property | Value |
|----------|-------|
| Type | `double` |
| Default | `1.0` |
| Range | `-1000.0` – `1000.0` |

> **Migration:** This setting replaces the old `MaxAverageDepth`. Older configs (before `ConfigVersion: 3`) are converted automatically — the key is renamed and the 0-centered additive value is shifted to the new multiplier scale (`value + 1`).

### Volatility1

An independent noise layer that adds chaos to the landscape on top of the biome height. Larger positive values = more chaotic terrain; negative values calm it down. The DimensionPreset's `NoiseAmplitude` scales this contribution globally across all biomes.

| Property | Value |
|----------|-------|
| Type | `double` |
| Default | `1.0` |
| Range | `-10000.0` – `1000.0` |

> **Note:** Negative config values are transformed internally as `v = 1 / (|v| + 1)`. The raw negative value is never used directly.

### Volatility2

Second independent noise layer, identical behaviour to `Volatility1` — an additive second chaos octave.

| Property | Value |
|----------|-------|
| Type | `double` |
| Default | `1.0` |
| Range | `-10000.0` – `1000.0` |

> **Note:** Same negative-value transformation as `Volatility1` applies.

### VolatilityWeight1

Controls how much `Volatility1` contributes relative to `Volatility2`. The engine compares these weights against an internal 0–1 delta to blend the two noise layers.

| Property | Value |
|----------|-------|
| Type | `double` |
| Default | `0.45` |
| Range | `-1000.0` – `1000.0` |

!!! warning "Do not 'fix' these to 0–1"
    Although the blending delta is in the 0–1 range, values around **46–48** (as used in Biome Bundle) produce smooth terrain, while values of 0–1 (as in DefaultPreset) produce jagged, ugly terrain. The high values are intentional — do not "correct" them to the 0–1 range.

### VolatilityWeight2

Controls how much `Volatility2` contributes. See `VolatilityWeight1` for the higher-values caveat.

| Property | Value |
|----------|-------|
| Type | `double` |
| Default | `0.5` |
| Range | `-1000.0` – `1000.0` |

### DisableBiomeHeight

When `true`, disables all noise except `Volatility1` and `Volatility2`, and removes the default block-chance-from-height calculation. Useful for flat or fully custom terrain shapes.

| Property | Value |
|----------|-------|
| Type | `boolean` |
| Default | `false` |

### CustomHeightControl

A list of 17 doubles, one per vertical segment (each segment is roughly 7 blocks tall, starting at the world bottom). Positive values increase the chance of solid blocks in that layer; negative values decrease it. Values can be very large depending on the desired effect.

| Property | Value |
|----------|-------|
| Type | `double[]` (17 entries) |
| Default | *(all zeros — no effect)* |

```properties
# Empty layer just above bedrock, rest normal
CustomHeightControl: 0.0,-2500.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0
```

---

## Blocks & Surface

Defines which blocks form the terrain surface, subsurface, stone base, and fluid fills, plus block-replacement rules and noise-driven surface variation.

### StoneBlock

The block filling the stone layer beneath the surface. When set to anything other than `minecraft:stone` (a "custom" stone block), it fills the entire column — **including below Y=0** — overriding the normal deepslate substitution. When left as `minecraft:stone`, terrain generation uses a **noisy / wavy** stone-to-deepslate transition around Y=0 (not a flat cut); non-terrain callers such as BO4 block replacement still use a flat cut at Y=0.

| Property | Value |
|----------|-------|
| Type | `material` |
| Default | `minecraft:stone` |

### SurfaceBlock

The block placed on the topmost exposed surface of terrain (e.g. grass, sand, mycelium).

| Property | Value |
|----------|-------|
| Type | `material` |
| Default | `minecraft:grass_block` |

### UnderWaterSurfaceBlock

The surface block used when the column top is below the water level. Defaults to `SurfaceBlock` if not set.

| Property | Value |
|----------|-------|
| Type | `material` |
| Default | `minecraft:dirt` |

### GroundBlock

The block placed in the subsurface layer immediately beneath `SurfaceBlock` (typically dirt, sand, etc.).

| Property | Value |
|----------|-------|
| Type | `material` |
| Default | `minecraft:dirt` |

### SandstoneBlock

The block that replaces vanilla sandstone when it generates under sand surfaces.

| Property | Value |
|----------|-------|
| Type | `material` |
| Default | `minecraft:sandstone` |

### RedSandstoneBlock

The block that replaces vanilla red sandstone under red sand surfaces.

| Property | Value |
|----------|-------|
| Type | `material` |
| Default | `minecraft:red_sandstone` |

### WaterBlock

The block used to fill the water level range (between `WaterLevelMin` and `WaterLevelMax`).

| Property | Value |
|----------|-------|
| Type | `material` |
| Default | `minecraft:water` |

### IceBlock

The block used where water freezes at the surface in cold biomes.

| Property | Value |
|----------|-------|
| Type | `material` |
| Default | `minecraft:ice` |

### PackedIceBlock

The block used for packed-ice surfaces (e.g. iceberg interiors).

| Property | Value |
|----------|-------|
| Type | `material` |
| Default | `minecraft:packed_ice` |

### SnowBlock

The block placed as a snow layer on surfaces in cold biomes.

| Property | Value |
|----------|-------|
| Type | `material` |
| Default | `minecraft:snow_block` |

### CooledLavaBlock

The block used for cooled or frozen lava. Set to `minecraft:obsidian` to simulate frozen lava lakes in cold biomes.

| Property | Value |
|----------|-------|
| Type | `material` |
| Default | `minecraft:lava` |

### WaterLevelMax

The upper Y boundary of fluid fill. Every empty block at or below this level (down to `WaterLevelMin`) is filled with `WaterBlock`. Only used when `UseWorldWaterLevel` is `false`.

| Property | Value |
|----------|-------|
| Type | `int` |
| Default | `63` |
| Range | world min Y – world max Y |

### WaterLevelMin

The lower Y boundary of fluid fill. Only used when `UseWorldWaterLevel` is `false`.

| Property | Value |
|----------|-------|
| Type | `int` |
| Default | `0` |
| Range | world min Y – world max Y |

### UseWorldWaterLevel

When `true`, overrides the biome-level `WaterLevelMax`/`WaterLevelMin` with the world-wide water level defined in `DimensionPresetConfig.ini`.

| Property | Value |
|----------|-------|
| Type | `boolean` |
| Default | `true` |

### UseFrozenOceanTemperature

When `true`, applies variable noise-based temperature within the biome to create patches of open water and ice — replicating vanilla Frozen Ocean behaviour.

| Property | Value |
|----------|-------|
| Type | `boolean` |
| Default | `false` |

### ReplacedBlocks

Post-generation block replacement rules. Replaces one block type with another, optionally within a Y-range. Only affects: `CustomObject`, `CustomStructure`, `Ore`, `UnderWaterOre`, `Vein`, `SurfacePatch`, `Boulder`, `IceSpike`. BO objects with `DoReplaceBlocks: false` are exempt.

| Property | Value |
|----------|-------|
| Type | `string list` |
| Default | *(empty — no replacements)* |

```properties
# Replace grass with dirt from Y 100-127, and gravel with glass everywhere
ReplacedBlocks: (GRASS,DIRT,100,127),(GRAVEL,GLASS)
```

### SurfaceAndGroundControl

Noise-driven surface/ground block variation. Each column has a noise value roughly in the range −7 to 7 (values near 0 most common). You define threshold bands: below each `MaxNoise` a specific surface / underwater-surface / ground triple is used. Supports special modes: `Mesa`, `MesaForest`, `MesaBryce`, and `Iceberg <SAGC>`.

| Property | Value |
|----------|-------|
| Type | `string` (parsed surface generator expression) |
| Default | *(empty — uses `SurfaceBlock`/`GroundBlock` directly)* |

```properties
# SurfaceBlock,UnderWaterSurfaceBlock,GroundBlock,MaxNoise[,...]
SurfaceAndGroundControl: STONE,STONE,STONE,-0.8,GRAVEL,GRAVEL,STONE,0.0,DIRT,DIRT,DIRT,10.0

# Mesa mode
SurfaceAndGroundControl: Mesa

# Iceberg with custom inner surface
SurfaceAndGroundControl: Iceberg STONE,STONE,STONE,-0.8,GRAVEL,GRAVEL,STONE,10.0
```

---

## Visuals & Weather

Sky/water/grass/foliage/fog colours, noise-driven colour control sets, the grass colour modifier, temperature, wetness, and ambient particles.

### SkyColor

The colour of the sky in this biome.

| Property | Value |
|----------|-------|
| Type | `color` |
| Default | `0x7BA5FF` |

### WaterColor

The colour of the water in this biome.

| Property | Value |
|----------|-------|
| Type | `color` |
| Default | `0xFFFFFF` |

### WaterColorControl

Noise-driven water colour override. Maps noise thresholds to colours; falls back to `WaterColor` above the highest threshold. Syntax: `Color,MaxNoise[,Color,MaxNoise,...]`.

| Property | Value |
|----------|-------|
| Type | `color set` |
| Default | *(not set)* |

```properties
WaterColorControl: 0xFFFFFF,-0.8,0x000000,0.0
```

### GrassColor

The colour of the grass in this biome.

| Property | Value |
|----------|-------|
| Type | `color` |
| Default | `0xFFFFFF` |

### GrassColorControl

Noise-driven grass colour override. See `WaterColorControl` for syntax.

| Property | Value |
|----------|-------|
| Type | `color set` |
| Default | *(not set)* |

### GrassColorModifier

Biome-specific modifier applied on top of the grass colour tint.

| Property | Value |
|----------|-------|
| Type | `enum` |
| Default | `None` |
| Options | `None`, `Swamp`, `DarkForest` |

### FoliageColor

The colour of the foliage (leaves) in this biome.

| Property | Value |
|----------|-------|
| Type | `color` |
| Default | `0xFFFFFF` |

### FoliageColorControl

Noise-driven foliage colour override. See `WaterColorControl` for syntax.

| Property | Value |
|----------|-------|
| Type | `color set` |
| Default | *(not set)* |

### FogColor

The colour of the fog in this biome.

| Property | Value |
|----------|-------|
| Type | `color` |
| Default | `0x000000` |

### FogDensity

Density of the fog. `0.0` mimics vanilla (no fog); `1.0` is fully opaque.

| Property | Value |
|----------|-------|
| Type | `float` |
| Default | `0.0` |
| Range | `0.0` – `1.0` |

### WaterFogColor

The colour of the fog rendered below the water surface in this biome.

| Property | Value |
|----------|-------|
| Type | `color` |
| Default | `0x000000` |

### ParticleType

Ambient particle effect for the biome. Use the `otg particles` console command for a list. Leave empty to disable.

| Property | Value |
|----------|-------|
| Type | `string` |
| Default | *(empty)* |

```properties
ParticleType: minecraft:white_ash
```

### ParticleProbability

Per-tick probability that a particle spawns.

| Property | Value |
|----------|-------|
| Type | `float` |
| Default | `0.0` |
| Range | `0.0` – `1.0` |

### BiomeTemperature

Controls precipitation type and snow placement. Values near `0.2` cause snow on mountain peaks above y≈90; values near `0.1` cover the whole biome in snow and ice.

| Property | Value |
|----------|-------|
| Type | `float` |
| Default | `0.5` |
| Range | `0.0` – `2.0` |

### BiomeWetness

Controls rain and snow occurrence in this biome.

| Property | Value |
|----------|-------|
| Type | `float` |
| Default | `0.5` |
| Range | `0.0` – `1.0` |

---

## Sounds & Music

Ambient, mood, and additions sounds, plus biome music and its playback timing.

### AmbientSound

Looping ambient sound for the biome. Leave empty to disable.

| Property | Value |
|----------|-------|
| Type | `string` |
| Default | *(empty)* |

```properties
AmbientSound: minecraft:ambient.cave
```

### MoodSound

Mood sound (plays at low frequency in dark areas). Leave empty to disable.

| Property | Value |
|----------|-------|
| Type | `string` |
| Default | `minecraft:ambient.cave` |

### MoodSoundDelay

Delay in ticks between mood sound plays.

| Property | Value |
|----------|-------|
| Type | `int` |
| Default | `6000` |
| Range | `0` – `2147483647` |

### MoodSearchRange

Maximum distance in blocks from the player at which the mood sound may play.

| Property | Value |
|----------|-------|
| Type | `int` |
| Default | `8` |
| Range | `0` – `2147483647` |

### MoodOffset

Spatial offset applied to the mood sound event position.

| Property | Value |
|----------|-------|
| Type | `double` |
| Default | `2.0` |

### AdditionsSound

High-frequency additions sound layer. Leave empty to disable.

| Property | Value |
|----------|-------|
| Type | `string` |
| Default | *(empty)* |

```properties
AdditionsSound: minecraft:ambient.soul_sand_valley.additions
```

### AdditionsTickChance

Per-tick probability that the additions sound plays.

| Property | Value |
|----------|-------|
| Type | `double` |
| Default | `0.0` |

### Music

Background music for the biome. Accepts a resource location. Leave empty to disable.

| Property | Value |
|----------|-------|
| Type | `string` |
| Default | *(empty)* |

```properties
Music: minecraft:music.overworld.lush_caves
```

### MusicMinDelay

Minimum delay in ticks before music restarts after finishing.

| Property | Value |
|----------|-------|
| Type | `int` |
| Default | `0` |
| Range | `0` – `2147483647` |

### MusicMaxDelay

Maximum delay in ticks before music restarts after finishing.

| Property | Value |
|----------|-------|
| Type | `int` |
| Default | `0` |
| Range | `0` – `2147483647` |

### ReplaceCurrentMusic

When `true`, this biome's music immediately replaces whatever is currently playing.

| Property | Value |
|----------|-------|
| Type | `boolean` |
| Default | `false` |

---

## Mob Spawning

Which mobs spawn in this biome, grouped by category, plus an inheritance shortcut for reusing another biome's spawn lists. All mob-list settings share the same JSON array format:

```properties
SpawnMonsters: [{"mob": "minecraft:spider", "weight": 100, "min": 4, "max": 4}, {"mob": "minecraft:zombie", "weight": 100, "min": 4, "max": 4}]
```

Use `/otg entities` to list valid mob IDs and `/otg biome -m` to inspect a biome's registered mobs.

### SpawnMonsters

Hostile mobs (zombies, skeletons, creepers, spiders, etc.).

| Property | Value |
|----------|-------|
| Type | `mob list (JSON)` |
| Default | *(empty list)* |

### SpawnCreatures

Friendly overworld creatures (sheep, cows, pigs, chickens, foxes, etc.).

| Property | Value |
|----------|-------|
| Type | `mob list (JSON)` |
| Default | *(empty list)* |

### SpawnWaterCreatures

Water creatures (squids, dolphins).

| Property | Value |
|----------|-------|
| Type | `mob list (JSON)` |
| Default | *(empty list)* |

### SpawnAmbientCreatures

Ambient creatures (bats).

| Property | Value |
|----------|-------|
| Type | `mob list (JSON)` |
| Default | *(empty list)* |

### SpawnWaterAmbientCreatures

Ambient water creatures (cod, salmon, pufferfish, tropical fish).

| Property | Value |
|----------|-------|
| Type | `mob list (JSON)` |
| Default | *(empty list)* |

### SpawnMiscCreatures

Miscellaneous creatures (iron golems, snow golems, villagers).

| Property | Value |
|----------|-------|
| Type | `mob list (JSON)` |
| Default | *(empty list)* |

### InheritMobsBiomeName

Inherit the internal mob spawn list of another biome (OTG or vanilla). Any mob category explicitly defined in this biome overrides the inherited list for that category.

| Property | Value |
|----------|-------|
| Type | `string` |
| Default | *(empty)* |

```properties
InheritMobsBiomeName: minecraft:plains
```

---

## Vanilla Structures

These settings control which vanilla Minecraft structures may spawn in a biome. Two systems exist side by side: the **legacy settings** below (enum/bool/float per structure type) and the **[structure tags](#structure-tags)** which are the runtime-authoritative on/off flags. On load, legacy settings are folded into the tag config automatically — you do not need to set both. Prefer the structure tags for new configs.

!!! note "Dead size/probability settings"
    `VillageSize`, `PillagerOutpostSize`, `BastionRemnantSize`, `MineshaftProbability`, `BuriedTreasureProbability`, `OceanRuinsLargeProbability`, and `OceanRuinsClusterProbability` are parsed and stored but **currently ignored at runtime on Fabric and NeoForge** — the tag system is binary on/off. They are documented for completeness.

### StrongholdsEnabled

Toggles strongholds spawning in this biome.

| Property | Value |
|----------|-------|
| Type | `boolean` |
| Default | `true` |

### OceanMonumentsEnabled

Toggles ocean monuments spawning in this biome.

| Property | Value |
|----------|-------|
| Type | `boolean` |
| Default | `false` |

### WoodlandMansionsEnabled

Toggles woodland mansions spawning in this biome.

| Property | Value |
|----------|-------|
| Type | `boolean` |
| Default | `false` |

### NetherFortressesEnabled

Toggles nether fortresses spawning in this biome.

| Property | Value |
|----------|-------|
| Type | `boolean` |
| Default | `false` |

### BuriedTreasureEnabled

Toggles buried treasure spawning in this biome.

| Property | Value |
|----------|-------|
| Type | `boolean` |
| Default | `false` |

### BuriedTreasureProbability

Probability of buried treasure spawning. *(Dead at runtime — see note above.)*

| Property | Value |
|----------|-------|
| Type | `float` |
| Default | `0.01` |
| Range | `0.0` – `1.0` |

### ShipWreckEnabled

Toggles shipwrecks spawning in this biome.

| Property | Value |
|----------|-------|
| Type | `boolean` |
| Default | `false` |

### ShipWreckBeachedEnabled

Toggles beached shipwrecks spawning in this biome.

| Property | Value |
|----------|-------|
| Type | `boolean` |
| Default | `false` |

### PillagerOutpostEnabled

Toggles pillager outposts spawning in this biome.

| Property | Value |
|----------|-------|
| Type | `boolean` |
| Default | `false` |

### PillagerOutpostSize

Size of the pillager outpost. *(Dead at runtime.)*

| Property | Value |
|----------|-------|
| Type | `int` |
| Default | `7` |
| Range | `0` – `2147483647` |

### BastionRemnantEnabled

Toggles bastion remnants spawning in this biome.

| Property | Value |
|----------|-------|
| Type | `boolean` |
| Default | `false` |

### BastionRemnantSize

Size of the bastion remnant. *(Dead at runtime.)*

| Property | Value |
|----------|-------|
| Type | `int` |
| Default | `6` |
| Range | `0` – `2147483647` |

### NetherFossilEnabled

Toggles nether fossils spawning in this biome.

| Property | Value |
|----------|-------|
| Type | `boolean` |
| Default | `false` |

### EndCityEnabled

Toggles end cities spawning in this biome.

| Property | Value |
|----------|-------|
| Type | `boolean` |
| Default | `false` |

### VillageType

The type of village that can spawn. Use `disabled` to suppress villages entirely.

| Property | Value |
|----------|-------|
| Type | `enum` |
| Default | `disabled` |
| Options | `disabled`, `wood`, `sandstone`, `taiga`, `savanna`, `snowy` |

### VillageSize

Size of the village. *(Dead at runtime.)*

| Property | Value |
|----------|-------|
| Type | `int` |
| Default | `6` |
| Range | `0` – `2147483647` |

### MineshaftType

The type of mineshaft that can spawn.

| Property | Value |
|----------|-------|
| Type | `enum` |
| Default | `normal` |
| Options | `disabled`, `normal`, `mesa` |

### MineshaftProbability

Probability of a mineshaft spawning. *(Dead at runtime.)*

| Property | Value |
|----------|-------|
| Type | `float` |
| Default | `0.004` |
| Range | `0.0` – `1.0` |

### RareBuildingType

The type of rare building that can spawn (desert pyramid, jungle temple, swamp hut, or igloo).

| Property | Value |
|----------|-------|
| Type | `enum` |
| Default | `disabled` |
| Options | `disabled`, `desertPyramid`, `jungleTemple`, `swampHut`, `igloo` |

### RuinedPortalType

The type of ruined portal that can spawn.

| Property | Value |
|----------|-------|
| Type | `enum` |
| Default | `disabled` |
| Options | `disabled`, `normal`, `desert`, `jungle`, `swamp`, `mountain`, `ocean`, `nether` |

### OceanRuinsType

The type of ocean ruins that can spawn.

| Property | Value |
|----------|-------|
| Type | `enum` |
| Default | `disabled` |
| Options | `disabled`, `cold`, `warm` |

### OceanRuinsLargeProbability

Probability of a large ocean ruin. *(Dead at runtime.)*

| Property | Value |
|----------|-------|
| Type | `float` |
| Default | `0.3` |
| Range | `0.0` – `1.0` |

### OceanRuinsClusterProbability

Probability of a cluster of ocean ruins. *(Dead at runtime.)*

| Property | Value |
|----------|-------|
| Type | `float` |
| Default | `0.9` |
| Range | `0.0` – `1.0` |

### Structure Tags

The runtime-authoritative way to enable vanilla structures. Each flag adds this biome to the corresponding vanilla `#minecraft:has_structure/*` biome tag. All flags default to `false`; set to `true` to opt in. Legacy `Vanilla Structures` values above are merged in automatically, so you only need these for tags with no legacy equivalent (e.g. `AncientCity`, `TrailRuins`, `TrialChambers`, `WoodlandHouse`).

`DisplayAllStructureTags` (boolean, default `false`) — when `true`, writes every tag flag to the `.bc` file even at default value (for review); no runtime effect.

| Flag | Vanilla tag (`has_structure/…`) |
|------|------|
| `AncientCity` | `ancient_city` |
| `BastionRemnant` | `bastion_remnant` |
| `BuriedTreasure` | `buried_treasure` |
| `DesertPyramid` | `desert_pyramid` |
| `EndCity` | `end_city` |
| `Igloo` | `igloo` |
| `JungleTemple` | `jungle_temple` |
| `Mineshaft` | `mineshaft` |
| `MineshaftMesa` | `mineshaft_mesa` |
| `NetherFortress` | `nether_fortress` |
| `NetherFossil` | `nether_fossil` |
| `OceanMonument` | `ocean_monument` |
| `OceanRuinCold` | `ocean_ruin_cold` |
| `OceanRuinWarm` | `ocean_ruin_warm` |
| `PillagerOutpost` | `pillager_outpost` |
| `RuinedPortalStandard` | `ruined_portal` |
| `RuinedPortalDesert` | `ruined_portal_desert` |
| `RuinedPortalJungle` | `ruined_portal_jungle` |
| `RuinedPortalMountain` | `ruined_portal_mountain` |
| `RuinedPortalNether` | `ruined_portal_nether` |
| `RuinedPortalOcean` | `ruined_portal_ocean` |
| `RuinedPortalSwamp` | `ruined_portal_swamp` |
| `Shipwreck` | `shipwreck` |
| `ShipwreckBeached` | `shipwreck_beached` |
| `Stronghold` | `stronghold` |
| `SwampHut` | `swamp_hut` |
| `TrailRuins` | `trail_ruins` |
| `TrialChambers` | `trial_chambers` |
| `VillageDesert` | `village_desert` |
| `VillagePlains` | `village_plains` |
| `VillageSavanna` | `village_savanna` |
| `VillageSnowy` | `village_snowy` |
| `VillageTaiga` | `village_taiga` |
| `WoodlandMansion` | `mansion` |
| `WoodlandHouse` | `woodland_house` |

---

## Underground Biomes

Underground biomes are a 3D-noise overlay on the normal 2D surface biome map. Rather than replacing the surface biome, each underground biome carves out organic, blob-shaped 3D regions beneath the terrain surface, driven by a continuous 3D noise field. `UndergroundRegionSize` controls the horizontal blob scale and `UndergroundVerticalScale` the aspect ratio. `UndergroundBiomeRarity` is a coverage percentage (0–100) — how much of the qualifying underground volume the biome fills, not a per-block random roll (100 fills every eligible block; 50 produces roughly half-filled blobs with normal stone between them).

By default underground biomes are fully decoupled from the surface: the temperature/wetness filters default to the full valid range, so any surface biome qualifies. Narrow `MinSurfaceTemperature`/`MaxSurfaceTemperature` or `MinSurfaceWetness`/`MaxSurfaceWetness` to pin a biome under specific climates.

The five `Cave*Scale` multipliers (all default `1.0`) let each underground biome independently amplify or suppress each vanilla cave type inside its region — cheese caverns, 3D spaghetti tunnels, 2D spaghetti tunnels, noodle caves, and pillars — cross-fading smoothly into surrounding normal stone. `1.0` everywhere produces identical caves to the default world.

The settings `UndergroundBiomeStartOffset`, `AllowedUndergroundBiomes`, and `DisallowedUndergroundBiomes` are set on **surface** biomes to control what may appear below them; all other settings here are set on the **underground** biome itself.

```properties
# Minimal underground biome
IsUndergroundBiome: true
UndergroundMinY: -64
UndergroundMaxY: 20
UndergroundBiomeRarity: 60.0
UndergroundRegionSize: 120
CaveCheeseScale: 2.0    # large open caverns — doubled inside this region
```

### IsUndergroundBiome

Marks this biome as an underground biome. When `false` (default) it is treated as a surface biome and the underground placement settings have no effect (except the three surface-side controls noted above).

| Property | Value |
|----------|-------|
| Type | `boolean` |
| Default | `false` |

### UndergroundMinY

Minimum Y coordinate where this underground biome can appear.

| Property | Value |
|----------|-------|
| Type | `int` |
| Default | `-64` |
| Range | `-2048` – `2048` |

### UndergroundMaxY

Maximum Y coordinate where this underground biome can appear.

| Property | Value |
|----------|-------|
| Type | `int` |
| Default | `320` |
| Range | `-2048` – `2048` |

### UndergroundPriority

Priority when multiple underground biomes qualify for the same position. Lower value = higher priority.

| Property | Value |
|----------|-------|
| Type | `int` |
| Default | `10` |
| Range | `0` – `1000` |

### UndergroundBiomeRarity

Coverage percentage of the qualifying underground volume this biome fills, driven by a 3D noise field. `100` fills the whole qualifying volume; `50` fills roughly half; `0` never appears. Not a per-block random roll.

| Property | Value |
|----------|-------|
| Type | `float` |
| Default | `100.0` |
| Range | `0.0` – `100.0` |

### UndergroundRegionSize

Approximate horizontal size in blocks of the organic blobs this biome forms. Larger = bigger continuous regions; smaller = more scattered patches.

| Property | Value |
|----------|-------|
| Type | `int` |
| Default | `96` |
| Range | `8` – `1024` |

### UndergroundVerticalScale

Vertical aspect ratio of the region noise. Below `1.0` stretches regions vertically (taller columns); above `1.0` flattens them (lens-shaped). Default `0.7` gives moderately tall regions.

| Property | Value |
|----------|-------|
| Type | `float` |
| Default | `0.7` |
| Range | `0.05` – `4.0` |

### CaveCheeseScale

Density multiplier for cheese caves (large open caverns) inside this biome's region. `1.0` = unchanged; `>1` = bigger / more frequent caverns; `<1` = smaller; `0` = none. Cross-fades smoothly at region edges.

| Property | Value |
|----------|-------|
| Type | `float` |
| Default | `1.0` |
| Range | `0.0` – `8.0` |

### CaveSpaghetti3dScale

Density multiplier for 3D spaghetti caves (winding tunnels and cave entrances). `1.0` = unchanged; `>1` = more/wider tunnels; `0` = none.

| Property | Value |
|----------|-------|
| Type | `float` |
| Default | `1.0` |
| Range | `0.0` – `8.0` |

### CaveSpaghetti2dScale

Density multiplier for 2D spaghetti caves (horizontal tunnel networks). `1.0` = unchanged; `>1` = more/wider tunnels; `0` = none.

| Property | Value |
|----------|-------|
| Type | `float` |
| Default | `1.0` |
| Range | `0.0` – `8.0` |

### CaveNoodleScale

Density multiplier for noodle caves (thin, twisting passages). `1.0` = unchanged; `>1` = more thin passages; `0` = none.

| Property | Value |
|----------|-------|
| Type | `float` |
| Default | `1.0` |
| Range | `0.0` – `8.0` |

### CavePillarScale

Density multiplier for pillars (solid stone columns that block carving). `1.0` = unchanged; `>1` = more/thicker pillars (denser, more bridged terrain); `0` = none.

| Property | Value |
|----------|-------|
| Type | `float` |
| Default | `1.0` |
| Range | `0.0` – `8.0` |

### MinSurfaceTemperature

Minimum `BiomeTemperature` of the surface biome above for this underground biome to be eligible. Default `0.0` with `MaxSurfaceTemperature: 2.0` means no temperature filtering.

| Property | Value |
|----------|-------|
| Type | `float` |
| Default | `0.0` |
| Range | `0.0` – `2.0` |

### MaxSurfaceTemperature

Maximum `BiomeTemperature` of the surface biome above. Default `2.0` with `MinSurfaceTemperature: 0.0` means no temperature filtering.

| Property | Value |
|----------|-------|
| Type | `float` |
| Default | `2.0` |
| Range | `0.0` – `2.0` |

### MinSurfaceWetness

Minimum `BiomeWetness` of the surface biome above. Default `0.0` means no lower boundary.

| Property | Value |
|----------|-------|
| Type | `float` |
| Default | `0.0` |
| Range | `0.0` – `1.0` |

### MaxSurfaceWetness

Maximum `BiomeWetness` of the surface biome above. Default `1.0` means no upper boundary.

| Property | Value |
|----------|-------|
| Type | `float` |
| Default | `1.0` |
| Range | `0.0` – `1.0` |

### UndergroundBiomeStartOffset

How many blocks below the estimated surface height underground biomes begin. Set on **surface** biomes; prevents underground biomes from surfacing near ground level.

| Property | Value |
|----------|-------|
| Type | `int` |
| Default | `8` |
| Range | `0` – `256` |

### AllowedUndergroundBiomes

If non-empty, only the listed underground biomes can appear below this surface biome. Set on **surface** biomes; leave empty to allow all that match.

| Property | Value |
|----------|-------|
| Type | `string list` |
| Default | *(empty)* |

```properties
AllowedUndergroundBiomes: LushCaves, DripstoneCaves
```

### DisallowedUndergroundBiomes

Underground biomes listed here never appear below this surface biome. Set on **surface** biomes.

| Property | Value |
|----------|-------|
| Type | `string list` |
| Default | *(empty)* |

```properties
DisallowedUndergroundBiomes: DeepDark
```

---

## Resource Queue

Resource spawning — ores, plants, trees, BO2/BO3/BO4 custom objects, dungeons, lakes, boulders, and more — is configured as a list of resource entries at the bottom of the `.bc` file, plus `Registry(...)` entries that run vanilla placed features. This uses its own syntax and is documented separately on the [Resource Queue](resource-queue.md) page.

```properties
# Examples
Ore(minecraft:coal_ore,17,20,100.0,0,127,minecraft:stone)
Tree(10,BigTree,30,Tree,100)
Registry(minecraft:lush_caves_vegetation,VEGETAL_DECORATION)
Dungeon(8.0,10,100)
```
