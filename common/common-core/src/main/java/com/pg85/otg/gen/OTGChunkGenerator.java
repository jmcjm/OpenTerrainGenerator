package com.pg85.otg.gen;

import com.pg85.otg.OTG;
import com.pg85.otg.config.settings.biome.BiomeSettings;
import com.pg85.otg.config.settings.biome.SurfaceSettings;
import com.pg85.otg.config.settings.biome.BiomeTerrainSettings;
import com.pg85.otg.config.settings.preset.TerrainSettings;
import com.pg85.otg.constants.Constants;
import com.pg85.otg.gen.biome.CachedBiomeProvider;
import com.pg85.otg.gen.carver.Carver;
import com.pg85.otg.gen.carver.CaveCarver;
import com.pg85.otg.gen.carver.RavineCarver;
import com.pg85.otg.gen.noise.OctavePerlinNoiseSampler;
import com.pg85.otg.gen.noise.PerlinNoiseSampler;
import com.pg85.otg.gen.noise.TerrainNoiseComputer;
import com.pg85.otg.gen.noise.legacy.NoiseGeneratorPerlinMesaBlocks;
import com.pg85.otg.util.materials.LocalMaterialData;
import com.pg85.otg.util.materials.LocalMaterials;
import com.pg85.otg.interfaces.*;
import com.pg85.otg.presets.DimensionPreset;
import com.pg85.otg.util.ChunkCoordinate;
import com.pg85.otg.util.gen.*;
import com.pg85.otg.util.helpers.MathHelper;
import com.pg85.otg.util.OTGLog;
import com.pg85.otg.util.logging.LogCategory;
import com.pg85.otg.util.logging.LogLevel;
import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectListIterator;
import lombok.Getter;

import java.util.Arrays;
import java.util.BitSet;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Generates the base terrain, sets stone/ground/surface blocks and does SurfaceAndGroundControl, generates caves and canyons.
 */
//@SuppressWarnings("deprecation")
public class OTGChunkGenerator implements ISurfaceGeneratorNoiseProvider {
    // "It's a number that made the worldgen look good!" - Dinnerbone 2020
    private static final double WORLD_GEN_CONSTANT = 684.412;

    // Reference Y sections - controls base terrain height calculation
    // Value 36 gives terrain at ~Y=85 for plains (about 20 blocks above sea level)
    // Original 256-world value was 33, but 1.18+ worlds with minY=-64 need slightly higher
    private static final float REFERENCE_Y_SECTIONS = 33.5f;

    private static final float[] BIOME_WEIGHT_TABLE = make(
            new float[65 * 65], (array) -> {
                for (int x = -32; x <= 32; ++x) {
                    for (int z = -32; z <= 32; ++z) {
                        float f = 10.0F / MathHelper.sqrt((float) (x * x + z * z) + 0.2F);
                        array[x + 32 + (z + 32) * 65] = f;
                    }
                }
            }
    );

    private static final float[] NOISE_WEIGHT_TABLE = make(
            new float[24 * 24 * 24], (array) -> {
                for (int z = 0; z < 24; ++z) {
                    for (int x = 0; x < 24; ++x) {
                        for (int y = 0; y < 24; ++y) {
                            array[z * 24 * 24 + x * 24 + y] = (float) calculateNoiseWeight(x - 12, y - 12, z - 12);
                        }
                    }
                }
            }
    );

    // TODO: ThreadLocal is used mostly as a crutch here, ideally these classes wouldn't maintain state.
    // ThreadLocal may have some overhead for the gets/sets, even when used on a single thread.
    // Some of these classes may not be thread-safe (tho testing seems ok), need to check all the internal state.

    // Set once in setSeed() before worker threads start.
    // Thread-safety: happens-before established by worker thread creation in queueChunksForWorkerThreads().
    private OctavePerlinNoiseSampler interpolationNoise;     // Volatility noise
    private OctavePerlinNoiseSampler lowerInterpolatedNoise; // Volatility1 noise
    private OctavePerlinNoiseSampler upperInterpolatedNoise; // Volatility2 noise
    private OctavePerlinNoiseSampler depthNoise;
    private PerlinNoiseSampler deepslateNoise; // noisy stone->deepslate boundary around Y=0

    private final DimensionPreset preset;
    private final OTGWorldInfo otgWorldInfo;
    private long seed;
    private final CachedBiomeProvider cachedBiomeProvider;
    private final com.pg85.otg.interfaces.ILayerSource undergroundLayerSource;
    private final IBiome[] undergroundBiomesById;
    private volatile java.util.function.ToIntBiFunction<Integer, Integer> surfaceHeightEstimator;
    private volatile com.pg85.otg.gen.biome.UndergroundBiomeResolver undergroundResolver;

    /**
     * Set while generating a throwaway "shadow" chunk for surface-height estimation.
     * Such chunks only need terrain shape, so populateNoise skips the underground biome
     * map — otherwise building the map calls the height estimator, which generates another
     * shadow chunk, which builds another map... recursing infinitely (StackOverflowError).
     */
    public static final ThreadLocal<Boolean> GENERATING_SHADOW_CHUNK = ThreadLocal.withInitial(() -> Boolean.FALSE);

