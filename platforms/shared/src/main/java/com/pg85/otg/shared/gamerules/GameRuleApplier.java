package com.pg85.otg.shared.gamerules;

import com.mojang.serialization.Dynamic;
import com.pg85.otg.config.dimensions.WorldPresetConfig;
import com.pg85.otg.config.settings.preset.GameRuleSettings;
import com.pg85.otg.util.OTGLog;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.GameRules;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Creates MC GameRules from OTG config (DimensionPresetConfig + optional WorldPresetConfig override).
 */
public final class GameRuleApplier {

    private GameRuleApplier() {}

    /**
     * Creates a new GameRules instance populated from DimensionPresetConfig settings
     * with optional WorldPresetConfig overrides (single layer).
     */
    public static GameRules createGameRules(
            @Nullable GameRuleSettings presetRules,
            @Nullable WorldPresetConfig.GameRules overrides,
            MinecraftServer server
    ) {
        return createGameRules(presetRules, overrides, null, server);
    }

    /**
     * Creates GameRules with 3-layer override hierarchy:
     * 1. DimensionPresetConfig.ini GameRules (base; vanilla defaults for non-OTG dimensions)
     * 2. WorldPreset YAML world-level GameRules (override)
     * 3. WorldPreset YAML per-dimension GameRules (override)
     *
     * Each layer only overrides non-null fields.
     */
    public static GameRules createGameRules(
            @Nullable GameRuleSettings presetRules,
            @Nullable WorldPresetConfig.GameRules worldLevelOverrides,
            @Nullable WorldPresetConfig.GameRules dimensionOverrides,
            MinecraftServer server
    ) {
        GameRules rules = new GameRules();

        // Layer 1: base from DimensionPresetConfig.ini (only if preset opts in).
        // Null presetRules (non-OTG dimension) leaves vanilla defaults as the base.
        if (presetRules != null && presetRules.isOverrideGameRules()) {
            applyFromPreset(rules, presetRules, server);
        }

        // Layer 2: world-level overrides from WorldPreset YAML
        if (worldLevelOverrides != null) {
            applyFromWorldPresetConfig(rules, worldLevelOverrides, server);
        }

        // Layer 3: per-dimension overrides from WorldPreset YAML
        if (dimensionOverrides != null) {
            applyFromWorldPresetConfig(rules, dimensionOverrides, server);
        }

        return rules;
    }

