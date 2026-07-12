package com.pg85.otg.gen.resource;

import com.pg85.otg.util.Pair;
import com.pg85.otg.config.settings.biome.BiomeSettings;
import com.pg85.otg.exceptions.InvalidConfigException;
import com.pg85.otg.interfaces.IWorldGenRegion;
import com.pg85.otg.util.OTGMaterialReader;
import com.pg85.otg.util.helpers.RandomHelper;
import com.pg85.otg.util.materials.LocalMaterialData;
import com.pg85.otg.util.materials.LocalMaterials;
import com.pg85.otg.util.materials.MaterialSet;
import com.pg85.otg.util.minecraft.PlantType;

import java.util.List;
import java.util.Random;

public class UnderWaterPlantResource extends FrequencyResourceBase
{
    private final int maxAltitude;
    private final int minAltitude;
    private final PlantType plant;
    private final MaterialSet sourceBlocks;

    public UnderWaterPlantResource(BiomeSettings biomeConfig, List<String> args) throws InvalidConfigException
    {
        super(biomeConfig, args);
        assureSize(6, args);

        this.plant = PlantType.getPlant(args.get(0), OTGMaterialReader.get());
        this.frequency = readInt(args.get(1), 1, 100);
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

        int j;
        int k;
        int m;
        LocalMaterialData worldMaterial;
        LocalMaterialData worldMaterialBelow;
        for (int i = 0; i < 64; i++) {
            j = x + rand.nextInt(8) - rand.nextInt(8);
            k = y + rand.nextInt(4) - rand.nextInt(4);
            m = z + rand.nextInt(8) - rand.nextInt(8);
            worldMaterial = worldGenregion.getMaterial(j, k, m);
            worldMaterialBelow = worldGenregion.getMaterial(j, k - 1, m);
            if (
                !LocalMaterials.WATER.isMaterial(worldMaterial) ||
                !this.sourceBlocks.contains(worldMaterialBelow)
            )
            {
                continue;
            }
            this.plant.spawn(worldGenregion, j, k, m);
        }
    }

    @Override
    public String toString()
    {
        return "UnderWaterPlant("
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
