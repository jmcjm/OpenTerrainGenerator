package com.pg85.otg.config.biome;

import com.pg85.otg.config.io.SettingsMap;
import com.pg85.otg.config.settings.biome.*;
import com.pg85.otg.config.settings.biome.generated.BiomePlacementSettings;
import com.pg85.otg.config.settings.preset.BlockSettings;
import com.pg85.otg.config.settings.preset.GenerationSettings;
import com.pg85.otg.constants.Constants;
import com.pg85.otg.constants.settings.IceSpikeType;
import com.pg85.otg.util.helpers.StringHelper;
import com.pg85.otg.util.minecraft.PlantType;
import com.pg85.otg.util.minecraft.SaplingType;

public class BiomeConfigWriter {
    public static void writeConfigSettings(BiomeConfig biomeConfig, SettingsMap writer) {
        boolean isTemplateBiome = biomeConfig.getIdentitySettings().isTemplateForBiome();

        writer.header1("Biome Identity");

        writer.putSetting(OutdatedSettings.IS_TEMPLATE_FOR_BIOME, biomeConfig.getIdentitySettings().isTemplateForBiome(),
                "Set this to true if this biome config is used with non-OTG biomes, configured in the DimensionPresetConfig via TemplateBiome()",
                "OTG generates the terrain for the biome as configured in this file and spawns resources, but also allows the biome to spawn ",
                "its own resources and mobs and apply its settings. Because of this, the following OTG settings cannot be used:",
                "- Colors, Mob spawning, particles, sounds, vanilla structures, wetness, temperature.",
                "What can be configured: ",
                " - Biome generator settings.",
                " - Terrain settings.",
                " - Resources. Non-OTG biome resources are currently spawned after all OTG resources in the resourcequeue.",
                " - OTG settings not mentioned above that are handled by OTG and don't rely on MC logic.");

        writer.putSetting(IdentitySettings.DISPLAY_NAME, biomeConfig.getIdentitySettings());

        writer.header1("Biome placement");

        writer.putSetting(BiomePlacementSettings.BIOME_SIZE, biomeConfig.getGenerationSettings().getBiomeSize(),
                "Biome size from 0 to GenerationDepth. Defines in which biome layer this biome will be generated (see GenerationDepth).",
                "Higher numbers result in a smaller biome, lower numbers a larger biome.",
                "How this setting is used depends on the value of BiomeMode in the DimensionPresetConfig.",
                "It will be used for:",
                "- normal biomes, ice biomes, isle biomes and border biomes when BiomeMode is set to NoGroups",
                "- biomes spawned as part of a BiomeGroup when BiomeMode is set to Normal.",
                "  For biomes spawned as isles, borders or rivers other settings are available.",
                "  Isle biomes:	" + BiomePlacementSettings.BIOME_SIZE_WHEN_ISLE + " (see below)",
                "  Border biomes: " + BiomePlacementSettings.BIOME_SIZE_WHEN_BORDER + " (see below)",
                "  River biomes:  " + GenerationSettings.RIVER_SIZE + " (see DimensionPresetConfig)");

        writer.putSetting(BiomePlacementSettings.BIOME_RARITY, biomeConfig.getGenerationSettings().getBiomeRarity(),
                "Biome rarity from 100 to 1. If this is normal or ice biome - chance to spawn this biome, then others.",
                "Example for normal biome :",
                "  100 rarity mean 1/6 chance than other ( with 6 default normal biomes).",
                "  50 rarity mean 1/11 chance than other",
                "For isle biomes see the " + BiomePlacementSettings.BIOME_RARITY_WHEN_ISLE + " setting below.",
                "Doesn`t work on Ocean and River (frozen versions too) biomes when not added as normal biome.");

        writer.putSetting(BiomePlacementSettings.BIOME_MAP_COLOR, biomeConfig.getGenerationSettings().getBiomeMapColor(),
                "The hexadecimal color value of this biome. Used in the output of the /otg map command,",
                "and used in the input of BiomeMode: FromImage.");

        writer.header2("Isle biomes", "To spawn a biome as an isle, first add it to the",
                GenerationSettings.ISLE_BIOMES + " list in the DimensionPresetConfig.", "");

        writer.putSetting(BiomePlacementSettings.ISLE_IN_BIOMES, biomeConfig.getGenerationSettings().getIsleInBiomes(),
                "List of biomes in which this biome will spawn as an isle.",
                "For example, Mushroom Isles spawn inside the Ocean biome.");

        writer.putSetting(BiomePlacementSettings.BIOME_SIZE_WHEN_ISLE, biomeConfig.getGenerationSettings().getBiomeSizeWhenIsle(),
                "Size of this biome when spawned as an isle biome in BiomeMode: Normal.",
                "Valid values range from 0 to GenerationDepth.",
                "Larger numbers give *smaller* islands. The biome must be smaller than the biome it's going",
                "to spawn in, so the " + BiomePlacementSettings.BIOME_SIZE_WHEN_ISLE + " number must be larger than the "
                        + BiomePlacementSettings.BIOME_SIZE + " of the other biome.");

        writer.putSetting(BiomePlacementSettings.BIOME_RARITY_WHEN_ISLE, biomeConfig.getGenerationSettings().getBiomeRarityWhenIsle(),
                "Rarity of this biome when spawned as an isle biome in BiomeMode: Normal.");

        writer.smallTitle("Border biomes", "To spawn a biome as a border, first add it to the",
                GenerationSettings.BORDER_BIOMES + " list in the DimensionPresetConfig.", "");

        writer.putSetting(BiomePlacementSettings.BORDER_IN_BIOMES, biomeConfig.getGenerationSettings().getBorderInBiomes(),
                "List of biomes this biome can be a border of.",
                "For example, the Beach biome is a border on the Ocean biome, so",
                "it can spawn anywhere on the border of an ocean.");

        writer.putSetting(BiomePlacementSettings.ONLY_BORDER_NEAR, biomeConfig.getGenerationSettings().getOnlyBorderNear(),
                "Whitelist of neighouring biomes that allow this border biome to spawn.");

        writer.putSetting(BiomePlacementSettings.NOT_BORDER_NEAR, biomeConfig.getGenerationSettings().getNotBorderNear(),
                "Blacklist of neighbouring biomes that do not allow this border biome to spawn.",
                "For example, the Beach biome will never spawn next to an Extreme Hills biome.",
                "Only used when OnlyBorderNear is empty / not used.");

        writer.putSetting(BiomePlacementSettings.BIOME_SIZE_WHEN_BORDER, biomeConfig.getGenerationSettings().getBiomeSizeWhenBorder(),
                "Size of this biome when spawned as a border biome in BiomeMode: Normal.",
                "Valid values range from 0 to GenerationDepth.",
                "Larger numbers give *smaller* borders. The biome must be smaller than the biome it's going",
                "to spawn in, so the " + BiomePlacementSettings.BIOME_SIZE_WHEN_BORDER + " number must be larger than the "
                        + BiomePlacementSettings.BIOME_SIZE + " of the other biome.");

        writer.header1("Terrain height and volatility");

        writer.putSetting(BiomeTerrainSettings.BIOME_HEIGHT, biomeConfig.getTerrainSettings().getBiomeHeight(),
                "BiomeHeight defines how much height will be added during terrain generation",
                "Must be between -10.0 and 10.0",
                "Value 0.0 is equivalent to half of map height with all other settings at defaults.");

        writer.putSetting(BiomeTerrainSettings.BIOME_VOLATILITY, biomeConfig.getTerrainSettings().getBiomeVolatility(), "Biome volatility.");

        writer.putSetting(BiomeTerrainSettings.SMOOTH_RADIUS, biomeConfig.getTerrainSettings().getSmoothRadius(),
                "Smooth radius between biomes. Must be between 0 and 32, inclusive. The resulting",
                "smooth radius seems to be  (thisSmoothRadius + 1 + smoothRadiusOfBiomeOnOtherSide) * 4 .",
                "So if two biomes next to each other have both a smooth radius of 2, the",
                "resulting smooth area will be (2 + 1 + 2) * 4 = 20 blocks wide.");

        writer.putSetting(BiomeTerrainSettings.CUSTOM_HEIGHT_CONTROL_SMOOTH_RADIUS, biomeConfig.getTerrainSettings().getCHCSmoothRadius(),
                "Works the same way as SmoothRadius but only acts on CustomHeightControl. Must be between 0 and 32, inclusive.",
                "Does nothing if Custom Height Control smoothing is not enabled in the world config.");

        writer.putSetting(BiomeTerrainSettings.PEAK_FACTOR, biomeConfig.getTerrainSettings().getPeakFactor(),
                "How strongly this biome responds to continental peaks (multiplier, 1.0 = full response).");

        writer.putSetting(BiomeTerrainSettings.VALLEY_FACTOR, biomeConfig.getTerrainSettings().getValleyFactor(),
                "How strongly this biome responds to continental valleys (multiplier, 1.0 = full response).");

        writer.putSetting(BiomeTerrainSettings.VOLATILITY_1, biomeConfig.getTerrainSettings().getVolatility1(),
                "Another type of noise. This noise is independent from biomes. The larger the values the more chaotic/volatile landscape generation becomes.",
                "Setting the values to negative will have the opposite effect and make landscape generation calmer/gentler.");

        writer.putSetting(BiomeTerrainSettings.VOLATILITY_2, biomeConfig.getTerrainSettings().getVolatility2());

        writer.putSetting(BiomeTerrainSettings.VOLATILITY_WEIGHT_1, biomeConfig.getTerrainSettings().getVolatilityWeight1(),
                "Adjust the weight of the corresponding volatility settings. This allows you to change how prevalent you want either of the volatility settings to be in the terrain.");

        writer.putSetting(BiomeTerrainSettings.VOLATILITY_WEIGHT_2, biomeConfig.getTerrainSettings().getVolatilityWeight2());

        writer.putSetting(BiomeTerrainSettings.DISABLE_BIOME_HEIGHT, biomeConfig.getTerrainSettings().isDisableBiomeHeight(),
                "Disable all noises except Volatility1 and Volatility2. Also disable default block chance from height.");

        writer.putSetting(BiomeTerrainSettings.CUSTOM_HEIGHT_CONTROL, biomeConfig.getTerrainSettings().getCustomHeightControl(),
                "List of custom height factors, 17 double entries, each controls about 7",
                "blocks height, starting at the bottom of the world. Positive entry - larger chance of spawn blocks, negative - smaller",
                "Values which affect your configuration may be found only experimentally. Values may be very big, like ~3000.0 depends from height",
                "Example:",
                "  CustomHeightControl:0.0,-2500.0,0.0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0",
                "Makes empty layer above bedrock layer. ");

        writer.header1("Rivers");

        writer.putSetting(BiomePlacementSettings.RIVER_BIOME, biomeConfig.getGenerationSettings().getRiverBiome(), "The biome used as the river biome.");

        writer.header1("Blocks");

        if (!isTemplateBiome) {
            writer.putSetting(SurfaceSettings.STONE_BLOCK, biomeConfig.getSurfaceSettings().getStoneBlock(),
                    "The stone block used for the biome, usually STONE.");

            writer.putSetting(SurfaceSettings.SURFACE_BLOCK, biomeConfig.getSurfaceSettings().getSurfaceBlock(),
                    "The surface block used for the biome, usually GRASS.");

            writer.putSetting(SurfaceSettings.GROUND_BLOCK, biomeConfig.getSurfaceSettings().getGroundBlock(),
                    "The ground block used for the biome, usually DIRT.");

            writer.putSetting(SurfaceSettings.SANDSTONE_BLOCK, biomeConfig.getSurfaceSettings().getSandStoneBlock(),
                    "The sandstone block used for the biome, usually SANDSTONE.");

            writer.putSetting(SurfaceSettings.RED_SANDSTONE_BLOCK, biomeConfig.getSurfaceSettings().getRedSandStoneBlock(),
                    "The red sandstone block used for the biome, usually RED_SANDSTONE.");

            writer.putSetting(SurfaceSettings.UNDER_WATER_SURFACE_BLOCK, biomeConfig.getSurfaceSettings().getUnderWaterSurfaceBlock(),
                    "The surface block used for the biome when underwater, usually the same as GroundBlock.");
        }

        writer.putSetting(SurfaceSettings.SURFACE_GENERATOR, biomeConfig.getSurfaceSettings().getSurfaceGenerator(),
                "Setting for biomes with more complex surface and ground blocks.",
                "Each column in the world has a noise value from what appears to be -7 to 7.",
                "Values near 0 are more common than values near -7 and 7. This setting is",
                "used to change the surface block based on the noise value for the column.",
                "1.12.2 Syntax: SurfaceBlockName,GroundBlockName,MaxNoise[,AnotherSurfaceBlockName,AnotherGroundBlockName,MaxNoise][,...]",
                "Example: " + SurfaceSettings.SURFACE_GENERATOR + ": STONE,STONE,-0.8,GRAVEL,STONE,0.0,DIRT,DIRT,10.0",
                "1.16.x Syntax: SurfaceBlockName,UnderWaterSurfaceBlockName,GroundBlockName,MaxNoise,[AnotherSurfaceBlockName,AnotherUnderWaterSurfaceBlockName,AnotherGroundBlockName,MaxNoise[,...]]",
                "  When the noise is below -0.8, stone is the surface and ground block, between -0.8 and 0",
                "  gravel with stone just below and between 0.0 and 10.0 there's only dirt.",
                "  Because 10.0 is higher than the noise can ever get, the normal " + SurfaceSettings.SURFACE_BLOCK,
                "  and " + SurfaceSettings.GROUND_BLOCK + " will never appear in this biome.", "",
                "Alternatively, you can use Mesa, MesaForest or MesaBryce to get blocks",
                "like the blocks found in the Mesa biomes.",
                "You can also use Iceberg to get iceberg generation like in vanilla frozen oceans. Iceberg accepts a normal SAGC string: \"Iceberg <SAGC>\", so you can use normal SAGC with it.");

        writer.putSetting(SurfaceSettings.REPLACED_BLOCKS, biomeConfig.getSurfaceSettings().getReplacedBlocks(),
                "Replace Variable: (blockFrom,blockTo[:blockDataTo][,minHeight,maxHeight])", "Example :",
                "  ReplacedBlocks: (GRASS,DIRT,100,127),(GRAVEL,GLASS)",
                "Replace grass block to dirt from 100 to 127 height and replace gravel to glass on all height ",
                "Only the following biome resources are affected: CustomObject, CustomStructure, Ore, UnderWaterOre, ",
                "Vein, SurfacePatch, Boulder, IceSpike.",
                "BO's used as CustomObject/CustomStructure may have DoReplaceBlocks:false to save performance.");

        writer.header2("Water / Lava & Frozen States");

        writer.putSetting(SurfaceSettings.USE_WORLD_WATER_LEVEL, biomeConfig.getSurfaceSettings().isUseWorldWaterLevel(),
                "Set this to false to use the \"Water / Lava & Frozen States\" settings of this biome.");

        writer.putSetting(SurfaceSettings.WATER_LEVEL_MAX, biomeConfig.getSurfaceSettings().getConfigWaterLevelMax(),
                "Set water level. Every empty between this levels will be fill water or another block from WaterBlock.");

        writer.putSetting(SurfaceSettings.WATER_LEVEL_MIN, biomeConfig.getSurfaceSettings().getConfigWaterLevelMin());

        writer.putSetting(SurfaceSettings.WATER_BLOCK, biomeConfig.getSurfaceSettings().getWaterBlock(),
                "The block used when placing water in the biome.");

        writer.putSetting(SurfaceSettings.ICE_BLOCK, biomeConfig.getSurfaceSettings().getIceBlock(),
                "The block used as ice. Ice only spawns if the BiomeTemperature is low enough.");

        writer.putSetting(SurfaceSettings.PACKED_ICE_BLOCK, biomeConfig.getSurfaceSettings().getPackedIceBlock(),
                "The block used as packed ice. Packed ice only spawns when using Iceberg SurfaceAndGroundControl.");

        writer.putSetting(SurfaceSettings.SNOW_BLOCK, biomeConfig.getSurfaceSettings().getSnowBlock(),
                "The block used as snow (block, not tile). Snow blocks only spawn when using Iceberg SurfaceAndGroundControl.");

        writer.putSetting(BlockSettings.COOLED_LAVA_BLOCK, biomeConfig.getSurfaceSettings().getCooledLavaBlock(),
                "The block used as cooled or frozen lava.",
                "Set this to OBSIDIAN for \"frozen\" lava lakes in cold biomes");

        writer.header1("Visuals and weather");

        if (!isTemplateBiome) {
            writer.putSetting(BiomeVisualSettings.BIOME_TEMPERATURE, biomeConfig.getVisualSettings().getBiomeTemperature(),
                    "Biome temperature. Float value from 0.0 to 2.0.",
                    "When this value is around 0.2, snow will fall on mountain peaks above y=90.",
                    "When this value is around 0.1, the whole biome will be covered in snow and ice.");

            writer.putSetting(SurfaceSettings.USE_FROZEN_OCEAN_TEMPERATURE, biomeConfig.getSurfaceSettings().isUseFrozenOceanTemperature(),
                    "Set this to true to use variable temperatures within the biome based on noise.",
                    "Used for vanilla Frozen Ocean and Deep Frozen Ocean biomes to create patches of water/ice.");

            writer.putSetting(BiomeVisualSettings.BIOME_WETNESS, biomeConfig.getVisualSettings().getBiomeWetness(),
                    "Biome wetness. Float value from 0.0 to 1.0.",
                    "Affects rain and snow.");

            writer.putSetting(BiomeVisualSettings.SKY_COLOR, biomeConfig.getVisualSettings().getSkyColor(), "Biome sky color.");

            writer.putSetting(BiomeVisualSettings.WATER_COLOR, biomeConfig.getVisualSettings().getWaterColor(), "Biome water color.");

            writer.putSetting(BiomeVisualSettings.WATER_COLOR_CONTROL, biomeConfig.getVisualSettings().getWaterColorControl(),
                    "Setting for biomes with more complex colors.",
                    "Each column in the world has a noise value from what appears to be -1 to 1.",
                    "Values near 0 are more common than values near -1 and 1. This setting is",
                    "used to change the water color based on the noise value for the column.",
                    "Syntax: Color,MaxNoise,[AnotherColor,MaxNoise[,...]]",
                    "Example: " + BiomeVisualSettings.WATER_COLOR_CONTROL + ": 0xFFFFFF,-0.8,0x000000,0.0",
                    "  When the noise is below -0.8, the water will be white, between -0.8 and 0",
                    "  the water will be black, and above 0 the water will be the normal " + BiomeVisualSettings.WATER_COLOR + ".");

            writer.putSetting(BiomeVisualSettings.GRASS_COLOR, biomeConfig.getVisualSettings().getGrassColor(),
                    "Biome grass color.");

            writer.putSetting(BiomeVisualSettings.GRASS_COLOR_CONTROL, biomeConfig.getVisualSettings().getGrassColorControl(),
                    "Biome grass color control. See " + BiomeVisualSettings.WATER_COLOR_CONTROL + ".");

            writer.putSetting(BiomeVisualSettings.GRASS_COLOR_MODIFIER, biomeConfig.getVisualSettings().getGrassColorModifier(),
                    "Biome grass color modifier, can be None, Swamp or DarkForest.");

            writer.putSetting(BiomeVisualSettings.FOLIAGE_COLOR, biomeConfig.getVisualSettings().getFoliageColor(),
                    "Biome foliage color.");

            writer.putSetting(BiomeVisualSettings.FOLIAGE_COLOR_CONTROL, biomeConfig.getVisualSettings().getFoliageColorControl(),
                    "Biome foliage color control. See " + BiomeVisualSettings.WATER_COLOR_CONTROL + ".");

            writer.putSetting(BiomeVisualSettings.FOG_COLOR, biomeConfig.getVisualSettings().getFogColor(),
                    "Biome fog color.");

            writer.putSetting(BiomeVisualSettings.FOG_DENSITY, biomeConfig.getVisualSettings().getFogDensity(),
                    "Biome fog density, from 0.0 to 1.0. 0 will mimic vanilla fog density.");

            writer.putSetting(BiomeVisualSettings.WATER_FOG_COLOR, biomeConfig.getVisualSettings().getWaterFogColor(),
                    "Biome water fog color.");

            writer.putSetting(BiomeVisualSettings.PARTICLE_TYPE, biomeConfig.getVisualSettings().getParticleType(),
                    "Biome particle type, for example minecraft:white_ash.",
                    "Use the \"otg particles\" console command to get a list of particles.");

            writer.putSetting(BiomeVisualSettings.PARTICLE_PROBABILITY, biomeConfig.getVisualSettings().getParticleProbability(),
                    "Biome particle probability, 0 by default.", "*TODO: Test different values and document usage.");

            writer.putSetting(BiomeVisualSettings.MUSIC, biomeConfig.getVisualSettings().getMusic(),
                    "Music for the biome, takes a resource location. Leave empty to disable. Examples: ",
                    "Music: minecraft:music_disc.cat", "Music: minecraft:music.nether.basalt_deltas");

            writer.putSetting(BiomeVisualSettings.MUSIC_MIN_DELAY, biomeConfig.getVisualSettings().getMusicMinDelay(),
                    "Minimum delay for music to start, in ticks");

            writer.putSetting(BiomeVisualSettings.MUSIC_MAX_DELAY, biomeConfig.getVisualSettings().getMusicMaxDelay(),
                    "Maximum delay for music to start, in ticks");

            writer.putSetting(BiomeVisualSettings.REPLACE_CURRENT_MUSIC, biomeConfig.getVisualSettings().isReplaceCurrentMusic(),
                    "Whether music replaces the current playing music in the client or not");

            writer.putSetting(BiomeVisualSettings.AMBIENT_SOUND, biomeConfig.getVisualSettings().getAmbientSound(),
                    "Ambient sound for the biome. Leave empty to disable. Example:",
                    "AmbientSound: minecraft:ambient.cave");

            writer.putSetting(BiomeVisualSettings.MOOD_SOUND, biomeConfig.getVisualSettings().getMoodSound(),
                    "Mood sound for the biome. Leave empty to disable. Example:",
                    "MoodSound: minecraft:ambient.crimson_forest.mood");

            writer.putSetting(BiomeVisualSettings.MOOD_SOUND_DELAY, biomeConfig.getVisualSettings().getMoodSoundDelay(),
                    "The delay in ticks between triggering mood sound");

            writer.putSetting(BiomeVisualSettings.MOOD_SEARCH_RANGE, biomeConfig.getVisualSettings().getMoodSearchRange(),
                    "How far from the player a mood sound can play");

            writer.putSetting(BiomeVisualSettings.MOOD_OFFSET, biomeConfig.getVisualSettings().getMoodOffset(),
                    "The offset of the sound event");

            writer.putSetting(BiomeVisualSettings.ADDITIONS_SOUND, biomeConfig.getVisualSettings().getAdditionsSound(),
                    "Additions sound for the biome. Leave empty to disable. Example:",
                    "AdditionsSound: minecraft:ambient.soul_sand_valley.additions");

            writer.putSetting(BiomeVisualSettings.ADDITIONS_TICK_CHANCE, biomeConfig.getVisualSettings().getAdditionsTickChance(),
                    "The tick chance that the additions sound plays");
        }

        writer.header1("Resource queue", "This section controls all resources spawning during decoration.",
                "The resources will be placed in this order.", "",
                "Keep in mind that a high size, frequency or rarity may slow down terrain generation.", "",
                "Possible resources:", "AboveWaterRes(BlockName,Frequency,Rarity)",
                "Boulder(BlockName,Frequency,Rarity,MinAltitude,MaxAltitude,BlockSource[,BlockSource2,..]",
                "Cactus(BlockName,Frequency,Rarity,MinAltitude,MaxAltitude,BlockSource[,BlockSource2,BlockSource3.....])",
                "CustomObject(Object[,AnotherObject[,...]])",
                "CustomStructure([Object,Object_Chance[,AnotherObject,Object_Chance[,...]]])",
                "Dungeon(Rarity,MinAltitude,MaxAltitude)", "Fossil(Rarity,MinAltitude,MaxAltitude)",
                "Grass(PlantType,Grouped/NotGrouped,Frequency,Rarity,BlockSource[,BlockSource2,BlockSource3.....])",
                "IceSpike(BlockName,IceSpikeType,Frequency,Rarity,MinAltitude,MaxAltitude,Blocksource[,BlockSource2,...])",
                "Liquid(BlockName,Frequency,Rarity,MinAltitude,MaxAltitude,BlockSource[,BlockSource2,BlockSource3.....])",
                "Ore(BlockName,Size,Frequency,Rarity,MinAltitude,MaxAltitude,BlockSource[,BlockSource2,BlockSource3.....],ExtendedParams,MaxSpawn)",
                "Plant(PlantType,Frequency,Rarity,MinAltitude,MaxAltitude,BlockSource[,BlockSource2,BlockSource3.....])",
                "Reed(BlockName,Frequency,Rarity,MinAltitude,MaxAltitude,BlockSource[,BlockSource2,BlockSource3.....])",
                "SmallLake(BlockName,Frequency,Rarity,MinAltitude,MaxAltitude)",
                "SurfacePatch(BlockName,DecorationBlockName,MinAltitude,MaxAltitude,BlockSource[,BlockSource2,BlockSource3.....])",
                "Tree(Frequency,TreeType,TreeTypeChance[,AdditionalTreeType,AdditionalTreeTypeChance.....],ExtendedParams,MaxSpawn)",
                "UnderGroundLake(MinSize,MaxSize,Frequency,Rarity,MinAltitude,MaxAltitude)",
                "UnderWaterOre(BlockName,Size,Frequency,Rarity,BlockSource[,BlockSource2,BlockSource3.....])",
                "UnderWaterPlant(PlantType,Frequency,Rarity,MinAltitude,MaxAltitude,BlockSource[,BlockSource2,BlockSource3.....])",
                "Vein(BlockName,MinRadius,MaxRadius,Rarity,OreSize,OreFrequency,OreRarity,MinAltitude,MaxAltitude,BlockSource[,BlockSource2,..])",
                "Vines(Frequency,Rarity,MinAltitude,MaxAltitude)",
                "Well(BaseBlockName,HalfSlabBlockName,WaterBlockName,Frequency,Rarity,MinAltitude,MaxAltitude,BlockSource[,BlockSource2,..])",
                "Bamboo(Frequency,Rarity,PodzolChance,BlockSource[,BlockSource2,BlockSource3.....])",
                "SeaGrass(Frequency,Rarity,TallChance)",
                "Kelp(Frequency,Rarity)",
                "SeaPickle(Frequency,Rarity,Attempts)",
                "Registry(RegistryKey,DecorationStage)",
                "CoralMushroom(Frequency,Rarity)",
                "CoralTree(Frequency,Rarity)",
                "CoralClaw(Frequency,Rarity)",
                "Iceberg(BlockName1,BlockName2,Chance[,AdditionalBlockName1,AdditionalBlockName2,AdditionalChance.....],TotalChance)",
                "BasaltColumn(BlockName,Frequency,Rarity,BaseSize,SizeVariance,BaseHeight,HeightVariance,MinAltitude,MaxAltitude,BlockSource[,BlockSource2,BlockSource3.....])", "",
                "BlockName:	  	The name of the block, can include data.",
                "BlockSource:	List of blocks the resource can spawn on/in. You can also use \"Solid\" or \"All\".",
                "Frequency:	  	Number of attempts to place this resource in each chunk.",
                "Rarity:		Chance for each attempt, Rarity:100 - mean 100% to pass, Rarity:1 - mean 1% to pass.",
                "MinAltitude and MaxAltitude: Height limits.", "TallChance:	Number between 0.0 and 1.0",
                "TreeType:		Tree (original oak tree) - BigTree - Birch - TallBirch - SwampTree -",
                "			HugeMushroom (randomly red or brown) - HugeRedMushroom - HugeBrownMushroom -",
                "			Taiga1 - Taiga2 - HugeTaiga1 - HugeTaiga2 -",
                "			JungleTree (the huge jungle tree) - GroundBush - CocoaTree (smaller jungle tree)",
                "			DarkOak (from the roofed forest biome) - Acacia",
                "			New for 1.16.5: CrimsonFungi, WarpedFungi, ChorusPlant.",
                "			You can also use your own custom objects, as long as they have Tree:true in their settings.",
                "TreeTypeChance: Similar to Rarity. Example:",
                "			Tree(10,Taiga1,35,Taiga2,100) - tries 10 times, for each attempt it tries to place Taiga1 (35% chance),",
                "			if that fails, it attempts to place Taiga2 (100% chance).",
                "PlantType:	  	One of the plant types: " + StringHelper.join(PlantType.values(), ", "),
                "			or a block name",
                "IceSpikeType:  One of the ice spike types: " + StringHelper.join(IceSpikeType.values(), ","),
                "Object:		Any custom object (bo2 or bo3) file but without the file extension. ",
                "RegistryKey:	Registry key for a (non-OTG) default or configured feature. For example: minecraft:plain_vegetation",
                "DecorationStage: Optional, one of the vanilla decoration stages.",
                "               Can be: RAW_GENERATION, LAKES, LOCAL_MODIFICATIONS, UNDERGROUND_STRUCTURES, ",
                "			SURFACE_STRUCTURES, STRONGHOLDS, UNDERGROUND_ORES, UNDERGROUND_DECORATION,",
                "			VEGETAL_DECORATION, TOP_LAYER_MODIFICATION. VEGETAL_DECORATION by default.",
                "ExtendedParams: Optional, set this to true if you want to use additional parameters, like MaxSpawn.",
                "MaxSpawn: 		Optional, used with Frequency. When MaxSpawn spawn attempts have succeeded, stop spawning (for the current chunk).",
                "			For example, you can do 100 spawn attempts per chunk but stop after 5 have succeeded.",
                "",
                "Plant and Grass resource: Both a resource of one block. Plant can place blocks underground, Grass cannot.",
                "UnderWaterPlant resource: Similar to plant, but places blocks underwater.",
                "Liquid resource: A one-block water or lava source",
                "SmallLake and UnderGroundLake resources: Small lakes of about 8x8 blocks",
                "Vein resource: Starts an area where ores will spawn. Can be slow, so use a low Rarity (smaller than 1).",
                "CustomStructure resource: Starts a BO3 or BO4 structure in the chunk if spawn requirements are met.",
                "");
        writer.addConfigFunctions(biomeConfig.getResourceSettings().getResourceQueue());

        writer.header1("Saplings",
                Constants.MOD_ID + " allows you to grow your custom objects from saplings, instead",
                "of the vanilla trees. Add one or more Sapling functions here to override vanilla",
                "spawning for that sapling.", "",
                "The syntax is: Sapling(SaplingType,TreeType,TreeType_Chance[,Additional_TreeType,Additional_TreeType_Chance.....])",
                "Works like Tree resource except first parameter.",
                "For custom saplings; Sapling(Custom,SaplingMaterial,WideTrunk,TreeType,TreeType_Chance.....)",
                "SaplingMaterial is the name of the sapling block.",
                "WideTrunk is 'true' or 'false', whether or not it requires 4 saplings.", "",
                "TreeType is one of the vanilla tree types (see resource queue comments) or a BO2/BO3 name.", "",
                "Sapling types: " + StringHelper.join(SaplingType.values(), ", "),
                "All - will make the tree spawn from all saplings, but not from mushrooms.",
                "BigJungle - for when 4 jungle saplings grow at once.",
                "RedMushroom/BrownMushroom - will only grow when bonemeal is used.", "");

        writer.addConfigFunctions(biomeConfig.getResourceSettings().getSaplingGrowers().values());
        writer.addConfigFunctions(biomeConfig.getResourceSettings().getCustomSaplingGrowers().values());
        writer.addConfigFunctions(biomeConfig.getResourceSettings().getCustomBigSaplingGrowers().values());

        if (!isTemplateBiome) {
            writer.header1("Vanilla structures", "Vanilla structure settings, each structure type has a global on/off",
                    "toggle in the DimensionPresetConfig, be sure to enable it to allow biomes to", "spawn structures.",
                    "* Fossils and Dungeons count as resources, not structures.");

//            writer.putSetting(BiomeStructureSettings.STRONGHOLDS_ENABLED, biomeConfig.getStructureSettings().isStrongholdsEnabled(),
//                    "Toggles strongholds spawning in this biome.");
//
//            writer.putSetting(BiomeStructureSettings.WOODLAND_MANSIONS_ENABLED, biomeConfig.getStructureSettings().isWoodlandMansionsEnabled(),
//                    "Toggles woodland mansions spawning in this biome.");
//
//            writer.putSetting(BiomeStructureSettings.OCEAN_MONUMENTS_ENABLED, biomeConfig.getStructureSettings().isOceanMonumentsEnabled(),
//                    "Toggles ocean monuments spawning in this biome.");
//
//            writer.putSetting(BiomeStructureSettings.NETHER_FORTRESSES_ENABLED, biomeConfig.getStructureSettings().isNetherFortressesEnabled(),
//                    "Toggles nether fortresses spawning in this biome.");

//            writer.putSetting(BiomeStructureSettings.VILLAGE_TYPE, biomeConfig.getStructureSettings().getVillageType(),
//                    "The type of villages in this biome. Can be wood, sandstone, taiga, savanna, snowy or disabled.");

            writer.putSetting(BiomeStructureSettings.VILLAGE_SIZE, biomeConfig.getStructureSettings().getVillageSize(),
                    "The size of villages in this biome, 6 by default.",
                    "*TODO: Test different values and document usage.");

//            writer.putSetting(BiomeStructureSettings.MINESHAFT_TYPE, biomeConfig.getStructureSettings().getMineshaftType(),
//                    "The type of mineshafts in this biome. Can be normal, mesa or disabled.");

            writer.putSetting(BiomeStructureSettings.MINESHAFT_PROBABILITY, biomeConfig.getStructureSettings().getMineshaftProbability(),
                    "Probability of mineshafts spawning, 0.004 by default.",
                    "*TODO: Test different values and document usage.");

//            writer.putSetting(BiomeStructureSettings.RARE_BUILDING_TYPE, biomeConfig.getStructureSettings().getRareBuildingType(),
//                    "The type of the aboveground rare building in this biome.",
//                    "Can be desertPyramid, jungleTemple, swampHut, igloo or disabled.");
//
//            writer.putSetting(BiomeStructureSettings.BURIED_TREASURE_ENABLED, biomeConfig.getStructureSettings().isBuriedTreasureEnabled(),
//                    "Toggles buried treasure spawning in this biome.");

            writer.putSetting(BiomeStructureSettings.BURIED_TREASURE_PROBABILITY, biomeConfig.getStructureSettings().getBuriedTreasureProbability(),
                    "Probability of buried treasure spawning, 0.01 by default.",
                    "*TODO: Test different values and document usage.");

//            writer.putSetting(BiomeStructureSettings.SHIP_WRECK_ENABLED, biomeConfig.getStructureSettings().isShipWreckEnabled(),
//                    "Toggles shipwrecks spawning in this biome.");
//
//            writer.putSetting(BiomeStructureSettings.SHIP_WRECK_BEACHED_ENABLED, biomeConfig.getStructureSettings().isShipWreckBeachedEnabled(),
//                    "Toggles beached shipwrecks spawning in this biome.");
//
//            writer.putSetting(BiomeStructureSettings.PILLAGER_OUTPOST_ENABLED, biomeConfig.getStructureSettings().isPillagerOutpostEnabled(),
//                    "Toggles pillager outposts spawning in this biome.");

            writer.putSetting(BiomeStructureSettings.PILLAGER_OUTPOST_SIZE, biomeConfig.getStructureSettings().getPillagerOutpostSize(),
                    "The size of pillager outposts in this biome, 7 by default.",
                    "*TODO: Test different values and document usage.");

//            writer.putSetting(BiomeStructureSettings.BASTION_REMNANT_ENABLED, biomeConfig.getStructureSettings().isBastionRemnantEnabled(),
//                    "Toggles bastion remnants spawning in this biome.");

            writer.putSetting(BiomeStructureSettings.BASTION_REMNANT_SIZE, biomeConfig.getStructureSettings().getBastionRemnantSize(),
                    "The size of bastion remnants in this biome, 6 by default.",
                    "*TODO: Test different values and document usage.");

//            writer.putSetting(BiomeStructureSettings.NETHER_FOSSIL_ENABLED, biomeConfig.getStructureSettings().isNetherFossilEnabled(),
//                    "Toggles nether fossils spawning in this biome.", "Caution: Nether fossils spawn at all heights.");
//
//            writer.putSetting(BiomeStructureSettings.END_CITY_ENABLED, biomeConfig.getStructureSettings().isEndCityEnabled(),
//                    "Toggles end cities spawning in this biome.");
//
//            writer.putSetting(BiomeStructureSettings.RUINED_PORTAL_TYPE, biomeConfig.getStructureSettings().getRuinedPortalType(),
//                    "The type of ruined portals in this biome.",
//                    "Can be normal, desert, jungle, swamp, mountain, ocean, nether or disabled.");
//
//            writer.putSetting(BiomeStructureSettings.OCEAN_RUINS_TYPE, biomeConfig.getStructureSettings().getOceanRuinsType(),
//                    "The type of ocean ruins in this biome.", "Can be cold, warm or disabled.");

            writer.putSetting(BiomeStructureSettings.OCEAN_RUINS_LARGE_PROBABILITY, biomeConfig.getStructureSettings().getOceanRuinsLargeProbability(),
                    "Probability of large ocean ruins spawning, 0.3 by default.",
                    "*TODO: Test different values and document usage.");

            writer.putSetting(BiomeStructureSettings.OCEAN_RUINS_CLUSTER_PROBABILITY, biomeConfig.getStructureSettings().getOceanRuinsClusterProbability(),
                    "Probability of ocean ruins spawning clusters, 0.9 by default.",
                    "*TODO: Test different values and document usage.");

            writer.header1("Mob spawning",
                    "Mob spawning is configured via mob groups, see http://minecraft.gamepedia.com/Spawn#Mob_spawning", "",
                    "A mobgroups is made of four parts; mob name, weight, min and max.",
                    "- Mob name is one of the Minecraft internal mob names. See http://minecraft.gamepedia.com/Chunk_format#Mobs",
                    "- Weight is used for a random selection. Must be a positive number.",
                    "- Min is the minimum amount of mobs spawning as a group. Must be a positive number.",
                    "- Max is the maximum amount of mobs spawning as a group. Must be a positive number.", "",
                    "Mob groups are written to the config files as Json.",
                    "Json is a tree document format: http://en.wikipedia.org/wiki/JSON",
                    "Syntax: {\"mob\": \"mobname\", \"weight\": integer, \"min\": integer, \"max\": integer}",
                    "Example: {\"mob\": \"minecraft:ocelot\", \"weight\": 10, \"min\": 2, \"max\": 6}",
                    "Example: {\"mob\": \"minecraft:mooshroom\", \"weight\": 5, \"min\": 2, \"max\": 2}",
                    "A json list of mobgroups looks like this: [ mobgroup, mobgroup, mobgroup... ]",
                    "This would be an ampty list: []", "You can validate your json here: http://jsonlint.com/", "",
                    "There are six categories of mobs: monsters, creatures, water creatures, ambient creatures, water ambient creatures and miscellaneous.",
                    "You can add your own mobs to the mobgroups below, mobs may only work when used in specific categories, depending on their type.",
                    "To see the mob category a mob belongs to, use /otg entities. The mob's category (if any) is listed after its name.",
                    "Also supports modded mobs, if they are of the correct mob category.");

            writer.putSetting(MobSettings.SPAWN_MONSTERS, biomeConfig.getMobSettings().getMonsters(),
                    "The monsters (blazes, cave spiders, creepers, drowned, elder guardians, ender dragons, endermen, endermites, evokers, ghasts, giants,",
                    "guardians, hoglins, husks, illusioners, magma cubes, phantoms, piglins, pillagers, ravagers, shulkers, silverfishes, skeletons, slimes,",
                    "spiders, strays, vexes, vindicators, witches, zoglins, zombies, zombie villagers, zombified piglins) that spawn in this biome.",
                    "For instance [{\"mob\": \"minecraft:spider\", \"weight\": 100, \"min\": 4, \"max\": 4}, {\"mob\": \"minecraft:zombie\", \"weight\": 100, \"min\": 4, \"max\": 4}]",
                    "Use the \"/otg entities\" console command to get a list of possible mobs and mob categories.",
                    "Use the \"/otg biome -m\" console command to get the list of registered mobs for a biome.");

            writer.putSetting(MobSettings.SPAWN_CREATURES, biomeConfig.getMobSettings().getCreatures(),
                    "The friendly creatures (bees, cats, chickens, cows, donkeys, foxes, horses, llama, mooshrooms, mules, ocelots, panda's, parrots,",
                    "pigs, polar bears, rabbits, sheep, skeleton horses, striders, trader llama's, turtles, wandering traders, wolves, zombie horses)",
                    "that spawn in this biome.",
                    "For instance [{\"mob\": \"minecraft:sheep\", \"weight\": 12, \"min\": 4, \"max\": 4}, {\"mob\": \"minecraft:pig\", \"weight\": 10, \"min\": 4, \"max\": 4}]",
                    "Use the \"/otg entities\" console command to get a list of possible mobs and mob categories.",
                    "Use the \"/otg biome -m\" console command to get the list of registered mobs for a biome.");

            writer.putSetting(MobSettings.SPAWN_WATER_CREATURES, biomeConfig.getMobSettings().getWaterCreatures(),
                    "The water creatures (squids and dolphins) that spawn in this biome",
                    "For instance [{\"mob\": \"minecraft:squid\", \"weight\": 10, \"min\": 4, \"max\": 4}]",
                    "Use the \"/otg entities\" console command to get a list of possible mobs and mob categories.",
                    "Use the \"/otg biome -m\" console command to get the list of registered mobs for a biome.");

            writer.putSetting(MobSettings.SPAWN_AMBIENT_CREATURES, biomeConfig.getMobSettings().getAmbientCreatures(),
                    "The ambient creatures (only bats in vanila) that spawn in this biome",
                    "For instance [{\"mob\": \"minecraft:bat\", \"weight\": 10, \"min\": 8, \"max\": 8}]",
                    "Use the \"/otg entities\" console command to get a list of possible mobs and mob categories.",
                    "Use the \"/otg biome -m\" console command to get the list of registered mobs for a biome.");

            writer.putSetting(MobSettings.SPAWN_WATER_AMBIENT_CREATURES, biomeConfig.getMobSettings().getWaterAmbientCreatures(),
                    "The ambient water creatures (cod, pufferfish, salmon, tropical fish) that spawn in this biome",
                    "For instance [{\"mob\": \"minecraft:cod\", \"weight\": 10, \"min\": 8, \"max\": 8}]",
                    "Use the \"/otg entities\" console command to get a list of possible mobs and mob categories.",
                    "Use the \"/otg biome -m\" console command to get the list of registered mobs for a biome.");

            writer.putSetting(MobSettings.SPAWN_MISC_CREATURES, biomeConfig.getMobSettings().getMiscCreatures(),
                    "The miscellaneous creatures (iron golems, snow golems and villagers) that spawn in this biome",
                    "For instance [{\"mob\": \"minecraft:villager\", \"weight\": 10, \"min\": 8, \"max\": 8}]",
                    "Use the \"/otg entities\" console command to get a list of possible mobs and mob categories.",
                    "Use the \"/otg biome -m\" console command to get the list of registered mobs for a biome.");

            writer.putSetting(MobSettings.INHERIT_MOBS_BIOME_NAME, biomeConfig.getMobSettings().getInheritMobsBiomeName(),
                    "Inherit the internal mobs list of another biome. Inherited mobs can be overridden using",
                    "the mob spawn settings in this biome config. Any mob type defined in this biome config",
                    "will override inherited mob settings for the same mob in the same mob category.",
                    "Use this setting to inherit mob spawn lists from other biomes.",
                    "Accepts both OTG and non-OTG (vanilla or other mods') biomes. See also: BiomeDictTags.");

            biomeConfig.getBiomeTagSettings().writeConfig(writer);

            biomeConfig.getBiomeStructureTagConfig().writeConfig(writer);
        }
    }
}
