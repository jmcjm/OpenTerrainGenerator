package com.pg85.otg.gen.resource;

import com.pg85.otg.util.Pair;
import com.pg85.otg.config.biome.BiomeResourceBase;
import com.pg85.otg.config.settings.biome.BiomeSettings;
import com.pg85.otg.exceptions.InvalidConfigException;
import com.pg85.otg.interfaces.IWorldGenRegion;
import com.pg85.otg.util.OTGLog;
import com.pg85.otg.util.OTGMaterialReader;
import com.pg85.otg.util.logging.LogCategory;
import com.pg85.otg.util.materials.MaterialGroup;
import com.pg85.otg.util.materials.MaterialSet;
import com.pg85.otg.util.minecraft.PlantType;

import java.util.List;

public abstract class VegetationResource extends BiomeResourceBase implements IBasicResource
{

    protected final PlantType plant;
    protected final VerticalMode verticalMode;
    protected final MaterialGroup environment;
    protected final int frequency;
    protected final double rarity;
    protected final int minAltitude;
    protected final int maxAltitude;

    protected final MaterialSet sourceBlocks;

    public VegetationResource(
        BiomeSettings biomeConfig,
        List<String> args,
        int sourceBlockIndex
    ) throws InvalidConfigException
    {
        super(biomeConfig, args);
        assureSize(11, args);

        plant = PlantType.getPlant(args.get(0), OTGMaterialReader.get());
        verticalMode = VerticalMode.valueOf(args.get(2));
        environment = MaterialGroup.valueOf(args.get(3));
        frequency = readInt(args.get(4), 1, 500);
        rarity = readRarity(args.get(5));
        Pair<Integer, Integer> elevations = readElevations(args.get(6), args.get(7));
        minAltitude = elevations.getFirst();
        maxAltitude = elevations.getSecond();
        sourceBlocks = readMaterials(args, sourceBlockIndex);
    }

    public int spawnPlant(IWorldGenRegion world, int x, int y, int z, int outOfBounds)
    {
        switch (verticalMode) {
            case Surface, Random -> {
                // Use the value as is
            }
            case Floor -> {
                y = toFloor(world, x, y, z);
            }
            case Ceiling -> {
                int highestY = world.getBlockAboveSolidHeight(x, z);
                if (y > highestY) {
                    // no ceiling can be found above - can't place
                    return outOfBounds;
                }
                y = toCeiling(world, x, y, z);
            }
        }

        // Check for out of bounds

        if (isOutsideBounds(y - 1, world.getWorldInfo())) {
            outOfBounds++;
            return outOfBounds;
        }

        // Check Environment and SourceBlock
        // Spawn the plant
        int checkOffset = (verticalMode == VerticalMode.Ceiling) ? -1 : 1;
        if (environment.contains(world.getMaterial(x, y + checkOffset, z)) &&
            sourceBlocks.contains(world.getMaterial(x, y, z)))
        {
            this.plant.spawn(world, x, y + checkOffset, z);
        }
        return outOfBounds;
    }

    public int toCeiling(IWorldGenRegion world, int x, int y, int z)
    {
        while (environment.contains(world.getMaterial(x, y, z)) &&
               world.getMaterial(x, y + 1, z) != null &&
               y < world.getWorldInfo().maxY()
        ) {
            y++;
        }
        return y;
    }

    public int toFloor(IWorldGenRegion world, int x, int y, int z)
    {
        while (environment.contains(world.getMaterial(x, y, z)) &&
               world.getMaterial(x, y - 1, z) != null &&
               y > world.getWorldInfo().minY()
        ) {
            y--;
        }
        return y;
    }

    public void logOutOfBounds(IWorldGenRegion world, int outOfBounds)
    {
        if (outOfBounds > 0) {
            OTGLog.warn(LogCategory.DECORATION,
                "VegetationResource: {} out of bounds placements of {} skipped at chunk {}",
                outOfBounds, plant.getName(), world.getDecorationArea().getChunkBeingDecorated());
        }
    }

    public enum VerticalMode
    {
        Surface,
        Floor,
        Ceiling,
        Random
    }
}
