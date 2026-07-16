package com.pg85.otg.shared.gen;

import com.pg85.otg.OTG;
import com.pg85.otg.config.settings.preset.NoiseCaveSettings;
import com.pg85.otg.util.OTGLog;
import com.pg85.otg.util.logging.LogCategory;
import com.pg85.otg.constants.Constants;
import com.pg85.otg.constants.settings.structure.CustomStructureType;
import com.pg85.otg.customobject.structures.CustomStructureCache;
import com.pg85.otg.gen.OTGChunkDecorator;
import com.pg85.otg.gen.OTGChunkGenerator;
import com.pg85.otg.interfaces.IBiome;
import com.pg85.otg.platform.noise.OTGNoiseRouterData;
import com.pg85.otg.presets.DimensionPreset;
import com.pg85.otg.shared.biome.IOTGBiomeProvider;
import com.pg85.otg.util.ChunkCoordinate;
import com.pg85.otg.gen.biome.UndergroundBiomeMap;
import com.pg85.otg.util.gen.ChunkBuffer;
import com.pg85.otg.util.gen.JigsawStructureData;
import com.pg85.otg.util.gen.OTGWorldInfo;
import com.pg85.otg.util.helpers.MathHelper;
import com.pg85.otg.util.materials.LocalMaterialData;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import lombok.Getter;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.Mth;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.*;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.carver.CarvingContext;
import net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

@Getter
public abstract class SharedOTGChunkGenerator extends ChunkGenerator {

    // --- Abstract methods for platform-specific operations ---

    protected abstract SharedWorldGenRegion createWorldGenRegion(
            String presetFolderName, WorldGenLevel worldGenLevel, ChunkAccess chunkAccess);

    protected abstract ChunkBuffer createChunkBuffer(ChunkAccess chunkAccess);

    protected abstract BiomeGenerationSettings getBiomeGenerationSettings(IBiome biome);

    public abstract Boolean checkHasVanillaStructureWithoutLoading(ServerLevel level, ChunkCoordinate chunkCoord);

    public abstract int getHighestBlockYInUnloadedChunk(int x, int z, boolean findSolid, boolean findLiquid, boolean ignoreLiquid, boolean ignoreSnow);

    public abstract LocalMaterialData getMaterialInUnloadedChunk(int x, int y, int z);

    // --- Fields ---

    protected final Holder<NoiseGeneratorSettings> settings;
    protected final IOTGBiomeProvider otgBiomeProvider;
    protected final OTGChunkGenerator internalGenerator;
    protected final DimensionPreset preset;
    protected Registry<Biome> biomeRegistry;
    protected final NoiseBasedChunkGenerator horribleDelegateForCarvers;
    protected Aquifer.FluidPicker globalFluidPicker = null;
    protected final OTGChunkDecorator chunkDecorator;
    protected CustomStructureCache structureCache = null;
    protected Long seed = 0L;
    protected ServerLevel serverLevel = null;
    protected final OTGWorldInfo otgWorldInfo;
    protected volatile RandomState caveRandomState = null;
    protected volatile SimplexNoise breakthroughNoise = null;
    protected volatile OTGNoiseRouterData.CaveDensityComponents caveComponents = null;

    // --- Timing counters (thread-safe) ---
    private static final int TIMING_LOG_INTERVAL = 50;
    private final AtomicInteger fillNoiseCount = new AtomicInteger();
    private final AtomicLong fillNoiseTotalNs = new AtomicLong();
    private final AtomicLong populateNoiseTotalNs = new AtomicLong();
    private final AtomicLong carveWithNoiseTotalNs = new AtomicLong();
    private final AtomicInteger carversCount = new AtomicInteger();
    private final AtomicLong carversTotalNs = new AtomicLong();
    private final AtomicInteger structuresCount = new AtomicInteger();
    private final AtomicLong structuresTotalNs = new AtomicLong();
    private final AtomicInteger decorationCount = new AtomicInteger();
    private final AtomicLong decorationTotalNs = new AtomicLong();
    private final AtomicLong superDecorationTotalNs = new AtomicLong();

    // --- Constructor ---

    protected SharedOTGChunkGenerator(
            IOTGBiomeProvider otgBiomeProvider,
            Holder<NoiseGeneratorSettings> settings,
            Registry<Biome> biomeRegistry
    ) {
        super((net.minecraft.world.level.biome.BiomeSource) otgBiomeProvider);
        this.otgBiomeProvider = otgBiomeProvider;
        this.settings = settings;
        int minY = settings.value().noiseSettings().minY();
        int maxY = settings.value().noiseSettings().height() + minY - 1;
        this.otgWorldInfo = new OTGWorldInfo(minY, maxY);
        this.internalGenerator = new OTGChunkGenerator(
                OTG.getEngine().getDimensionPresetLoader().getDimensionPresetByFolderName(otgBiomeProvider.getPresetFolderName()),
                (com.pg85.otg.interfaces.ILayerSource) otgBiomeProvider,
                OTG.getEngine().getDimensionPresetLoader().getGlobalIdMapping(otgBiomeProvider.getPresetFolderName()),
                otgWorldInfo
        );
        this.preset = OTG.getEngine().getDimensionPresetLoader().getDimensionPresetByFolderName(otgBiomeProvider.getPresetFolderName());
        this.biomeRegistry = biomeRegistry;
        this.horribleDelegateForCarvers = new NoiseBasedChunkGenerator(
                (net.minecraft.world.level.biome.BiomeSource) otgBiomeProvider, settings);
        this.chunkDecorator = new OTGChunkDecorator();
        this.globalFluidPicker = createFluidPicker(settings.value());
    }

