package com.pg85.otg.shared.portals;

import com.pg85.otg.shared.dimensions.DimensionKeys;
import com.pg85.otg.shared.dimensions.DimensionManager;
import com.pg85.otg.shared.gen.SharedOTGChunkGenerator;
import com.pg85.otg.presets.Preset;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

public class SharedOTGPortalBlock extends NetherPortalBlock {

    private static volatile Function<String, SharedOTGPortalBlock> portalBlockLookup;

    // Per-player time spent standing in a portal. entityInside runs every tick,
    // so consecutive game ticks increment the counter and a gap resets it.
    // No persistence needed - it's a transient countdown.
    private static final Map<UUID, long[]> portalTicks = new HashMap<>();

    public static void init(Function<String, SharedOTGPortalBlock> portalBlockLookup) {
        SharedOTGPortalBlock.portalBlockLookup = portalBlockLookup;
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
        if (!entity.isPassenger() && !entity.isVehicle() && entity.canChangeDimensions()) {
            if (level.isClientSide) {
                return;
            }

            if (entity.isOnPortalCooldown()) {
                entity.setPortalCooldown();
            } else {
                if (entity instanceof Player player) {
                    long gameTime = level.getGameTime();
                    long[] ticks = portalTicks.computeIfAbsent(player.getUUID(), k -> new long[2]);
                    // ticks[0] = ticks in portal, ticks[1] = last game time seen
                    ticks[0] = (gameTime - ticks[1] <= 1) ? ticks[0] + 1 : 1;
                    ticks[1] = gameTime;

                    int waitTime = player.getAbilities().invulnerable ? 1 : 80;
                    if (ticks[0] >= waitTime) {
                        doTeleport(player, (ServerLevel) level);
                        portalTicks.remove(player.getUUID());
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
            // The destination-side portal is the return trip, so it carries the color
            // of the level the entity is leaving - that way travelling back finds the
            // portal the player originally came from.
            String returnColor = getPortalColorOfLevel(serverLevel);
            SharedOTGTeleporter.teleport(entity, destination, returnColor);
        }
    }

    private ServerLevel findDestination(Entity entity, ServerLevel currentLevel) {
        MinecraftServer server = currentLevel.getServer();

        String overworldColor = getPortalColorOfLevel(server.overworld());

        if (currentLevel.dimension() == Level.OVERWORLD) {
            // A portal of the overworld preset's own color, standing in the overworld,
            // leads nowhere - don't spawn a second dimension of the overworld's preset.
            if (this.portalColor.equals(overworldColor)) {
                return null;
            }
        } else {
            // A portal of the dimension's own color leads back to the overworld,
            // and so does a portal whose color belongs to the overworld's preset.
            if (this.portalColor.equals(getPortalColorOfLevel(currentLevel))
                    || this.portalColor.equals(overworldColor)) {
                return server.overworld();
            }
        }

        ServerLevel target = findOTGDimensionByColor(server, this.portalColor);
        return target == currentLevel ? server.overworld() : target;
    }

    private ServerLevel findOTGDimensionByColor(MinecraftServer server, String targetColor) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension() == Level.OVERWORLD ||
                level.dimension() == Level.NETHER ||
                level.dimension() == Level.END) {
                continue;
            }

            if (level.getChunkSource().getGenerator() instanceof SharedOTGChunkGenerator) {
                String dimColor = getPortalColorOfLevel(level);
                if (targetColor.equals(dimColor)) {
                    return level;
                }
            }
        }

        return findAndLoadDimensionByColor(server, targetColor);
    }

    private ServerLevel findAndLoadDimensionByColor(MinecraftServer server, String targetColor) {
        Optional<Preset> presetOpt = SharedPortalConfigResolver.findPresetByColor(targetColor);
        if (presetOpt.isEmpty()) {
            return null;
        }

        Preset preset = presetOpt.get();
        return loadOrCreateDimension(server, preset);
    }

    private ServerLevel loadOrCreateDimension(MinecraftServer server, Preset preset) {
        String dimName = DimensionKeys.normalizeName(preset.getFolderName());
        ResourceKey<Level> levelKey = DimensionKeys.otg(dimName);

        ServerLevel existing = server.getLevel(levelKey);
        if (existing != null) {
            return existing;
        }

        DimensionManager manager = DimensionManager.get();
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

    /** Effective portal color of the preset generating this level, or "default" for non-OTG levels. */
    private String getPortalColorOfLevel(ServerLevel level) {
        if (level.getChunkSource().getGenerator() instanceof SharedOTGChunkGenerator gen) {
            Preset preset = gen.getPreset();
            if (preset != null) {
                return PortalConfigLookup.effectiveColorOf(preset.getFolderName());
            }
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