    /** Current chunk's underground biome map, bound during fillFromNoise so the cave-density graph can read per-region cave scales. */
    public static final ThreadLocal<com.pg85.otg.interfaces.IUndergroundBiomeMap> CURRENT_CAVE_MAP = new ThreadLocal<>();

    private static final int NOISE_SIZE_X = 4;
    @Getter
    private final int noiseSizeY;
    private static final int NOISE_SIZE_Z = 4;

    private final ThreadLocal<NoiseCache> noiseCache;
    private final ThreadLocal<double[][][]> noiseDataBuffer;
    private NoiseGeneratorPerlinMesaBlocks biomeBlocksNoiseGen;
    // Carvers
    private final Carver caves;
    private final Carver ravines;
    // Biome blocks noise
    // TODO: Use new noise?
    private final ThreadLocal<double[]> biomeBlocksNoise =
            ThreadLocal.withInitial(() -> new double[Constants.CHUNK_SIZE * Constants.CHUNK_SIZE]);
    private final ThreadLocal<BiomeBlocksNoiseCache> biomeBlocksNoiseCache =
            ThreadLocal.withInitial(BiomeBlocksNoiseCache::new);

    // Performance tracking
    private static final int PERF_LOG_INTERVAL = 100;
    private final AtomicInteger chunksGenerated = new AtomicInteger(0);
    private final AtomicLong totalBiomeTimeMs = new AtomicLong(0);
    private final AtomicLong totalNoiseTimeMs = new AtomicLong(0);
    private final AtomicLong totalBlockPlaceTimeMs = new AtomicLong(0);
    private final AtomicLong totalSurfaceTimeMs = new AtomicLong(0);
    private final AtomicLong totalCarveTimeMs = new AtomicLong(0);

    /**
     * Holder for primitive values to avoid ThreadLocal boxing overhead.
     */
    private static class BiomeBlocksNoiseCache {
        int lastX = Integer.MAX_VALUE;
        int lastZ = Integer.MAX_VALUE;
        double lastNoise = 0.0;
        final double[] buffer = new double[1];
    }

    public OTGChunkGenerator(
            DimensionPreset preset,
            ILayerSource biomeProvider,
            IBiome[] biomesById,
            OTGWorldInfo otgWorldInfo
    ) {
        this.preset = preset;
        this.otgWorldInfo = otgWorldInfo;
        this.cachedBiomeProvider = new CachedBiomeProvider(biomeProvider, biomesById);
        this.undergroundLayerSource = biomeProvider;
        this.undergroundBiomesById = biomesById;

        this.noiseSizeY = otgWorldInfo.getHeight() / Constants.PIECE_Y_SIZE;
        this.noiseCache = ThreadLocal.withInitial(() -> new NoiseCache(128, this.noiseSizeY + 1));
        // Pre-allocate noise data buffer to avoid allocation per chunk (was ~4KB per chunk)
        this.noiseDataBuffer = ThreadLocal.withInitial(() -> {
            double[][][] buffer = new double[2][NOISE_SIZE_Z + 1][];
            for (int z = 0; z < NOISE_SIZE_Z + 1; z++) {
                buffer[0][z] = new double[this.noiseSizeY + 1];
                buffer[1][z] = new double[this.noiseSizeY + 1];
            }
            return buffer;
        });

        this.caves = new CaveCarver(preset.getConfig());
        this.ravines = new RavineCarver(preset.getConfig());

    }

    public void setSeed(long seed) {
        this.seed = seed;
        // Force the lazy underground resolver to rebuild with the new seed (keeps it
        // consistent with the biome source's resolver, which also rebuilds on setSeed).
        this.undergroundResolver = null;
        this.cachedBiomeProvider.setSeed(seed);
        // Setup noises
        Random random = new Random(seed);

        var samplers = TerrainNoiseComputer.createNoiseSamplers(random);
        this.interpolationNoise = samplers.interpolation();
        this.lowerInterpolatedNoise = samplers.lower();
        this.upperInterpolatedNoise = samplers.upper();
        this.depthNoise = samplers.depth();
        this.biomeBlocksNoiseGen = new NoiseGeneratorPerlinMesaBlocks(random, 4);
        // Separate seed so it doesn't perturb the terrain sampler sequence above.
        this.deepslateNoise = new PerlinNoiseSampler(new Random(seed ^ 0x6CB9D7E3A155EC53L));
    }

    private static final int DEEPSLATE_BAND = 8;
    private static final double DEEPSLATE_NOISE_SCALE = 1.0 / 16.0;

    /**
     * Picks the terrain fill block for a column position, applying the biome's ReplaceBlocks.
     * Custom StoneBlock biomes use their block everywhere. Default-stone biomes transition from
     * stone to deepslate across a noisy band around Y=0 (instead of a flat cut at exactly Y=0).
     */
    private LocalMaterialData stoneOrDeepslate(SurfaceSettings ss, int x, int y, int z) {
        LocalMaterialData base;
        if (ss.hasCustomStoneBlock()) {
            base = ss.getStoneBlock();
        } else if (y > DEEPSLATE_BAND) {
            base = ss.getStoneBlock();
        } else if (y < -DEEPSLATE_BAND) {
            base = LocalMaterials.DEEPSLATE;
        } else {
            double n = deepslateNoise.sample(
                    x * DEEPSLATE_NOISE_SCALE, y * DEEPSLATE_NOISE_SCALE, z * DEEPSLATE_NOISE_SCALE, 0.0, 0.0);
            double bias = -(double) y / DEEPSLATE_BAND; // +1 at y=-band (deepslate), -1 at y=+band (stone)
            base = (n < bias) ? LocalMaterials.DEEPSLATE : ss.getStoneBlock();
        }
        return ss.applyStoneReplacement(base, y);
    }

