package com.pg85.otg.shared.portals;

import com.pg85.otg.OTG;
import com.pg85.otg.config.dimensions.WorldPresetConfig;
import com.pg85.otg.config.dimensions.WorldPresetConfig.OTGDimension;
import com.pg85.otg.shared.commands.OTGCommandRegistrar;
import com.pg85.otg.shared.dimensions.DimensionKeys;
import com.pg85.otg.shared.dimensions.DimensionManager;
import com.pg85.otg.shared.gen.SharedOTGChunkGenerator;
import com.pg85.otg.presets.DimensionPreset;
import com.pg85.otg.util.DimensionNameUtils;
import com.pg85.otg.util.materials.LocalMaterialData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public class SharedOTGPortalBlock extends NetherPortalBlock {

    private static volatile Function<String, SharedOTGPortalBlock> portalBlockLookup;
    private static volatile Function<Player, IPortalPlayerData> playerDataAccessor;

    public static void init(
            Function<String, SharedOTGPortalBlock> portalBlockLookup,
            Function<Player, IPortalPlayerData> playerDataAccessor
    ) {
        SharedOTGPortalBlock.portalBlockLookup = portalBlockLookup;
        SharedOTGPortalBlock.playerDataAccessor = playerDataAccessor;
    }

    public static SharedOTGPortalBlock lookupPortalBlock(String color) {
        return portalBlockLookup != null ? portalBlockLookup.apply(color) : null;
    }

    private final String portalColor;

    public SharedOTGPortalBlock(Properties settings, String portalColor) {
        super(settings);
        this.portalColor = portalColor;
    }

    public String getPortalColor() {
        return portalColor;
    }

    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (!entity.isPassenger() && !entity.isVehicle() && entity.canUsePortal(false)) {
            if (level.isClientSide) {
                entity.setAsInsidePortal(this, pos);
                return;
            }

            if (entity.isOnPortalCooldown()) {
                entity.setPortalCooldown();
            } else {
                if (entity instanceof Player player) {
                    IPortalPlayerData data = playerDataAccessor.apply(player);
                    data.setPortalState(true, this.portalColor);

                    int portalTime = data.getPortalTime();
                    int waitTime = this.getPortalTransitionTime((ServerLevel) level, player);

                    if (portalTime >= waitTime) {
                        doTeleport(player, (ServerLevel) level);
                        data.setPortalTime(0);
                    }
                } else {
                    doTeleport(entity, (ServerLevel) level);
                }
            }
        }
    }

    private void doTeleport(Entity entity, ServerLevel serverLevel) {
        if (serverLevel == null) return;

        ServerLevel destination = findDestination(entity, serverLevel);

        if (destination != null && !entity.isPassenger()) {
            entity.setPortalCooldown();
            SharedOTGTeleporter.teleport(entity, destination, this.portalColor);
        }
    }

    private ServerLevel findDestination(Entity entity, ServerLevel currentLevel) {
        MinecraftServer server = currentLevel.getServer();

        if (currentLevel.dimension() == Level.OVERWORLD) {
            return findOTGDimensionByColor(server, this.portalColor);
        }

        if (currentLevel.getChunkSource().getGenerator() instanceof SharedOTGChunkGenerator) {
            WorldPresetConfig activeWorldPreset = WorldPresetPortalResolver.getActiveWorldPreset();
            String dimColor = getEffectivePortalColor(currentLevel, activeWorldPreset);
            if (this.portalColor.equals(dimColor)) {
                return server.overworld();
            }
            return null;
        }

        // Non-OTG custom dimension: return to the overworld if this level is a portal
        // target whose effective color matches this portal.
        Optional<PortalTarget> target = PortalTargetResolver.findByLevelKey(currentLevel.dimension());
        if (target.isPresent() && this.portalColor.equals(target.get().color())) {
            return server.overworld();
        }

        return null;
    }

    private ServerLevel findOTGDimensionByColor(MinecraftServer server, String targetColor) {
        Optional<PortalTarget> targetOpt = PortalTargetResolver.findByColor(targetColor);
        if (targetOpt.isEmpty()) {
            return null;
        }
        PortalTarget target = targetOpt.get();

        ServerLevel existing = server.getLevel(target.levelKey());
        if (existing != null) {
            return existing;
        }

        if (target.isNonOTG()) {
            // Non-OTG dimensions only exist if they were part of the world's level stems
            // at creation; there is no runtime-creation path for them.
            return null;
        }

        DimensionPreset preset = OTG.getEngine().getDimensionPresetLoader()
            .getDimensionPresetByFolderName(target.presetFolderName());
        if (preset == null) {
            return null;
        }
        return loadOrCreateDimension(server, preset);
    }

    private ServerLevel loadOrCreateDimension(MinecraftServer server, DimensionPreset preset) {
        String dimName = DimensionKeys.normalizeName(preset.getFolderName());
        ResourceKey<Level> levelKey = DimensionKeys.otg(dimName);

        ServerLevel existing = server.getLevel(levelKey);
        if (existing != null) {
            return existing;
        }

        DimensionManager manager = OTGCommandRegistrar.getDimensionManager();
        if (manager == null) {
            return null;
        }

        if (manager.getDimensionInfo(dimName).isPresent()) {
            if (manager.loadDimensionRuntime(dimName)) {
                return server.getLevel(levelKey);
            }
            return null;
        }

        var result = manager.createDimension(preset.getFolderName());

        if (result.success()) {
            return server.getLevel(levelKey);
        } else {
            return null;
        }
    }

    /**
     * Returns the effective portal color for a level, considering YAML overrides (R1).
     * Falls back to the DimensionPreset color if no YAML override is present.
     */
    private String getEffectivePortalColor(ServerLevel level, WorldPresetConfig activeWorldPreset) {
        if (level.getChunkSource().getGenerator() instanceof SharedOTGChunkGenerator gen) {
            String baseColor = gen.getPortalColor();

            // R1: Check for YAML color override
            if (activeWorldPreset != null) {
                DimensionPreset preset = gen.getPreset();
                if (preset != null) {
                    OTGDimension dimEntry = WorldPresetPortalResolver.findDimensionEntry(activeWorldPreset, preset.getFolderName());
                    if (dimEntry != null && WorldPresetPortalResolver.hasOverride(dimEntry.PortalColor)) {
                        baseColor = dimEntry.PortalColor;
                    }
                }
            }

            return DimensionNameUtils.normalizeColor(baseColor);
        }
        return "default";
    }

    public static boolean tryCreatePortal(LevelAccessor level, BlockPos pos, List<LocalMaterialData> frameBlocks, String portalColor) {
        return tryCreatePortal(level, pos, frameBlocks, portalColor, 2, 21, 3, 21);
    }

    public static boolean tryCreatePortal(LevelAccessor level, BlockPos pos, List<LocalMaterialData> frameBlocks, String portalColor,
                                          int minWidth, int maxWidth, int minHeight, int maxHeight) {
        PortalSize sizeX = new PortalSize(level, pos, Direction.Axis.X, frameBlocks, minWidth, maxWidth, minHeight, maxHeight);
        if (sizeX.isValid() && sizeX.portalBlockCount == 0) {
            sizeX.placePortalBlocks(portalColor);
            return true;
        }

        PortalSize sizeZ = new PortalSize(level, pos, Direction.Axis.Z, frameBlocks, minWidth, maxWidth, minHeight, maxHeight);
        if (sizeZ.isValid() && sizeZ.portalBlockCount == 0) {
            sizeZ.placePortalBlocks(portalColor);
            return true;
        }

        return false;
    }

    public static class PortalSize {
        private final LevelAccessor level;
        private final Direction.Axis axis;
        private final Direction rightDir;
        private final Direction leftDir;
        private final List<LocalMaterialData> frameBlocks;
        private final int minWidth;
        private final int maxWidth;
        private final int minHeight;
        private final int maxHeight;
        public int portalBlockCount = 0;
        public BlockPos bottomLeft;
        public int height;
        public int width;

        public PortalSize(LevelAccessor level, BlockPos pos, Direction.Axis axis, List<LocalMaterialData> frameBlocks,
                          int minWidth, int maxWidth, int minHeight, int maxHeight) {
            this.level = level;
            this.axis = axis;
            this.frameBlocks = frameBlocks;
            this.minWidth = minWidth;
            this.maxWidth = maxWidth;
            this.minHeight = minHeight;
            this.maxHeight = maxHeight;

            if (axis == Direction.Axis.X) {
                this.leftDir = Direction.EAST;
                this.rightDir = Direction.WEST;
            } else {
                this.leftDir = Direction.NORTH;
                this.rightDir = Direction.SOUTH;
            }

            BlockPos bottomPos = pos;
            while (bottomPos.getY() > level.getMinBuildHeight() && isEmpty(level.getBlockState(bottomPos.below()))) {
                bottomPos = bottomPos.below();
            }

            int distLeft = getDistanceToEdge(bottomPos, leftDir);
            if (distLeft > 0) {
                this.bottomLeft = bottomPos.relative(leftDir, distLeft - 1);
                this.width = getDistanceToEdge(bottomLeft, rightDir);
                if (width < minWidth || width > maxWidth) {
                    this.bottomLeft = null;
                    this.width = 0;
                }
            } else {
                this.bottomLeft = bottomPos;
                this.width = getDistanceToEdge(bottomLeft, rightDir);
                if (width < minWidth || width > maxWidth) {
                    this.bottomLeft = null;
                    this.width = 0;
                }
            }

            if (this.bottomLeft != null) {
                this.height = calculateHeight();
            }
        }

        private int getDistanceToEdge(BlockPos pos, Direction dir) {
            for (int i = 0; i <= maxWidth; i++) {
                BlockPos checkPos = pos.relative(dir, i);
                if (!isEmpty(level.getBlockState(checkPos)) || !isFrameBlock(level.getBlockState(checkPos.below()))) {
                    BlockPos framePos = pos.relative(dir, i);
                    return isFrameBlock(level.getBlockState(framePos)) ? i : 0;
                }
            }
            return 0;
        }

        private int calculateHeight() {
            outer:
            for (int h = 0; h <= maxHeight; h++) {
                for (int w = 0; w < this.width; w++) {
                    BlockPos checkPos = bottomLeft.relative(rightDir, w).above(h);
                    BlockState state = level.getBlockState(checkPos);

                    if (!isEmpty(state)) {
                        break outer;
                    }

                    if (state.getBlock() instanceof SharedOTGPortalBlock) {
                        portalBlockCount++;
                    }

                    if (w == 0 && !isFrameBlock(level.getBlockState(checkPos.relative(leftDir)))) {
                        break outer;
                    }
                    if (w == width - 1 && !isFrameBlock(level.getBlockState(checkPos.relative(rightDir)))) {
                        break outer;
                    }
                }
                this.height = h + 1;
            }

            for (int w = 0; w < this.width; w++) {
                if (!isFrameBlock(level.getBlockState(bottomLeft.relative(rightDir, w).above(height)))) {
                    this.height = 0;
                    break;
                }
            }

            return (height >= minHeight && height <= maxHeight) ? height : 0;
        }

        private boolean isEmpty(BlockState state) {
            return state.isAir() || state.getBlock() == Blocks.WATER || state.getBlock() instanceof SharedOTGPortalBlock;
        }

        private boolean isFrameBlock(BlockState state) {
            return SharedPortalConfigResolver.isFrameBlock(state, frameBlocks);
        }

        public boolean isValid() {
            return bottomLeft != null && width >= minWidth && width <= maxWidth && height >= minHeight && height <= maxHeight;
        }

        public void placePortalBlocks(String portalColor) {
            Block portalBlock = SharedOTGPortalBlock.lookupPortalBlock(portalColor);
            if (portalBlock == null) return;

            BlockState portalState = portalBlock.defaultBlockState().setValue(NetherPortalBlock.AXIS, this.axis);

            for (int w = 0; w < this.width; w++) {
                for (int h = 0; h < this.height; h++) {
                    BlockPos portalPos = bottomLeft.relative(rightDir, w).above(h);
                    if (level instanceof Level realLevel) {
                        realLevel.setBlock(portalPos, portalState, Block.UPDATE_ALL);
                    }
                }
            }

            if (level instanceof Level realLevel) {
                SharedPortalRegistry.register(realLevel.dimension(), portalColor, bottomLeft.immutable());
            }
        }
    }
}