    private static void applyFromPreset(GameRules rules, GameRuleSettings s, MinecraftServer server) {
        // Boolean rules
        rules.getRule(GameRules.RULE_DOFIRETICK).set(s.isDoFireTick(), server);
        rules.getRule(GameRules.RULE_MOBGRIEFING).set(s.isMobGriefing(), server);
        rules.getRule(GameRules.RULE_KEEPINVENTORY).set(s.isKeepInventory(), server);
        rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(s.isDoMobSpawning(), server);
        rules.getRule(GameRules.RULE_DOMOBLOOT).set(s.isDoMobLoot(), server);
        rules.getRule(GameRules.RULE_DOBLOCKDROPS).set(s.isDoTileDrops(), server);
        rules.getRule(GameRules.RULE_DOENTITYDROPS).set(s.isDoEntityDrops(), server);
        rules.getRule(GameRules.RULE_COMMANDBLOCKOUTPUT).set(s.isCommandBlockOutput(), server);
        rules.getRule(GameRules.RULE_NATURAL_REGENERATION).set(s.isNaturalRegeneration(), server);
        rules.getRule(GameRules.RULE_DAYLIGHT).set(s.isDoDaylightCycle(), server);
        rules.getRule(GameRules.RULE_LOGADMINCOMMANDS).set(s.isLogAdminCommands(), server);
        rules.getRule(GameRules.RULE_SHOWDEATHMESSAGES).set(s.isShowDeathMessages(), server);
        rules.getRule(GameRules.RULE_SENDCOMMANDFEEDBACK).set(s.isSendCommandFeedback(), server);
        rules.getRule(GameRules.RULE_SPECTATORSGENERATECHUNKS).set(s.isSpectatorsGenerateChunks(), server);
        rules.getRule(GameRules.RULE_DISABLE_ELYTRA_MOVEMENT_CHECK).set(s.isDisableElytraMovementCheck(), server);
        rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(s.isDoWeatherCycle(), server);
        rules.getRule(GameRules.RULE_LIMITED_CRAFTING).set(s.isDoLimitedCrafting(), server);
        rules.getRule(GameRules.RULE_ANNOUNCE_ADVANCEMENTS).set(s.isAnnounceAdvancements(), server);
        rules.getRule(GameRules.RULE_DISABLE_RAIDS).set(s.isDisableRaids(), server);
        rules.getRule(GameRules.RULE_DOINSOMNIA).set(s.isDoInsomnia(), server);
        rules.getRule(GameRules.RULE_DROWNING_DAMAGE).set(s.isDrowningDamage(), server);
        rules.getRule(GameRules.RULE_FALL_DAMAGE).set(s.isFallDamage(), server);
        rules.getRule(GameRules.RULE_FIRE_DAMAGE).set(s.isFireDamage(), server);
        rules.getRule(GameRules.RULE_DO_PATROL_SPAWNING).set(s.isDoPatrolSpawning(), server);
        rules.getRule(GameRules.RULE_DO_TRADER_SPAWNING).set(s.isDoTraderSpawning(), server);
        rules.getRule(GameRules.RULE_FORGIVE_DEAD_PLAYERS).set(s.isForgiveDeadPlayers(), server);
        rules.getRule(GameRules.RULE_UNIVERSAL_ANGER).set(s.isUniversalAnger(), server);
        // New 1.21.1 boolean rules
        rules.getRule(GameRules.RULE_PROJECTILESCANBREAKBLOCKS).set(s.isProjectilesCanBreakBlocks(), server);
        rules.getRule(GameRules.RULE_REDUCEDDEBUGINFO).set(s.isReducedDebugInfo(), server);
        rules.getRule(GameRules.RULE_DO_IMMEDIATE_RESPAWN).set(s.isDoImmediateRespawn(), server);
        rules.getRule(GameRules.RULE_FREEZE_DAMAGE).set(s.isFreezeDamage(), server);
        rules.getRule(GameRules.RULE_DO_WARDEN_SPAWNING).set(s.isDoWardenSpawning(), server);
        rules.getRule(GameRules.RULE_BLOCK_EXPLOSION_DROP_DECAY).set(s.isBlockExplosionDropDecay(), server);
        rules.getRule(GameRules.RULE_MOB_EXPLOSION_DROP_DECAY).set(s.isMobExplosionDropDecay(), server);
        rules.getRule(GameRules.RULE_TNT_EXPLOSION_DROP_DECAY).set(s.isTntExplosionDropDecay(), server);
        rules.getRule(GameRules.RULE_WATER_SOURCE_CONVERSION).set(s.isWaterSourceConversion(), server);
        rules.getRule(GameRules.RULE_LAVA_SOURCE_CONVERSION).set(s.isLavaSourceConversion(), server);
        rules.getRule(GameRules.RULE_GLOBAL_SOUND_EVENTS).set(s.isGlobalSoundEvents(), server);
        rules.getRule(GameRules.RULE_DO_VINES_SPREAD).set(s.isDoVinesSpread(), server);
        rules.getRule(GameRules.RULE_ENDER_PEARLS_VANISH_ON_DEATH).set(s.isEnderPearlsVanishOnDeath(), server);

        // Integer rules
        rules.getRule(GameRules.RULE_RANDOMTICKING).set(s.getRandomTickSpeed(), server);
        rules.getRule(GameRules.RULE_SPAWN_RADIUS).set(s.getSpawnRadius(), server);
        rules.getRule(GameRules.RULE_MAX_ENTITY_CRAMMING).set(s.getMaxEntityCramming(), server);
        rules.getRule(GameRules.RULE_MAX_COMMAND_CHAIN_LENGTH).set(s.getMaxCommandChainLength(), server);
        // New 1.21.1 integer rules
        rules.getRule(GameRules.RULE_MAX_COMMAND_FORK_COUNT).set(s.getMaxCommandForkCount(), server);
        rules.getRule(GameRules.RULE_COMMAND_MODIFICATION_BLOCK_LIMIT).set(s.getCommandModificationBlockLimit(), server);
        rules.getRule(GameRules.RULE_PLAYERS_NETHER_PORTAL_DEFAULT_DELAY).set(s.getPlayersNetherPortalDefaultDelay(), server);
        rules.getRule(GameRules.RULE_PLAYERS_NETHER_PORTAL_CREATIVE_DELAY).set(s.getPlayersNetherPortalCreativeDelay(), server);
        rules.getRule(GameRules.RULE_PLAYERS_SLEEPING_PERCENTAGE).set(s.getPlayersSleepingPercentage(), server);
        rules.getRule(GameRules.RULE_SNOW_ACCUMULATION_HEIGHT).set(s.getSnowAccumulationHeight(), server);
        rules.getRule(GameRules.RULE_SPAWN_CHUNK_RADIUS).set(s.getSpawnChunkRadius(), server);
    }