    public void setSurfaceHeightEstimator(java.util.function.ToIntBiFunction<Integer, Integer> estimator) {
        this.surfaceHeightEstimator = estimator;
    }

    private com.pg85.otg.gen.biome.UndergroundBiomeResolver undergroundResolver() {
        com.pg85.otg.gen.biome.UndergroundBiomeResolver r = this.undergroundResolver;
        if (r == null) {
            synchronized (this) {
                r = this.undergroundResolver;
                if (r == null) {
                    r = new com.pg85.otg.gen.biome.UndergroundBiomeResolver(this.undergroundBiomesById, this.seed);
                    this.undergroundResolver = r;
                }
            }
        }
        return r;
    }

    public com.pg85.otg.gen.biome.UndergroundBiomeMap buildUndergroundBiomeMap(
            com.pg85.otg.util.ChunkCoordinate chunkCoord,
            com.pg85.otg.util.gen.OTGWorldInfo worldInfo) {
        return com.pg85.otg.gen.biome.UndergroundBiomeMap.build(
                undergroundResolver(), this.undergroundLayerSource.getSampler(),
                this.surfaceHeightEstimator, chunkCoord, worldInfo, this.undergroundBiomesById);
    }

    /** @return true if any underground biome defines a non-1.0 cave-type scale (else carving uses constant scaling). */
    public boolean hasUndergroundCaveScaling() {
        if (this.undergroundBiomesById == null) return false;
        for (IBiome b : this.undergroundBiomesById) {
            if (b == null) continue;
            com.pg85.otg.config.settings.biome.UndergroundBiomeSettings ubs = b.getBiomeSettings().getUndergroundSettings();
            if (ubs == null || !ubs.isUndergroundBiome()) continue;
            for (float s : ubs.getCaveScales()) {
                if (s != 1.0f) return true;
            }
        }
        return false;
    }

    public ICachedBiomeProvider getCachedBiomeProvider() {
        return this.cachedBiomeProvider;
    }

    private static <T> T make(T object, Consumer<T> consumer) {
        consumer.accept(object);
        return object;
    }

    private static double getNoiseWeight(int x, int y, int z) {
        int arrayX = x + 12;
        int arrayZ = y + 12;
        int arrayY = z + 12;
        if (arrayX >= 0 && arrayX < 24) {
            if (arrayZ >= 0 && arrayZ < 24) {
                return arrayY >= 0 && arrayY < 24 ?
                        (double) NOISE_WEIGHT_TABLE[arrayY * 24 * 24 + arrayX * 24 + arrayZ] :
                        0.0D;
            } else {
                return 0.0D;
            }
        } else {
            return 0.0D;
        }
    }

    private static double calculateNoiseWeight(int x, int y, int z) {
        // Make a circle cutout
        double sqrXZ = x * x + z * z;

        // Offset the y to prevent 0
        double offsetY = (double) y + 0.5D;

        // Square the y to make a
        double sqrY = offsetY * offsetY;

        // Get the density of the current position
        double density = Math.pow(Math.E, -(sqrY / 16.0D + sqrXZ / 16.0D));

        // Controls the density (bottom is solid, top is air)
        double yOffset = -offsetY * MathHelper.fastInverseSqrt(sqrY / 2.0D + sqrXZ / 2.0D) / 2.0D;

        // Multiply the density by the y offset to get the final density
        return yOffset * density;
    }

    private double sampleNoise(
            int x, int y, int z,
            double horizontalScale, double verticalScale,
            double horizontalStretch, double verticalStretch,
            double volatility1, double volatility2,
            double volatilityWeight1, double volatilityWeight2
    ) {
        return TerrainNoiseComputer.sampleNoise(
                x, y, z,
                horizontalScale, verticalScale,
                horizontalStretch, verticalStretch,
                volatility1, volatility2,
                volatilityWeight1, volatilityWeight2,
                this.interpolationNoise, this.lowerInterpolatedNoise, this.upperInterpolatedNoise
        );
    }

    private double getInterpolationNoise(int x, int y, int z, double horizontalStretch, double verticalStretch) {
        return TerrainNoiseComputer.getInterpolationNoise(this.interpolationNoise, x, y, z, horizontalStretch, verticalStretch);
    }

    private double getInterpolatedNoise(OctavePerlinNoiseSampler sampler, int x, int y, int z, double horizontalScale, double verticalScale) {
        return TerrainNoiseComputer.getInterpolatedNoise(sampler, x, y, z, horizontalScale, verticalScale);
    }

    private double getExtraHeightAt(int x, int z, double maxAverageDepth, double maxAverageHeight) {
        return TerrainNoiseComputer.getExtraHeightAt(this.depthNoise, x, z, maxAverageDepth, maxAverageHeight);
    }

