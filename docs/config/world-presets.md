# WorldPreset YAML

WorldPreset YAML files define complete worlds by composing multiple DimensionPresets. They control which preset generates each dimension (overworld, nether, end, custom), plus world-level settings like GameRules and portal configuration.

Located in `WorldPresets/` inside your OTG config folder. Every `.yaml` or `.yml` file in this folder is automatically loaded at startup and appears in the world creation GUI.

---

## Minimal Example

```yaml
Version: 1
DisplayName: "My World"
Description: "OTG overworld with vanilla nether and end"
Overworld:
  PresetFolderName: "DefaultPreset"
Settings:
  GenerateStructures: true
  BonusChest: false
```

This creates a world type called **My World** that uses the `DefaultPreset` DimensionPreset for the overworld and vanilla generation for the nether and end.

---

## Full Structure

```yaml
Version: 1
DisplayName: "Example World"          # REQUIRED — shown in world creation GUI
Description: "A complete example"
ModpackName: "My Modpack"             # Optional modpack identifier

# World-level GameRules — applied to ALL dimensions
GameRules:
  DoFireTick: true
  MobGriefing: false
  KeepInventory: true
  RandomTickSpeed: 3

# Overworld dimension
Overworld:
  PresetFolderName: "DefaultPreset"   # OTG DimensionPreset folder name

# Nether — omit or leave empty for vanilla
Nether:
  PresetFolderName: "MyNetherPreset"

# End — omit or leave empty for vanilla
End:
  PresetFolderName: "MySkylands"

# Custom dimensions (optional, array)
Dimensions:
  - PresetFolderName: "AlienWorld"
    Seed: 42
    PortalBlocks: "minecraft:emerald_block"
    PortalColor: "green"
    PortalMob: "minecraft:zombified_piglin"
    PortalIgnitionSource: "minecraft:flint_and_steel"
    RespawnInDimension: true
    GameRules:                        # Per-dimension overrides
      DoDaylightCycle: false
      DoWeatherCycle: false

# World settings
Settings:
  GenerateStructures: true
  BonusChest: false
```

---

## Fields Reference

### Root Fields

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `Version` | `int` | Yes | Always `1` |
| `DisplayName` | `string` | Yes | Name shown in world creation GUI |
| `Description` | `string` | No | Tooltip description |
| `ModpackName` | `string` | No | Modpack identifier |
| `Overworld` | `object` | No | Overworld configuration |
| `Nether` | `object` | No | Nether configuration |
| `End` | `object` | No | End configuration |
| `Dimensions` | `array` | No | Custom dimension list |
| `Settings` | `object` | No | World creation settings |
| `GameRules` | `object` | No | World-level GameRule overrides |

### Dimension Object

Used for `Overworld`, `Nether`, `End`, and each entry in `Dimensions`.

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `PresetFolderName` | `string` | — | DimensionPreset folder name (omit for vanilla) |
| `Seed` | `long` | — | Per-dimension seed offset |
| `PortalBlocks` | `string` | — | Portal frame block (custom dimensions only) |
| `PortalColor` | `string` | — | Portal color name |
| `PortalMob` | `string` | — | Entity spawned from portal |
| `PortalIgnitionSource` | `string` | — | Item to ignite portal |
| `RespawnInDimension` | `boolean` | `false` | Players respawn in this dimension instead of overworld |
| `GameRules` | `object` | — | Per-dimension GameRule overrides |

Additional dimension fields:

| Field | Type | Description |
|-------|------|-------------|
| `NonOTGWorldType` | `string` | Use a non-OTG generator for this entry. Looks up the value as a WorldPreset in MC's registry. Vanilla types: `flat`, `amplified`, `large_biomes`, `normal`. Modded types: use `modid:name` (e.g. `biomesoplenty:biomesoplenty`). Works on `Overworld`, `Nether`, `End` and `Dimensions` entries. On `Nether`/`End`, the referenced preset's nether/end dimension is used; custom `Dimensions` entries use its overworld generator. If the preset is not found: slots fall back to vanilla, custom entries are skipped. Mutually exclusive with `PresetFolderName`. |
| `DimensionName` | `string` | Required for non-OTG `Dimensions` entries: the dimension's registry name (registered as `otg:<name>`). Ignored on `Overworld`/`Nether`/`End` (their keys are fixed) and optional for OTG entries (defaults to the preset folder name). |
| `NonOTGGeneratorSettings` | `string` | Reserved for future use. Currently not implemented. |

### Settings Object

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `GenerateStructures` | `boolean` | `true` | Enable vanilla structure generation |
| `BonusChest` | `boolean` | `false` | Place a bonus chest at spawn |

---

## GameRules Override Hierarchy

GameRules follow a 3-layer override system:

1. **DimensionPresetConfig.ini** — base values (when `OverrideGameRules: true`)
2. **WorldPreset YAML — world-level** — overrides the INI values for all dimensions
3. **WorldPreset YAML — per-dimension** — overrides world-level for a specific dimension

Any field left out (null) means "don't override — use the previous layer's value."