    // --- Seed management ---

    @Override
    public ChunkGeneratorStructureState createState(HolderLookup<StructureSet> structureSetLookup, RandomState randomState, long seed) {
        if (this.seed == 0L) {
            this.setSeed(BiomeManager.obfuscateSeed(seed));
        }
        initCaveComponents(randomState);
        return super.createState(structureSetLookup, randomState, seed);
    }

    public void setSeed(Long seed) {
        synchronized (this) {
            if (this.seed == 0L) {
                this.seed = seed;
                otgBiomeProvider.setSeed(seed);
                internalGenerator.setSeed(seed);
            }
        }
    }

    // --- Cave initialization ---

    protected void initCaveComponents(RandomState randomState) {
        if (this.caveRandomState != null) return;
        if (!this.preset.getConfig().getCarverSettings().isUseModernCaves()) return;
        synchronized (this) {
            if (this.caveRandomState != null) return;
            NoiseCaveSettings caveCfg = this.preset.getConfig().getNoiseCaveSettings();
            NoiseSettings ns = this.settings.value().noiseSettings();

            boolean regionScaling = this.internalGenerator.hasUndergroundCaveScaling();
            OTGNoiseRouterData.CaveDensityComponents components = OTGNoiseRouterData.caveDensityComponentsForCarving(
                    randomState.noises, caveCfg,
                    this.preset.getFolderName(), ns.minY(), ns.height() + ns.minY(), regionScaling
            );

            DensityFunction zero = DensityFunctions.constant(0);
            NoiseRouter caveRouter = new NoiseRouter(
                    components.spaghetti(),
                    components.cheese(),
                    components.noodle(),
                    zero,
                    zero, zero, zero, zero,
                    zero, zero, zero,
                    components.combined(),
                    zero, zero, zero
            );

            NoiseGeneratorSettings original = this.settings.value();
            NoiseGeneratorSettings caveOnlySettings = new NoiseGeneratorSettings(
                    original.noiseSettings(), original.defaultBlock(), original.defaultFluid(),
                    caveRouter, original.surfaceRule(), original.spawnTarget(),
                    original.seaLevel(), original.disableMobGeneration(),
                    false, false, original.useLegacyRandomSource()
            );

            this.caveRandomState = RandomState.create(caveOnlySettings, randomState.noises, this.seed);
            NoiseRouter processedRouter = this.caveRandomState.router();
            this.caveComponents = new OTGNoiseRouterData.CaveDensityComponents(
                    processedRouter.barrierNoise(),
                    processedRouter.fluidLevelFloodednessNoise(),
                    processedRouter.fluidLevelSpreadNoise(),
                    processedRouter.finalDensity()
            );
            this.breakthroughNoise = new SimplexNoise(new WorldgenRandom(new LegacyRandomSource(this.seed ^ 0xCA0EB1A5L)));
            OTGLog.info(LogCategory.MAIN, "Created cave-only RandomState (terrain-independent density, debug components resolved)");
        }
    }

    // --- Server level ---

    public void setServerLevel(ServerLevel serverLevel) {
        synchronized (this) {
            if (this.serverLevel == null) {
                this.serverLevel = serverLevel;
                if (this.biomeRegistry == null) {
                    this.biomeRegistry = serverLevel.registryAccess().registryOrThrow(Registries.BIOME);
                }
                // Underground biome resolution only needs an approximate surface height for
                // its 16-block depth fade. The shadow-chunk estimator generated a FULL chunk
                // (plus ThreadSafeLRUCache lock contention) per uncached column — profiled at
                // ~16 ms/chunk of pure lock-wait inside applyCarvers' carverBiome lookups and
                // effectively double-generated every chunk. Pure noise-column math is lock-free
                // and ~50x cheaper; shadow generation stays in use where exact block columns
                // matter (BO4s, spawn logic, getHighestBlockYInUnloadedChunk callers).
                otgBiomeProvider.setSurfaceHeightEstimator(this::estimateSurfaceHeightCached);
                this.internalGenerator.setSurfaceHeightEstimator(this::estimateSurfaceHeightCached);
            }
        }
    }

    // --- Biome decoration ---

    @Override
    public void applyBiomeDecoration(WorldGenLevel worldGenLevel, ChunkAccess chunkAccess, StructureManager structureManager) {
        long t0 = System.nanoTime();
        if (!OTG.getEngine().getPluginConfig().getDecorationEnabled()) {
            return;
        }
        ChunkCoordinate chunkBeingDecorated = getChunkCoordinate(worldGenLevel, chunkAccess);
        SharedWorldGenRegion worldGenRegion = createWorldGenRegion(
                this.preset.getFolderName(), worldGenLevel, chunkAccess);
        IBiome biome = this.internalGenerator.getCachedBiomeProvider().getNoiseBiome(
                (chunkAccess.getPos().x << 2) + 2, (chunkAccess.getPos().z << 2) + 2);

        Path worldSaveFolder = worldGenLevel.getLevel().getServer().getWorldPath(LevelResource.PLAYER_DATA_DIR).getParent();

        UndergroundBiomeMap undergroundMap =
                this.internalGenerator.buildUndergroundBiomeMap(chunkBeingDecorated, this.otgWorldInfo);
        this.chunkDecorator.decorate(chunkBeingDecorated, worldGenRegion, biome.getBiomeSettings(), getStructureCache(worldSaveFolder), undergroundMap);
        long tSuper = System.nanoTime();
        super.applyBiomeDecoration(worldGenLevel, chunkAccess, structureManager);
        long superElapsed = System.nanoTime() - tSuper;
        superDecorationTotalNs.addAndGet(superElapsed);

        if (!biome.getBiomeSettings().getIdentitySettings().isTemplateForBiome()) {
            this.chunkDecorator.doSnowAndIce(worldGenRegion, chunkBeingDecorated);
        }
        long totalElapsed = System.nanoTime() - t0;
        decorationTotalNs.addAndGet(totalElapsed);
        int count = decorationCount.incrementAndGet();
        if (count % TIMING_LOG_INTERVAL == 0) {
            OTGLog.info(LogCategory.PERFORMANCE, "decoration #{} avg={}ms (superDecorate avg={}ms)", count,
                    String.format("%.1f", decorationTotalNs.get() / 1_000_000.0 / count),
                    String.format("%.1f", superDecorationTotalNs.get() / 1_000_000.0 / count));
        }
    }