    public void getNoiseColumn(double[] buffer, int x, int z) {
        // TODO: check only for edges
        this.noiseCache.get().get(buffer, x, z);
    }

    private void generateNoiseColumn(double[] noiseColumn, int noiseX, int noiseZ) {
        BiomeSettings center = this.cachedBiomeProvider.getNoiseBiomeConfig(noiseX, noiseZ, true);

        float height = 0; // depth
        float volatility = 0; // scale
        double volatility1 = 0;
        double volatility2 = 0;
        double horizontalFracture = 0;
        double verticalFracture = 0;
        double volatilityWeight1 = 0;
        double volatilityWeight2 = 0;
        double maxAverageDepth = 0;
        double maxAverageHeight = 0;
        double[] chc = new double[this.noiseSizeY + 1];
        float weight = 0;
        BiomeTerrainSettings centerTerrainSettings = center.getTerrainSettings();
        float centerHeight = centerTerrainSettings.getBiomeHeight();
        int smoothRadius = centerTerrainSettings.getSmoothRadius();
        int chcSmoothRadius = centerTerrainSettings.getCHCSmoothRadius();
        int largestRadius = Math.max(smoothRadius, chcSmoothRadius);
        int areaSize = largestRadius * 2 + 1;
        BiomeSettings[] biomes = this.cachedBiomeProvider.getNoiseBiomeConfigsForRegion(
                noiseX - largestRadius,
                noiseZ - largestRadius,
                areaSize
        );
        BiomeSettings biome;
        BiomeTerrainSettings biomeTerrainSettings;
        TerrainSettings terrainSettings = this.preset.getConfig().getTerrainSettings();
        int worldHeightCap = otgWorldInfo.getHeight();
        float heightAt;
        float weightAt;
        int cacheX;
        int cacheZ;
        for (int x1 = -smoothRadius; x1 <= smoothRadius; ++x1) {
            cacheX = x1 + largestRadius;
            for (int z1 = -smoothRadius; z1 <= smoothRadius; ++z1) {
                cacheZ = z1 + largestRadius;
                int biomeIndex = cacheX * areaSize + cacheZ;
                if (biomeIndex < 0 || biomeIndex >= biomes.length) {
                    OTGLog.error("Invalid biome index {} (max={}) at noiseX={}, noiseZ={}, x1={}, z1={}, areaSize={}",
                        biomeIndex, biomes.length, noiseX, noiseZ, x1, z1, areaSize);
                    continue;
                }
                biome = biomes[biomeIndex];
                if (biome == null) {
                    OTGLog.error("Null biome at noiseX={}, noiseZ={}, index={}", noiseX, noiseZ, biomeIndex);
                    continue;
                }
                biomeTerrainSettings = biome.getTerrainSettings();
                heightAt = biomeTerrainSettings.getBiomeHeight();
                weightAt = BIOME_WEIGHT_TABLE[x1 + 32 + (z1 + 32) * 65] / (heightAt + 2.0F);
                weightAt = Math.abs(weightAt); // This is required to prevent seams when height goes below -2
                // Vanilla halves the weight of neighbors that are higher than the center biome,
                // creating softer low-to-high transitions and preventing steep cliffs at biome borders
                if (heightAt > centerHeight) {
                    weightAt /= 2.0F;
                }

                weight += weightAt;

                height += heightAt * weightAt;
                volatility += biomeTerrainSettings.getBiomeVolatility() * weightAt;
                volatility1 += biomeTerrainSettings.getVolatility1() * weightAt;
                volatility2 += biomeTerrainSettings.getVolatility2() * weightAt;
                horizontalFracture += terrainSettings.getFractureHorizontal() * weightAt;
                verticalFracture += terrainSettings.getFractureVertical() * weightAt;
                volatilityWeight1 += biomeTerrainSettings.getVolatilityWeight1() * weightAt;
                volatilityWeight2 += biomeTerrainSettings.getVolatilityWeight2() * weightAt;
                maxAverageDepth += biomeTerrainSettings.getValleyFactor() * weightAt;
                maxAverageHeight += biomeTerrainSettings.getPeakFactor() * weightAt;
            }
        }

        // CHC Smoothing
        double chcWeight = 0;
        for (int x1 = -chcSmoothRadius; x1 <= chcSmoothRadius; ++x1) {
            cacheX = x1 + largestRadius;
            for (int z1 = -chcSmoothRadius; z1 <= chcSmoothRadius; ++z1) {
                cacheZ = z1 + largestRadius;
                int chcBiomeIndex = cacheX * areaSize + cacheZ;
                if (chcBiomeIndex < 0 || chcBiomeIndex >= biomes.length || biomes[chcBiomeIndex] == null) {
                    continue;
                }
                biome = biomes[chcBiomeIndex];

                heightAt = biome.getTerrainSettings().getBiomeHeight();
                weightAt = BIOME_WEIGHT_TABLE[x1 + 32 + (z1 + 32) * 65] / (heightAt + 2.0F);
                weightAt = Math.abs(weightAt);

                chcWeight += weightAt;

                for (int y = 0; y < this.noiseSizeY + 1; y++) {
                    chc[y] += biome.getCHCData(y) * weightAt;
                }
            }
        }

        // Normalize biome data
        height /= weight;
        volatility /= weight;
        volatility1 /= weight;
        volatility2 /= weight;
        horizontalFracture /= weight;
        verticalFracture /= weight;
        volatilityWeight1 /= weight;
        volatilityWeight2 /= weight;
        maxAverageDepth /= weight;
        maxAverageHeight /= weight;

        // Normalize CHC
        for (int y = 0; y < this.noiseSizeY + 1; y++) {
            chc[y] /= chcWeight;
        }

        // Vary the height with more noise
        float extraHeight = (float) (getExtraHeightAt(noiseX, noiseZ, maxAverageDepth, maxAverageHeight) * 0.2);

        // Do some math on volatility and height
        volatility = volatility * 0.9f + 0.1f;
        height = (height * 4.0F - 1.0F) / 8.0F;

        // Factor in y sections (use reference from old 256-block world for consistent terrain height)
        height = REFERENCE_Y_SECTIONS * (2.0f + height + extraHeight) / 4.0f;

        double falloff;
        double horizontalScale;
        double verticalScale;
        double noise;
        for (int y = 0; y <= this.noiseSizeY; ++y) {
            // Calculate falloff - controls how quickly terrain density drops with height difference
            // Using fixed coefficient 6.0 (was 12*128/worldHeight which varied with world size)
            falloff = (height - y) * 6.0D / volatility;
            if (falloff > 0.0) {
                falloff *= 4.0;
            }

            horizontalScale = WORLD_GEN_CONSTANT * horizontalFracture;
            verticalScale = WORLD_GEN_CONSTANT * verticalFracture;
            noise = sampleNoise(
                    noiseX,
                    y,
                    noiseZ,
                    horizontalScale,
                    verticalScale,
                    horizontalScale / 80,
                    verticalScale / 160,
                    volatility1,
                    volatility2,
                    volatilityWeight1,
                    volatilityWeight2
            );

            if (!center.getTerrainSettings().isDisableBiomeHeight()) {
                // Add the falloff at this height
                noise += falloff;

                // TODO: get rid of this - anti-floating terrain should be solved via proper noise settings
                //  and biome .bc configuration, not a hardcoded penalty
                double heightDiff = y - height;
                if (heightDiff > 4) {
                    double floatingPenalty = (heightDiff - 4) * (heightDiff - 4) * 0.5;
                    noise -= floatingPenalty;
                }

                // Reduce the last 4 layers (dynamically calculated based on world height)
                // For 256 world (32 layers): y > 28, for 384 world (48 layers): y > 44
                int reductionStartY = this.noiseSizeY - 4;
                if (y > reductionStartY) {
                    noise = MathHelper.clampedLerp(noise, -10, ((double) y - reductionStartY) / 4.0);
                }
            }

            // Add chc data
            noise += chc[y];

            // Store value
            noiseColumn[y] = noise;

            // DEBUG: Log noise values for edge chunks at specific Y levels
//            if (debugThisColumn && (y == 0 || y == 8 || y == 16 || y == 24 || y == 32)) {
//                System.err.println(String.format("  y=%d: noise=%.2f, falloff=%.2f", y, noise, falloff));
//            }
        }
    }

