package com.pg85.otg.shared.gen;

import com.pg85.otg.config.preset.DimensionPresetConfig;
import com.pg85.otg.config.settings.biome.BiomeSettings;
import com.pg85.otg.gen.OTGChunkGenerator;
import com.pg85.otg.interfaces.IBiome;
import com.pg85.otg.interfaces.ICachedBiomeProvider;
import com.pg85.otg.interfaces.IEntityFunction;
import com.pg85.otg.interfaces.IPluginConfig;
import com.pg85.otg.interfaces.IUndergroundBiomeMap;
import com.pg85.otg.util.ChunkCoordinate;
import com.pg85.otg.util.OTGLog;
import com.pg85.otg.util.Vec3;
import com.pg85.otg.util.biome.ReplaceBlockMatrix;
import com.pg85.otg.util.gen.LocalWorldGenRegion;
import com.pg85.otg.util.gen.OTGWorldInfo;
import com.pg85.otg.util.logging.LogCategory;
import com.pg85.otg.shared.materials.SharedMaterialData;
import com.pg85.otg.shared.util.SharedNBTHelper;
import com.pg85.otg.util.materials.LocalMaterialData;
import com.pg85.otg.util.materials.LocalMaterials;
import com.pg85.otg.util.minecraft.TreeType;
import com.pg85.otg.util.nbt.NamedBinaryTag;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.features.CaveFeatures;
import net.minecraft.data.worldgen.features.EndFeatures;
import net.minecraft.data.worldgen.features.TreeFeatures;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;

import java.util.Optional;
import java.util.Random;

public abstract class SharedWorldGenRegion extends LocalWorldGenRegion {
    private final DimensionPresetConfig presetConfig;
    private final OTGWorldInfo otgWorldInfo;
    protected final WorldGenLevel worldGenLevel;
    protected final ChunkGenerator chunkGenerator;
    private final int EMPTY;

    // Underground biome region mask
    private IUndergroundBiomeMap undergroundMask;
    private int activeUndergroundBiomeId = -1;

    protected SharedWorldGenRegion(
        String presetFolderName,
        IPluginConfig pluginConfig,
        DimensionPresetConfig presetConfig,
        OTGWorldInfo otgWorldInfo,
        WorldGenLevel worldGenLevel,
        ChunkAccess chunkAccess,
        ChunkGenerator chunkGenerator,
        OTGChunkGenerator internalGenerator
    ) {
        super(
            presetFolderName,
            pluginConfig,
            presetConfig,
            chunkAccess.getPos().x,
            chunkAccess.getPos().z,
            internalGenerator.getCachedBiomeProvider()
        );
        this.presetConfig = presetConfig;
        this.otgWorldInfo = otgWorldInfo;
        this.worldGenLevel = worldGenLevel;
        this.chunkGenerator = chunkGenerator;
        this.EMPTY = otgWorldInfo.getEmptyChunkIndex();
    }

    // --- Abstract methods: platform-specific ---

    protected LocalMaterialData fromBlockState(BlockState blockState) {
        return SharedMaterialData.ofBlockState(blockState);
    }

    protected BlockState toBlockState(LocalMaterialData material) {
        return ((SharedMaterialData) material).getState();
    }

    protected CompoundTag convertNBT(NamedBinaryTag tag) {
        return SharedNBTHelper.getNMSFromNBTTagCompound(tag);
    }

    protected abstract OTGChunkGenerator getInternalGenerator();

    protected abstract LocalMaterialData getMaterialInUnloadedChunk(int x, int y, int z);

    protected abstract int getHighestBlockYInUnloadedChunk(int x, int z, boolean findSolid, boolean findLiquid, boolean ignoreLiquid, boolean ignoreSnow);

    // --- Shared implementation ---

    @Override
    public OTGWorldInfo getWorldInfo() {
        return this.otgWorldInfo;
    }

    @Override
    public long getSeed() {
        return worldGenLevel.getSeed();
    }

    @Override
    public ChunkCoordinate getSpawnChunk() {
        if (this.getConfig().getSpawnSettings().isSpawnPointSet()) {
            return ChunkCoordinate.fromBlockCoords(
                this.getConfig().getSpawnSettings().getSpawnPointX(),
                this.getConfig().getSpawnSettings().getSpawnPointZ()
            );
        } else {
            BlockPos spawnPos = this.worldGenLevel.getLevel().getSharedSpawnPos();
            return ChunkCoordinate.fromBlockCoords(spawnPos.getX(), spawnPos.getZ());
        }
    }

