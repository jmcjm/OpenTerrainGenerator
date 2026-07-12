package com.pg85.otg.gen.resource;

import com.pg85.otg.util.Pair;
import com.pg85.otg.config.settings.biome.BiomeSettings;
import com.pg85.otg.exceptions.InvalidConfigException;
import com.pg85.otg.interfaces.IWorldGenRegion;
import com.pg85.otg.util.helpers.RandomHelper;

import java.util.List;
import java.util.Random;

public final class FossilResource extends FrequencyResourceBase
{
    private final int rarity;
    private final int maxAltitude;
    private final int minAltitude;

    public FossilResource(BiomeSettings biomeConfig, List<String> args) throws InvalidConfigException
    {
        super(biomeConfig, args);
        assureSize(1, args);

        this.frequency = 1;
        this.rarity = readInt(args.get(0), 1, Integer.MAX_VALUE);
        if (args.size() >= 3) {
            Pair<Integer, Integer> elevations = readElevations(args.get(1), args.get(2));
            this.minAltitude = elevations.getFirst();
            this.maxAltitude = elevations.getSecond();
        } else {
            // Extremely rough default for legacy presets
            this.minAltitude = 30;
            this.maxAltitude = 60;
        }
    }

    @Override
    public String toString()
    {
        return "Fossil(" + this.rarity + "," + this.minAltitude + "," + this.maxAltitude + ")";
    }

    @Override
    public void spawn(IWorldGenRegion world, Random random, int x, int z)
    {
        int y = RandomHelper.numberInRange(random, this.minAltitude, this.maxAltitude);
        world.placeFossil(random, x, y, z);
    }
}