    // Only applies fields explicitly set in YAML (non-null). Unset fields keep preset values.
    private static void applyFromWorldPresetConfig(GameRules rules, WorldPresetConfig.GameRules dc, MinecraftServer server) {
        // Boolean overrides — null means "not specified, keep preset value"
        if (dc.DoFireTick != null) rules.getRule(GameRules.RULE_DOFIRETICK).set(dc.DoFireTick, server);
        if (dc.MobGriefing != null) rules.getRule(GameRules.RULE_MOBGRIEFING).set(dc.MobGriefing, server);
        if (dc.KeepInventory != null) rules.getRule(GameRules.RULE_KEEPINVENTORY).set(dc.KeepInventory, server);
        if (dc.DoMobSpawning != null) rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(dc.DoMobSpawning, server);
        if (dc.DoMobLoot != null) rules.getRule(GameRules.RULE_DOMOBLOOT).set(dc.DoMobLoot, server);
        if (dc.DoTileDrops != null) rules.getRule(GameRules.RULE_DOBLOCKDROPS).set(dc.DoTileDrops, server);
        if (dc.DoEntityDrops != null) rules.getRule(GameRules.RULE_DOENTITYDROPS).set(dc.DoEntityDrops, server);
        if (dc.CommandBlockOutput != null) rules.getRule(GameRules.RULE_COMMANDBLOCKOUTPUT).set(dc.CommandBlockOutput, server);
        if (dc.NaturalRegeneration != null) rules.getRule(GameRules.RULE_NATURAL_REGENERATION).set(dc.NaturalRegeneration, server);
        if (dc.DoDaylightCycle != null) rules.getRule(GameRules.RULE_DAYLIGHT).set(dc.DoDaylightCycle, server);
        if (dc.LogAdminCommands != null) rules.getRule(GameRules.RULE_LOGADMINCOMMANDS).set(dc.LogAdminCommands, server);
        if (dc.ShowDeathMessages != null) rules.getRule(GameRules.RULE_SHOWDEATHMESSAGES).set(dc.ShowDeathMessages, server);
        if (dc.SendCommandFeedback != null) rules.getRule(GameRules.RULE_SENDCOMMANDFEEDBACK).set(dc.SendCommandFeedback, server);
        if (dc.SpectatorsGenerateChunks != null) rules.getRule(GameRules.RULE_SPECTATORSGENERATECHUNKS).set(dc.SpectatorsGenerateChunks, server);
        if (dc.DisableElytraMovementCheck != null) rules.getRule(GameRules.RULE_DISABLE_ELYTRA_MOVEMENT_CHECK).set(dc.DisableElytraMovementCheck, server);
        if (dc.DoWeatherCycle != null) rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(dc.DoWeatherCycle, server);
        if (dc.DoLimitedCrafting != null) rules.getRule(GameRules.RULE_LIMITED_CRAFTING).set(dc.DoLimitedCrafting, server);
        if (dc.AnnounceAdvancements != null) rules.getRule(GameRules.RULE_ANNOUNCE_ADVANCEMENTS).set(dc.AnnounceAdvancements, server);
        if (dc.DisableRaids != null) rules.getRule(GameRules.RULE_DISABLE_RAIDS).set(dc.DisableRaids, server);
        if (dc.DoInsomnia != null) rules.getRule(GameRules.RULE_DOINSOMNIA).set(dc.DoInsomnia, server);
        if (dc.DrowningDamage != null) rules.getRule(GameRules.RULE_DROWNING_DAMAGE).set(dc.DrowningDamage, server);
        if (dc.FallDamage != null) rules.getRule(GameRules.RULE_FALL_DAMAGE).set(dc.FallDamage, server);
        if (dc.FireDamage != null) rules.getRule(GameRules.RULE_FIRE_DAMAGE).set(dc.FireDamage, server);
        if (dc.DoPatrolSpawning != null) rules.getRule(GameRules.RULE_DO_PATROL_SPAWNING).set(dc.DoPatrolSpawning, server);
        if (dc.DoTraderSpawning != null) rules.getRule(GameRules.RULE_DO_TRADER_SPAWNING).set(dc.DoTraderSpawning, server);
        if (dc.ForgiveDeadPlayers != null) rules.getRule(GameRules.RULE_FORGIVE_DEAD_PLAYERS).set(dc.ForgiveDeadPlayers, server);
        if (dc.UniversalAnger != null) rules.getRule(GameRules.RULE_UNIVERSAL_ANGER).set(dc.UniversalAnger, server);
        if (dc.ProjectilesCanBreakBlocks != null) rules.getRule(GameRules.RULE_PROJECTILESCANBREAKBLOCKS).set(dc.ProjectilesCanBreakBlocks, server);
        if (dc.ReducedDebugInfo != null) rules.getRule(GameRules.RULE_REDUCEDDEBUGINFO).set(dc.ReducedDebugInfo, server);
        if (dc.DoImmediateRespawn != null) rules.getRule(GameRules.RULE_DO_IMMEDIATE_RESPAWN).set(dc.DoImmediateRespawn, server);
        if (dc.FreezeDamage != null) rules.getRule(GameRules.RULE_FREEZE_DAMAGE).set(dc.FreezeDamage, server);
        if (dc.DoWardenSpawning != null) rules.getRule(GameRules.RULE_DO_WARDEN_SPAWNING).set(dc.DoWardenSpawning, server);
        if (dc.BlockExplosionDropDecay != null) rules.getRule(GameRules.RULE_BLOCK_EXPLOSION_DROP_DECAY).set(dc.BlockExplosionDropDecay, server);
        if (dc.MobExplosionDropDecay != null) rules.getRule(GameRules.RULE_MOB_EXPLOSION_DROP_DECAY).set(dc.MobExplosionDropDecay, server);
        if (dc.TntExplosionDropDecay != null) rules.getRule(GameRules.RULE_TNT_EXPLOSION_DROP_DECAY).set(dc.TntExplosionDropDecay, server);
        if (dc.WaterSourceConversion != null) rules.getRule(GameRules.RULE_WATER_SOURCE_CONVERSION).set(dc.WaterSourceConversion, server);
        if (dc.LavaSourceConversion != null) rules.getRule(GameRules.RULE_LAVA_SOURCE_CONVERSION).set(dc.LavaSourceConversion, server);
        if (dc.GlobalSoundEvents != null) rules.getRule(GameRules.RULE_GLOBAL_SOUND_EVENTS).set(dc.GlobalSoundEvents, server);
        if (dc.DoVinesSpread != null) rules.getRule(GameRules.RULE_DO_VINES_SPREAD).set(dc.DoVinesSpread, server);
        if (dc.EnderPearlsVanishOnDeath != null) rules.getRule(GameRules.RULE_ENDER_PEARLS_VANISH_ON_DEATH).set(dc.EnderPearlsVanishOnDeath, server);
        // Integer overrides
        if (dc.RandomTickSpeed != null) rules.getRule(GameRules.RULE_RANDOMTICKING).set(dc.RandomTickSpeed, server);
        if (dc.SpawnRadius != null) rules.getRule(GameRules.RULE_SPAWN_RADIUS).set(dc.SpawnRadius, server);
        if (dc.MaxEntityCramming != null) rules.getRule(GameRules.RULE_MAX_ENTITY_CRAMMING).set(dc.MaxEntityCramming, server);
        if (dc.MaxCommandChainLength != null) rules.getRule(GameRules.RULE_MAX_COMMAND_CHAIN_LENGTH).set(dc.MaxCommandChainLength, server);
        if (dc.MaxCommandForkCount != null) rules.getRule(GameRules.RULE_MAX_COMMAND_FORK_COUNT).set(dc.MaxCommandForkCount, server);
        if (dc.CommandModificationBlockLimit != null) rules.getRule(GameRules.RULE_COMMAND_MODIFICATION_BLOCK_LIMIT).set(dc.CommandModificationBlockLimit, server);
        if (dc.PlayersNetherPortalDefaultDelay != null) rules.getRule(GameRules.RULE_PLAYERS_NETHER_PORTAL_DEFAULT_DELAY).set(dc.PlayersNetherPortalDefaultDelay, server);
        if (dc.PlayersNetherPortalCreativeDelay != null) rules.getRule(GameRules.RULE_PLAYERS_NETHER_PORTAL_CREATIVE_DELAY).set(dc.PlayersNetherPortalCreativeDelay, server);
        if (dc.PlayersSleepingPercentage != null) rules.getRule(GameRules.RULE_PLAYERS_SLEEPING_PERCENTAGE).set(dc.PlayersSleepingPercentage, server);
        if (dc.SnowAccumulationHeight != null) rules.getRule(GameRules.RULE_SNOW_ACCUMULATION_HEIGHT).set(dc.SnowAccumulationHeight, server);
        if (dc.SpawnChunkRadius != null) rules.getRule(GameRules.RULE_SPAWN_CHUNK_RADIUS).set(dc.SpawnChunkRadius, server);
    }

    /**
     * Serializes a GameRules instance to a map for persistence.
     */
    public static Map<String, Object> toMap(GameRules rules) {
        Map<String, Object> map = new LinkedHashMap<>();
        var tag = rules.createTag();
        for (String key : tag.getAllKeys()) {
            String value = tag.getString(key);
            try {
                map.put(key, Integer.parseInt(value));
            } catch (NumberFormatException e) {
                map.put(key, Boolean.parseBoolean(value));
            }
        }
        return map;
    }

    /**
     * Deserializes a GameRules instance from a persisted map.
     * Returns vanilla defaults if data is corrupt.
     */
    public static GameRules fromMap(Map<String, Object> map) {
        try {
            var tag = new CompoundTag();
            for (var entry : map.entrySet()) {
                tag.putString(entry.getKey(), String.valueOf(entry.getValue()));
            }
            return new GameRules(new Dynamic<>(NbtOps.INSTANCE, tag));
        } catch (Exception e) {
            OTGLog.error("Failed to deserialize GameRules from storage, using vanilla defaults: {}", e.getMessage());
            return new GameRules();
        }
    }
}