    @Override
    public ICachedBiomeProvider getCachedBiomeProvider() {
        return getInternalGenerator().getCachedBiomeProvider();
    }

    @Override
    public IBiome getBiomeForDecoration(int x, int z) {
        // TOOD: Don't use this.decorationArea == null for worldgenregions
        // doing things outside of population, split up worldgenregion
        // into separate classes, one for decoration, one for non-decoration.
        return this.decorationBiomeCache != null ?
            this.decorationBiomeCache.getBiome(x, z) :
            this.getCachedBiomeProvider().getBiome(x, z);
    }

    @Override
    public BiomeSettings getBiomeConfigForDecoration(int worldX, int worldZ) {
        // TOOD: Don't use this.decorationArea == null for worldgenregions
        // doing things outside of population, split up worldgenregion
        // into separate classes, one for decoration, one for non-decoration.
        return this.decorationBiomeCache != null ?
            this.decorationBiomeCache.getBiomeConfig(worldX, worldZ) :
            this.getCachedBiomeProvider().getBiomeConfig(worldX, worldZ);
    }

    @Override
    public boolean placeTree(TreeType type, Random rand, int x, int y, int z) {
        if (isOutsideWorldHeight(y)) {
            return false;
        }
        BlockPos pos = new BlockPos(x, y, z);

        var r = worldGenLevel.getLevel().registryAccess().registry(Registries.CONFIGURED_FEATURE);
        if (r.isEmpty()) {
            OTGLog.fatal("Failed to get registry for configured features.");
            return true; // keep it from trying again
        }
        var featureRegistry = r.get();

        try {
            Optional<ConfiguredFeature<?, ?>> tree;
            switch (type) {
                case Tree -> tree = featureRegistry.getOptional(TreeFeatures.OAK);
                case BigTree -> tree = featureRegistry.getOptional(TreeFeatures.FANCY_OAK);
                case Forest, Birch -> tree = featureRegistry.getOptional(TreeFeatures.BIRCH);
                case TallBirch -> tree = featureRegistry.getOptional(TreeFeatures.SUPER_BIRCH_BEES_0002);
                case HugeMushroom -> {
                    if (rand.nextBoolean()) {
                        tree = featureRegistry.getOptional(TreeFeatures.HUGE_BROWN_MUSHROOM);
                    } else {
                        tree = featureRegistry.getOptional(TreeFeatures.HUGE_RED_MUSHROOM);
                    }
                }
                case HugeRedMushroom -> tree = featureRegistry.getOptional(TreeFeatures.HUGE_RED_MUSHROOM);
                case HugeBrownMushroom -> tree = featureRegistry.getOptional(TreeFeatures.HUGE_BROWN_MUSHROOM);
                case SwampTree -> tree = featureRegistry.getOptional(TreeFeatures.SWAMP_OAK);
                case Taiga1 -> tree = featureRegistry.getOptional(TreeFeatures.PINE);
                case Taiga2 -> tree = featureRegistry.getOptional(TreeFeatures.SPRUCE);
                case JungleTree -> tree = featureRegistry.getOptional(TreeFeatures.MEGA_JUNGLE_TREE);
                case CocoaTree -> tree = featureRegistry.getOptional(TreeFeatures.JUNGLE_TREE);
                case GroundBush -> tree = featureRegistry.getOptional(TreeFeatures.JUNGLE_BUSH);
                case Acacia -> tree = featureRegistry.getOptional(TreeFeatures.ACACIA);
                case DarkOak -> tree = featureRegistry.getOptional(TreeFeatures.DARK_OAK);
                case HugeTaiga1 -> tree = featureRegistry.getOptional(TreeFeatures.MEGA_PINE);
                case HugeTaiga2 -> tree = featureRegistry.getOptional(TreeFeatures.MEGA_SPRUCE);
                case CrimsonFungi -> tree = featureRegistry.getOptional(TreeFeatures.CRIMSON_FUNGUS_PLANTED);
                case WarpedFungi -> tree = featureRegistry.getOptional(TreeFeatures.WARPED_FUNGUS_PLANTED);
                case ChorusPlant -> tree = featureRegistry.getOptional(EndFeatures.CHORUS_PLANT);
                case Mangrove -> tree = featureRegistry.getOptional(TreeFeatures.MANGROVE);
                case TallMangrove -> tree = featureRegistry.getOptional(TreeFeatures.TALL_MANGROVE);
                case Cherry -> tree = featureRegistry.getOptional(TreeFeatures.CHERRY);
                default -> throw new RuntimeException("Failed to handle tree of type " + type);
            }
            return tree.map(
                           configuredFeature -> configuredFeature.place(
                               worldGenLevel,
                               chunkGenerator,
                               worldGenLevel.getRandom(),
                               pos
                           ))
                       .orElse(false);
        } catch (NullPointerException | IndexOutOfBoundsException ex) {
            OTGLog.error(LogCategory.DECORATION, "Treegen caused an error: {}", (Object) ex.getStackTrace());
            // Return true to prevent further attempts.
            return true;
        }
    }

