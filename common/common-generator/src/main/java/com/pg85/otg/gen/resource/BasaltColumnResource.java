package com.pg85.otg.gen.resource;

import com.pg85.otg.util.Pair;
import com.pg85.otg.config.settings.biome.BiomeSettings;
import com.pg85.otg.exceptions.InvalidConfigException;
import com.pg85.otg.gen.resource.util.PositionHelper;
import com.pg85.otg.interfaces.IWorldGenRegion;
import com.pg85.otg.util.materials.LocalMaterialData;
import com.pg85.otg.util.materials.MaterialSet;

import java.util.List;
import java.util.Random;

public class BasaltColumnResource extends FrequencyResourceBase
{
    private final int baseSize;
    private final int sizeVariance;
    private final int baseHeight;
    private final int heightVariance;
    private final int minAltitude;
    private final int maxAltitude;
    private final LocalMaterialData material;
    private final MaterialSet sourceBlocks;

    public BasaltColumnResource(BiomeSettings biomeConfig, List<String> args)
        throws InvalidConfigException
    {
        super(biomeConfig, args);
        assureSize(8, args);

        this.material = readMaterial(args.get(0));
        this.frequency = readFrequency(args.get(1));
        this.rarity = readRarity(args.get(2));
        this.baseSize = readInt(args.get(3), 1, 5);
        this.sizeVariance = readInt(args.get(4), 0, 5);
        this.baseHeight = readInt(args.get(5), 1, 5);
        this.heightVariance = readInt(args.get(6), 0, 5);
        Pair<Integer, Integer> elevations = readElevations(args.get(7), args.get(8));
        this.minAltitude = elevations.getFirst();
        this.maxAltitude = elevations.getSecond();
        this.sourceBlocks = readMaterials(args, 9);
    }

    @Override
    public void spawn(IWorldGenRegion world, Random random, int x, int z)
    {
        int y = world.getHighestBlockYAt(x, z, true, false, true, true, true) + 1;

        if (y < this.minAltitude || y > this.maxAltitude)
            return;

        if (!canPlaceAt(world, x, y, z))
            return;

        int height =
            this.heightVariance == 0 ? this.baseHeight : this.baseHeight + random.nextInt(this.heightVariance + 1);

        boolean isLarge = (random.nextFloat() < 0.9F);
        int maxSpread = Math.min(height, isLarge ? 5 : 8);
        int clusterSize = isLarge ? 50 : 15;

        for (int[] point : PositionHelper.randomBetweenClosed(
            random, clusterSize, x - maxSpread, y, z - maxSpread,
            x + maxSpread, y, z + maxSpread
        )) {
            int columnHeight = height - PositionHelper.distManhattan(point[0], point[1], point[2], x, y, z);
            if (columnHeight >= 0)
                placeColumn(
                    world,
                    y,
                    point[0],
                    point[1],
                    point[2],
                    columnHeight,
                    this.sizeVariance == 0 ?
                        this.baseSize :
                        this.baseSize + random.nextInt(this.sizeVariance + 1)
                );

        }
    }

    private void placeColumn(
        IWorldGenRegion world, int baseY, int x, int y, int z, int maxHeight,
        int radius
    )
    {

        for (int[] offsetPos : PositionHelper.betweenClosed(
            x - radius, y, z - radius, x + radius, y,
            z + radius
        )) {
            int distanceFromCenter = PositionHelper.distManhattan(offsetPos[0], offsetPos[1], offsetPos[2], x, y, z);

            int[] targetPos = world.getMaterialDirect(offsetPos[0], offsetPos[1], offsetPos[2]).isAir()
                ? findSurface(world, baseY, offsetPos, distanceFromCenter)
                : findAir(world, offsetPos, distanceFromCenter);
            if (targetPos == null)
                continue;

            int currentHeight = maxHeight - distanceFromCenter / 2;
            int x2 = targetPos[0];
            int y2 = targetPos[1];
            int z2 = targetPos[2];

            while (currentHeight >= 0) {
                LocalMaterialData current = world.getMaterialDirect(x2, y2, z2);
                if (this.sourceBlocks.contains(current)) {
                    world.setBlockDirect(x2, y2, z2, this.material);
                    y2++;
                } else if (current.isMaterial(this.material)) {
                    y2++;

                } else {
                    break;
                }
                currentHeight--;

            }
        }
    }

    private int[] findAir(IWorldGenRegion world, int[] pos, int distanceLimit)
    {
        while (pos[1] <= world.getWorldInfo().maxY() && distanceLimit > 0) {
            distanceLimit--;
            if (this.sourceBlocks.contains(world.getMaterialDirect(pos[0], pos[1], pos[2])))
                return pos;

            pos[1]++;
        }
        return null;
    }

    private static boolean canPlaceAt(IWorldGenRegion world, int x, int y, int z)
    {
        if (world.getMaterialDirect(x, y, z).isAir()) {
            return (!world.getMaterialDirect(x, y - 1, z).isAir());
        }
        return false;
    }

    private static int[] findSurface(IWorldGenRegion world, int minY, int[] pos, int distanceLimit)
    {
        while (pos[1] > minY && distanceLimit > 0) {
            distanceLimit--;
            if (canPlaceAt(world, pos[0], pos[1], pos[2]))
                return pos;

            pos[1]--;
        }
        return null;
    }

    @Override
    public String toString()
    {
        return "BasaltColumn(" + this.material + "," + this.frequency + "," + this.rarity + "," + this.baseSize + ","
               + this.sizeVariance + "," + this.baseHeight + "," + this.heightVariance + "," + this.minAltitude + ","
               + this.maxAltitude + makeMaterials(this.sourceBlocks) + ")";
    }
}