    // Surface / ground / stone blocks / SAGC

    public void populateNoise(
            OTGWorldInfo worldHeight,
            ChunkBuffer buffer,
            ChunkCoordinate chunkCoord,
            ObjectList<JigsawStructureData> structures,
            Random random
    ) {
        ILogger logger = OTG.getEngine().getLogger();

        ObjectListIterator<JigsawStructureData> structureIterator = structures.iterator();

        long startTime = System.currentTimeMillis();

        // Fill waterLevel array, used when placing stone/ground/surface blocks.
        // This 256 is a combined x/z size, not y.
        int[] waterLevel = new int[Constants.OTHER_256];

        int blockX = chunkCoord.getBlockX();
        int blockZ = chunkCoord.getBlockZ();

        // Shadow chunks (height probes) skip the map to avoid estimator→shadow recursion.
        com.pg85.otg.gen.biome.UndergroundBiomeMap undergroundMap = GENERATING_SHADOW_CHUNK.get()
                ? com.pg85.otg.gen.biome.UndergroundBiomeMap.empty()
                : buildUndergroundBiomeMap(chunkCoord, worldHeight);
        // Bind the chunk's underground map for the cave-density graph (read per-block in carveWithNoise via
        // RegionScaleFunction). MUST stay bound until carving finishes: nothing between here and the carve may
        // re-trigger shadow-chunk gen (which would rebind EMPTY and silently disable cave scaling). Cleared in
        // SharedOTGChunkGenerator.fillFromNoise's finally.
        CURRENT_CAVE_MAP.set(undergroundMap);
        SurfaceSettings[] ugSurfaceById = new SurfaceSettings[this.undergroundBiomesById.length];

        // --- Phase 1: Biome lookup ---
        long biomeStart = System.currentTimeMillis();
        IBiome[] biomes = this.cachedBiomeProvider.getBiomesForChunk(chunkCoord);
        // Cache biome settings to avoid 98k getBiomeSettings() calls per chunk (was called per-block, now per-column)
        BiomeSettings[] biomeConfigCache = new BiomeSettings[Constants.OTHER_256];
        for (int x = 0; x < Constants.CHUNK_SIZE; x++) {
            for (int z = 0; z < Constants.CHUNK_SIZE; z++) {
                int idx = x * Constants.CHUNK_SIZE + z;
                BiomeSettings settings = biomes[idx].getBiomeSettings();
                biomeConfigCache[idx] = settings;
                // TODO: water levels used to be interpolated via bilinear interpolation. Do we still need to do that?
                waterLevel[idx] = settings.getSurfaceSettings().getWaterLevelMax();
            }
        }
        long biomeTime = System.currentTimeMillis() - biomeStart;

        // --- Phase 2: Noise column init ---
        // Reuse pre-allocated noise data buffer from ThreadLocal (avoids ~4KB allocation per chunk)
        double[][][] noiseData = this.noiseDataBuffer.get();
        long noiseStart = System.currentTimeMillis();
        // Initialize noise data on the x0 column.
        for (int noiseZ = 0; noiseZ < NOISE_SIZE_Z + 1; ++noiseZ) {
            this.getNoiseColumn(
                    noiseData[0][noiseZ],
                    chunkCoord.getChunkX() * NOISE_SIZE_X,
                    chunkCoord.getChunkZ() * NOISE_SIZE_Z + noiseZ
            );
        }
        long noiseTime = System.currentTimeMillis() - noiseStart;
        long blockPlaceStart = System.currentTimeMillis();

        BiomeSettings biomeConfig;
        // [0, 4] -> x noise chunks
        int noiseZ;
        double x0z0y0;
        double x0z1y0;
        double x1z0y0;
        double x1z1y0;
        double x0z0y1;
        double x0z1y1;
        double x1z0y1;
        double x1z1y1;
        int realY;
        double yLerp;
        double x0z0;
        double x1z0;
        double x0z1;
        double x1z1;
        int realX;
        int localX;
        double xLerp;
        double z0;
        double z1;
        int realZ;
        int localZ;
        double zLerp;
        double rawNoise;
        double density;
        int structureX;
        int structureY;
        int structureZ;
        JigsawStructureData structure;
        double[][] xColumn;
        for (int noiseX = 0; noiseX < NOISE_SIZE_X; ++noiseX) {
            // Initialize noise data on the x1 column
            for (noiseZ = 0; noiseZ < NOISE_SIZE_Z + 1; ++noiseZ) {
                this.getNoiseColumn(
                        noiseData[1][noiseZ],
                        chunkCoord.getChunkX() * NOISE_SIZE_X + noiseX + 1,
                        chunkCoord.getChunkZ() * NOISE_SIZE_Z + noiseZ
                );
            }

            // [0, 4] -> z noise chunks
            for (noiseZ = 0; noiseZ < NOISE_SIZE_Z; ++noiseZ) {
                // [0, 32] -> y noise chunks
                for (int noiseY = this.noiseSizeY - 1; noiseY >= 0; --noiseY) {
                    // Lower samples
                    x0z0y0 = noiseData[0][noiseZ][noiseY];
                    x0z1y0 = noiseData[0][noiseZ + 1][noiseY];
                    x1z0y0 = noiseData[1][noiseZ][noiseY];
                    x1z1y0 = noiseData[1][noiseZ + 1][noiseY];
                    // Upper samples
                    x0z0y1 = noiseData[0][noiseZ][noiseY + 1];
                    x0z1y1 = noiseData[0][noiseZ + 1][noiseY + 1];
                    x1z0y1 = noiseData[1][noiseZ][noiseY + 1];
                    x1z1y1 = noiseData[1][noiseZ + 1][noiseY + 1];

                    // [0, 8] -> y noise pieces
                    for (int pieceY = 8 - 1; pieceY >= 0; --pieceY) {
                        realY = worldHeight.minY() + noiseY * 8 + pieceY;

                        // progress within loop
                        yLerp = (double) pieceY / 8.0;

                        // Interpolate noise data based on y progress
                        x0z0 = MathHelper.lerp(yLerp, x0z0y0, x0z0y1);
                        x1z0 = MathHelper.lerp(yLerp, x1z0y0, x1z0y1);
                        x0z1 = MathHelper.lerp(yLerp, x0z1y0, x0z1y1);
                        x1z1 = MathHelper.lerp(yLerp, x1z1y0, x1z1y1);

                        // [0, 4] -> x noise pieces
                        for (int pieceX = 0; pieceX < 4; ++pieceX) {
                            realX = blockX + noiseX * 4 + pieceX;
                            localX = realX & 15;
                            xLerp = (double) pieceX / 4.0;
                            // Interpolate noise based on x progress
                            z0 = MathHelper.lerp(xLerp, x0z0, x1z0);
                            z1 = MathHelper.lerp(xLerp, x0z1, x1z1);

                            // [0, 4) -> z noise pieces
                            for (int pieceZ = 0; pieceZ < 4; ++pieceZ) {
                                realZ = blockZ + noiseZ * 4 + pieceZ;
                                localZ = realZ & 15;
                                zLerp = (double) pieceZ / 4.0;
                                // Get the real noise here by interpolating the last 2 noises together
                                rawNoise = MathHelper.lerp(zLerp, z0, z1);
                                // Normalize the noise from (-256, 256) to [-1, 1]
                                density = MathHelper.clamp(rawNoise / 200.0D, -1.0D, 1.0D);

                                biomeConfig = biomeConfigCache[localX * 16 + localZ];
                                SurfaceSettings surfaceSettings = biomeConfig.getSurfaceSettings();

                                // TODO: make this bigger and look better
                                // Iterate through structures to add density
                                for (
                                        density = density / 2.0D - density * density * density / 24.0D;
                                        structureIterator.hasNext();
                                        density += getNoiseWeight(structureX, structureY, structureZ) * 0.8D
                                ) {
                                    structure = structureIterator.next();
                                    structureX = Math.max(0, Math.max(structure.minX - realX, realX - structure.maxX));
                                    structureY = realY - (structure.minY + (structure.useDelta ? structure.delta : 0));
                                    structureZ = Math.max(0, Math.max(structure.minZ - realZ, realZ - structure.maxZ));
                                }
                                structureIterator.back(structures.size());
                                if (density > 0.0) {
                                    SurfaceSettings placeSurface = surfaceSettings;
                                    int ugId = undergroundMap.getUndergroundBiomeId(realX, realY, realZ);
                                    if (ugId >= 0) {
                                        SurfaceSettings cached = ugSurfaceById[ugId];
                                        if (cached == null) {
                                            cached = undergroundMap.getBiome(ugId).getBiomeSettings().getSurfaceSettings();
                                            ugSurfaceById[ugId] = cached;
                                        }
                                        placeSurface = cached;
                                    }
                                    buffer.setBlock(
                                            localX,
                                            realY,
                                            localZ,
                                            stoneOrDeepslate(placeSurface, realX, realY, realZ)
                                    );
                                    buffer.setHighestBlockForColumn(pieceX + noiseX * 4, noiseZ * 4 + pieceZ, realY);
                                } else if (realY < waterLevel[localX * 16 + localZ]
                                           && realY > surfaceSettings.getWaterLevelMin()) {
                                    buffer.setBlock(
                                            localX,
                                            realY,
                                            localZ,
                                            surfaceSettings.getWaterBlockReplaced(realY)
                                    );
                                    buffer.setHighestBlockForColumn(pieceX + noiseX * 4, noiseZ * 4 + pieceZ, realY);
                                }
                            }
                        }
                    }
                }
            }

            // Reuse noise data from the previous column for speed
            xColumn = noiseData[0];
            noiseData[0] = noiseData[1];
            noiseData[1] = xColumn;
        }

        long blockPlaceTime = System.currentTimeMillis() - blockPlaceStart;

        // --- Phase 4: Surface & ground control ---
        long surfaceStart = System.currentTimeMillis();
        doSurfaceAndGroundControl(biomes, random, worldHeight, this.seed, buffer, waterLevel);
        long surfaceTime = System.currentTimeMillis() - surfaceStart;

        // Track performance stats
        totalBiomeTimeMs.addAndGet(biomeTime);
        totalNoiseTimeMs.addAndGet(noiseTime);
        totalBlockPlaceTimeMs.addAndGet(blockPlaceTime);
        totalSurfaceTimeMs.addAndGet(surfaceTime);

        int count = chunksGenerated.incrementAndGet();
        if (count % PERF_LOG_INTERVAL == 0) {
            long tBiome = totalBiomeTimeMs.get();
            long tNoise = totalNoiseTimeMs.get();
            long tBlock = totalBlockPlaceTimeMs.get();
            long tSurface = totalSurfaceTimeMs.get();
            long tCarve = totalCarveTimeMs.get();
            long total = tBiome + tNoise + tBlock + tSurface + tCarve;
            if (total > 0) {
                logger.log(
                        LogLevel.INFO,
                        LogCategory.PERFORMANCE,
                        String.format("TerrainGen stats (%d chunks): Biome=%.1f%% (%dms), Noise=%.1f%% (%dms), BlockPlace=%.1f%% (%dms), Surface=%.1f%% (%dms), Carve=%.1f%% (%dms), Avg=%.2fms/chunk",
                                count,
                                100.0 * tBiome / total, tBiome,
                                100.0 * tNoise / total, tNoise,
                                100.0 * tBlock / total, tBlock,
                                100.0 * tSurface / total, tSurface,
                                100.0 * tCarve / total, tCarve,
                                (double) total / count)
                );
            }
        }

        long totalTime = System.currentTimeMillis() - startTime;
        if (logger.isEnabled(LogLevel.WARN, LogCategory.PERFORMANCE) && totalTime > 50) {
            logger.warn(
                    LogCategory.PERFORMANCE,
                    "Slow chunk {}: {}ms (biome={}, noise={}, blocks={}, surface={})",
                    chunkCoord, totalTime,
                    biomeTime, noiseTime, blockPlaceTime, surfaceTime
            );
        }
    }