    // --- Structure cache ---

    public void saveStructureCache() {
        if (this.chunkDecorator.getIsSaveRequired() && this.structureCache != null) {
            this.structureCache.saveToDisk(chunkDecorator);
        }
    }

    public CustomStructureCache getStructureCache(Path worldSaveFolder) {
        if (this.structureCache == null) {
            this.structureCache = OTG.getEngine().createCustomStructureCache(
                    this.preset.getFolderName(),
                    worldSaveFolder,
                    this.seed,
                    CustomStructureType.BO4 == this.preset.getConfig().getResourceSettings().getCustomStructureType());
        }
        return this.structureCache;
    }

    // --- Structure generation ---

    private static ChunkCoordinate getChunkCoordinate(WorldGenLevel worldGenLevel, ChunkAccess chunkAccess) {
        int worldX = chunkAccess.getPos().x * Constants.CHUNK_SIZE;
        int worldZ = chunkAccess.getPos().z * Constants.CHUNK_SIZE;

        WorldgenRandom worldgenRandom = new WorldgenRandom(worldGenLevel.getRandom());
        worldgenRandom.setDecorationSeed(worldGenLevel.getSeed(), worldX, worldZ);

        return ChunkCoordinate.fromBlockCoords(worldX, worldZ);
    }

    @Override
    public void createStructures(
            RegistryAccess registryAccess,
            ChunkGeneratorStructureState chunkGeneratorStructureState,
            StructureManager structureManager,
            ChunkAccess chunkAccess,
            StructureTemplateManager structureTemplateManager
    ) {
        long t0 = System.nanoTime();
        super.createStructures(registryAccess, chunkGeneratorStructureState, structureManager, chunkAccess, structureTemplateManager);
        long elapsed = System.nanoTime() - t0;
        structuresTotalNs.addAndGet(elapsed);
        int count = structuresCount.incrementAndGet();
        if (count % TIMING_LOG_INTERVAL == 0) {
            OTGLog.info(LogCategory.PERFORMANCE, "createStructures #{} avg={}ms", count,
                    String.format("%.1f", structuresTotalNs.get() / 1_000_000.0 / count));
        }
    }

    @Override
    public void createReferences(
            WorldGenLevel worldGenRegion,
            StructureManager structureManager,
            ChunkAccess chunkAccess
    ) {
        if (this.serverLevel == null) {
            this.setServerLevel(worldGenRegion.getLevel());
        }
        super.createReferences(worldGenRegion, structureManager, chunkAccess);
    }

    // --- Fluid picker ---

    private static Aquifer.FluidPicker createFluidPicker(NoiseGeneratorSettings noiseGeneratorSettings) {
        Aquifer.FluidStatus fluidStatus = new Aquifer.FluidStatus(-54, Blocks.LAVA.defaultBlockState());
        int i = noiseGeneratorSettings.seaLevel();
        Aquifer.FluidStatus fluidStatus2 = new Aquifer.FluidStatus(i, noiseGeneratorSettings.defaultFluid());
        return (j, k, l) -> {
            if (k < Math.min(-54, i)) {
                return fluidStatus;
            }
            return fluidStatus2;
        };
    }

    // --- Carvers ---

