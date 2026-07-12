package com.pg85.otg.test.materials;

import com.pg85.otg.util.materials.LocalMaterials;

import java.util.ArrayList;

/**
 * Initializes all LocalMaterials static fields with TestMaterialData instances.
 * This allows the generator to run without Minecraft.
 *
 * Call init() once at test startup before any code accesses LocalMaterials.
 */
public class TestMaterials {

    private static boolean initialized = false;

    /**
     * Initializes all LocalMaterials fields with TestMaterialData instances.
     * Safe to call multiple times - subsequent calls are no-ops.
     */
    public static void init() {
        if (initialized) {
            return;
        }

        // Air types
        LocalMaterials.AIR = TestMaterialData.air("minecraft:air");
        LocalMaterials.CAVE_AIR = TestMaterialData.air("minecraft:cave_air");
        LocalMaterials.STRUCTURE_VOID = TestMaterialData.air("minecraft:structure_void");

        // Special blocks
        LocalMaterials.COMMAND_BLOCK = TestMaterialData.solid("minecraft:command_block");
        LocalMaterials.STRUCTURE_BLOCK = TestMaterialData.solid("minecraft:structure_block");

        // Terrain blocks
        LocalMaterials.GRASS = TestMaterialData.solid("minecraft:grass_block");
        LocalMaterials.DIRT = TestMaterialData.solid("minecraft:dirt");
        LocalMaterials.PODZOL = TestMaterialData.solid("minecraft:podzol");
        LocalMaterials.CLAY = TestMaterialData.solid("minecraft:clay");
        LocalMaterials.TERRACOTTA = TestMaterialData.solid("minecraft:terracotta");
        LocalMaterials.WHITE_TERRACOTTA = TestMaterialData.solid("minecraft:white_terracotta");
        LocalMaterials.ORANGE_TERRACOTTA = TestMaterialData.solid("minecraft:orange_terracotta");
        LocalMaterials.YELLOW_TERRACOTTA = TestMaterialData.solid("minecraft:yellow_terracotta");
        LocalMaterials.BROWN_TERRACOTTA = TestMaterialData.solid("minecraft:brown_terracotta");
        LocalMaterials.RED_TERRACOTTA = TestMaterialData.solid("minecraft:red_terracotta");
        LocalMaterials.SILVER_TERRACOTTA = TestMaterialData.solid("minecraft:light_gray_terracotta");
        LocalMaterials.STONE = TestMaterialData.solid("minecraft:stone");
        LocalMaterials.DEEPSLATE = TestMaterialData.solid("minecraft:deepslate");
        LocalMaterials.NETHERRACK = TestMaterialData.solid("minecraft:netherrack");
        LocalMaterials.END_STONE = TestMaterialData.solid("minecraft:end_stone");
        LocalMaterials.SAND = TestMaterialData.solid("minecraft:sand");
        LocalMaterials.RED_SAND = TestMaterialData.solid("minecraft:red_sand");
        LocalMaterials.SANDSTONE = TestMaterialData.solid("minecraft:sandstone");
        LocalMaterials.RED_SANDSTONE = TestMaterialData.solid("minecraft:red_sandstone");
        LocalMaterials.GRAVEL = TestMaterialData.solid("minecraft:gravel");
        LocalMaterials.MOSSY_COBBLESTONE = TestMaterialData.solid("minecraft:mossy_cobblestone");
        LocalMaterials.SNOW = TestMaterialData.nonSolid("minecraft:snow");
        LocalMaterials.SNOW_BLOCK = TestMaterialData.solid("minecraft:snow_block");
        LocalMaterials.TORCH = TestMaterialData.nonSolid("minecraft:torch");
        LocalMaterials.BEDROCK = TestMaterialData.solid("minecraft:bedrock");
        LocalMaterials.MAGMA = TestMaterialData.solid("minecraft:magma_block");
        LocalMaterials.ICE = TestMaterialData.solid("minecraft:ice");
        LocalMaterials.PACKED_ICE = TestMaterialData.solid("minecraft:packed_ice");
        LocalMaterials.BLUE_ICE = TestMaterialData.solid("minecraft:blue_ice");
        LocalMaterials.FROSTED_ICE = TestMaterialData.solid("minecraft:frosted_ice");
        LocalMaterials.GLOWSTONE = TestMaterialData.solid("minecraft:glowstone");
        LocalMaterials.MYCELIUM = TestMaterialData.solid("minecraft:mycelium");
        LocalMaterials.STONE_SLAB = TestMaterialData.solid("minecraft:stone_slab");

        // Liquids
        LocalMaterials.WATER = TestMaterialData.liquid("minecraft:water");
        LocalMaterials.LAVA = TestMaterialData.liquid("minecraft:lava");

        // Tree logs
        LocalMaterials.ACACIA_LOG = TestMaterialData.solid("minecraft:acacia_log");
        LocalMaterials.BIRCH_LOG = TestMaterialData.solid("minecraft:birch_log");
        LocalMaterials.DARK_OAK_LOG = TestMaterialData.solid("minecraft:dark_oak_log");
        LocalMaterials.JUNGLE_LOG = TestMaterialData.solid("minecraft:jungle_log");
        LocalMaterials.OAK_LOG = TestMaterialData.solid("minecraft:oak_log");
        LocalMaterials.SPRUCE_LOG = TestMaterialData.solid("minecraft:spruce_log");
        LocalMaterials.STRIPPED_ACACIA_LOG = TestMaterialData.solid("minecraft:stripped_acacia_log");
        LocalMaterials.STRIPPED_BIRCH_LOG = TestMaterialData.solid("minecraft:stripped_birch_log");
        LocalMaterials.STRIPPED_DARK_OAK_LOG = TestMaterialData.solid("minecraft:stripped_dark_oak_log");
        LocalMaterials.STRIPPED_JUNGLE_LOG = TestMaterialData.solid("minecraft:stripped_jungle_log");
        LocalMaterials.STRIPPED_OAK_LOG = TestMaterialData.solid("minecraft:stripped_oak_log");
        LocalMaterials.STRIPPED_SPRUCE_LOG = TestMaterialData.solid("minecraft:stripped_spruce_log");

        // Tree wood (bark on all sides)
        LocalMaterials.ACACIA_WOOD = TestMaterialData.solid("minecraft:acacia_wood");
        LocalMaterials.BIRCH_WOOD = TestMaterialData.solid("minecraft:birch_wood");
        LocalMaterials.DARK_OAK_WOOD = TestMaterialData.solid("minecraft:dark_oak_wood");
        LocalMaterials.JUNGLE_WOOD = TestMaterialData.solid("minecraft:jungle_wood");
        LocalMaterials.OAK_WOOD = TestMaterialData.solid("minecraft:oak_wood");
        LocalMaterials.SPRUCE_WOOD = TestMaterialData.solid("minecraft:spruce_wood");

        // Tree leaves
        LocalMaterials.ACACIA_LEAVES = TestMaterialData.solid("minecraft:acacia_leaves");
        LocalMaterials.BIRCH_LEAVES = TestMaterialData.solid("minecraft:birch_leaves");
        LocalMaterials.DARK_OAK_LEAVES = TestMaterialData.solid("minecraft:dark_oak_leaves");
        LocalMaterials.JUNGLE_LEAVES = TestMaterialData.solid("minecraft:jungle_leaves");
        LocalMaterials.OAK_LEAVES = TestMaterialData.solid("minecraft:oak_leaves");
        LocalMaterials.SPRUCE_LEAVES = TestMaterialData.solid("minecraft:spruce_leaves");

        // Flowers
        LocalMaterials.POPPY = TestMaterialData.nonSolid("minecraft:poppy");
        LocalMaterials.BLUE_ORCHID = TestMaterialData.nonSolid("minecraft:blue_orchid");
        LocalMaterials.ALLIUM = TestMaterialData.nonSolid("minecraft:allium");
        LocalMaterials.AZURE_BLUET = TestMaterialData.nonSolid("minecraft:azure_bluet");
        LocalMaterials.RED_TULIP = TestMaterialData.nonSolid("minecraft:red_tulip");
        LocalMaterials.ORANGE_TULIP = TestMaterialData.nonSolid("minecraft:orange_tulip");
        LocalMaterials.WHITE_TULIP = TestMaterialData.nonSolid("minecraft:white_tulip");
        LocalMaterials.PINK_TULIP = TestMaterialData.nonSolid("minecraft:pink_tulip");
        LocalMaterials.OXEYE_DAISY = TestMaterialData.nonSolid("minecraft:oxeye_daisy");
        LocalMaterials.YELLOW_FLOWER = TestMaterialData.nonSolid("minecraft:dandelion");
        LocalMaterials.DEAD_BUSH = TestMaterialData.nonSolid("minecraft:dead_bush");
        LocalMaterials.FERN = TestMaterialData.nonSolid("minecraft:fern");
        LocalMaterials.LONG_GRASS = TestMaterialData.nonSolid("minecraft:grass");

        // Mushrooms
        LocalMaterials.RED_MUSHROOM_BLOCK = TestMaterialData.solid("minecraft:red_mushroom_block");
        LocalMaterials.BROWN_MUSHROOM_BLOCK = TestMaterialData.solid("minecraft:brown_mushroom_block");
        LocalMaterials.RED_MUSHROOM = TestMaterialData.nonSolid("minecraft:red_mushroom");
        LocalMaterials.BROWN_MUSHROOM = TestMaterialData.nonSolid("minecraft:brown_mushroom");

        // Misc plants
        LocalMaterials.PUMPKIN = TestMaterialData.solid("minecraft:pumpkin");
        LocalMaterials.CACTUS = TestMaterialData.solid("minecraft:cactus");
        LocalMaterials.MELON_BLOCK = TestMaterialData.solid("minecraft:melon");
        LocalMaterials.VINE = TestMaterialData.nonSolid("minecraft:vine");
        LocalMaterials.WATER_LILY = TestMaterialData.nonSolid("minecraft:lily_pad");
        LocalMaterials.SUGAR_CANE_BLOCK = TestMaterialData.nonSolid("minecraft:sugar_cane");
        LocalMaterials.BAMBOO = TestMaterialData.nonSolid("minecraft:bamboo");
        LocalMaterials.BAMBOO_SMALL = TestMaterialData.nonSolid("minecraft:bamboo");
        LocalMaterials.BAMBOO_LARGE = TestMaterialData.nonSolid("minecraft:bamboo");
        LocalMaterials.BAMBOO_LARGE_GROWING = TestMaterialData.nonSolid("minecraft:bamboo");
        LocalMaterials.SEAGRASS = TestMaterialData.nonSolid("minecraft:seagrass");
        LocalMaterials.TALL_SEAGRASS_LOWER = TestMaterialData.nonSolid("minecraft:tall_seagrass");
        LocalMaterials.TALL_SEAGRASS_UPPER = TestMaterialData.nonSolid("minecraft:tall_seagrass");
        LocalMaterials.KELP = TestMaterialData.nonSolid("minecraft:kelp");
        LocalMaterials.KELP_PLANT = TestMaterialData.nonSolid("minecraft:kelp_plant");
        LocalMaterials.VINE_NORTH = TestMaterialData.nonSolid("minecraft:vine");
        LocalMaterials.VINE_SOUTH = TestMaterialData.nonSolid("minecraft:vine");
        LocalMaterials.VINE_EAST = TestMaterialData.nonSolid("minecraft:vine");
        LocalMaterials.VINE_WEST = TestMaterialData.nonSolid("minecraft:vine");
        LocalMaterials.SEA_PICKLE = TestMaterialData.nonSolid("minecraft:sea_pickle");

        // Saplings
        LocalMaterials.ACACIA_SAPLING = TestMaterialData.nonSolid("minecraft:acacia_sapling");
        LocalMaterials.BAMBOO_SAPLING = TestMaterialData.nonSolid("minecraft:bamboo_sapling");
        LocalMaterials.BIRCH_SAPLING = TestMaterialData.nonSolid("minecraft:birch_sapling");
        LocalMaterials.DARK_OAK_SAPLING = TestMaterialData.nonSolid("minecraft:dark_oak_sapling");
        LocalMaterials.JUNGLE_SAPLING = TestMaterialData.nonSolid("minecraft:jungle_sapling");
        LocalMaterials.OAK_SAPLING = TestMaterialData.nonSolid("minecraft:oak_sapling");
        LocalMaterials.SPRUCE_SAPLING = TestMaterialData.nonSolid("minecraft:spruce_sapling");

        // Coral lists (empty for headless testing)
        LocalMaterials.CORAL_BLOCKS = new ArrayList<>();
        LocalMaterials.WALL_CORALS = new ArrayList<>();
        LocalMaterials.CORALS = new ArrayList<>();

        // Double tall plants
        LocalMaterials.DOUBLE_TALL_GRASS_LOWER = TestMaterialData.nonSolid("minecraft:tall_grass");
        LocalMaterials.DOUBLE_TALL_GRASS_UPPER = TestMaterialData.nonSolid("minecraft:tall_grass");
        LocalMaterials.LARGE_FERN_LOWER = TestMaterialData.nonSolid("minecraft:large_fern");
        LocalMaterials.LARGE_FERN_UPPER = TestMaterialData.nonSolid("minecraft:large_fern");
        LocalMaterials.LILAC_LOWER = TestMaterialData.nonSolid("minecraft:lilac");
        LocalMaterials.LILAC_UPPER = TestMaterialData.nonSolid("minecraft:lilac");
        LocalMaterials.PEONY_LOWER = TestMaterialData.nonSolid("minecraft:peony");
        LocalMaterials.PEONY_UPPER = TestMaterialData.nonSolid("minecraft:peony");
        LocalMaterials.ROSE_BUSH_LOWER = TestMaterialData.nonSolid("minecraft:rose_bush");
        LocalMaterials.ROSE_BUSH_UPPER = TestMaterialData.nonSolid("minecraft:rose_bush");
        LocalMaterials.SUNFLOWER_LOWER = TestMaterialData.nonSolid("minecraft:sunflower");
        LocalMaterials.SUNFLOWER_UPPER = TestMaterialData.nonSolid("minecraft:sunflower");

        // Ores
        LocalMaterials.COAL_ORE = TestMaterialData.solid("minecraft:coal_ore");
        LocalMaterials.DIAMOND_ORE = TestMaterialData.solid("minecraft:diamond_ore");
        LocalMaterials.EMERALD_ORE = TestMaterialData.solid("minecraft:emerald_ore");
        LocalMaterials.GOLD_ORE = TestMaterialData.solid("minecraft:gold_ore");
        LocalMaterials.IRON_ORE = TestMaterialData.solid("minecraft:iron_ore");
        LocalMaterials.LAPIS_ORE = TestMaterialData.solid("minecraft:lapis_ore");
        LocalMaterials.QUARTZ_ORE = TestMaterialData.solid("minecraft:nether_quartz_ore");
        LocalMaterials.REDSTONE_ORE = TestMaterialData.solid("minecraft:redstone_ore");

        // Ore blocks
        LocalMaterials.GOLD_BLOCK = TestMaterialData.solid("minecraft:gold_block");
        LocalMaterials.IRON_BLOCK = TestMaterialData.solid("minecraft:iron_block");
        LocalMaterials.REDSTONE_BLOCK = TestMaterialData.solid("minecraft:redstone_block");
        LocalMaterials.DIAMOND_BLOCK = TestMaterialData.solid("minecraft:diamond_block");
        LocalMaterials.LAPIS_BLOCK = TestMaterialData.solid("minecraft:lapis_block");
        LocalMaterials.COAL_BLOCK = TestMaterialData.solid("minecraft:coal_block");
        LocalMaterials.QUARTZ_BLOCK = TestMaterialData.solid("minecraft:quartz_block");
        LocalMaterials.EMERALD_BLOCK = TestMaterialData.solid("minecraft:emerald_block");

        // Misc
        LocalMaterials.BERRY_BUSH = TestMaterialData.nonSolid("minecraft:sweet_berry_bush");

        initialized = true;
    }
}
