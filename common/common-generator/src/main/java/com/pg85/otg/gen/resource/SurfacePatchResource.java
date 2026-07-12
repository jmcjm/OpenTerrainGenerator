package com.pg85.otg.gen.resource;

import com.pg85.otg.util.Pair;
import com.pg85.otg.config.biome.BiomeResourceBase;
import com.pg85.otg.config.settings.biome.BiomeSettings;
import com.pg85.otg.constants.Constants;
import com.pg85.otg.exceptions.InvalidConfigException;
import com.pg85.otg.gen.noise.OctaveSimplexNoiseSampler;
import com.pg85.otg.interfaces.IMaterialReader;
import com.pg85.otg.interfaces.IWorldGenRegion;
import com.pg85.otg.util.OTGMaterialReader;
import com.pg85.otg.util.biome.ReplaceBlockMatrix;
import com.pg85.otg.util.materials.LocalMaterialData;
import com.pg85.otg.util.materials.MaterialSet;
import com.pg85.otg.util.minecraft.PlantType;

import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

/**
 * Generates patches based on noise.
 * TODO: Expose the noise weights and scale to the config
 */
public class SurfacePatchResource extends BiomeResourceBase implements IBasicResource
{
    private final LocalMaterialData material;
    private final PlantType decorationAboveReplacementPlant;
    private final int maxAltitude;
    private final int minAltitude;
    /**
     * To get nice patches, we need our own noise generator here
     */
    private final OctaveSimplexNoiseSampler noiseGen;
    private final MaterialSet sourceBlocks;

    public SurfacePatchResource(BiomeSettings biomeConfig, List<String> args) throws InvalidConfigException
    {
        super(biomeConfig, args);
        assureSize(4, args);
        IMaterialReader materialReader = OTGMaterialReader.get();
        this.material = materialReader.readMaterial(args.get(0));

        // Technically, this avoids replaceblocks for the decoration block
        // PlantType has a fallback to just accept any block as plant, so this can be any block, not just plants
        this.decorationAboveReplacementPlant = PlantType.getPlant(args.get(1), materialReader);

        Pair<Integer, Integer> elevations = readElevations(args.get(2), args.get(3));
        this.minAltitude = elevations.getFirst();
        this.maxAltitude = elevations.getSecond();
        this.sourceBlocks = readMaterials(args, 4);
        // TODO: Find good values for octaves, or expose to config
        this.noiseGen = new OctaveSimplexNoiseSampler(new Random(2345L), IntStream.of(-1, 1));
    }

    @Override
    public void spawnForChunkDecoration(IWorldGenRegion worldGenRegion, Random random)
    {
        int chunkX = worldGenRegion.getDecorationArea().getChunkBeingDecoratedMinX();
        int chunkZ = worldGenRegion.getDecorationArea().getChunkBeingDecoratedMinZ();
        int x;
        int z;
        for (int z0 = 0; z0 < Constants.CHUNK_SIZE; z0++) {
            for (int x0 = 0; x0 < Constants.CHUNK_SIZE; x0++) {
                x = chunkX + x0;
                z = chunkZ + z0;
                spawn(worldGenRegion, random, false, x, z);
            }
        }
    }

    public void spawn(IWorldGenRegion worldGenRegion, Random rand, boolean villageInChunk, int x, int z)
    {
        int y = worldGenRegion.getHighestBlockAboveYAt(x, z) - 1;
        if (y < this.minAltitude || y > this.maxAltitude) {
            return;
        }

        double yNoise = this.noiseGen.sample(x * 0.25D, z * 0.25D, false);
        if (yNoise > 0.0D) {
            LocalMaterialData materialAtLocation = worldGenRegion.getMaterial(x, y, z);
            if (this.sourceBlocks.contains(materialAtLocation)) {
                ReplaceBlockMatrix replaceBlocks =
                    worldGenRegion.getBiomeConfigForDecoration(x, z).getSurfaceSettings().getReplacedBlocks();
                worldGenRegion.setBlock(x, y, z, this.material, replaceBlocks);
                if (yNoise < 0.12D) {
                    this.decorationAboveReplacementPlant.spawn(worldGenRegion, x, y + 1, z);
                }
            }
        }
    }

    @Override
    public String toString()
    {
        return "SurfacePatch("
               + this.material
               + ","
               + this.decorationAboveReplacementPlant
               + ","
               + this.minAltitude
               + ","
               + this.maxAltitude
               + ","
               + this.sourceBlocks
               + ")";
    }
}
