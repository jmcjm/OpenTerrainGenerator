package com.pg85.otg.client.editor.data;

import com.pg85.otg.client.preview.world.PreviewBiomes;
import com.pg85.otg.client.preview.world.PreviewWorld;
import com.pg85.otg.gen.noise.BlendedBiomeParams;
import com.pg85.otg.gen.noise.TerrainNoisePipeline;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;

import java.util.List;
import java.util.Random;

/**
 * Generates terrain heightmap using OTG's actual noise pipeline.
 * Computes noise at grid points (every 4 blocks) and interpolates — same as OTG.
 * All formulas match OTGChunkGenerator.generateNoiseColumn().
 */
public class BiomeHeightmapGenerator {

    private static final int NOISE_SECTION_HEIGHT = 8;
    private static final int NOISE_SIZE_Y = 48;
    private static final int NOISE_GRID_SPACING = 4; // blocks per noise grid cell

    private static final BlockState STONE = Blocks.STONE.defaultBlockState();
    private static final BlockState GRASS = Blocks.GRASS_BLOCK.defaultBlockState();
    private static final BlockState DIRT = Blocks.DIRT.defaultBlockState();
    private static final BlockState WATER = Blocks.WATER.defaultBlockState();
    private static final BlockState SAND = Blocks.SAND.defaultBlockState();
    private static final BlockState BEDROCK = Blocks.BEDROCK.defaultBlockState();

    public record GenerationResult(Vector3f center, float radius, int[] heightmap, int size) {}

    private record PreviewSetup(TerrainNoisePipeline pipeline, BlendedBiomeParams params, int waterLevel) {}

    /**
     * Generates heightmap and places blocks into world.
     * Can be called from any thread (block placement is thread-safe in PreviewWorld).
     * Caller must do renderer.compileAll() on render thread AFTER this returns.
     */
    public static GenerationResult generate(PreviewWorld world,
                                             List<PropertyValue> biomeProperties,
                                             List<PropertyValue> presetProperties,
                                             long seed, int size) {
        world.clear();
        PreviewSetup setup = buildPipeline(biomeProperties, presetProperties, seed);
        double[][] noiseColumns = computeNoiseColumns(size, setup);
        GenerationResult result = interpolateAndPlaceBlocks(world, noiseColumns, size, setup.waterLevel());
        // Assign the previewed biome so grass/leaves/water tint with colour instead of white.
        world.fillBiome(buildPreviewBiome(biomeProperties));
        return result;
    }

    /**
     * Builds a transient biome from the edited colour/climate properties, mirroring
     * {@code BiomeFactory.getSpecialEffects}: a colour left at the default white (0xFFFFFF) means
     * "use the vanilla climate-derived colour", so it falls back to temperature/downfall.
     */
    private static Holder<Biome> buildPreviewBiome(List<PropertyValue> props) {
        float temperature = getFloat(props, "BiomeTemperature", 0.5f);
        float downfall = getFloat(props, "BiomeWetness", 0.5f);
        int foliage = getColor(props, "FoliageColor", 0xFFFFFF);
        int grass = getColor(props, "GrassColor", 0xFFFFFF);
        int water = getColor(props, "WaterColor", 0xFFFFFF);
        int waterFog = getColor(props, "WaterFogColor", 0x000000);
        int fog = getColor(props, "FogColor", 0x000000);
        int sky = getColor(props, "SkyColor", 0x7BA5FF);

        Integer foliageOverride = foliage != 0xFFFFFF ? foliage : null;
        Integer grassOverride = grass != 0xFFFFFF ? grass : null;
        int waterColor = water != 0xFFFFFF ? water : 4159204;       // vanilla default water
        int waterFogColor = waterFog != 0x000000 ? waterFog : 329011; // vanilla default water fog
        int fogColor = fog != 0x000000 ? fog : 0xC0D8FF;            // neutral overworld fog (does not tint blocks)

        BiomeSpecialEffects.GrassColorModifier modifier = switch (getString(props, "GrassColorModifier", "None")) {
            case "Swamp" -> BiomeSpecialEffects.GrassColorModifier.SWAMP;
            case "DarkForest" -> BiomeSpecialEffects.GrassColorModifier.DARK_FOREST;
            default -> null;
        };

        return PreviewBiomes.build(temperature, downfall, fogColor, waterFogColor, waterColor, sky,
                foliageOverride, grassOverride, modifier);
    }