    @Override
    public LocalMaterialData getMaterial(int x, int y, int z) {
        if (isOutsideBounds(x, y, z)) {
            return null;
        }

        return getMaterialDirect(x, y, z);
    }

    @Override
    public LocalMaterialData getMaterial(int x, int y, int z, Vec3 vec) {
        return getMaterial(x + vec.dx(), y + vec.dy(), z + vec.dz());
    }

    private boolean isOutsideBounds(int x, int y, int z) {
        return isOutsideWorldHeight(y) || isOutsideDecorationArea(x, z);
    }

    private boolean isOutsideWorldHeight(int y) {
        return y > otgWorldInfo.maxY() || y < otgWorldInfo.minY();
    }

    private boolean isOutsideDecorationArea(int x, int z) {
        return !this.decorationArea.isInAreaBeingDecorated(x, z);
    }

    @Override
    public LocalMaterialData getMaterialDirect(int x, int y, int z) {
        return fromBlockState(this.worldGenLevel.getBlockState(new BlockPos(x, y, z)));
    }

    @Override
    public int getBlockAboveLiquidHeight(int x, int z) {
        int highestY = getHighestBlockYAt(x, z, false, true, false, false, false);
        if (highestY > EMPTY) {
            return highestY + 1;
        } else {
            return EMPTY;
        }
    }

    @Override
    public int getHighestSolidBlockAt(int x, int z) {
        return getHighestBlockYAt(x, z, true, false, true, false, false);
    }

    @Override
    public int getHighestYAt(int x, int z) {
        return getHighestBlockYAt(x, z, true, true, false, false, false);
    }

    @Override
    public int getBlockAboveSolidHeight(int x, int z) {
        int highestY = getHighestBlockYAt(x, z, true, false, true, true, false);
        if (highestY > EMPTY) {
            return highestY + 1;
        } else {
            return EMPTY;
        }
    }

    @Override
    public int getHighestBlockAboveYAt(int x, int z) {
        int highestY = getHighestBlockYAt(x, z, true, true, false, false, false);
        if (highestY > EMPTY) {
            return highestY + 1;
        } else {
            return EMPTY;
        }
    }

    @Override
    public int getHighestBlockYAt(
        int x,
        int z,
        boolean findSolid,
        boolean findLiquid,
        boolean ignoreLiquid,
        boolean ignoreSnow,
        boolean ignoreLeaves
    ) {
        if (isOutsideDecorationArea(x, z)) {
            return EMPTY;
        }

        int heightMapY = worldGenLevel.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);

