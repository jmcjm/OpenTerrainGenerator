package com.pg85.otg.gen.resource;

import com.pg85.otg.util.Pair;
import com.pg85.otg.config.settings.biome.BiomeSettings;
import com.pg85.otg.exceptions.InvalidConfigException;
import com.pg85.otg.interfaces.IWorldGenRegion;
import com.pg85.otg.util.Vec3;
import com.pg85.otg.util.materials.LocalMaterialData;
import com.pg85.otg.util.materials.LocalMaterials;

import java.util.List;
import java.util.Random;

public class VinesResource extends FrequencyResourceBase
{

    private enum Direction
    {
        SOUTH(LocalMaterials.VINE_SOUTH, new Vec3(0, 0, 1)),
        NORTH(LocalMaterials.VINE_NORTH, new Vec3(0, 0, -1)),
        EAST(LocalMaterials.VINE_EAST, new Vec3(1, 0, 0)),
        WEST(LocalMaterials.VINE_WEST, new Vec3(-1, 0, 0));

        private final LocalMaterialData data;
        private final Vec3 vec;

        Direction(LocalMaterialData data, Vec3 vec)
        {
            this.data = data;
            this.vec = vec;
        }

        Direction getClockwise()
        {
            return switch (this) {
                case NORTH -> EAST;
                case EAST -> SOUTH;
                case SOUTH -> WEST;
                case WEST -> NORTH;
            };
        }
    }

    private final int maxAltitude;
    private final int minAltitude;

    public VinesResource(BiomeSettings biomeConfig, List<String> args) throws InvalidConfigException
    {
        super(biomeConfig, args);

        assureSize(4, args);
        this.frequency = readInt(args.get(0), 1, 100);
        this.rarity = readRarity(args.get(1));
        Pair<Integer, Integer> elevations = readElevations(args.get(2), args.get(3));
        this.minAltitude = elevations.getFirst();
        this.maxAltitude = elevations.getSecond();
    }

    @Override
    public void spawn(IWorldGenRegion worldGenRegion, Random rand, int x, int z)
    {
        int targetX = x;
        int targetZ = z;
        int y = this.minAltitude;

        LocalMaterialData worldMaterial;
        LocalMaterialData sourceBlock;
        // Start with a random direction
        Direction direction = Direction.values()[rand.nextInt(4)];
        while (y <= this.maxAltitude) {
            worldMaterial = worldGenRegion.getMaterial(targetX, y, targetZ);
            if (worldMaterial != null && worldMaterial.isAir()) {
                for (int i = 0; i < 4; i++) {

                    sourceBlock = worldGenRegion.getMaterial(targetX, y, targetZ, direction.vec);

                    // Check if we can place the vine here
                    if (sourceBlock != null &&
                        sourceBlock.isSolid() &&
                        !sourceBlock.isMaterial(LocalMaterials.BAMBOO)
                    )
                    {
                        worldGenRegion.setBlock(targetX, y, targetZ, direction.data);
                        break;
                    }
                    // Rotate if we can't place - remove constant south bias
                    // and keep consistency in the same x/z placement
                    direction = direction.getClockwise();
                }
            } else {
                targetX = x + rand.nextInt(4) - rand.nextInt(4);
                targetZ = z + rand.nextInt(4) - rand.nextInt(4);
            }
            y++;
        }
    }

    @Override
    public String toString()
    {
        return "Vines(" + this.frequency + "," + this.rarity + "," + this.minAltitude + "," + this.maxAltitude + ")";
    }
}