    @Override
    public void applyCarvers(WorldGenRegion worldGenRegion, long seed, RandomState randomState, BiomeManager biomeManager, StructureManager structureManager, ChunkAccess chunkAccess, GenerationStep.Carving carving) {
        long t0 = System.nanoTime();
        if (this.preset.getConfig().getCarverSettings().isUseModernCaves()) {
            applyVanillaCarversLazily(worldGenRegion, seed, randomState, biomeManager, structureManager, chunkAccess, carving);
            long elapsed = System.nanoTime() - t0;
            carversTotalNs.addAndGet(elapsed);
            int count = carversCount.incrementAndGet();
            if (count % TIMING_LOG_INTERVAL == 0) {
                OTGLog.info(LogCategory.PERFORMANCE, "applyCarvers(modern) #{} avg={}ms", count,
                        String.format("%.1f", carversTotalNs.get() / 1_000_000.0 / count));
            }
            return;
        }

        handleOTGCarvers(seed, chunkAccess, carving);

        List<String> defaultCavesAndRavines = Arrays.asList("minecraft:cave", "minecraft:underwater_cave", "minecraft:nether_cave", "minecraft:canyon", "minecraft:underwater_canyon");

        BiomeManager biomeManager2 = biomeManager.withDifferentSource((i, j, k) -> this.getBiomeSource().getNoiseBiome(i, j, k, randomState.sampler()));
        WorldgenRandom worldgenRandom = new WorldgenRandom(new LegacyRandomSource(RandomSupport.generateUniqueSeed()));
        int i2 = 8;
        ChunkPos chunkPos = chunkAccess.getPos();
        NoiseChunk noiseChunk = chunkAccess.getOrCreateNoiseChunk(chunkAccess2 -> this.createNoiseChunk(chunkAccess2, structureManager, Blender.of(worldGenRegion), randomState));
        CarvingMask carvingMask = ((ProtoChunk) chunkAccess).getOrCreateCarvingMask(carving);
        Aquifer aquifer = noiseChunk.aquifer();
        CarvingContext carvingContext = new CarvingContext(this.horribleDelegateForCarvers, worldGenRegion.registryAccess(), chunkAccess.getHeightAccessorForGeneration(), noiseChunk, randomState, this.settings.value().surfaceRule());
        for (int j2 = -8; j2 <= 8; ++j2) {
            for (int k2 = -8; k2 <= 8; ++k2) {
                ChunkPos chunkPos2 = new ChunkPos(chunkPos.x + j2, chunkPos.z + k2);
                ChunkAccess chunkAccess22 = worldGenRegion.getChunk(chunkPos2.x, chunkPos2.z);
                BiomeGenerationSettings biomeGenerationSettings = chunkAccess22.carverBiome(() -> this.getBiomeGenerationSettings(this.getBiomeSource().getNoiseBiome(QuartPos.fromBlock(chunkPos2.getMinBlockX()), 0, QuartPos.fromBlock(chunkPos2.getMinBlockZ()), randomState.sampler())));
                Iterable<Holder<ConfiguredWorldCarver<?>>> iterable = biomeGenerationSettings.getCarvers(carving);
                int m = 0;
                for (Holder<ConfiguredWorldCarver<?>> carver : iterable) {
                    if (defaultCavesAndRavines.stream().noneMatch(
                            b -> b.equalsIgnoreCase(carver.unwrapKey().map(Objects::toString).orElse(""))
                    ) && carver.isBound())
                    {
                        ConfiguredWorldCarver<?> configuredWorldCarver = carver.value();
                        worldgenRandom.setLargeFeatureSeed(seed + (long) m, chunkPos2.x, chunkPos2.z);
                        if (configuredWorldCarver.isStartChunk(worldgenRandom)) {
                            configuredWorldCarver.carve(carvingContext, chunkAccess, biomeManager2::getBiome, worldgenRandom, aquifer, chunkPos2, carvingMask);
                        }
                        ++m;
                    }
                }
            }
        }
    }

    /**
     * Vanilla {@code NoiseBasedChunkGenerator.applyCarvers} semantics with lazy NoiseChunk/
     * CarvingContext creation. OTG-built biomes carry no carvers (BiomeFactory attaches none),
     * so for most chunks the 17x17 scan finds nothing and we skip NoiseChunk + aquifer +
     * Beardifier entirely (~27-38 ms/chunk measured with the blind delegate). Template biomes
     * (vanilla holders) keep their vanilla carvers — carver seeds and iteration order match
     * vanilla exactly ({@code setLargeFeatureSeed(seed + m)} with unconditional m increment),
     * so world output is identical to the previous delegate call.
     */
    private void applyVanillaCarversLazily(WorldGenRegion worldGenRegion, long seed, RandomState randomState,
                                           BiomeManager biomeManager, StructureManager structureManager,
                                           ChunkAccess chunkAccess, GenerationStep.Carving carving) {
        BiomeManager biomeManager2 = biomeManager.withDifferentSource((i, j, k) ->
                this.getBiomeSource().getNoiseBiome(i, j, k, randomState.sampler()));
        WorldgenRandom worldgenRandom = new WorldgenRandom(new LegacyRandomSource(RandomSupport.generateUniqueSeed()));
        ChunkPos chunkPos = chunkAccess.getPos();

        NoiseChunk noiseChunk = null;
        CarvingMask carvingMask = null;
        Aquifer aquifer = null;
        CarvingContext carvingContext = null;

        for (int j2 = -8; j2 <= 8; ++j2) {
            for (int k2 = -8; k2 <= 8; ++k2) {
                ChunkPos chunkPos2 = new ChunkPos(chunkPos.x + j2, chunkPos.z + k2);
                ChunkAccess neighborChunk = worldGenRegion.getChunk(chunkPos2.x, chunkPos2.z);
                BiomeGenerationSettings biomeGenerationSettings = neighborChunk.carverBiome(() ->
                        this.getBiomeGenerationSettings(this.getBiomeSource().getNoiseBiome(
                                QuartPos.fromBlock(chunkPos2.getMinBlockX()), 0,
                                QuartPos.fromBlock(chunkPos2.getMinBlockZ()), randomState.sampler())));
                int m = 0;
                for (Holder<ConfiguredWorldCarver<?>> carver : biomeGenerationSettings.getCarvers(carving)) {
                    // Noise caves REPLACE the vanilla default carvers. Template biomes
                    // (real vanilla biome holders) carry minecraft:cave/canyon — running
                    // them on top of noise caves double-carved those chunks and cost
                    // ~24 ms/chunk. Same filter the legacy path uses; m stays positional
                    // so surviving (modded) carvers keep their vanilla seeds.
                    if (!carver.isBound() || isDefaultCaveOrRavine(carver)) {
                        ++m;
                        continue;
                    }
                    ConfiguredWorldCarver<?> configuredWorldCarver = carver.value();
                    worldgenRandom.setLargeFeatureSeed(seed + (long) m, chunkPos2.x, chunkPos2.z);
                    if (configuredWorldCarver.isStartChunk(worldgenRandom)) {
                        if (carvingContext == null) {
                            noiseChunk = chunkAccess.getOrCreateNoiseChunk(chunkAccess2 ->
                                    this.createNoiseChunk(chunkAccess2, structureManager, Blender.of(worldGenRegion), randomState));
                            carvingMask = ((ProtoChunk) chunkAccess).getOrCreateCarvingMask(carving);
                            aquifer = noiseChunk.aquifer();
                            carvingContext = new CarvingContext(this.horribleDelegateForCarvers,
                                    worldGenRegion.registryAccess(), chunkAccess.getHeightAccessorForGeneration(),
                                    noiseChunk, randomState, this.settings.value().surfaceRule());
                        }
                        configuredWorldCarver.carve(carvingContext, chunkAccess, biomeManager2::getBiome,
                                worldgenRandom, aquifer, chunkPos2, carvingMask);
                    }
                    ++m;
                }
            }
        }
    }

