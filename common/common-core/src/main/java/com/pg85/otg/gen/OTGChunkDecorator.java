package com.pg85.otg.gen;

import com.pg85.otg.OTG;
import com.pg85.otg.config.ConfigFunction;
import com.pg85.otg.config.ErroredFunction;
import com.pg85.otg.config.biome.BiomeConfig;
import com.pg85.otg.constants.settings.structure.CustomStructureType;
import com.pg85.otg.customobject.CustomObject;
import com.pg85.otg.customobject.CustomObjectManager;
import com.pg85.otg.customobject.bo3.BO3;
import com.pg85.otg.customobject.config.CustomObjectResourcesManager;
import com.pg85.otg.customobject.resource.ICustomObjectResource;
import com.pg85.otg.customobject.resource.ICustomStructureResource;
import com.pg85.otg.customobject.structures.CustomStructureCache;
import com.pg85.otg.customobject.util.BO3Enums.SpawnHeightEnum;
import com.pg85.otg.gen.biome.UndergroundBiomeMap;
import com.pg85.otg.gen.resource.IBasicResource;
import com.pg85.otg.gen.surface.FrozenSurfaceHelper;
import com.pg85.otg.config.settings.biome.BiomeSettings;
import com.pg85.otg.interfaces.IChunkDecorator;
import com.pg85.otg.interfaces.IMaterialReader;
import com.pg85.otg.interfaces.IModLoadedChecker;
import com.pg85.otg.interfaces.IWorldGenRegion;
import com.pg85.otg.util.ChunkCoordinate;
import com.pg85.otg.util.OTGLog;
import com.pg85.otg.util.OTGMaterialReader;
import com.pg85.otg.util.bo3.Rotation;
import com.pg85.otg.util.logging.LogCategory;
import com.pg85.otg.util.logging.LogLevel;

import it.unimi.dsi.fastutil.ints.IntList;

import java.nio.file.Path;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Takes care of resource decoration. Spawns all OTG resources. Some of the decoration steps (like vanilla 
 * structures and mob spawning) use mc logic and are spawned by mc itself. For those decoration steps, OTG 
 * only fills in the required configurations when registering the biomes in the platform-specific layer (see OTGBiome/ForgeBiome).
 */
public class OTGChunkDecorator implements IChunkDecorator
{
	// Locking objects / checks to prevent decorate running on multiple threads,
	// or when the world is waiting for an opportunity to save.
	private final Object lockingObject = new Object();
	private final AtomicInteger decorating = new AtomicInteger(0);
	private volatile boolean saving;
	private volatile boolean saveRequired;

	// Per-region locks for BO4 - chunks in different regions can be processed in parallel
	// Region size: 32x32 chunks (512x512 blocks)
	private static final int REGION_SIZE_BITS = 5;  // 2^5 = 32 chunks
	private final ConcurrentHashMap<Long, Object> regionLocks = new ConcurrentHashMap<>();

	// Performance tracking
	private final AtomicLong totalBo4TimeMs = new AtomicLong(0);
	private final AtomicLong totalResourceTimeMs = new AtomicLong(0);
	private final AtomicLong totalCustomObjectTimeMs = new AtomicLong(0);
	private final AtomicLong totalCustomStructureTimeMs = new AtomicLong(0);
	private final AtomicLong totalBasicResourceTimeMs = new AtomicLong(0);
	private final AtomicInteger chunksDecorated = new AtomicInteger(0);
	private final ConcurrentHashMap<String, AtomicLong> resourceTypeTimings = new ConcurrentHashMap<>();
	private static final int LOG_INTERVAL = 100;  // Log stats every N chunks

	public OTGChunkDecorator()
	{
	}

	/**
	 * Get lock object for the region containing this chunk.
	 * Chunks in different regions can be decorated in parallel.
	 */
	private Object getRegionLock(ChunkCoordinate coord) {
		long regionX = coord.getChunkX() >> REGION_SIZE_BITS;
		long regionZ = coord.getChunkZ() >> REGION_SIZE_BITS;
		long regionKey = (regionX << 32) | (regionZ & 0xFFFFFFFFL);
		return regionLocks.computeIfAbsent(regionKey, k -> new Object());
	}

	/**
	 * Track time spent on each resource type.
	 */
	private void trackResourceType(String resourceType, long timeMs) {
		resourceTypeTimings.computeIfAbsent(resourceType, k -> new AtomicLong(0)).addAndGet(timeMs);
	}

