# DimensionPresetConfig.ini

The main configuration file for a DimensionPreset. Controls biome distribution, terrain shape, caves, structures, blocks, and dimension properties.

Located at `DimensionPresets/<PresetName>/DimensionPresetConfig.ini`.

---

## Preset Info

Identity and metadata for the preset.

### SettingsMode

How OTG updates this config file on load.

| Property | Value |
|----------|-------|
| Type | `enum` |
| Default | `WriteAll` |
| Options | `WriteAll`, `WriteWithoutComments`, `WriteDisable` |

- **WriteAll** — regenerates the file with all settings and comments
- **WriteWithoutComments** — regenerates without comment blocks
- **WriteDisable** — never modifies the file

### Author

| Property | Value |
|----------|-------|
| Type | `string` |
| Default | `Unknown` |

### DisplayName

Name shown in the world creation GUI. Defaults to the preset folder name if blank.

| Property | Value |
|----------|-------|
| Type | `string` |
| Default | *(folder name)* |

### RegistryName

Shortened name used in biome resource locations (e.g. `otg:<registry_name>/biome_name`). Defaults to folder name if blank.

| Property | Value |
|----------|-------|
| Type | `string` |
| Default | *(folder name)* |

### Description

| Property | Value |
|----------|-------|
| Type | `string` |
| Default | `No description given` |

### MajorVersion

Incrementing this makes PresetPacker save a new copy of the preset.

| Property | Value |
|----------|-------|
| Type | `int` |
| Default | `0` |

### MinorVersion

Incrementing this makes PresetPacker overwrite the existing copy.

| Property | Value |
|----------|-------|
| Type | `int` |
| Default | `0` |

### SelectableInWorldCreation

Whether this preset appears in the world creation GUI as a selectable world type.

| Property | Value |
|----------|-------|
| Type | `boolean` |
| Default | `true` |

---

## Visual Settings

### WorldFog

Distance fog color. Can be overridden per biome.

| Property | Value |
|----------|-------|
| Type | `color` |
| Default | `0xC0D8FF` |
| Range | `0x000000` – `0xFFFFFF` |

---

## Biome Distribution

Controls how biomes are placed in the world.

### BiomeMode

| Property | Value |
|----------|-------|
| Type | `enum` |
| Default | `Normal` |
| Options | `Normal`, `FromImage` |

