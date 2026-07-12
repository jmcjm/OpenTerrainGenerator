package com.pg85.otg.gen.resource;

import com.pg85.otg.util.Pair;
import com.pg85.otg.config.settings.biome.BiomeSettings;
import com.pg85.otg.exceptions.InvalidConfigException;
import com.pg85.otg.interfaces.IWorldGenRegion;
import com.pg85.otg.util.OTGMaterialReader;
import com.pg85.otg.util.helpers.RandomHelper;
import com.pg85.otg.util.materials.LocalMaterialData;
import com.pg85.otg.util.materials.MaterialSet;
import com.pg85.otg.util.minecraft.PlantType;

import java.util.List;
import java.util.Random;

public class PlantResource extends FrequencyResourceBase
{
    private final int maxAltitude;
    private final int minAltitude;
    private final PlantType plant;
    private final MaterialSet sourceBlocks;

    public PlantResource(BiomeSettings biomeConfig, List<String> args) throws InvalidConfigException
    {
        super(biomeConfig, args);
        assureSize(6, args);

        this.plant = PlantType.getPlant(args.get(0), OTGMaterialReader.get());
        this.frequency = readFrequency(args.get(1));
        this.rarity = readRarity(args.get(2));
        Pair<Integer, Integer> elevations = readElevations(args.get(3), args.get(4));
        this.minAltitude = elevations.getFirst();
        this.maxAltitude = elevations.getSecond();
        this.sourceBlocks = readMaterials(args, 5);
    }

    @Override
    public void spawn(IWorldGenRegion worldGenregion, Random rand, int x, int z)
    {
        int y = RandomHelper.numberInRange(rand, this.minAltitude, this.maxAltitude);

        LocalMaterialData worldMaterial;
        LocalMaterialData worldMaterialBelow;

        int localX;
        int localY;
        int localZ;
        for (int i = 0; i < 64; i++) {
            localX = x + rand.nextInt(8) - rand.nextInt(8);
            localY = y + rand.nextInt(4) - rand.nextInt(4);
            localZ = z + rand.nextInt(8) - rand.nextInt(8);
            worldMaterial = worldGenregion.getMaterial(localX, localY, localZ);
            worldMaterialBelow = worldGenregion.getMaterial(localX, localY - 1, localZ);
            if (
                (worldMaterial == null || !worldMaterial.isAir()) ||
                (worldMaterialBelow == null || !this.sourceBlocks.contains(worldMaterialBelow))
            )
            {
                continue;
            }

            this.plant.spawn(worldGenregion, localX, localY, localZ);
        }
    }

    @Override
    public String toString()
    {
        return "Plant("
               + this.plant.getName()
               + ","
               + this.frequency
               + ","
               + this.rarity
               + ","
               + this.minAltitude
               + ","
               + this.maxAltitude
               + makeMaterials(this.sourceBlocks)
               + ")";
    }
}