    public void carve(ChunkBuffer chunk, long seed, BitSet carvingMask, boolean cavesEnabled, boolean ravinesEnabled) {
        // TODO: it should be possible to cache these carver graphs to make larger carvers more efficient and easier to use
        if (cavesEnabled || ravinesEnabled) {
            long carveStart = System.currentTimeMillis();
            Random random = new Random();
            ChunkCoordinate chunkCoordinate = chunk.getChunkCoordinate();
            int chunkX = chunkCoordinate.getChunkX();
            int chunkZ = chunkCoordinate.getChunkZ();
            for (int localChunkX = chunkX - 8; localChunkX <= chunkX + 8; ++localChunkX) {
                for (int localChunkZ = chunkZ - 8; localChunkZ <= chunkZ + 8; ++localChunkZ) {
                    setCarverSeed(random, seed, localChunkX, localChunkZ);

                    if (cavesEnabled && this.caves.isStartChunk(random, localChunkX, localChunkZ)) {
                        this.caves.carve(
                                this,
                                chunk,
                                random,
                                localChunkX,
                                localChunkZ,
                                chunkX,
                                chunkZ,
                                carvingMask,
                                this.cachedBiomeProvider,
                                this.otgWorldInfo
                        );
                    }

                    setCarverSeed(random, seed, localChunkX, localChunkZ);

                    if (ravinesEnabled && this.ravines.isStartChunk(random, localChunkX, localChunkZ)) {
                        this.ravines.carve(
                                this,
                                chunk,
                                random,
                                localChunkX,
                                localChunkZ,
                                chunkX,
                                chunkZ,
                                carvingMask,
                                this.cachedBiomeProvider,
                                this.otgWorldInfo
                        );
                    }
                }
            }
            totalCarveTimeMs.addAndGet(System.currentTimeMillis() - carveStart);
        }
    }