    private static final List<String> DEFAULT_CAVES_AND_RAVINES = List.of(
            "minecraft:cave", "minecraft:underwater_cave", "minecraft:nether_cave",
            "minecraft:canyon", "minecraft:underwater_canyon");

    private static boolean isDefaultCaveOrRavine(Holder<ConfiguredWorldCarver<?>> carver) {
        String key = carver.unwrapKey().map(k -> k.location().toString()).orElse("");
        return DEFAULT_CAVES_AND_RAVINES.contains(key);
    }

    private void handleOTGCarvers(long seed, ChunkAccess chunkAccess, GenerationStep.Carving carving) {
        IBiome biome = this.internalGenerator.getCachedBiomeProvider().getNoiseBiome(chunkAccess.getPos().x << 2, chunkAccess.getPos().z << 2);
        BiomeGenerationSettings biomegenerationsettings = getBiomeGenerationSettings(biome);
        Iterable<Holder<ConfiguredWorldCarver<?>>> iterable = biomegenerationsettings.getCarvers(carving);

        List<String> defaultCaves = Arrays.asList("minecraft:cave", "minecraft:underwater_cave", "minecraft:nether_cave");
        boolean cavesEnabled = this.preset.getConfig().getCarverSettings().isCavesEnabled();
        if (cavesEnabled) {
            for (Holder<ConfiguredWorldCarver<?>> carver : iterable) {
                if (defaultCaves.stream().noneMatch(
                        b -> b.equalsIgnoreCase(carver.unwrapKey().map(Objects::toString).orElse(""))
                )) {
                    cavesEnabled = false;
                    break;
                }
            }
        }

        List<String> defaultRavines = Arrays.asList("minecraft:canyon", "minecraft:underwater_canyon");
        boolean ravinesEnabled = this.preset.getConfig().getCarverSettings().isRavinesEnabled();
        if (ravinesEnabled) {
            for (Holder<ConfiguredWorldCarver<?>> carver : iterable) {
                if (defaultRavines.stream().noneMatch(
                        b -> b.equalsIgnoreCase(carver.unwrapKey().map(Objects::toString).orElse(""))
                )) {
                    ravinesEnabled = false;
                    break;
                }
            }
        }

        ChunkBuffer chunkBuffer = createChunkBuffer(chunkAccess);
        CarvingMask carvingMask = ((ProtoChunk) chunkAccess).getOrCreateCarvingMask(carving);
        BitSet bitSet = BitSet.valueOf(carvingMask.toArray());
        internalGenerator.carve(
                chunkBuffer,
                seed,
                bitSet,
                cavesEnabled,
                ravinesEnabled
        );
    }

    private NoiseChunk createNoiseChunk(ChunkAccess chunkAccess, StructureManager structureManager, Blender blender, RandomState randomState) {
        return NoiseChunk.forChunk(chunkAccess, randomState, Beardifier.forStructuresInChunk(structureManager, chunkAccess.getPos()), this.settings.value(), this.globalFluidPicker, blender);
    }

    // --- Surface / spawning ---

