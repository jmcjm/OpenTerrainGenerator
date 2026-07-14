package com.pg85.otg.config.dimensions;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.pg85.otg.util.OTGLog;
import com.pg85.otg.util.logging.LogCategory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

/**
 * Defines a complete world configuration: which DimensionPresets to use for
 * overworld/nether/end, any custom dimensions, and optional GameRules overrides.
 * Serialized as YAML in the WorldPresets/ folder.
 */
public class WorldPresetConfig
{
	private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

	public boolean isModpackConfig = false;
	// Use capitals since we're serialising to yaml and want to make it look nice.
	public int Version;
	public String DisplayName;
	public String Description;
	public String ModpackName;
	public OTGOverWorld Overworld;
	public OTGDimension Nether;
	public OTGDimension End;
	public List<OTGDimension> Dimensions = new ArrayList<>();
	public Settings Settings;
	public GameRules GameRules;
	
	// Parameterless constructor for deserialisation
	public WorldPresetConfig() { }
	
	public boolean isModpackConfig()
	{
		return this.isModpackConfig;
	}
	
	public static WorldPresetConfig createDefaultConfig()
	{
		WorldPresetConfig config = new WorldPresetConfig();
		config.Overworld = new OTGOverWorld(null, -1, null, null);
		config.Nether = new OTGDimension(null, -1);
		config.End = new OTGDimension(null, -1);
		return config;
	}

	public String toYamlString()
	{
		try {
			return YAML_MAPPER.writeValueAsString(this);
		} catch (JsonProcessingException e) {
			OTGLog.error(LogCategory.CONFIGS, "Failed to serialize world preset config to YAML: {}", e.getMessage());
		}
		return null;
	}
	
	// Clone method for GUI, to accommodate cancel button / rollbacks.
	public WorldPresetConfig clone()
	{
		WorldPresetConfig clone = new WorldPresetConfig();

		clone.isModpackConfig = this.isModpackConfig;
		clone.Version = this.Version;
		clone.DisplayName = this.DisplayName;
		clone.Description = this.Description;
		clone.ModpackName = this.ModpackName;
		clone.Overworld = this.Overworld == null ? null : this.Overworld.clone();
		clone.Nether = this.Nether == null ? null : this.Nether.clone();
		clone.End = this.End == null ? null : this.End.clone();
		clone.Dimensions = new ArrayList<>();
		for(OTGDimension dim : this.Dimensions)
		{
			clone.Dimensions.add(dim.clone());
		}
		clone.Settings = this.Settings == null ? null : this.Settings.clone();
		clone.GameRules = this.GameRules == null ? null : this.GameRules.clone();
		
		return clone;
	}
	
	public static class OTGOverWorld extends OTGDimension
	{
		public OTGOverWorld()
		{
			super();
		}

		public OTGOverWorld(String presetFolderName, long seed, String nonOTGWorldType, String nonOTGGeneratorSettings)
		{
			super(presetFolderName, seed);
			this.NonOTGWorldType = nonOTGWorldType;
			this.NonOTGGeneratorSettings = nonOTGGeneratorSettings;
		}

		public OTGOverWorld clone()
		{
			OTGOverWorld clone = new OTGOverWorld(this.PresetFolderName, this.Seed, this.NonOTGWorldType, this.NonOTGGeneratorSettings);
			clone.DimensionName = this.DimensionName;
			clone.PortalBlocks = this.PortalBlocks;
			clone.PortalColor = this.PortalColor;
			clone.PortalMob = this.PortalMob;
			clone.PortalIgnitionSource = this.PortalIgnitionSource;
			clone.RespawnInDimension = this.RespawnInDimension;
			clone.GameRules = this.GameRules == null ? null : this.GameRules.clone();
			return clone;
		}
	}

	public static class OTGDimension
	{
		public String PresetFolderName;
		public String DimensionName;
		public String NonOTGWorldType;
		public String NonOTGGeneratorSettings;
		public long Seed;
		public String PortalBlocks;
		public String PortalColor;
		public String PortalMob;
		public String PortalIgnitionSource;
		public Boolean RespawnInDimension;
		public GameRules GameRules;