    private long setCarverSeed(Random random, long seed, int x, int z) {
        random.setSeed(seed);
        long i = random.nextLong();
        long j = random.nextLong();
        long k = (long) x * i ^ (long) z * j ^ seed;
        random.setSeed(k);
        return k;
    }

    private void doSurfaceAndGroundControl(
            IBiome[] biomes,
            Random random,
            OTGWorldInfo OTGWorldInfo,
            long worldSeed,
            ChunkBuffer chunkBuffer,
            int[] waterLevel
    ) {
        // Process surface and ground blocks for each column in the chunk
        ChunkCoordinate chunkCoord = chunkBuffer.getChunkCoordinate();
        double d1 = 0.03125D;
        this.biomeBlocksNoise.set(this.biomeBlocksNoiseGen.getRegion(
                this.biomeBlocksNoise.get(),
                chunkCoord.getBlockX(),
                chunkCoord.getBlockZ(),
                Constants.CHUNK_SIZE,
                Constants.CHUNK_SIZE,
                d1 * 2.0D,
                d1 * 2.0D,
                1.0D
        ));
        GeneratingChunk generatingChunk =
                new GeneratingChunk(random, waterLevel, this.biomeBlocksNoise.get(), OTGWorldInfo);
        IBiome biome;
        for (int x = 0; x < Constants.CHUNK_SIZE; x++) {
            for (int z = 0; z < Constants.CHUNK_SIZE; z++) {
                // Get the current biome config and some properties
                biome = biomes[x * Constants.CHUNK_SIZE + z];
                biome.getBiomeSettings().getSurfaceSettings().doSurfaceAndGroundControl(
                        worldSeed,
                        generatingChunk,
                        chunkBuffer,
                        chunkCoord.getBlockX() + x,
                        chunkCoord.getBlockZ() + z,
                        biome
                )
                ;
            }
        }
    }