    @Override
    public void buildSurface(WorldGenRegion worldGenRegion, StructureManager structureManager, RandomState randomState, ChunkAccess chunkAccess) {
        // surface is handled in fillFromNoise
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion worldGenRegion) {
        ChunkPos chunkPos = worldGenRegion.getCenter();
        Holder<Biome> biome = worldGenRegion.getBiome(chunkPos.getWorldPosition().atY(worldGenRegion.getMaxBuildHeight() - 1));
        WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(RandomSupport.generateUniqueSeed()));
        random.setDecorationSeed(worldGenRegion.getSeed(), chunkPos.getMinBlockX(), chunkPos.getMinBlockZ());
        NaturalSpawner.spawnMobsForChunkGeneration(worldGenRegion, biome, chunkPos, random);
    }

    // --- Noise generation ---

    @Override
    public int getGenDepth() {
        return this.settings.value().noiseSettings().height();
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(
            Blender blender, RandomState randomState, StructureManager structureManager,
            ChunkAccess chunkAccess
    ) {
        long t0 = System.nanoTime();
        ChunkCoordinate chunkCoord = ChunkCoordinate.fromChunkCoords(
                chunkAccess.getPos().x, chunkAccess.getPos().z);

        ObjectList<JigsawStructureData> structures = new ObjectArrayList<>(10);
        ChunkPos pos = chunkAccess.getPos();
        for (StructureStart start : structureManager.startsForStructure(pos,
                s -> s.terrainAdaptation() != TerrainAdjustment.NONE
                        && s.terrainAdaptation() != TerrainAdjustment.BURY)) {
            if (start.isValid()) {
                for (StructurePiece piece : start.getPieces()) {
                    if (piece instanceof PoolElementStructurePiece poolPiece
                            && poolPiece.getElement().getProjection() == StructureTemplatePool.Projection.RIGID
                            && piece.isCloseToChunk(pos, 12)) {
                        BoundingBox box = piece.getBoundingBox();
                        structures.add(new JigsawStructureData(
                                box.minX(), box.minY(), box.minZ(),
                                box.maxX(), poolPiece.getGroundLevelDelta(), box.maxZ(),
                                true, 0, 0, 0));
                    }
                }
            }
        }

        ChunkBuffer buffer = createChunkBuffer(chunkAccess);
        Random random = getRandomFromChunkCoord(chunkCoord);
        long tNoise = System.nanoTime();
        try {
            this.internalGenerator.populateNoise(otgWorldInfo, buffer,
                    buffer.getChunkCoordinate(), structures, random);
            long noiseElapsed = System.nanoTime() - tNoise;
            populateNoiseTotalNs.addAndGet(noiseElapsed);

            if (this.preset.getConfig().getCarverSettings().isUseModernCaves()) {
                long tCarve = System.nanoTime();
                carveWithNoise(blender, randomState, structureManager, chunkAccess, buffer);
                long carveElapsed = System.nanoTime() - tCarve;
                carveWithNoiseTotalNs.addAndGet(carveElapsed);
            }
        } finally {
            OTGChunkGenerator.CURRENT_CAVE_MAP.remove();
        }

        long totalElapsed = System.nanoTime() - t0;
        fillNoiseTotalNs.addAndGet(totalElapsed);
        int count = fillNoiseCount.incrementAndGet();
        if (count % TIMING_LOG_INTERVAL == 0) {
            OTGLog.info(LogCategory.PERFORMANCE, "fillFromNoise #{} avg={}ms (populateNoise={}ms carveWithNoise={}ms)", count,
                    String.format("%.1f", fillNoiseTotalNs.get() / 1_000_000.0 / count),
                    String.format("%.1f", populateNoiseTotalNs.get() / 1_000_000.0 / count),
                    String.format("%.1f", carveWithNoiseTotalNs.get() / 1_000_000.0 / count));
        }

        return CompletableFuture.completedFuture(chunkAccess);
    }

    // --- Noise carving ---

    private void carveWithNoise(Blender blender, RandomState randomState, StructureManager structureManager, ChunkAccess targetChunk, ChunkBuffer terrainBuffer) {
        initCaveComponents(randomState);

        DensityFunction caveDensity = this.caveRandomState.router().finalDensity();

        NoiseSettings noiseSettings = this.settings.value().noiseSettings();
        int minY = noiseSettings.minY();
        int maxY = minY + noiseSettings.height();

        BlockPos.MutableBlockPos blockPos = new BlockPos.MutableBlockPos();
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState debugCheese = Blocks.YELLOW_STAINED_GLASS.defaultBlockState();
        BlockState debugSpaghetti = Blocks.RED_STAINED_GLASS.defaultBlockState();
        BlockState debugNoodle = Blocks.BLUE_STAINED_GLASS.defaultBlockState();
        boolean debugCaveTypes = this.preset.getConfig().getNoiseCaveSettings().isDebugCaveTypes();

        OTGNoiseRouterData.CaveDensityComponents components = this.caveComponents;

        int carved = 0, skippedSolid = 0;
        boolean firstChunk = targetChunk.getPos().x == 0 && targetChunk.getPos().z == 0;

        int minX = targetChunk.getPos().getMinBlockX();
        int minZ = targetChunk.getPos().getMinBlockZ();

        NoiseCaveSettings caveCfg = this.preset.getConfig().getNoiseCaveSettings();
        int suppressionRange = caveCfg.getSurfaceSuppressionRange();
        double breakthroughChance = caveCfg.getSurfaceBreakthroughChance();
        double breakthroughScale = caveCfg.getSurfaceBreakthroughScale();
        double breakthroughThreshold = 1.0 - 2.0 * breakthroughChance;

        // Cave density is evaluated on a 4x8x4 cell-corner lattice and trilinearly
        // interpolated per block — same strategy vanilla NoiseChunk uses. Evaluating the
        // full density tree per block (~90k evals/chunk) measured at 79 ms/chunk; cell
        // corners bring that down to ~1.2k evals (~1-3 ms/chunk). The cacheOnce/interpolated
        // markers in the router are inert with SinglePointContext, so per-block evaluation
        // recomputed every noise octave from scratch on every block.
        final int cellXZ = 4;
        final int cellY = 8;
        final int cornersXZ = Constants.CHUNK_SIZE / cellXZ + 1;
        final int cellsY = (maxY - minY + cellY - 1) / cellY;
        final int cornersY = cellsY + 1;

        double[][][] corners = new double[cornersXZ][cornersY][cornersXZ];
        double[][][] cornersSpaghetti = null;
        double[][][] cornersCheese = null;
        double[][][] cornersNoodle = null;
        boolean debugComponents = debugCaveTypes && components != null;
        if (debugComponents) {
            cornersSpaghetti = new double[cornersXZ][cornersY][cornersXZ];
            cornersCheese = new double[cornersXZ][cornersY][cornersXZ];
            cornersNoodle = new double[cornersXZ][cornersY][cornersXZ];
        }

        for (int cx = 0; cx < cornersXZ; cx++) {
            int cornerX = minX + cx * cellXZ;
            for (int cz = 0; cz < cornersXZ; cz++) {
                int cornerZ = minZ + cz * cellXZ;
                for (int cy = 0; cy < cornersY; cy++) {
                    int cornerY = minY + cy * cellY;
                    DensityFunction.SinglePointContext ctx =
                            new DensityFunction.SinglePointContext(cornerX, cornerY, cornerZ);
                    corners[cx][cy][cz] = caveDensity.compute(ctx);
                    if (debugComponents) {
                        cornersSpaghetti[cx][cy][cz] = components.spaghetti().compute(ctx);
                        cornersCheese[cx][cy][cz] = components.cheese().compute(ctx);
                        cornersNoodle[cx][cy][cz] = components.noodle().compute(ctx);
                    }
                }
            }
        }

        for (int x = 0; x < Constants.CHUNK_SIZE; x++) {
            int worldX = minX + x;
            for (int z = 0; z < Constants.CHUNK_SIZE; z++) {
                int worldZ = minZ + z;

                int surfaceY = terrainBuffer.getHighestBlockForColumn(x, z);

                boolean isBreakthroughColumn = false;
                if (breakthroughChance > 0.0) {
                    double bNoise = this.breakthroughNoise.getValue(
                            worldX / breakthroughScale, worldZ / breakthroughScale);
                    isBreakthroughColumn = bNoise >= breakthroughThreshold;
                }

                for (int worldY = minY; worldY < maxY; worldY++) {
                    blockPos.set(worldX, worldY, worldZ);
                    BlockState existing = targetChunk.getBlockState(blockPos);
                    if (existing.isAir() || existing.liquid() || existing.is(Blocks.BEDROCK)) continue;

                    double density = trilerp(corners, x, worldY - minY, z, cellXZ, cellY);

                    if (!isBreakthroughColumn && surfaceY > 0) {
                        int distFromSurface = surfaceY - worldY;
                        if (distFromSurface >= 0 && distFromSurface < suppressionRange) {
                            double t = 1.0 - (double) distFromSurface / suppressionRange;
                            double suppressionFactor = 0.5 * t * t;
                            density += suppressionFactor;
                        }
                    }

                    if (density <= 0) {
                        if (debugComponents) {
                            double spaghettiD = trilerp(cornersSpaghetti, x, worldY - minY, z, cellXZ, cellY);
                            double cheeseD = trilerp(cornersCheese, x, worldY - minY, z, cellXZ, cellY);
                            double noodleD = trilerp(cornersNoodle, x, worldY - minY, z, cellXZ, cellY);

                            BlockState debugBlock;
                            if (cheeseD <= spaghettiD && cheeseD <= noodleD) {
                                debugBlock = debugCheese;
                            } else if (spaghettiD <= cheeseD && spaghettiD <= noodleD) {
                                debugBlock = debugSpaghetti;
                            } else {
                                debugBlock = debugNoodle;
                            }
                            targetChunk.setBlockState(blockPos, debugBlock, false);
                        } else {
                            targetChunk.setBlockState(blockPos, air, false);
                        }
                        carved++;
                    } else {
                        skippedSolid++;
                    }
                }
            }
        }

        if (firstChunk) {
            OTGLog.info(LogCategory.PERFORMANCE, "carveWithNoise chunk(0,0): carved={} skippedSolid={}", carved, skippedSolid);
        }
    }

    /**
     * Trilinear interpolation over a cell-corner density grid. lx/ly/lz are block offsets
     * from the chunk origin (ly measured from minY).
     */
    private static double trilerp(double[][][] corners, int lx, int ly, int lz, int cellXZ, int cellY) {
        int cx = lx / cellXZ;
        int cy = ly / cellY;
        int cz = lz / cellXZ;
        double tx = (lx - cx * cellXZ) / (double) cellXZ;
        double ty = (ly - cy * cellY) / (double) cellY;
        double tz = (lz - cz * cellXZ) / (double) cellXZ;
        return Mth.lerp3(tx, ty, tz,
                corners[cx][cy][cz],         corners[cx + 1][cy][cz],
                corners[cx][cy + 1][cz],     corners[cx + 1][cy + 1][cz],
                corners[cx][cy][cz + 1],     corners[cx + 1][cy][cz + 1],
                corners[cx][cy + 1][cz + 1], corners[cx + 1][cy + 1][cz + 1]);
    }

    // --- Utility ---

    public @NotNull Random getRandomFromChunkCoord(ChunkCoordinate chunkCoord) {
        return new Random(this.seed + chunkCoord.getChunkX() * 341873128712L + chunkCoord.getChunkZ() * 132897987541L);
    }

    @Override
    public int getSeaLevel() {
        return settings.value().seaLevel();
    }

    @Override
    public int getMinY() {
        return settings.value().noiseSettings().minY();
    }

    @Override
    public int getBaseHeight(int i, int j, Heightmap.Types types, LevelHeightAccessor levelHeightAccessor, RandomState randomState) {
        return this.sampleHeightmap(i, j, null, types.isOpaque());
    }

    @Override
    public NoiseColumn getBaseColumn(int i, int j, LevelHeightAccessor levelHeightAccessor, RandomState randomState) {
        BlockState[] blockStates = new BlockState[levelHeightAccessor.getHeight()];
        this.sampleHeightmap(i, j, blockStates, null);
        return new NoiseColumn(levelHeightAccessor.getMinBuildHeight(), blockStates);
    }

    @Override
    public void addDebugScreenInfo(List<String> list, RandomState randomState, BlockPos blockPos) {
        IBiome biome = this.internalGenerator.getCachedBiomeProvider().getNoiseBiome(blockPos.getX(), blockPos.getZ());
        list.add("Preset: " + this.preset.getFolderName());
        list.add("Biome: " + biome.getBiomeSettings().getIdentitySettings().getDisplayName());
        list.add("OTG Debug { " + otgWorldInfo.toString() + " }");
    }

    // --- Heightmap sampling ---

    /** Quart-resolution surface height estimates for underground biome resolution. */
    private final com.pg85.otg.util.ThreadSafeLRUCache<Long, Integer> surfaceEstimateCache =
            new com.pg85.otg.util.ThreadSafeLRUCache<>(16384);

    /**
     * Approximate surface height (highest non-air, so ocean surface over water) from pure
     * noise-column math — no chunk generation, no locks beyond the Caffeine cache. Cached at
     * quart resolution: underground biome placement samples quart-aligned coords, so aligned
     * callers always see identical values and chunk borders stay seam-consistent.
     */
    private int estimateSurfaceHeightCached(int worldX, int worldZ) {
        int quartX = worldX >> 2;
        int quartZ = worldZ >> 2;
        long key = ((long) quartX << 32) ^ (quartZ & 0xFFFFFFFFL);
        return this.surfaceEstimateCache.computeIfAbsent(key, k ->
                sampleHeightmap(quartX << 2, quartZ << 2, null, state -> !state.isAir()));
    }

    private int sampleHeightmap(int x, int z, @Nullable BlockState[] blockStates, @Nullable Predicate<BlockState> predicate) {
        int minY = this.settings.value().noiseSettings().minY();
        int xStart = Math.floorDiv(x, 4);
        int zStart = Math.floorDiv(z, 4);
        int xProgress = Math.floorMod(x, 4);
        int zProgress = Math.floorMod(z, 4);
        double xLerp = (double) xProgress / 4.0;
        double zLerp = (double) zProgress / 4.0;
        double[][] noiseData = new double[4][this.internalGenerator.getNoiseSizeY() + 1];

        for (int i = 0; i < noiseData.length; i++) {
            noiseData[i] = new double[this.internalGenerator.getNoiseSizeY() + 1];
        }

        this.internalGenerator.getNoiseColumn(noiseData[0], xStart, zStart);
        this.internalGenerator.getNoiseColumn(noiseData[1], xStart, zStart + 1);
        this.internalGenerator.getNoiseColumn(noiseData[2], xStart + 1, zStart);
        this.internalGenerator.getNoiseColumn(noiseData[3], xStart + 1, zStart + 1);

        for (int noiseY = this.internalGenerator.getNoiseSizeY() - 1; noiseY >= 0; --noiseY) {
            double val000 = noiseData[0][noiseY];
            double val010 = noiseData[1][noiseY];
            double val100 = noiseData[2][noiseY];
            double val110 = noiseData[3][noiseY];
            double val001 = noiseData[0][noiseY + 1];
            double val011 = noiseData[1][noiseY + 1];
            double val101 = noiseData[2][noiseY + 1];
            double val111 = noiseData[3][noiseY + 1];

            for (int pieceY = 7; pieceY >= 0; --pieceY) {
                double yLerp = (double) pieceY / 8.0;
                double density = MathHelper.lerp3(xLerp, yLerp, zLerp, val000, val100, val010, val110, val001, val101, val011, val111);

                int y = (noiseY * 8) + pieceY;

                BlockState state = this.getBlockState(density, y + minY);
                if (blockStates != null) {
                    blockStates[y] = state;
                }

                if (predicate != null && predicate.test(state)) {
                    return y + minY + 1;
                }
            }
        }

        return minY;
    }

    private BlockState getBlockState(double density, int y) {
        if (density > 0.0D) {
            return this.settings.value().defaultBlock();
        } else if (y < this.getSeaLevel()) {
            return this.settings.value().defaultFluid();
        } else {
            return Blocks.AIR.defaultBlockState();
        }
    }

    // --- Portal settings ---

    public String getPortalColor() {
        if (preset != null && preset.getConfig() != null) {
            return preset.getConfig().getPortalSettings().getPortalColor();
        }
        return "default";
    }

    public List<LocalMaterialData> getPortalBlocks() {
        if (preset != null && preset.getConfig() != null) {
            return preset.getConfig().getPortalSettings().getPortalBlocks();
        }
        return new ArrayList<>();
    }

    public String getPortalMob() {
        if (preset != null && preset.getConfig() != null) {
            return preset.getConfig().getPortalSettings().getPortalMob();
        }
        return "minecraft:zombified_piglin";
    }

    public String getPortalIgnitionSource() {
        if (preset != null && preset.getConfig() != null) {
            return preset.getConfig().getPortalSettings().getPortalIgnitionSource();
        }
        return "minecraft:flint_and_steel";
    }

    public int getPortalMinWidth() {
        if (preset != null && preset.getConfig() != null) {
            return preset.getConfig().getPortalSettings().getPortalMinWidth();
        }
        return 2;
    }

    public int getPortalMaxWidth() {
        if (preset != null && preset.getConfig() != null) {
            return preset.getConfig().getPortalSettings().getPortalMaxWidth();
        }
        return 21;
    }

    public int getPortalMinHeight() {
        if (preset != null && preset.getConfig() != null) {
            return preset.getConfig().getPortalSettings().getPortalMinHeight();
        }
        return 3;
    }

    public int getPortalMaxHeight() {
        if (preset != null && preset.getConfig() != null) {
            return preset.getConfig().getPortalSettings().getPortalMaxHeight();
        }
        return 21;
    }
}