```yaml
# World-level: applies to ALL dimensions
GameRules:
  KeepInventory: true       # All dimensions keep inventory
  DoFireTick: true

Dimensions:
  - PresetFolderName: "Hellscape"
    GameRules:
      DoFireTick: false     # Only Hellscape overrides fire tick
      # KeepInventory not specified → inherits true from world-level
```

### Available GameRules

All 52 vanilla GameRules are supported. Boolean rules:

`DoFireTick`, `MobGriefing`, `KeepInventory`, `DoMobSpawning`, `DoMobLoot`, `DoTileDrops`, `DoEntityDrops`, `NaturalRegeneration`, `DoDaylightCycle`, `DoWeatherCycle`, `CommandBlockOutput`, `LogAdminCommands`, `SendCommandFeedback`, `ShowDeathMessages`, `AnnounceAdvancements`, `DisableElytraMovementCheck`, `SpectatorsGenerateChunks`, `DoLimitedCrafting`, `DrowningDamage`, `FallDamage`, `FireDamage`, `FreezeDamage`, `DoInsomnia`, `DoPatrolSpawning`, `DoTraderSpawning`, `ForgiveDeadPlayers`, `UniversalAnger`, `DisableRaids`, `DoWardenSpawning`, `BlockExplosionDropDecay`, `MobExplosionDropDecay`, `TntExplosionDropDecay`, `WaterSourceConversion`, `LavaSourceConversion`, `DoVinesSpread`, `ReducedDebugInfo`, `DoImmediateRespawn`, `ProjectilesCanBreakBlocks`, `EnderPearlsVanishOnDeath`, `GlobalSoundEvents`

Integer rules:

`SpawnRadius`, `RandomTickSpeed`, `MaxEntityCramming`, `MaxCommandChainLength`, `MaxCommandForkCount`, `CommandModificationBlockLimit`, `PlayersNetherPortalDefaultDelay`, `PlayersNetherPortalCreativeDelay`, `PlayersSleepingPercentage`, `SnowAccumulationHeight`, `SpawnChunkRadius`

!!! note "Global-state rules"
    Day time and weather are stored **once per world save** and advanced only by the
    overworld — Minecraft has no per-dimension clock. A per-dimension
    `DoDaylightCycle`/`DoWeatherCycle` override therefore only takes effect when set on
    the **Overworld** entry; on other dimensions the rule value is applied (visible via
    `/gamerule`) but time and weather keep following the overworld. For a dimension with
    frozen time, use a DimensionPreset with `FixedTime` in `DimensionPresetConfig.ini`
    instead.

---

## Portal Configuration

Custom dimensions (entries in the `Dimensions` array) automatically get OTG portals. Configure the portal appearance and behavior per dimension.

### Portal Colors

`default`, `beige`, `black`, `blue`, `crystalblue`, `darkblue`, `darkgreen`, `darkred`, `emerald`, `flame`, `gold`, `green`, `grey`, `lightblue`, `lightgreen`, `orange`, `pink`, `red`, `white`, `yellow`

### Example

```yaml
Dimensions:
  - PresetFolderName: "CrystalCaves"
    PortalBlocks: "minecraft:diamond_block"
    PortalColor: "crystalblue"
    PortalMob: "minecraft:enderman"
    PortalIgnitionSource: "minecraft:flint_and_steel"
    RespawnInDimension: true
```

Build a portal frame from diamond blocks, light it with flint and steel, and you'll teleport to the CrystalCaves dimension.

---

## Using Vanilla Generation

To use vanilla generation for a dimension instead of OTG, omit `PresetFolderName`:

```yaml
# OTG overworld, vanilla nether and end
Overworld:
  PresetFolderName: "DefaultPreset"
# Nether and End sections omitted = vanilla
```

## Non-OTG dimensions

Worldgen mods that normally replace the overworld (e.g. Biomes O' Plenty) can instead be
mounted as a separate dimension, reachable through an OTG portal, with their own GameRules:

```yaml
Overworld:
  PresetFolderName: "Biome Bundle"

Dimensions:
- DimensionName: "bop_world"
  NonOTGWorldType: "biomesoplenty:biomesoplenty"
  PortalColor: "purple"
  PortalBlocks: "minecraft:amethyst_block"
  PortalIgnitionSource: "minecraft:flint_and_steel"
  GameRules:
    KeepInventory: true
```

Notes:

- `Seed` is ignored on non-OTG entries — vanilla and modded generators always use the world seed.
- A non-OTG entry without `PortalBlocks` gets no OTG portal (reachable via commands or other mods only).
- GameRules on non-OTG entries apply on top of vanilla defaults (there is no DimensionPreset ini layer).
- On `Nether`/`End` slots, `NonOTGWorldType` extracts that preset's nether/end dimension — useful mainly for modded WorldPresets that define custom nether/end generation (vanilla presets like `flat` carry the standard nether/end).

---

## File Loading

- All `.yaml` and `.yml` files in `WorldPresets/` are loaded automatically at startup
- Unknown fields are silently ignored (lenient parsing)
- `DisplayName` is **required** — YAMLs without it won't appear in the GUI
- File names don't matter — the `DisplayName` field determines the GUI label