    private static PreviewSetup buildPipeline(List<PropertyValue> biomeProperties,
                                              List<PropertyValue> presetProperties, long seed) {
        float biomeHeight = getFloat(biomeProperties, "BiomeHeight", 0.1f);
        float biomeVolatility = getFloat(biomeProperties, "BiomeVolatility", 0.3f);
        double volatility1 = getFloat(biomeProperties, "Volatility1", 1.0f);
        double volatility2 = getFloat(biomeProperties, "Volatility2", 1.0f);
        double volatilityWeight1 = getFloat(biomeProperties, "VolatilityWeight1", 0.45f);
        double volatilityWeight2 = getFloat(biomeProperties, "VolatilityWeight2", 0.5f);
        double valleyFactor = getFloat(biomeProperties, "ValleyFactor", 1.0f);
        double peakFactor = getFloat(biomeProperties, "PeakFactor", 1.0f);

        boolean useWorldWater = getBool(biomeProperties, "UseWorldWaterLevel", true);
        int waterLevel = useWorldWater
            ? getInt(presetProperties, "WaterLevelMax", 63)
            : getInt(biomeProperties, "WaterLevelMax", 63);
        double fractureH = getFloat(presetProperties, "FractureHorizontal", 0f);
        double fractureV = getFloat(presetProperties, "FractureVertical", 0f);

        var samplers = TerrainNoisePipeline.createNoiseSamplers(new Random(seed));
        TerrainNoisePipeline pipeline = new TerrainNoisePipeline(
            samplers.interpolation(), samplers.lower(), samplers.upper(), samplers.depth(),
            NOISE_SIZE_Y,
            TerrainNoisePipeline.REFERENCE_SURFACE_SECTIONS,
            getFloat(presetProperties, "ContinentalScale", 0.2f),
            getFloat(presetProperties, "ContinentalBias", -0.05f),
            getFloat(presetProperties, "BaseHeightFraction", 0.46875f),
            getFloat(presetProperties, "BiomeHeightWeight", 0.125f),
            getFloat(presetProperties, "ContinentalHeightWeight", 0.25f),
            getFloat(presetProperties, "FalloffSteepness", 6.0f),
            getFloat(presetProperties, "NoiseAmplitude", 1.0f)
        );
        BlendedBiomeParams params = new BlendedBiomeParams(
            biomeHeight, biomeVolatility,
            volatility1, volatility2,
            fractureH, fractureV,
            volatilityWeight1, volatilityWeight2,
            valleyFactor, peakFactor
        );
        return new PreviewSetup(pipeline, params, waterLevel);
    }

    private static double[][] computeNoiseColumns(int size, PreviewSetup setup) {
        int noiseCountX = size / NOISE_GRID_SPACING + 1;
        int noiseCountZ = size / NOISE_GRID_SPACING + 1;
        double[][] noiseColumns = new double[noiseCountX * noiseCountZ][];
        double[] zeroChc = new double[NOISE_SIZE_Y + 1];

        for (int nx = 0; nx < noiseCountX; nx++) {
            for (int nz = 0; nz < noiseCountZ; nz++) {
                double[] column = new double[NOISE_SIZE_Y + 1];
                setup.pipeline().generateColumn(column, nx, nz, setup.params(), zeroChc, false);
                noiseColumns[nx * noiseCountZ + nz] = column;
            }
        }
        return noiseColumns;
    }

    private static GenerationResult interpolateAndPlaceBlocks(PreviewWorld world,
                                                                double[][] noiseColumns,
                                                                int size, int waterLevel) {
        int noiseCountX = size / NOISE_GRID_SPACING + 1;
        int noiseCountZ = size / NOISE_GRID_SPACING + 1;

        int[] heightmap = new int[size * size];
        int minSurfaceY = 999, maxSurfaceY = 0;

        for (int bx = 0; bx < size; bx++) {
            for (int bz = 0; bz < size; bz++) {
                int nx = bx / NOISE_GRID_SPACING;
                int nz = bz / NOISE_GRID_SPACING;
                double fracX = (double)(bx % NOISE_GRID_SPACING) / NOISE_GRID_SPACING;
                double fracZ = (double)(bz % NOISE_GRID_SPACING) / NOISE_GRID_SPACING;

                int nx1 = Math.min(nx + 1, noiseCountX - 1);
                int nz1 = Math.min(nz + 1, noiseCountZ - 1);

                double[] col00 = noiseColumns[nx * noiseCountZ + nz];
                double[] col10 = noiseColumns[nx1 * noiseCountZ + nz];
                double[] col01 = noiseColumns[nx * noiseCountZ + nz1];
                double[] col11 = noiseColumns[nx1 * noiseCountZ + nz1];

                int surfaceY = findSurfaceY(col00, col10, col01, col11, fracX, fracZ);

                surfaceY = Math.max(1, Math.min(319, surfaceY));
                heightmap[bx * size + bz] = surfaceY;
                if (surfaceY < minSurfaceY) minSurfaceY = surfaceY;
                if (surfaceY > maxSurfaceY) maxSurfaceY = surfaceY;

                placeColumnBlocks(world, bx, bz, surfaceY, waterLevel);
            }
        }

        float centerY = (minSurfaceY + maxSurfaceY) / 2f;
        Vector3f center = new Vector3f(size / 2f, centerY, size / 2f);
        float radius = Math.max(size, maxSurfaceY - minSurfaceY + 20);

        return new GenerationResult(center, radius, heightmap, size);
    }