	/**
	 * Create a deterministic Random for this chunk.
	 * Each chunk gets its own Random instance - no shared state.
	 */
	private Random createChunkRandom(long worldSeed, ChunkCoordinate chunkCoord) {
		Random seedRandom = new Random(worldSeed);
		long l1 = seedRandom.nextLong() / 2L * 2L + 1L;
		long l2 = seedRandom.nextLong() / 2L * 2L + 1L;
		long chunkSeed = (long) chunkCoord.getChunkX() * l1 + (long) chunkCoord.getChunkZ() * l2 ^ worldSeed;
		return new Random(chunkSeed);
	}
	
	@Override
	public boolean getIsSaveRequired()
	{
		return this.saveRequired;
	}

	@Override
	public boolean isDecorating()
	{
		return this.decorating.get() != 0;
	}

	@Override
	public void beginSave()
	{
		this.saving = true;
	}

	@Override
	public void endSave()
	{
		this.saveRequired = false;
		this.saving = false;
	}

	@Override
	public Object getLockingObject()
	{
		return this.lockingObject;
	}

	public void decorate(ChunkCoordinate chunkCoord, IWorldGenRegion worldGenRegion, BiomeSettings biomeConfig, CustomStructureCache structureCache, UndergroundBiomeMap undergroundMap)
	{
		// Wait for save to complete (lock-free spin)
		boolean loggedWait = false;
		while (this.saving) {
			if (!loggedWait) {
				OTGLog.warn("Decorate waiting on SaveToDisk. Although other mods could be causing this and there may not be any problem, this can potentially cause an endless loop!");
				loggedWait = true;
			}
			Thread.yield();
		}

		this.decorating.incrementAndGet();
		this.saveRequired = true;

		try {
			Path otgRootFolder = OTG.getEngine().getOTGRootFolder();
			CustomObjectManager customObjectManager = OTG.getEngine().getCustomObjectManager();
			IMaterialReader materialReader = OTGMaterialReader.get();
			CustomObjectResourcesManager customObjectResourcesManager = OTG.getEngine().getCustomObjectResourcesManager();
			IModLoadedChecker modLoadedChecker = OTG.getEngine().getModLoadedChecker();

			// Create per-chunk Random - thread-safe, no shared state
			Random rand = createChunkRandom(worldGenRegion.getSeed(), chunkCoord);

			doDecorate(chunkCoord, worldGenRegion, biomeConfig, materialReader, otgRootFolder, structureCache, customObjectManager, customObjectResourcesManager, modLoadedChecker, rand, undergroundMap);
		} finally {
			this.decorating.decrementAndGet();

			// Log performance stats periodically
			int count = chunksDecorated.incrementAndGet();
			if (count % LOG_INTERVAL == 0) {
				long bo4Ms = totalBo4TimeMs.get();
				long resMs = totalResourceTimeMs.get();
				long customObjMs = totalCustomObjectTimeMs.get();
				long customStructMs = totalCustomStructureTimeMs.get();
				long basicResMs = totalBasicResourceTimeMs.get();
				long totalMs = bo4Ms + resMs;
				if (totalMs > 0) {
					OTGLog.info(LogCategory.PERFORMANCE,
						String.format("Decoration stats (%d chunks): BO4=%.1f%% (%dms), Resources=%.1f%% (%dms), Avg=%.2fms/chunk",
							count,
							100.0 * bo4Ms / totalMs, bo4Ms,
							100.0 * resMs / totalMs, resMs,
							(double) totalMs / count));

					// Log resource breakdown
					if (resMs > 0) {
						OTGLog.info(LogCategory.PERFORMANCE,
							String.format("  Resource breakdown: CustomObject=%.1f%% (%dms), CustomStruct=%.1f%% (%dms), Basic=%.1f%% (%dms)",
								100.0 * customObjMs / resMs, customObjMs,
								100.0 * customStructMs / resMs, customStructMs,
								100.0 * basicResMs / resMs, basicResMs));
					}

					// Log top 5 resource types by time
					StringBuilder topResources = new StringBuilder("  Top resources: ");
					resourceTypeTimings.entrySet().stream()
						.sorted((a, b) -> Long.compare(b.getValue().get(), a.getValue().get()))
						.limit(5)
						.forEach(e -> topResources.append(String.format("%s=%dms, ", e.getKey(), e.getValue().get())));
					OTGLog.info(LogCategory.PERFORMANCE, topResources.toString());
				}
			}
		}
	}