        return getHighestBlockYAt(
            worldGenLevel,
            x,
            heightMapY,
            z,
            findSolid,
            findLiquid,
            ignoreLiquid,
            ignoreSnow,
            ignoreLeaves
        );
    }

    protected int getHighestBlockYAt(
        WorldGenLevel worldGenLevel,
        int internalX,
        int heightMapY,
        int internalZ,
        boolean findSolid,
        boolean findLiquid,
        boolean ignoreLiquid,
        boolean ignoreSnow,
        boolean ignoreLeaves
    ) {
        LocalMaterialData material;
        boolean isSolid;
        boolean isLiquid;
        BlockState blockState;
        Block block;

        for (int i = heightMapY; i >= 0; i--) {
            blockState = worldGenLevel.getBlockState(new BlockPos(internalX, i, internalZ));
            block = blockState.getBlock();
            material = fromBlockState(blockState);
            isLiquid = material.isLiquid();
            isSolid =
                (
                    (
                        material.isSolid() &&
                        (!ignoreLeaves || (!material.isLog() && !material.isLeaves()))
                    )
                    ||
                    (
                        !ignoreLeaves && material.isLeaves()
                    ) || (
                        !ignoreSnow &&
                        block == Blocks.SNOW
                    )
                );
            if (!(ignoreLiquid && isLiquid)) {
                if ((findSolid && isSolid) || (findLiquid && isLiquid)) {
                    return i;
                }
                if ((findSolid && isLiquid) || (findLiquid && isSolid)) {
                    return EMPTY;
                }
            }
        }

        // Can happen if this is a worldGenLevel filled with air
        return EMPTY;
    }

    @Override
    public int getHeightMapHeight(int x, int z) {
        return this.worldGenLevel.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
    }

    @Override
    public int getLightLevel(int x, int y, int z) {
        if (isOutsideBounds(x, y, z)) {
            return -1;
        }

        int chunkX = x >> 4;
        int chunkZ = z >> 4;

        // Check if the chunk has been lit, otherwise cancel.
        if (worldGenLevel.getChunk(chunkX, chunkZ).getPersistedStatus().isOrAfter(ChunkStatus.LIGHT)) {
            // Get the light level of the block state? Different from old behaviour
            // TODO: Check that this does not break in 1.20
            return this.worldGenLevel.getLightEmission(new BlockPos(x, y, z));
        }
        return -1;
    }

    @Override
    public void beginUndergroundBiomeMask(IUndergroundBiomeMap map, int activeUndergroundBiomeId) {
        this.undergroundMask = map;
        this.activeUndergroundBiomeId = activeUndergroundBiomeId;
    }

    @Override
    public void endUndergroundBiomeMask() {
        this.undergroundMask = null;
        this.activeUndergroundBiomeId = -1;
    }

    private boolean maskedOut(int x, int y, int z) {
        IUndergroundBiomeMap m = this.undergroundMask;
        return m != null && m.getUndergroundBiomeId(x, y, z) != this.activeUndergroundBiomeId;
    }

    // TODO: Only used by resources using 3x3 decoration atm (so icebergs). Align all resources
    // to use 3x3, make them use the decoration cache and remove this method.
    @Override
    public void setBlockDirect(int x, int y, int z, LocalMaterialData material) {
        if (maskedOut(x, y, z)) return;
        if (material == null || material.isEmpty()) {
            return;
        }
        BiomeSettings biomeConfig = this.getCachedBiomeProvider().getBiomeConfig(x, z, true);
        if (biomeConfig.getSurfaceSettings().getReplacedBlocks() != null) {
            material = material.parseWithBiomeAndHeight(
                biomeConfig.biomeConfigsHaveReplacement(),
                biomeConfig.getSurfaceSettings().getReplacedBlocks(),
                y
            );
        }
        BlockState state = toBlockState(material);
        if (state == null) {
            return;
        }
        this.worldGenLevel.setBlock(new BlockPos(x, y, z), state, 18);
    }

    @Override
    public void setBlock(int x, int y, int z, LocalMaterialData material) {
        if (maskedOut(x, y, z)) return;
        setBlock(x, y, z, material, null, null);
    }

    @Override
    public void setBlock(int x, int y, int z, LocalMaterialData material, NamedBinaryTag metaDataTag) {
        if (maskedOut(x, y, z)) return;
        setBlock(x, y, z, material, metaDataTag, null);
    }

    @Override
    public void setBlock(int x, int y, int z, LocalMaterialData material, ReplaceBlockMatrix replaceBlocksMatrix) {
        if (maskedOut(x, y, z)) return;
        setBlock(x, y, z, material, null, replaceBlocksMatrix);
    }

    @Override
    public void setBlock(
        int x,
        int y,
        int z,
        LocalMaterialData material,
        NamedBinaryTag nbt,
        ReplaceBlockMatrix replaceBlocksMatrix
    ) {
        if (maskedOut(x, y, z)) return;
        if (isOutsideWorldHeight(y)) {
            return;
        }
        if (material == null || material.isEmpty()) {
            return;
        }
        if (replaceBlocksMatrix != null) {
            material = material.parseWithBiomeAndHeight(
                this.presetConfig.isBiomeConfigsHaveReplacement(),
                replaceBlocksMatrix,
                y
            );
        }
        BlockPos pos = new BlockPos(x, y, z);
        BlockState placedState = toBlockState(material);
        if (placedState == null) {
            return;
        }
        // Notify world: (2 | 16) == update client, don't update observers
        this.worldGenLevel.setBlock(pos, placedState, 18);

        // Update connection states for blocks like panes, bars, fences, walls.
        // These blocks have directional properties (north/south/east/west) that
        // depend on adjacent blocks, but flag 18 skips neighbor updates.
        BlockState updatedState = Block.updateFromNeighbourShapes(placedState, this.worldGenLevel, pos);
        if (updatedState != placedState) {
            this.worldGenLevel.setBlock(pos, updatedState, 18);
            // Also update horizontal neighbors that may need to connect to this block
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                BlockPos neighborPos = pos.relative(dir);
                BlockState neighborState = this.worldGenLevel.getBlockState(neighborPos);
                BlockState updatedNeighbor = Block.updateFromNeighbourShapes(neighborState, this.worldGenLevel, neighborPos);
                if (updatedNeighbor != neighborState) {
                    this.worldGenLevel.setBlock(neighborPos, updatedNeighbor, 18);
                }
            }
        }

        if (material.isLiquid()) {
            // TODO: Do fluid ticks
        } else if (material.isMaterial(LocalMaterials.COMMAND_BLOCK)) {
            // TODO: Tick command blocks
        }

        if (nbt != null) {
            this.attachTag(x, y, z, nbt, worldGenLevel.getBlockState(pos));
        }
    }

    private void attachTag(int x, int y, int z, NamedBinaryTag Tag, BlockState state) {
        CompoundTag nms = convertNBT(Tag);
        nms.put("x", IntTag.valueOf(x));
        nms.put("y", IntTag.valueOf(y));
        nms.put("z", IntTag.valueOf(z));

        BlockEntity tileEntity = this.worldGenLevel.getBlockEntity(new BlockPos(x, y, z));
        if (tileEntity != null) {
            tileEntity.loadCustomOnly(nms, this.worldGenLevel.registryAccess());
        } else {
            OTGLog.error(LogCategory.CUSTOM_OBJECTS,
                "Skipping tile entity with id {}, cannot be placed at {},{},{}",
                nms.getString("id"), x, y, z);
        }
    }

    public BlockEntity getBlockEntity(BlockPos blockPos) {
        return this.worldGenLevel.getBlockEntity(blockPos);
    }

    @Override
    public void spawnEntity(IEntityFunction entityData) {
        if (entityData.getY() < otgWorldInfo.minY() || entityData.getY() > otgWorldInfo.maxY()) {
            OTGLog.error(LogCategory.CUSTOM_OBJECTS,
                "Failed to spawn mob for Entity() {}, y position out of bounds", entityData.makeString());
            return;
        }

        // Fetch entity type for Entity() mob name
        Entity entity = null;
        Optional<EntityType<?>> type1 = EntityType.byString(entityData.getResourceLocation());
        EntityType<?> type2;
        if (type1.isPresent()) {
            type2 = type1.get();
        } else {
            OTGLog.error(LogCategory.CUSTOM_OBJECTS,
                "Could not parse mob for Entity() {}, mob type could not be found.", entityData.makeString());
            return;
        }

        // Check for any .txt or .nbt file containing nbt data for the entity
        CompoundTag nbtTagCompound = null;
        if (
            entityData.getNameTagOrNBTFileName() != null &&
            (
                entityData.getNameTagOrNBTFileName().toLowerCase().trim().endsWith(".txt")
                || entityData.getNameTagOrNBTFileName().toLowerCase().trim().endsWith(".nbt")
            )
        ) {
            nbtTagCompound = new CompoundTag();
            if (entityData.getNameTagOrNBTFileName().toLowerCase().trim().endsWith(".txt")) {
                try {
                    // Parse SNBT (text-based NBT format like {PersistenceRequired:1})
                    nbtTagCompound = TagParser.parseTag(entityData.getMetaData());
                } catch (Exception e) {
                    OTGLog.error(LogCategory.CUSTOM_OBJECTS,
                        "Could not parse nbt for Entity() {}, file: {}",
                        entityData.makeString(), entityData.getNameTagOrNBTFileName());
                    throw new RuntimeException(
                        "Could not parse nbt for Entity() "
                        + entityData.makeString()
                        + ", file: "
                        + entityData.getNameTagOrNBTFileName(), e
                    );
                }
                // Specify which type of entity to spawn
                nbtTagCompound.putString("id", entityData.getResourceLocation());
            } else if (entityData.getNBTTag() != null) {
                nbtTagCompound = convertNBT(entityData.getNBTTag());
            }
        }

        if (nbtTagCompound == null) {
            // Create entity without nbt data
            try {
                entity = type2.create(worldGenLevel.getLevel());
            } catch (Exception exception) {
                OTGLog.error(LogCategory.CUSTOM_OBJECTS,
                    "Could not create entity for Entity() {}, exception: {}",
                    entityData.makeString(), exception.getMessage());
                return;
            }
            if (entity == null) {
                OTGLog.error(LogCategory.CUSTOM_OBJECTS,
                    "Could not create entity for Entity() {}, MC returned null.", entityData.makeString());
                return;
            } else {
                entity.moveTo(
                    entityData.getX(),
                    entityData.getY(),
                    entityData.getZ(),
                    this.worldGenLevel.getRandom().nextFloat() * 360.0F,
                    0.0F
                );
            }
        } else {
            // Create entity with nbt data
            try {
                entity = EntityType.loadEntityRecursive(
                    nbtTagCompound, this.worldGenLevel.getLevel(), (entity1) ->
                    {
                        entity1.moveTo(
                            entityData.getX(),
                            entityData.getY(),
                            entityData.getZ(),
                            this.worldGenLevel.getRandom().nextFloat() * 360.0F,
                            0.0F
                        );
                        return entity1;
                    }
                );
            } catch (Exception ignored) {
                OTGLog.warn(LogCategory.CUSTOM_OBJECTS, "Failed to load entity from NBT: {}", ignored.getMessage());
            }
            if (entity == null) {
                OTGLog.error(LogCategory.CUSTOM_OBJECTS,
                    "Could not create entity for Entity() {}, MC returned null.", entityData.makeString());
                return;
            }
        }
        // Create and spawn entities according to group size
        for (int r = 0; r < entityData.getGroupSize(); r++) {
            if (r != 0) {
                if (nbtTagCompound == null) {
                    // Create entity without nbt data
                    try {
                        entity = type2.create(this.worldGenLevel.getLevel());
                    } catch (Exception exception) {
                        return;
                    }
                    if (entity == null) {
                        return;
                    } else {
                        entity.moveTo(
                            entityData.getX(),
                            entityData.getY(),
                            entityData.getZ(),
                            this.worldGenLevel.getRandom().nextFloat() * 360.0F,
                            0.0F
                        );
                    }
                } else {
                    // Create entity with nbt data
                    entity = EntityType.loadEntityRecursive(
                        nbtTagCompound, this.worldGenLevel.getLevel(), (entity1) -> {
                            entity1.moveTo(
                                entityData.getX(),
                                entityData.getY(),
                                entityData.getZ(),
                                this.worldGenLevel.getRandom().nextFloat() * 360.0F,
                                0.0F
                            );
                            return entity1;
                        }
                    );
                }
                if (entity == null) {
                    return;
                }
            }

            // TODO: Non-mob entities, aren't those handled via Block(nbt), chests, armor stands etc?
            if (entity instanceof Monster monster) {
                // If the block is a solid block or entity is a fish out of water, cancel
                LocalMaterialData block = fromBlockState(this.worldGenLevel.getBlockState(new BlockPos(
                    (int) entityData.getX(),
                    entityData.getY(),
                    (int) entityData.getZ()
                )));
                if (
                    block.isSolid() ||
                    (
                        (
                            monster.canBreatheUnderwater()
                            || monster instanceof Guardian
                        )
                        && !block.isLiquid()
                    )
                ) {
                    OTGLog.error(LogCategory.CUSTOM_OBJECTS,
                        "Could not spawn entity at {} {} {} for Entity() {}, a solid block was found or a water mob tried to spawn outside of water.",
                        entityData.getX(), entityData.getY(), entityData.getZ(), entityData.makeString());
                    continue;
                }

                // Attach nametag if one was provided via Entity()
                String nameTag = entityData.getNameTagOrNBTFileName();
                if (nameTag != null && !nameTag.toLowerCase().trim().endsWith(".txt") && !nameTag.toLowerCase()
                                                                                                 .trim()
                                                                                                 .endsWith(".nbt")) {
                    entity.setCustomName(Component.literal(nameTag));
                }
                // Make sure Entity() mobs don't de-spawn, regardless of nbt data
                monster.setPersistenceRequired();

                SpawnGroupData spawnGroupData =
                    monster.finalizeSpawn(
                        this.worldGenLevel,
                        this.worldGenLevel.getCurrentDifficultyAt(new BlockPos(
                            (int) entityData.getX(),
                            entityData.getY(),
                            (int) entityData.getZ()
                        )),
                        MobSpawnType.CHUNK_GENERATION,
                        null // TODO: Missing functionality in EntityFunction
                    );
                this.worldGenLevel.addFreshEntity(monster);
            }
        }
    }

    @Override
    public void placeDungeon(Random random, int x, int y, int z) {
        Feature.MONSTER_ROOM.place(
            FeatureConfiguration.NONE,
            worldGenLevel,
            chunkGenerator,
            worldGenLevel.getRandom(),
            new BlockPos(x, y, z)
        );
    }

    @Override
    public void placeFossil(Random random, int x, int y, int z) {
        var r = worldGenLevel.getLevel().registryAccess().registry(Registries.CONFIGURED_FEATURE);
        if (r.isPresent()) {
            var feature = r.get().getOptional(CaveFeatures.FOSSIL_COAL);
            feature.ifPresent(configuredFeature -> configuredFeature.place(
                worldGenLevel,
                chunkGenerator,
                worldGenLevel.getRandom(),
                new BlockPos(x, y, z)
            ));
        }
    }

    @Override
    public boolean isInsideWorldBorder(ChunkCoordinate chunkCoordinate) {
        return worldGenLevel.getWorldBorder().isWithinBounds(chunkCoordinate.getChunkX(), chunkCoordinate.getChunkZ());
    }

    @Override
    public LocalMaterialData getMaterialWithoutLoading(int x, int y, int z) {
        if (isOutsideWorldHeight(y)) {
            return null;
        }
        ChunkPos pos = ChunkPos.minFromRegion(x, z);
        ChunkAccess chunk = null;

        if (this.decorationArea.isInAreaBeingDecorated(x, z)) {
            chunk = this.worldGenLevel.hasChunk(pos.x, pos.z)
                ? this.worldGenLevel.getChunk(pos.x, pos.z, ChunkStatus.CARVERS, false)
                : null;
        }

        if (chunk == null) {
            return getMaterialInUnloadedChunk(x, y, z);
        }
        return fromBlockState(worldGenLevel.getBlockState(new BlockPos(x, y, z)));
    }

    @Override
    public int getHighestBlockYAtWithoutLoading(
        int x,
        int z,
        boolean findSolid,
        boolean findLiquid,
        boolean ignoreLiquid,
        boolean ignoreSnow,
        boolean ignoreLeaves
    ) {

        ChunkPos pos = ChunkPos.minFromRegion(x, z);
        ChunkAccess chunk = null;


        if (this.decorationArea.isInAreaBeingDecorated(x, z)) {
            chunk = this.worldGenLevel.hasChunk(pos.x, pos.z)
                ? this.worldGenLevel.getChunk(pos.x, pos.z, ChunkStatus.CARVERS, false)
                : null;
        }
        if (chunk == null || !chunk.getPersistedStatus().isOrAfter(ChunkStatus.CARVERS)) {
            return getHighestBlockYInUnloadedChunk(
                x,
                z,
                findSolid,
                findLiquid,
                ignoreLiquid,
                ignoreSnow
            );
        }

        int levelHeight = worldGenLevel.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
        return getHighestBlockYAt(
            worldGenLevel,
            x,
            levelHeight,
            z,
            findSolid,
            findLiquid,
            ignoreLiquid,
            ignoreSnow,
            ignoreLeaves
        );
    }

    @Override
    public boolean chunkHasDefaultStructure(Random worldRandom, ChunkCoordinate chunkCoordinate) {
        // TODO: Fix when shadow generation is implemented
        return false;
    }

    @Override
    public double getBiomeBlocksNoiseValue(int xInWorld, int zInWorld) {
        return getInternalGenerator().getBiomeBlocksNoiseValue(xInWorld, zInWorld);
    }
}