- **Normal** — biomes generated using biome groups, land/ocean distribution, and temperature
- **FromImage** — biomes read from a PNG image file (see [Image Settings](#image-settings))

### GenerationDepth

Maximum depth for BiomeSize, RiverSize, and LandSize calculations. Each +1 roughly doubles biome size.

| Property | Value |
|----------|-------|
| Type | `int` |
| Default | `10` |
| Range | `1` – `20` |

### BiomeRarityScale

Maximum biome rarity value. Raise above 100 for finer-grained control or very rare biomes.

| Property | Value |
|----------|-------|
| Type | `int` |
| Default | `100` |
| Range | `1`+ |

### OldGroupRarity

Use pre-1.16 biome group rarity calculation.

| Property | Value |
|----------|-------|
| Type | `boolean` |
| Default | `false` |

### OldLandRarity

Use old land rarity system. When `false`, LandRarity works as a straight percentage.

| Property | Value |
|----------|-------|
| Type | `boolean` |
| Default | `false` |

### LandRarity

Land occurrence as a percentage. Higher = more land, less ocean.

| Property | Value |
|----------|-------|
| Type | `int` |
| Default | `99` |
| Range | `0` – `100` |

### LandSize

Land mass size. 0 = largest. Must be lower than any biome group's BiomeSize.

| Property | Value |
|----------|-------|
| Type | `int` |
| Default | `0` |
| Range | `0` – `20` |

### LandFuzzy

Adds small ocean biomes at continent edges, creating more inland lakes but larger overall continents.

| Property | Value |
|----------|-------|
| Type | `int` |
| Default | `5` |
| Range | `0` – `20` |

### ForceLandAtSpawn

Ensures land always generates at or near coordinates 0,0.

| Property | Value |
|----------|-------|
| Type | `boolean` |
| Default | `true` |

### OceanBiomeSize

Ocean biome size. Higher values = smaller individual ocean biomes.

| Property | Value |
|----------|-------|
| Type | `int` |
| Default | `6` |
| Range | `0` – `20` |

### DefaultOceanBiome

Fallback ocean biome when temperature doesn't match a specific variant.

| Property | Value |
|----------|-------|
| Type | `string` |
| Default | `Ocean` |

### DefaultWarmOceanBiome

| Property | Value |
|----------|-------|
| Type | `string` |
| Default | `Warm Ocean` |

### DefaultLukewarmOceanBiome

| Property | Value |
|----------|-------|
| Type | `string` |
| Default | `Lukewarm Ocean` |

### DefaultColdOceanBiome

| Property | Value |
|----------|-------|
| Type | `string` |
| Default | `Cold Ocean` |

### DefaultFrozenOceanBiome

| Property | Value |
|----------|-------|
| Type | `string` |
| Default | `Frozen Ocean` |

### FrozenOcean

Ocean water freezes near cold biomes.

| Property | Value |
|----------|-------|
| Type | `boolean` |
| Default | `true` |

### OceanFreezingTemperature

Maximum biome temperature considered "cold" for ocean freezing. Below 0.15 = snow, 0.15–0.95 = rain, above 1.0 = dry.

| Property | Value |
|----------|-------|
| Type | `double` |
| Default | `0.15` |
| Range | `0.0` – `2.0` |

---

## Rivers

### RiversEnabled

| Property | Value |
|----------|-------|
| Type | `boolean` |
| Default | `true` |

### RandomRivers

`false` = rivers follow biome borders. `true` = random placement.

| Property | Value |
|----------|-------|
| Type | `boolean` |
| Default | `false` |

### RiverRarity

Higher = more rivers. Must be between 0 and GenerationDepth.

| Property | Value |
|----------|-------|
| Type | `int` |
| Default | `4` |
| Range | `0` – `20` |

### RiverSize

Higher = wider rivers.

| Property | Value |
|----------|-------|
| Type | `int` |
| Default | `0` |
| Range | `0` – `20` |

---

## Image Settings

Used when `BiomeMode: FromImage`. Biomes are read from a PNG file where each pixel color maps to a biome.

### ImageFile

PNG file (no transparency) in the preset folder.

| Property | Value |
|----------|-------|
| Type | `string` |
| Default | `map.png` |

### ImageMode

What happens outside the image boundaries.

| Property | Value |
|----------|-------|
| Type | `enum` |
| Default | `Mirror` |
| Options | `Repeat`, `Mirror`, `ContinueNormal`, `FillEmpty` |

- **Repeat** — tiles the image
- **Mirror** — mirrors at edges
- **ContinueNormal** — switches to Normal biome mode outside the image
- **FillEmpty** — fills with a single biome (see ImageFillBiome)

### ImageOrientation

Image rotation. North = no rotation, East = 90° CCW, South = 180°, West = 270° CCW.

| Property | Value |
|----------|-------|
| Type | `enum` |
| Default | `West` |
| Options | `North`, `East`, `South`, `West` |

### ImageFillBiome

Biome used outside image boundaries when `ImageMode: FillEmpty`.

| Property | Value |
|----------|-------|
| Type | `string` |
| Default | `Ocean` |

### ImageXOffset

Horizontal offset for the map origin.

| Property | Value |
|----------|-------|
| Type | `int` |
| Default | `0` |

### ImageZOffset

Vertical offset for the map origin.

| Property | Value |
|----------|-------|
| Type | `int` |
| Default | `0` |

---

## Terrain Height & Volatility

### WorldHeightScaleBits

Terrain height scale. Each +1 doubles terrain height. 8 = 256 blocks of usable terrain height.

| Property | Value |
|----------|-------|
| Type | `int` |
| Default | `8` |
| Range | `5` – `9` |

### WorldHeightCapBits

Maximum terrain height cap. No terrain generates above 2^value blocks. For 1.18+ (384-block world height), use 9.

| Property | Value |
|----------|-------|
| Type | `int` |
| Default | `9` |
| Range | `5` – `9` |

### FractureHorizontal

Horizontal terrain fracturing. Positive = more fractured coastlines and biome edges. Negative = smoother, more relaxed terrain.

| Property | Value |
|----------|-------|
| Type | `double` |
| Default | `0.0` |
| Range | `-500.0` – `500.0` |

### FractureVertical

Vertical terrain fracturing. Positive = more cliffs, overhangs, and floating islands. Negative = spiky peaks but fewer overhangs.

| Property | Value |
|----------|-------|
| Type | `double` |
| Default | `0.0` |
| Range | `-500.0` – `500.0` |

### ContinentalScale

Overall amplitude of the continental height variation — the large-scale terrain undulation that biomes respond to via their `PeakFactor`/`ValleyFactor`. `0.2` is the default. Higher values make the continental highs and lows more dramatic. Setting `ContinentalScale: 0` fully disables continental undulation, giving a flat baseline (terrain shape then comes only from biome height and volatility noise).

| Property | Value |
|----------|-------|
| Type | `double` |
| Default | `0.2` |
| Range | `0.0` – `10.0` |

### ContinentalBias

Shifts the balance between valleys and peaks in the continental noise. Negative values produce more valleys than peaks; positive values produce more peaks than valleys. Measured splits: `-0.05` ≈ 55% valleys, `-0.15` ≈ 65% valleys, `-0.30` ≈ 77% valleys.

| Property | Value |
|----------|-------|
| Type | `double` |
| Default | `-0.05` |
| Range | `-1.0` – `1.0` |

### BaseHeightFraction

Where the terrain surface sits, as a fraction of world height, when a biome's `BiomeHeight` is `0`. `0.46875` places the surface at roughly half the world height (the default). Lower values sink the surface (more sky, shallower ground); higher values raise it (higher surface, deeper underground).

| Property | Value |
|----------|-------|
| Type | `double` |
| Default | `0.46875` |
| Range | `0.0` – `1.0` |

### BiomeHeightWeight

How much a biome's `BiomeHeight` config shifts the terrain surface. `0` puts every biome at the same baseline; higher values create more dramatic height differences between biomes.

| Property | Value |
|----------|-------|
| Type | `double` |
| Default | `0.125` |
| Range | `0.0` – `1.0` |

### ContinentalHeightWeight

How much the continental noise shifts the terrain surface. `0` means continental noise has no effect on surface position; higher values create larger-scale terrain undulation.

| Property | Value |
|----------|-------|
| Type | `double` |
| Default | `0.25` |
| Range | `0.0` – `1.0` |

### FalloffSteepness

Controls how sharply terrain transitions from solid to air. Higher values = thinner transition zone = sharper terrain edges. Lower values = thicker transition zone = smoother, more blobby terrain. The default `6.0` produces roughly an 80-block transition at the default biome volatility (`0.3`).

| Property | Value |
|----------|-------|
| Type | `double` |
| Default | `6.0` |
| Range | `0.1` – `100.0` |

### NoiseAmplitude

Global multiplier for the terrain noise contribution — scales the effect of every biome's `Volatility1`/`Volatility2` uniformly. `1.0` = noise at face value, higher = more chaotic terrain, `0` = falloff-only terrain (no volatility noise at all).

| Property | Value |
|----------|-------|
| Type | `double` |
| Default | `1.0` |
| Range | `0.0` – `1000.0` |

### BetterSnowFall

When `false`, places a single snow layer on the highest block. When `true`, places 1–8 snow layers based on biome temperature, and snow falls through leaves.

| Property | Value |
|----------|-------|
| Type | `boolean` |
| Default | `false` |

---

## Blocks

### DefaultStoneBlock

Block used as "stone" in biomes that don't specify their own.

| Property | Value |
|----------|-------|
| Type | `block` |
| Default | `minecraft:stone` |

### WaterBlock

Block used for water level fill.

| Property | Value |
|----------|-------|
| Type | `block` |
| Default | `minecraft:water` |

### WaterLevelMax

Water fills empty space from this level downward.

| Property | Value |
|----------|-------|
| Type | `int` |
| Default | `63` |

### WaterLevelMin

Water fills empty space from this level upward.

| Property | Value |
|----------|-------|
| Type | `int` |
| Default | `-67` |

### IceBlock

Block used instead of water in frozen areas.

| Property | Value |
|----------|-------|
| Type | `block` |
| Default | `minecraft:ice` |

### CooledLavaBlock

Block used for cooled lava. Set to `minecraft:obsidian` for frozen lava in cold biomes.

| Property | Value |
|----------|-------|
| Type | `block` |
| Default | `minecraft:lava` |

### BedrockBlock

| Property | Value |
|----------|-------|
| Type | `block` |
| Default | `minecraft:bedrock` |

### CarverLavaBlock

Block replacing air in caves below CarverLavaBlockHeight.

| Property | Value |
|----------|-------|
| Type | `block` |
| Default | `minecraft:lava` |

### CarverLavaBlockHeight

Y level up to which cave air is replaced with CarverLavaBlock.

| Property | Value |
|----------|-------|
| Type | `int` |
| Default | `10` |

### DisableBedrock

Don't generate the bottom bedrock layer.

| Property | Value |
|----------|-------|
| Type | `boolean` |
| Default | `false` |

### CeilingBedrock

Generate a ceiling bedrock layer (like the Nether).

| Property | Value |
|----------|-------|
| Type | `boolean` |
| Default | `false` |

### FlatBedrock

Single flat bedrock layer instead of scattered.

| Property | Value |
|----------|-------|
| Type | `boolean` |
| Default | `false` |

### RemoveSurfaceStone

Place biome surface block on top of all exposed stone.

| Property | Value |
|----------|-------|
| Type | `boolean` |
| Default | `false` |

---

## Legacy Caves & Ravines

OTG's original cave and ravine carvers. These run alongside or instead of vanilla 1.18+ noise caves depending on configuration.

### UseModernCaves

Enable the 1.18+ noise cave pipeline and aquifers. When `false`, only legacy OTG carvers are used.

| Property | Value |
|----------|-------|
| Type | `boolean` |
| Default | `false` |

### LegacyCarversEnabled

Allow legacy OTG carvers when modern caves are disabled.

| Property | Value |
|----------|-------|
| Type | `boolean` |
| Default | `true` |

### CavesEnabled

| Property | Value |
|----------|-------|
| Type | `boolean` |
| Default | `true` |

### EvenCaveDistribution

Use flat random distribution instead of biased-toward-bottom distribution.

| Property | Value |
|----------|-------|
| Type | `boolean` |
| Default | `false` |

### CaveRarity

Chance per chunk that a cave system starts.

| Property | Value |
|----------|-------|
| Type | `int` |
| Default | `14` |
| Range | `0` – `100` |

### CaveFrequency

Cave generation attempts per chunk.

| Property | Value |
|----------|-------|
| Type | `int` |
| Default | `15` |
| Range | `0` – `200` |

### CaveMinAltitude

| Property | Value |
|----------|-------|
| Type | `int` |
| Default | `8` |

### CaveMaxAltitude

| Property | Value |
|----------|-------|
| Type | `int` |
| Default | `128` |

### IndividualCaveRarity

Chance for a single cavern without an accompanying cave system.

| Property | Value |
|----------|-------|
| Type | `int` |
| Default | `25` |
| Range | `0` – `100` |

### CaveSystemFrequency

Cave system start attempts per frequency cycle.

| Property | Value |
|----------|-------|
| Type | `int` |
| Default | `1` |
| Range | `0` – `200` |

### CaveSystemPocketMinSize

| Property | Value |
|----------|-------|
| Type | `int` |
| Default | `0` |
| Range | `0` – `100` |

### CaveSystemPocketMaxSize

| Property | Value |
|----------|-------|
| Type | `int` |
| Default | `3` |
| Range | `0` – `100` |

### CaveSystemPocketChance

Chance for an additional cave pocket beyond the individual cave trigger.

| Property | Value |
|----------|-------|
| Type | `int` |
| Default | `0` |
| Range | `0` – `100` |

### RavinesEnabled

| Property | Value |
|----------|-------|
| Type | `boolean` |
| Default | `true` |

### RavineRarity

Chance per chunk that a ravine generates.

| Property | Value |
|----------|-------|
| Type | `int` |
| Default | `2` |
| Range | `0` – `100` |

### RavineMinAltitude

| Property | Value |
|----------|-------|
| Type | `int` |
| Default | `20` |

### RavineMaxAltitude

| Property | Value |
|----------|-------|
| Type | `int` |
| Default | `68` |

### RavineMinLength

| Property | Value |
|----------|-------|
| Type | `int` |
| Default | `84` |
| Range | `1` – `500` |

### RavineMaxLength

| Property | Value |
|----------|-------|
| Type | `int` |
| Default | `112` |
| Range | `1` – `500` |

### RavineDepth

Depth multiplier for ravines.

| Property | Value |
|----------|-------|
| Type | `double` |
| Default | `3.0` |
| Range | `0.1` – `15.0` |

---

## Noise Caves (1.18+)

Modern noise-based cave generation. Requires `UseModernCaves: true`.

### NoiseCavesEnabled

Enable noise cave carving (cheese, spaghetti, and noodle caves).

| Property | Value |
|----------|-------|
| Type | `boolean` |
| Default | `true` |

### AquifersEnabled

Enable vanilla-style aquifers — water and lava filling in noise caves.

| Property | Value |
|----------|-------|
| Type | `boolean` |
| Default | `true` |

### OreVeinsEnabled

Generate large 1.18+ ore veins.

| Property | Value |
|----------|-------|
| Type | `boolean` |
| Default | `true` |

### NoiseCaveFinalDensityOffset

Added to final cave density. Negative = more caves, positive = fewer caves.

| Property | Value |
|----------|-------|
| Type | `double` |
| Default | `0.0` |
| Range | `-5.0` – `5.0` |

### NoiseCaveFinalDensityScale

Final density multiplier. Values >1 = denser rock (smaller caves), <1 = larger caves.

| Property | Value |
|----------|-------|
| Type | `double` |
| Default | `1.0` |
| Range | `0.1` – `5.0` |

### NoiseCaveSpaghetti2DScale

Spaghetti 2D cave thickness/strength multiplier.

| Property | Value |
|----------|-------|
| Type | `double` |
| Default | `1.0` |
| Range | `0.0` – `5.0` |

### NoiseCaveSpaghetti3DScale

Spaghetti 3D cave (entrances, rarity, thickness) multiplier.

| Property | Value |
|----------|-------|
| Type | `double` |
| Default | `1.0` |
| Range | `0.0` – `5.0` |

### NoiseCaveNoodleScale

Noodle tunnel density multiplier.

| Property | Value |
|----------|-------|
| Type | `double` |
| Default | `1.0` |
| Range | `0.0` – `5.0` |

### NoiseCavePillarScale

Pillar thickness/quantity multiplier in cheese caves.

| Property | Value |
|----------|-------|
| Type | `double` |
| Default | `1.0` |
| Range | `0.0` – `5.0` |

### NoiseCaveVeinsEnabled

Enable ore veins in noise caves.

| Property | Value |
|----------|-------|
| Type | `boolean` |
| Default | `true` |

### NoiseCaveVeinMinY

| Property | Value |
|----------|-------|
| Type | `int` |
| Default | `-60` |
| Range | `-128` – `320` |

### NoiseCaveVeinMaxY

| Property | Value |
|----------|-------|
| Type | `int` |
| Default | `50` |
| Range | `-128` – `320` |

### NoiseCaveSurfaceSuppressionRange

Blocks below surface where caves are suppressed. Lower values = caves closer to surface.

| Property | Value |
|----------|-------|
| Type | `int` |
| Default | `30` |
| Range | `5` – `100` |

### NoiseCaveSurfaceBreakthroughChance

Fraction of terrain surface that allows caves to break through.

| Property | Value |
|----------|-------|
| Type | `double` |
| Default | `0.1` |
| Range | `0.0` – `1.0` |

### NoiseCaveSurfaceBreakthroughScale

Noise scale for breakthrough regions. Larger = bigger entrance areas.

| Property | Value |
|----------|-------|
| Type | `double` |
| Default | `128.0` |
| Range | `16.0` – `512.0` |

### NoiseCaveDebugCaveTypes

Color cave air by type: yellow = cheese, red = spaghetti, blue = noodle.

| Property | Value |
|----------|-------|
| Type | `boolean` |
| Default | `false` |

### Noise Parameters (Expert)

Each noise parameter has two settings: `FirstOctave` (int, -64 to 64) and `Amplitudes` (comma-separated doubles). These control the Perlin noise octaves used by each cave type.

!!! warning "Expert Only"
    Changing noise parameters can produce broken or unplayable terrain. Only modify these if you understand how noise octaves work.

**Aquifer noise:**

- `NoiseParamBarrierFirstOctave` / `NoiseParamBarrierAmplitudes`
- `NoiseParamFloodednessFirstOctave` / `NoiseParamFloodednessAmplitudes`
- `NoiseParamLavaFirstOctave` / `NoiseParamLavaAmplitudes`
- `NoiseParamSpreadFirstOctave` / `NoiseParamSpreadAmplitudes`

**Pillar noise:**

- `NoiseParamPillarFirstOctave` / `NoiseParamPillarAmplitudes`
- `NoiseParamRarenessFirstOctave` / `NoiseParamRarenessAmplitudes`
- `NoiseParamThicknessFirstOctave` / `NoiseParamThicknessAmplitudes`

**Spaghetti 2D noise:**

- `NoiseParamSpaghetti2DFirstOctave` / `NoiseParamSpaghetti2DAmplitudes`
- `NoiseParamElevationFirstOctave` / `NoiseParamElevationAmplitudes`
- `NoiseParamModulatorFirstOctave` / `NoiseParamModulatorAmplitudes`
- `NoiseParamSpaghetti2DThicknessFirstOctave` / `NoiseParamSpaghetti2DThicknessAmplitudes`

**Spaghetti 3D noise:**

- `NoiseParamSpaghetti3D1FirstOctave` / `NoiseParamSpaghetti3D1Amplitudes`
- `NoiseParamSpaghetti3D2FirstOctave` / `NoiseParamSpaghetti3D2Amplitudes`
- `NoiseParamSpaghetti3DRarityFirstOctave` / `NoiseParamSpaghetti3DRarityAmplitudes`
- `NoiseParamSpaghetti3DThicknessFirstOctave` / `NoiseParamSpaghetti3DThicknessAmplitudes`
- `NoiseParamRoughnessFirstOctave` / `NoiseParamRoughnessAmplitudes`
- `NoiseParamRoughnessModulatorFirstOctave` / `NoiseParamRoughnessModulatorAmplitudes`

**General cave noise:**

- `NoiseParamCaveEntranceFirstOctave` / `NoiseParamCaveEntranceAmplitudes`
- `NoiseParamCaveLayerFirstOctave` / `NoiseParamCaveLayerAmplitudes`
- `NoiseParamCaveCheeseFirstOctave` / `NoiseParamCaveCheeseAmplitudes`

**Noodle noise:**

- `NoiseParamNoodleFirstOctave` / `NoiseParamNoodleAmplitudes`
- `NoiseParamNoodleThicknessFirstOctave` / `NoiseParamNoodleThicknessAmplitudes`
- `NoiseParamRidgeAFirstOctave` / `NoiseParamRidgeAAmplitudes`
- `NoiseParamRidgeBFirstOctave` / `NoiseParamRidgeBAmplitudes`

**Ore vein noise:**

- `NoiseParamOreVeininessFirstOctave` / `NoiseParamOreVeininessAmplitudes`
- `NoiseParamOreVeinAFirstOctave` / `NoiseParamOreVeinAAmplitudes`
- `NoiseParamOreVeinBFirstOctave` / `NoiseParamOreVeinBAmplitudes`
- `NoiseParamOreGapFirstOctave` / `NoiseParamOreGapAmplitudes`

---

## Vanilla Structures

Enable/disable and configure spacing for vanilla structure generation.

### Structure Toggles

| Setting | Default | Description |
|---------|---------|-------------|
| `VillagesEnabled` | `true` | Villages |
| `MineshaftsEnabled` | `true` | Mineshafts |
| `StrongholdsEnabled` | `true` | Strongholds |
| `RareBuildingsEnabled` | `true` | Desert pyramids, jungle temples, igloos, swamp huts |
| `WoodlandsMansionsEnabled` | `true` | Woodland mansions |
| `OceanMonumentsEnabled` | `true` | Ocean monuments |
| `NetherFortressesEnabled` | `false` | Nether fortresses |
| `BuriedTreasureEnabled` | `true` | Buried treasure |
| `OceanRuinsEnabled` | `true` | Ocean ruins |
| `PillagerOutpostsEnabled` | `true` | Pillager outposts |
| `BastionRemnantsEnabled` | `true` | Bastion remnants |
| `NetherFossilsEnabled` | `true` | Nether fossils |
| `EndCitiesEnabled` | `true` | End cities |
| `RuinedPortalsEnabled` | `true` | Ruined portals |
| `ShipwrecksEnabled` | `true` | Shipwrecks |

### Structure Spacing & Separation

Spacing = average distance between structures (in chunks). Separation = minimum distance. Works like vanilla datapack structure settings.

| Structure | Spacing | Separation |
|-----------|---------|------------|
| Village | `32` | `8` |
| Mineshaft | `1` | `0` |
| Stronghold | `1` | `0` |
| Ocean Monument | `32` | `5` |
| End City | `20` | `11` |
| Woodland Mansion | `80` | `20` |
| Buried Treasure | `1` | `0` |
| Ruined Portal | `40` | `15` |
| Shipwreck | `24` | `4` |
| Ocean Ruin | `20` | `8` |
| Bastion Remnant | `27` | `4` |
| Nether Fortress | `27` | `4` |
| Nether Fossil | `2` | `1` |
| Desert Pyramid | `32` | `8` |
| Igloo | `32` | `8` |
| Jungle Temple | `32` | `8` |
| Swamp Hut | `32` | `8` |
| Pillager Outpost | `32` | `8` |

Setting names follow the pattern `<Structure>Spacing` and `<Structure>Separation` (e.g. `VillageSpacing`, `VillageSeparation`).

### Stronghold-Specific Settings

| Setting | Type | Default | Description |
|---------|------|---------|-------------|
| `StrongholdDistance` | `int` | `32` | Distance from world center (in chunks) |
| `StrongholdSpread` | `int` | `3` | Spread factor |
| `StrongholdCount` | `int` | `128` | Total strongholds in world |

---

## Custom Structures

### CustomStructureType

| Property | Value |
|----------|-------|
| Type | `enum` |
| Default | `BO3` |
| Options | `BO3`, `BO4` |

- **BO3** — seed-based placement, simpler, faster
- **BO4** — collision detection, advanced branching, more complex

### BO3AtSpawn

BO3 structure placed at world spawn. Max 32x32 blocks.

| Property | Value |
|----------|-------|
| Type | `string` |
| Default | *(empty)* |

### UseOldBO3StructureRarity

Use pre-1.16 double-rarity system. When `false`, rarity comes only from the `CustomStructure()` resource tag.

| Property | Value |
|----------|-------|
| Type | `boolean` |
| Default | `true` |

### MaximumCustomStructureRadius

Max radius (in chunks) for BO3 CustomStructure objects. Not used for BO4.

| Property | Value |
|----------|-------|
| Type | `int` |
| Default | `5` |
| Range | `1` – `100` |

### DecorationBoundsCheck

When `true`, limits objects to 32x32. When `false`, allows larger objects but makes generation direction-dependent.

| Property | Value |
|----------|-------|
| Type | `boolean` |
| Default | `true` |

### DisableOreGen

Disable all `Ore()`, `UnderWaterOre()`, and `Vein()` biome resources that use ore blocks.

| Property | Value |
|----------|-------|
| Type | `boolean` |
| Default | `false` |

---

## Spawn Point

### FixedSpawnPoint

Enable fixed spawn coordinates instead of searching for a suitable location.

| Property | Value |
|----------|-------|
| Type | `boolean` |
| Default | `false` |

### SpawnPointX

| Property | Value |
|----------|-------|
| Type | `int` |
| Default | `0` |

### SpawnPointY

| Property | Value |
|----------|-------|
| Type | `int` |
| Default | `0` |

### SpawnPointZ

| Property | Value |
|----------|-------|
| Type | `int` |
| Default | `0` |

### SpawnPointAngle

Player look angle at spawn (degrees).

| Property | Value |
|----------|-------|
| Type | `float` |
| Default | `0.0` |

---

## Portal Settings

Portal configuration for custom OTG dimensions. These settings only apply to custom dimensions — not the overworld, nether, or end.

### PortalBlocks

Block(s) that form the portal frame.

| Property | Value |
|----------|-------|
| Type | `block` |
| Default | `minecraft:quartz_block` |

### PortalColor

| Property | Value |
|----------|-------|
| Type | `enum` |
| Default | `default` |
| Options | `default`, `beige`, `black`, `blue`, `crystalblue`, `darkblue`, `darkgreen`, `darkred`, `emerald`, `flame`, `gold`, `green`, `grey`, `lightblue`, `lightgreen`, `orange`, `pink`, `red`, `white`, `yellow` |

### PortalMob

Entity that spawns from the portal.

| Property | Value |
|----------|-------|
| Type | `string` |
| Default | `minecraft:zombified_piglin` |

### PortalIgnitionSource

Item used to ignite the portal.

| Property | Value |
|----------|-------|
| Type | `string` |
| Default | `minecraft:flint_and_steel` |

### PortalMinWidth

Minimum interior portal width.

| Property | Value |
|----------|-------|
| Type | `int` |
| Default | `2` |
| Range | `1` – `21` |

### PortalMaxWidth

| Property | Value |
|----------|-------|
| Type | `int` |
| Default | `21` |
| Range | `2` – `64` |

### PortalMinHeight

Minimum interior portal height.

| Property | Value |
|----------|-------|
| Type | `int` |
| Default | `3` |
| Range | `2` – `21` |

### PortalMaxHeight

| Property | Value |
|----------|-------|
| Type | `int` |
| Default | `21` |
| Range | `3` – `64` |

---

## Dimension Settings

Properties of the dimension itself — height, lighting, physics.

### DimensionType

| Property | Value |
|----------|-------|
| Type | `enum` |
| Default | `OTG` |
| Options | `OVERWORLD`, `NETHER`, `END`, `OTG` |

Must be `OTG` to use the custom dimension settings below.

### MinY

Minimum Y coordinate. Must be a multiple of 16.

| Property | Value |
|----------|-------|
| Type | `int` |
| Default | `-64` |

### Height

Total world height. Must be a multiple of 16.

| Property | Value |
|----------|-------|
| Type | `int` |
| Default | `384` |

### LogicalHeight

Maximum height for nether portal placement and chorus fruit teleportation. Cannot exceed Height.

| Property | Value |
|----------|-------|
| Type | `int` |
| Default | `384` |

### FixedTime

Fixed time of day (in ticks). `-1` = normal day/night cycle. `6000` = perpetual noon. `18000` = perpetual midnight.

| Property | Value |
|----------|-------|
| Type | `long` |
| Default | `-1` |
| Range | `-1`, `0` – `24000` |

### HasSkylight

Dimension has a sky and sunlight. `false` for nether/end-like dimensions.

| Property | Value |
|----------|-------|
| Type | `boolean` |
| Default | `true` |

### HasCeiling

Dimension has a ceiling. Affects mob spawning, weather, and map rendering. `true` for nether-like dimensions.

| Property | Value |
|----------|-------|
| Type | `boolean` |
| Default | `false` |

### UltraWarm

Water evaporates and sponges dry instantly. `true` for nether-like dimensions.

| Property | Value |
|----------|-------|
| Type | `boolean` |
| Default | `false` |

### Natural

When `false`, compasses spin randomly, beds don't work, and mobs won't spawn from portals.

| Property | Value |
|----------|-------|
| Type | `boolean` |
| Default | `true` |

### CoordinateScale

Block travel ratio relative to other dimensions. `1.0` = normal (overworld). `8.0` = nether-style (1 block = 8 in overworld).

| Property | Value |
|----------|-------|
| Type | `double` |
| Default | `1.0` |

### CreateDragonFight

Enable the ender dragon fight in this dimension.

| Property | Value |
|----------|-------|
| Type | `boolean` |
| Default | `false` |

### PiglinSafe

Piglins won't convert to zombified piglins.

| Property | Value |
|----------|-------|
| Type | `boolean` |
| Default | `false` |

### BedWorks

Beds can be used to skip time.

| Property | Value |
|----------|-------|
| Type | `boolean` |
| Default | `true` |

### RespawnAnchorWorks

| Property | Value |
|----------|-------|
| Type | `boolean` |
| Default | `true` |

### HasRaids

| Property | Value |
|----------|-------|
| Type | `boolean` |
| Default | `true` |

### InfiniBurn

Tag controlling which blocks burn infinitely.

| Property | Value |
|----------|-------|
| Type | `string` |
| Default | `minecraft:infiniburn_overworld` |

### EffectsLocation

Visual effects style. Use `minecraft:the_nether` for nether fog, `minecraft:the_end` for end sky.

| Property | Value |
|----------|-------|
| Type | `string` |
| Default | `minecraft:overworld` |

### AmbientLight

Base ambient light level. `0.0` for overworld/end, `0.1` for nether.

| Property | Value |
|----------|-------|
| Type | `double` |
| Default | `0.0` |

### MonsterSpawnLightLevel

Maximum light level at which monsters can spawn.

| Property | Value |
|----------|-------|
| Type | `int` |
| Default | `7` |
| Range | `0` – `15` |

### MonsterSpawnLightVariationMin

| Property | Value |
|----------|-------|
| Type | `int` |
| Default | `0` |
| Range | `0` – `15` |

### MonsterSpawnLightVariationMax

| Property | Value |
|----------|-------|
| Type | `int` |
| Default | `7` |
| Range | `0` – `15` |

---

## GameRules

Per-dimension GameRule overrides. Set `OverrideGameRules: true` to enable.

These can also be overridden at the WorldPreset YAML level — see [WorldPreset YAML](world-presets.md) for the 3-layer hierarchy.

### OverrideGameRules

| Property | Value |
|----------|-------|
| Type | `boolean` |
| Default | `false` |

### Vanilla Behavior

| Setting | Type | Default | Description |
|---------|------|---------|-------------|
| `DoFireTick` | `boolean` | `true` | Fire spreads and causes damage |
| `MobGriefing` | `boolean` | `true` | Mobs can destroy blocks |
| `KeepInventory` | `boolean` | `false` | Keep items on death |
| `DoMobSpawning` | `boolean` | `true` | Natural mob spawning |
| `DoMobLoot` | `boolean` | `true` | Mobs drop loot |
| `DoTileDrops` | `boolean` | `true` | Blocks drop when broken |
| `DoEntityDrops` | `boolean` | `true` | Entities drop items |
| `NaturalRegeneration` | `boolean` | `true` | Player health regeneration |
| `DoDaylightCycle` | `boolean` | `true` | Day/night cycle |
| `DoWeatherCycle` | `boolean` | `true` | Weather changes |

### Command & Admin

| Setting | Type | Default | Description |
|---------|------|---------|-------------|
| `CommandBlockOutput` | `boolean` | `true` | Command blocks show output |
| `LogAdminCommands` | `boolean` | `true` | Log admin commands |
| `SendCommandFeedback` | `boolean` | `true` | Show command feedback |
| `ShowDeathMessages` | `boolean` | `true` | Death messages |
| `AnnounceAdvancements` | `boolean` | `true` | Advancement announcements |

### Spawning & Limits

| Setting | Type | Default | Description |
|---------|------|---------|-------------|
| `SpawnRadius` | `int` | `10` | Spawn protection radius |
| `RandomTickSpeed` | `int` | `3` | Block update frequency |
| `MaxEntityCramming` | `int` | `24` | Max entities in one block |
| `MaxCommandChainLength` | `int` | `65536` | Max command chain |
| `MaxCommandForkCount` | `int` | `65536` | Max command fork depth |
| `CommandModificationBlockLimit` | `int` | `32768` | Blocks modifiable by commands |

### Movement & Mechanics

| Setting | Type | Default | Description |
|---------|------|---------|-------------|
| `DisableElytraMovementCheck` | `boolean` | `false` | Disable elytra speed check |
| `SpectatorsGenerateChunks` | `boolean` | `true` | Spectators load chunks |
| `DoLimitedCrafting` | `boolean` | `false` | Only unlocked recipes craftable |

### Damage

| Setting | Type | Default | Description |
|---------|------|---------|-------------|
| `DrowningDamage` | `boolean` | `true` | Drowning damage |
| `FallDamage` | `boolean` | `true` | Fall damage |
| `FireDamage` | `boolean` | `true` | Fire damage |
| `FreezeDamage` | `boolean` | `true` | Freeze damage |

### Mob Behavior

| Setting | Type | Default | Description |
|---------|------|---------|-------------|
| `DoInsomnia` | `boolean` | `true` | Phantoms spawn |
| `DoPatrolSpawning` | `boolean` | `true` | Patrols spawn |
| `DoTraderSpawning` | `boolean` | `true` | Wandering traders |
| `ForgiveDeadPlayers` | `boolean` | `true` | Mobs forget dead players |
| `UniversalAnger` | `boolean` | `false` | Mobs share anger |
| `DisableRaids` | `boolean` | `false` | Disable raids |
| `DoWardenSpawning` | `boolean` | `true` | Wardens spawn |

### Explosions & Decay

| Setting | Type | Default | Description |
|---------|------|---------|-------------|
| `BlockExplosionDropDecay` | `boolean` | `true` | Block explosion drop decay |
| `MobExplosionDropDecay` | `boolean` | `true` | Mob explosion drop decay |
| `TntExplosionDropDecay` | `boolean` | `false` | TNT explosion drop decay |

### Liquids

| Setting | Type | Default | Description |
|---------|------|---------|-------------|
| `WaterSourceConversion` | `boolean` | `true` | Water forms source blocks |
| `LavaSourceConversion` | `boolean` | `false` | Lava forms source blocks |
| `DoVinesSpread` | `boolean` | `true` | Vines spread |

### Miscellaneous

| Setting | Type | Default | Description |
|---------|------|---------|-------------|
| `ReducedDebugInfo` | `boolean` | `false` | Less debug info on F3 |
| `DoImmediateRespawn` | `boolean` | `false` | Skip death screen |
| `ProjectilesCanBreakBlocks` | `boolean` | `true` | Projectiles break blocks |
| `EnderPearlsVanishOnDeath` | `boolean` | `true` | Ender pearls vanish on death |
| `GlobalSoundEvents` | `boolean` | `true` | Sounds heard everywhere |
| `PlayersNetherPortalDefaultDelay` | `int` | `80` | Portal cooldown (ticks) |
| `PlayersNetherPortalCreativeDelay` | `int` | `1` | Creative portal cooldown |
| `PlayersSleepingPercentage` | `int` | `100` | % players to skip night |
| `SnowAccumulationHeight` | `int` | `1` | Max snow layers |
| `SpawnChunkRadius` | `int` | `2` | Spawn chunk radius |