	// TODO: Fire decoration events.
	private void doDecorate(ChunkCoordinate chunkCoord, IWorldGenRegion worldGenRegion, BiomeSettings biomeConfig, IMaterialReader materialReader, Path otgRootFolder, CustomStructureCache structureCache, CustomObjectManager customObjectManager, CustomObjectResourcesManager customObjectResourcesManager, IModLoadedChecker modLoadedChecker, Random rand, UndergroundBiomeMap undergroundMap)
	{
		if (biomeConfig == null)
		{
			OTGLog.error(LogCategory.DECORATION,
				"Unknown biome at {},{} (chunk {}). Could not decorate chunk.",
				chunkCoord.getChunkX(),
				chunkCoord.getChunkZ(),
				chunkCoord
			);
			return;
		}

		// Use BO4 logic for BO4 worlds
		if(worldGenRegion.getConfig().getResourceSettings().getCustomStructureType() == CustomStructureType.BO4)
		{
			long bo4Start = System.currentTimeMillis();
			// Per-region lock - chunks in different regions can be processed in parallel
			// This allows ~32x more parallelism than global lock while still preventing
			// conflicts within a region where BO4 structures might overlap.
			synchronized(getRegionLock(chunkCoord))
			{
				plotAndSpawnBO4s(structureCache, worldGenRegion, ChunkCoordinate.fromChunkCoords(chunkCoord.getChunkX(), chunkCoord.getChunkZ()), chunkCoord, otgRootFolder, customObjectManager, materialReader, customObjectResourcesManager, modLoadedChecker, rand);
			}
			totalBo4TimeMs.addAndGet(System.currentTimeMillis() - bo4Start);
		}

		if(
			worldGenRegion.getSpawnChunk().equals(chunkCoord) &&
			worldGenRegion.getConfig().getResourceSettings().getBO3AtSpawn() != null &&
			!worldGenRegion.getConfig().getResourceSettings().getBO3AtSpawn().trim().isEmpty()
		)
		{
			handleBO3AtSpawn(worldGenRegion, chunkCoord, worldGenRegion.getConfig().getResourceSettings().getBO3AtSpawn(), worldGenRegion.getPresetFolderName(), otgRootFolder, structureCache, customObjectManager, materialReader, customObjectResourcesManager, modLoadedChecker, rand);
		}

		long resourcesStart = System.currentTimeMillis();
		// Resource sequence — surface biome
		processResourceQueue(biomeConfig, worldGenRegion, structureCache, otgRootFolder, customObjectManager, materialReader, customObjectResourcesManager, modLoadedChecker, rand);

		// Underground biome resource passes — each masked to its own 3D region
		if (undergroundMap != null && !undergroundMap.isEmpty())
		{
			IntList present = undergroundMap.getPresentBiomeIds();
			for (int i = 0; i < present.size(); i++)
			{
				int ugId = present.getInt(i);
				BiomeSettings ugConfig = undergroundMap.getBiome(ugId).getBiomeSettings();
				worldGenRegion.beginUndergroundBiomeMask(undergroundMap, ugId);
				try
				{
					processResourceQueue(ugConfig, worldGenRegion, structureCache, otgRootFolder,
							customObjectManager, materialReader, customObjectResourcesManager, modLoadedChecker, rand);
				}
				finally
				{
					worldGenRegion.endUndergroundBiomeMask();
				}
			}
		}

		// Time the whole resource pass (surface + underground) so the per-resource-type
		// counters stay coherent with totalResourceTimeMs in the periodic perf breakdown.
		long resourcesTime = System.currentTimeMillis() - resourcesStart;
		totalResourceTimeMs.addAndGet(resourcesTime);
		if (OTGLog.isEnabled(LogLevel.WARN, LogCategory.PERFORMANCE) && resourcesTime > 50)
		{
			OTGLog.warn(LogCategory.PERFORMANCE, "Processing resources in biome {} took {}ms", biomeConfig.getIdentitySettings().getBiomeName(), resourcesTime);
		}
	}