		public OTGDimension() {}

		public OTGDimension(String presetFolderName, long seed)
		{
			this.PresetFolderName = presetFolderName;
			this.Seed = seed;
		}

		/** True when this entry references a non-OTG generator (MC WorldPreset registry key). */
		public boolean isNonOTG()
		{
			return this.NonOTGWorldType != null && !this.NonOTGWorldType.isBlank();
		}

		/** True when this entry references an OTG DimensionPreset. */
		public boolean hasPreset()
		{
			return this.PresetFolderName != null && !this.PresetFolderName.isBlank();
		}

		public OTGDimension clone()
		{
			OTGDimension otgDimension = new OTGDimension(this.PresetFolderName, this.Seed);
			otgDimension.DimensionName = this.DimensionName;
			otgDimension.NonOTGWorldType = this.NonOTGWorldType;
			otgDimension.NonOTGGeneratorSettings = this.NonOTGGeneratorSettings;
			otgDimension.PortalBlocks = this.PortalBlocks;
			otgDimension.PortalColor = this.PortalColor;
			otgDimension.PortalMob = this.PortalMob;
			otgDimension.PortalIgnitionSource = this.PortalIgnitionSource;
			otgDimension.RespawnInDimension = this.RespawnInDimension;
			otgDimension.GameRules = this.GameRules == null ? null : this.GameRules.clone();
			return otgDimension;
		}
	}

	public static class Settings
	{
		public boolean GenerateStructures;
		public boolean BonusChest;
		
		public Settings() {}
		
		public Settings clone()
		{
			Settings settings = new Settings();
			settings.GenerateStructures = this.GenerateStructures;
			settings.BonusChest = this.BonusChest;
			return settings;
		}
	}
	
	// Nullable wrappers — null means "not specified in YAML, don't override".
	// Jackson deserializes missing YAML fields as null for boxed types.
	public static class GameRules
	{
		public Boolean DoFireTick;
		public Boolean MobGriefing;
		public Boolean KeepInventory;
		public Boolean DoMobSpawning;
		public Boolean DoMobLoot;
		public Boolean DoTileDrops;
		public Boolean DoEntityDrops;
		public Boolean CommandBlockOutput;
		public Boolean NaturalRegeneration;
		public Boolean DoDaylightCycle;
		public Boolean LogAdminCommands;
		public Boolean ShowDeathMessages;
		public Integer RandomTickSpeed;
		public Boolean SendCommandFeedback;
		public Boolean SpectatorsGenerateChunks;
		public Integer SpawnRadius;
		public Boolean DisableElytraMovementCheck;
		public Integer MaxEntityCramming;
		public Boolean DoWeatherCycle;
		public Boolean DoLimitedCrafting;
		public Integer MaxCommandChainLength;
		public Boolean AnnounceAdvancements;
		public Boolean DisableRaids;
		public Boolean DoInsomnia;
		public Boolean DrowningDamage;
		public Boolean FallDamage;
		public Boolean FireDamage;
		public Boolean DoPatrolSpawning;
		public Boolean DoTraderSpawning;
		public Boolean ForgiveDeadPlayers;
		public Boolean UniversalAnger;
		public Boolean ProjectilesCanBreakBlocks;
		public Boolean ReducedDebugInfo;
		public Boolean DoImmediateRespawn;
		public Boolean FreezeDamage;
		public Boolean DoWardenSpawning;
		public Boolean BlockExplosionDropDecay;
		public Boolean MobExplosionDropDecay;
		public Boolean TntExplosionDropDecay;
		public Boolean WaterSourceConversion;
		public Boolean LavaSourceConversion;
		public Boolean GlobalSoundEvents;
		public Boolean DoVinesSpread;
		public Boolean EnderPearlsVanishOnDeath;
		public Integer MaxCommandForkCount;
		public Integer CommandModificationBlockLimit;
		public Integer PlayersNetherPortalDefaultDelay;
		public Integer PlayersNetherPortalCreativeDelay;
		public Integer PlayersSleepingPercentage;
		public Integer SnowAccumulationHeight;
		public Integer SpawnChunkRadius;

