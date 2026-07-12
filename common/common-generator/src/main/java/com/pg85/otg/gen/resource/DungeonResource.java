package com.pg85.otg.gen.resource;

import com.pg85.otg.util.Pair;
import com.pg85.otg.config.settings.biome.BiomeSettings;
import com.pg85.otg.exceptions.InvalidConfigException;
import com.pg85.otg.interfaces.IWorldGenRegion;
import com.pg85.otg.util.helpers.RandomHelper;

import java.util.List;
import java.util.Random;

public class DungeonResource extends FrequencyResourceBase
{
    private final int maxAltitude;
    private final int minAltitude;

    public DungeonResource(BiomeSettings biomeConfig, List<String> args) throws InvalidConfigException
    {
        super(biomeConfig, args);
        assureSize(3, args);

        // Support legacy format with 4 args: Dungeon(Frequency, Rarity, MinAltitude, MaxAltitude)
        // New format has 3 args: Dungeon(Rarity, MinAltitude, MaxAltitude) with frequency=1
        if (args.size() >= 4) {
            // Legacy format - first arg is frequency (ignored, we use 1)
            this.frequency = 1;
            this.rarity = readRarity(args.get(1));
            Pair<Integer, Integer> elevations = readElevations(args.get(2), args.get(3));
            this.minAltitude = elevations.getFirst();
            this.maxAltitude = elevations.getSecond();
        } else {
            // New format
            this.frequency = 1;
            this.rarity = readRarity(args.get(0));
            Pair<Integer, Integer> elevations = readElevations(args.get(1), args.get(2));
            this.minAltitude = elevations.getFirst();
            this.maxAltitude = elevations.getSecond();
        }
    }

    @Override
    public String toString()
    {
        return "Dungeon(" + this.rarity + "," + this.minAltitude + "," + this.maxAltitude + ")";
    }

    @Override
    public void spawn(IWorldGenRegion world, Random random, int x, int z)
    {
        int y = RandomHelper.numberInRange(random, this.minAltitude, this.maxAltitude);
        world.placeDungeon(random, x, y, z);
    }
}