	private void processResourceQueue(BiomeSettings biomeConfig, IWorldGenRegion worldGenRegion,
			CustomStructureCache structureCache, Path otgRootFolder, CustomObjectManager customObjectManager,
			IMaterialReader materialReader, CustomObjectResourcesManager customObjectResourcesManager,
			IModLoadedChecker modLoadedChecker, Random rand)
	{
		var resourceQueue = ((BiomeConfig)biomeConfig).getResourceQueue();
		for (ConfigFunction<BiomeSettings> res : resourceQueue)
		{
			long startTime = System.currentTimeMillis();
			String resourceType = res.getClass().getSimpleName();

			if (res instanceof ICustomObjectResource)
			{
				((ICustomObjectResource)res).processForChunkDecoration(structureCache, worldGenRegion, rand, otgRootFolder, customObjectManager, materialReader, customObjectResourcesManager, modLoadedChecker);
				long elapsed = System.currentTimeMillis() - startTime;
				totalCustomObjectTimeMs.addAndGet(elapsed);
				trackResourceType(resourceType, elapsed);
				if (OTGLog.isEnabled(LogLevel.WARN, LogCategory.PERFORMANCE) && elapsed > 50)
				{
					OTGLog.warn(LogCategory.PERFORMANCE, "Processing resource {} in biome {} took {}ms", res, biomeConfig.getIdentitySettings().getBiomeName(), elapsed);
				}
			}
			else if (res instanceof ICustomStructureResource)
			{
				((ICustomStructureResource)res).processForChunkDecoration(structureCache, worldGenRegion, rand, otgRootFolder, customObjectManager, materialReader, customObjectResourcesManager, modLoadedChecker);
				long elapsed = System.currentTimeMillis() - startTime;
				totalCustomStructureTimeMs.addAndGet(elapsed);
				trackResourceType(resourceType, elapsed);
				if (OTGLog.isEnabled(LogLevel.WARN, LogCategory.PERFORMANCE) && elapsed > 50)
				{
					OTGLog.warn(LogCategory.PERFORMANCE, "Processing resource {} in biome {} took {}ms", res, biomeConfig.getIdentitySettings().getBiomeName(), elapsed);
				}
			}
			else if (res instanceof IBasicResource)
			{
				((IBasicResource)res).processForChunkDecoration(worldGenRegion, rand);
				long elapsed = System.currentTimeMillis() - startTime;
				totalBasicResourceTimeMs.addAndGet(elapsed);
				trackResourceType(resourceType, elapsed);
				if (OTGLog.isEnabled(LogLevel.WARN, LogCategory.PERFORMANCE) && elapsed > 50)
				{
					OTGLog.warn(LogCategory.PERFORMANCE, "Processing resource {} in biome {} took {}ms", res, biomeConfig.getIdentitySettings().getBiomeName(), elapsed);
				}
			}
			else if(res instanceof ErroredFunction)
			{
				if (OTGLog.isEnabled(LogLevel.ERROR, LogCategory.DECORATION))
				{
					if(!((ErroredFunction<BiomeSettings>)res).isLogged)
					{
						((ErroredFunction<BiomeSettings>)res).isLogged = true;
						OTGLog.error(LogCategory.DECORATION, "Errored setting ignored for biome {} : {}", biomeConfig.getIdentitySettings().getBiomeName(), toString());
					}
				}
			}
		}
	}

	public void doSnowAndIce(IWorldGenRegion worldGenRegion, ChunkCoordinate chunkCoord)
	{
		// Snow and ice
		// TODO: Snow is appearing below structures, indicating it spawned before 
		// it should. Check and align decoration bounds for resources and make sure
		// freezing is done during the correct decoration step.
		FrozenSurfaceHelper.freezeChunk(worldGenRegion, chunkCoord);
	}