    // Used by sagc for generating surface/ground block patterns
    public double getBiomeBlocksNoiseValue(int blockX, int blockZ) {
        BiomeBlocksNoiseCache cache = this.biomeBlocksNoiseCache.get();
        if (cache.lastX != blockX || cache.lastZ != blockZ) {
            double d1 = 0.03125D;
            cache.lastNoise = this.biomeBlocksNoiseGen.getRegion(
                    cache.buffer,
                    blockX,
                    blockZ,
                    1,
                    1,
                    d1 * 2.0D,
                    d1 * 2.0D,
                    1.0D
            )[0];
            cache.lastX = blockX;
            cache.lastZ = blockZ;
        }
        return cache.lastNoise;
    }

    private class NoiseCache {
        private final long[] keys;
        private final double[] values;
        private final int mask;

        private NoiseCache(int size, int noiseSize) {
            size = MathHelper.smallestEncompassingPowerOfTwo(size);
            this.mask = size - 1;

            this.keys = new long[size];
            Arrays.fill(this.keys, Long.MIN_VALUE);
            this.values = new double[size * noiseSize];
        }

        public double[] get(double[] buffer, int noiseX, int noiseZ) {
            long key = key(noiseX, noiseZ);
            int idx = hash(key) & this.mask;

            // if the entry here has a key that matches ours, we have a cache hit
            if (this.keys[idx] == key) {
                // Copy values into buffer
                System.arraycopy(this.values, idx * buffer.length, buffer, 0, buffer.length);
            } else {
                // cache miss: sample and put the result into our cache entry

                // Sample the noise column to store the new values
                generateNoiseColumn(buffer, noiseX, noiseZ);

                // Create copy of the array
                System.arraycopy(buffer, 0, this.values, idx * buffer.length, buffer.length);

                this.keys[idx] = key;
            }

            return buffer;
        }

        private int hash(long key) {
            return (int) HashCommon.mix(key);
        }

        private long key(int x, int z) {
            return MathHelper.toLong(x, z);
        }
    }
}