		public GameRules() {}

		public GameRules clone()
		{
			GameRules gameRules = new GameRules();
			gameRules.DoFireTick = this.DoFireTick;
			gameRules.MobGriefing = this.MobGriefing;
			gameRules.KeepInventory = this.KeepInventory;
			gameRules.DoMobSpawning = this.DoMobSpawning;
			gameRules.DoMobLoot = this.DoMobLoot;
			gameRules.DoTileDrops = this.DoTileDrops;
			gameRules.DoEntityDrops = this.DoEntityDrops;
			gameRules.CommandBlockOutput = this.CommandBlockOutput;
			gameRules.NaturalRegeneration = this.NaturalRegeneration;
			gameRules.DoDaylightCycle = this.DoDaylightCycle;
			gameRules.LogAdminCommands = this.LogAdminCommands;
			gameRules.ShowDeathMessages = this.ShowDeathMessages;
			gameRules.RandomTickSpeed = this.RandomTickSpeed;
			gameRules.SendCommandFeedback = this.SendCommandFeedback;
			gameRules.SpectatorsGenerateChunks = this.SpectatorsGenerateChunks;
			gameRules.SpawnRadius = this.SpawnRadius;
			gameRules.DisableElytraMovementCheck = this.DisableElytraMovementCheck;
			gameRules.MaxEntityCramming = this.MaxEntityCramming;
			gameRules.DoWeatherCycle = this.DoWeatherCycle;
			gameRules.DoLimitedCrafting = this.DoLimitedCrafting;
			gameRules.MaxCommandChainLength = this.MaxCommandChainLength;
			gameRules.AnnounceAdvancements = this.AnnounceAdvancements;
			gameRules.DisableRaids = this.DisableRaids;
			gameRules.DoInsomnia = this.DoInsomnia;
			gameRules.DrowningDamage = this.DrowningDamage;
			gameRules.FallDamage = this.FallDamage;
			gameRules.FireDamage = this.FireDamage;
			gameRules.DoPatrolSpawning = this.DoPatrolSpawning;
			gameRules.DoTraderSpawning = this.DoTraderSpawning;
			gameRules.ForgiveDeadPlayers = this.ForgiveDeadPlayers;
			gameRules.UniversalAnger = this.UniversalAnger;
			gameRules.ProjectilesCanBreakBlocks = this.ProjectilesCanBreakBlocks;
			gameRules.ReducedDebugInfo = this.ReducedDebugInfo;
			gameRules.DoImmediateRespawn = this.DoImmediateRespawn;
			gameRules.FreezeDamage = this.FreezeDamage;
			gameRules.DoWardenSpawning = this.DoWardenSpawning;
			gameRules.BlockExplosionDropDecay = this.BlockExplosionDropDecay;
			gameRules.MobExplosionDropDecay = this.MobExplosionDropDecay;
			gameRules.TntExplosionDropDecay = this.TntExplosionDropDecay;
			gameRules.WaterSourceConversion = this.WaterSourceConversion;
			gameRules.LavaSourceConversion = this.LavaSourceConversion;
			gameRules.GlobalSoundEvents = this.GlobalSoundEvents;
			gameRules.DoVinesSpread = this.DoVinesSpread;
			gameRules.EnderPearlsVanishOnDeath = this.EnderPearlsVanishOnDeath;
			gameRules.MaxCommandForkCount = this.MaxCommandForkCount;
			gameRules.CommandModificationBlockLimit = this.CommandModificationBlockLimit;
			gameRules.PlayersNetherPortalDefaultDelay = this.PlayersNetherPortalDefaultDelay;
			gameRules.PlayersNetherPortalCreativeDelay = this.PlayersNetherPortalCreativeDelay;
			gameRules.PlayersSleepingPercentage = this.PlayersSleepingPercentage;
			gameRules.SnowAccumulationHeight = this.SnowAccumulationHeight;
			gameRules.SpawnChunkRadius = this.SpawnChunkRadius;
			return gameRules;
		}
	}
}