	private void plotAndSpawnBO4s(CustomStructureCache structureCache, IWorldGenRegion worldGenRegion, ChunkCoordinate chunkCoord, ChunkCoordinate chunkBeingDecorated, Path otgRootFolder, CustomObjectManager customObjectManager, IMaterialReader materialReader, CustomObjectResourcesManager customObjectResourcesManager, IModLoadedChecker modLoadedChecker, Random rand)
	{
		// Plot and spawn BO4's for all chunks that may have blocks spawned on them while decorating this chunk,
		// so we can be sure those chunks have had a chance to plot+spawn bo4's before other resources.

		structureCache.plotBo4Structures(worldGenRegion, rand, chunkCoord, otgRootFolder, customObjectManager, materialReader, customObjectResourcesManager, modLoadedChecker);
		structureCache.plotBo4Structures(worldGenRegion, rand, ChunkCoordinate.fromChunkCoords(chunkCoord.getChunkX() + 1, chunkCoord.getChunkZ()), otgRootFolder, customObjectManager, materialReader, customObjectResourcesManager, modLoadedChecker);
		structureCache.plotBo4Structures(worldGenRegion, rand, ChunkCoordinate.fromChunkCoords(chunkCoord.getChunkX() , chunkCoord.getChunkZ() + 1), otgRootFolder, customObjectManager, materialReader, customObjectResourcesManager, modLoadedChecker);
		structureCache.plotBo4Structures(worldGenRegion, rand, ChunkCoordinate.fromChunkCoords(chunkCoord.getChunkX() + 1, chunkCoord.getChunkZ() + 1), otgRootFolder, customObjectManager, materialReader, customObjectResourcesManager, modLoadedChecker);

		spawnBO4(structureCache, worldGenRegion, chunkCoord, otgRootFolder, customObjectManager, materialReader, customObjectResourcesManager, modLoadedChecker);
		spawnBO4(structureCache, worldGenRegion, ChunkCoordinate.fromChunkCoords(chunkCoord.getChunkX() + 1, chunkCoord.getChunkZ()), otgRootFolder, customObjectManager, materialReader, customObjectResourcesManager, modLoadedChecker);
		spawnBO4(structureCache, worldGenRegion, ChunkCoordinate.fromChunkCoords(chunkCoord.getChunkX(), chunkCoord.getChunkZ() + 1), otgRootFolder, customObjectManager, materialReader, customObjectResourcesManager, modLoadedChecker);
		spawnBO4(structureCache, worldGenRegion, ChunkCoordinate.fromChunkCoords(chunkCoord.getChunkX() + 1, chunkCoord.getChunkZ() + 1), otgRootFolder, customObjectManager, materialReader, customObjectResourcesManager, modLoadedChecker);
	}

	private void spawnBO4(CustomStructureCache structureCache, IWorldGenRegion worldGenRegion, ChunkCoordinate chunkCoord, Path otgRootFolder, CustomObjectManager customObjectManager, IMaterialReader materialReader, CustomObjectResourcesManager manager, IModLoadedChecker modLoadedChecker)
	{
		structureCache.spawnBo4Chunk(worldGenRegion, chunkCoord, otgRootFolder, customObjectManager, materialReader, manager, modLoadedChecker);
	}
	
	private void handleBO3AtSpawn(IWorldGenRegion worldGenRegion, ChunkCoordinate targetChunk, String bo3AtSpawn, String presetFolderName, Path otgRootFolder, CustomStructureCache structureCache, CustomObjectManager customObjectManager, IMaterialReader materialReader, CustomObjectResourcesManager customObjectResourcesManager, IModLoadedChecker modLoadedChecker, Random rand)
	{
		// If a BO3AtSpawn has been defined, spawn it.
		CustomObject customObject = customObjectManager.getGlobalObjects().getObjectByName(
			bo3AtSpawn,
			presetFolderName,
			otgRootFolder,
			customObjectManager,
			materialReader,
			customObjectResourcesManager,
			modLoadedChecker
		);
		if(customObject != null)
		{
			if(customObject instanceof BO3 bo3)
			{
				int y = 1;
				SpawnHeightEnum spawnHeight = bo3.getConfig().getSpawnHeight();
				if(spawnHeight == SpawnHeightEnum.highestBlock || spawnHeight == SpawnHeightEnum.surface)
				{
					y = worldGenRegion.getHighestBlockAboveYAt(targetChunk.getBlockX() + 15, targetChunk.getBlockZ() + 15) - 1;
				}
				else if(spawnHeight == SpawnHeightEnum.highestSolidBlock || spawnHeight == SpawnHeightEnum.solidSurface)
				{
					y = worldGenRegion.getBlockAboveSolidHeight(targetChunk.getBlockX() + 15, targetChunk.getBlockZ() + 15) - 1;
				}
				else if(spawnHeight == SpawnHeightEnum.randomY)
				{
					y = (int) (bo3.getConfig().minHeight + (rand.nextDouble() * (bo3.getConfig().maxHeight - bo3.getConfig().minHeight)));
				}

				y += bo3.getConfig().getSpawnHeightOffset();
				// This may spawn the structure across chunk borders.
				bo3.spawnForced(
					structureCache,
					worldGenRegion,
					rand,
					Rotation.NORTH,
					targetChunk.getBlockX() + 16 + bo3.getXOffset(Rotation.NORTH),
					y,
					targetChunk.getBlockZ() + 16 + bo3.getZOffset(Rotation.NORTH),
					true
				);
			}
		}
	}
}