    private static int findSurfaceY(double[] col00, double[] col10,
                                      double[] col01, double[] col11,
                                      double fracX, double fracZ) {
        for (int y = NOISE_SIZE_Y - 1; y >= 0; y--) {
            for (int subY = NOISE_SECTION_HEIGHT - 1; subY >= 0; subY--) {
                double fracY = (double) subY / NOISE_SECTION_HEIGHT;
                int y1 = Math.min(y + 1, NOISE_SIZE_Y);

                double d00 = col00[y] + (col00[y1] - col00[y]) * fracY;
                double d10 = col10[y] + (col10[y1] - col10[y]) * fracY;
                double d01 = col01[y] + (col01[y1] - col01[y]) * fracY;
                double d11 = col11[y] + (col11[y1] - col11[y]) * fracY;

                double dx0 = d00 + (d10 - d00) * fracX;
                double dx1 = d01 + (d11 - d01) * fracX;
                double rawDensity = dx0 + (dx1 - dx0) * fracZ;

                double density = Math.max(-1, Math.min(1, rawDensity / 200.0));
                density = density / 2.0 - density * density * density / 24.0;

                if (density > 0) {
                    return y * NOISE_SECTION_HEIGHT + subY;
                }
            }
        }
        return 0;
    }

    private static void placeColumnBlocks(PreviewWorld world, int bx, int bz,
                                            int surfaceY, int waterLevel) {
        world.setBlockState(new BlockPos(bx, 0, bz), BEDROCK);
        for (int y = 1; y < surfaceY - 3; y++) {
            world.setBlockState(new BlockPos(bx, y, bz), STONE);
        }
        for (int y = Math.max(1, surfaceY - 3); y < surfaceY; y++) {
            world.setBlockState(new BlockPos(bx, y, bz), DIRT);
        }
        if (surfaceY <= waterLevel + 2) {
            world.setBlockState(new BlockPos(bx, surfaceY, bz), SAND);
        } else {
            world.setBlockState(new BlockPos(bx, surfaceY, bz), GRASS);
        }
        for (int y = surfaceY + 1; y <= waterLevel; y++) {
            world.setBlockState(new BlockPos(bx, y, bz), WATER);
        }
    }

    // --- Property helpers ---

    private static <T> T getProperty(List<PropertyValue> props, String name, T defaultValue,
                                      java.util.function.Function<String, T> parser) {
        if (props == null) return defaultValue;
        for (PropertyValue v : props) {
            if (v.getDefinition().name().equals(name)) {
                try { return parser.apply(v.getValue()); }
                catch (Exception e) { return defaultValue; }
            }
        }
        return defaultValue;
    }

    private static boolean getBool(List<PropertyValue> p, String n, boolean d) {
        return getProperty(p, n, d, v -> "true".equalsIgnoreCase(v));
    }

    private static float getFloat(List<PropertyValue> p, String n, float d) {
        return getProperty(p, n, d, Float::parseFloat);
    }

    private static int getInt(List<PropertyValue> p, String n, int d) {
        return getProperty(p, n, d, Integer::parseInt);
    }

    private static String getString(List<PropertyValue> p, String n, String d) {
        return getProperty(p, n, d, v -> v);
    }

    /** Reads a colour setting (e.g. {@code 0x7BA5FF}) as a 24-bit RGB int. */
    private static int getColor(List<PropertyValue> p, String n, int d) {
        return getProperty(p, n, d, BiomeHeightmapGenerator::parseColor) & 0xFFFFFF;
    }

    private static int parseColor(String s) {
        s = s.trim();
        if (s.startsWith("0x") || s.startsWith("0X")) return (int) Long.parseLong(s.substring(2), 16);
        if (s.startsWith("#")) return (int) Long.parseLong(s.substring(1), 16);
        try { return Integer.parseInt(s); }
        catch (NumberFormatException e) { return (int) Long.parseLong(s, 16); }
    }
}
