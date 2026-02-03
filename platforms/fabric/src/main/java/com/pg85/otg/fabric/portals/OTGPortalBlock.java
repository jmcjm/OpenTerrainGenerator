package com.pg85.otg.fabric.portals;

import com.pg85.otg.fabric.OTGPlugin;
import com.pg85.otg.fabric.dimensions.DimensionKeys;
import com.pg85.otg.fabric.dimensions.FabricDimensionManager;
import com.pg85.otg.fabric.gen.OTGFabricChunkGenerator;
import com.pg85.otg.fabric.portals.components.OTGComponents;
import com.pg85.otg.presets.Preset;
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

public class OTGPortalBlock extends NetherPortalBlock {

    private final String portalColor;

    public OTGPortalBlock(Properties settings, String portalColor) {
        super(settings);
        this.portalColor = portalColor;
    }

    public String getPortalColor() {
        return portalColor;
    }

    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (!entity.isPassenger() && !entity.isVehicle() && entity.canChangeDimensions()) {
            // Trigger visual effect on client side
            if (level.isClientSide) {
                entity.handleInsidePortal(pos);
                return;
            }

            if (entity.isOnPortalCooldown()) {
                entity.setPortalCooldown();
            } else {
                if (entity instanceof Player player) {
                    var component = OTGComponents.get(player);
                    component.setPortalState(true, this.portalColor);

                    int portalTime = component.getPortalTime();
                    int waitTime = player.getPortalWaitTime();

                    if (portalTime >= waitTime) {
                        doTeleport(player, (ServerLevel) level);
                        component.setPortalTime(0);
                    }
                } else {
                    doTeleport(entity, (ServerLevel) level);
                }
            }
        }
    }

    private void doTeleport(Entity entity, ServerLevel serverLevel) {
        if (serverLevel == null) return;

        MinecraftServer server = serverLevel.getServer();
        ServerLevel destination = findDestination(entity, serverLevel);

        if (destination != null && !entity.isPassenger()) {
            entity.setPortalCooldown();
            OTGTeleporter.teleport(entity, destination, this.portalColor);
        }
    }

    private ServerLevel findDestination(Entity entity, ServerLevel currentLevel) {
        MinecraftServer server = currentLevel.getServer();

        // If in overworld, find OTG dimension with matching color
        if (currentLevel.dimension() == Level.OVERWORLD) {
            return findOTGDimensionByColor(server, this.portalColor);
        }

        // If in OTG dimension, check if color matches and go to overworld
        if (currentLevel.getChunkSource().getGenerator() instanceof OTGFabricChunkGenerator) {
            String dimColor = getWorldPortalColor(currentLevel);
            if (this.portalColor.equals(dimColor)) {
                return server.overworld();
            }
        }

        return null;
    }

    private ServerLevel findOTGDimensionByColor(MinecraftServer server, String targetColor) {
        // First check already loaded dimensions - direct color match, no collision handling
        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension() == Level.OVERWORLD ||
                level.dimension() == Level.NETHER ||
                level.dimension() == Level.END) {
                continue;
            }

            if (level.getChunkSource().getGenerator() instanceof OTGFabricChunkGenerator) {
                String dimColor = getWorldPortalColor(level);
                if (targetColor.equals(dimColor)) {
                    return level;
                }
            }
        }

        // Not found in loaded dimensions - look through presets and load dynamically
        return findAndLoadDimensionByColor(server, targetColor);
    }

    private ServerLevel findAndLoadDimensionByColor(MinecraftServer server, String targetColor) {
        Optional<Preset> presetOpt = PortalConfigResolver.findPresetByColor(targetColor);
        if (presetOpt.isEmpty()) {
            return null;
        }

        Preset preset = presetOpt.get();
        return loadOrCreateDimension(server, preset);
    }

    private ServerLevel loadOrCreateDimension(MinecraftServer server, Preset preset) {
        String dimName = DimensionKeys.normalizeName(preset.getFolderName());
        ResourceKey<Level> levelKey = DimensionKeys.otg(dimName);

        // Check if dimension already loaded in runtime
        ServerLevel existing = server.getLevel(levelKey);
        if (existing != null) {
            return existing;
        }

        // Use FabricDimensionManager for proper persistence (storage + datapack)
        FabricDimensionManager manager = OTGPlugin.getDimensionManager();
        if (manager == null) {
            return null;
        }

        // Check if dimension exists in storage but not loaded (e.g., created by command, needs restart normally)
        if (manager.getDimensionInfo(dimName).isPresent()) {
            // Dimension exists in storage - load it at runtime via manager
            if (manager.loadDimensionRuntime(dimName)) {
                return server.getLevel(levelKey);
            }
            return null;
        }

        // Dimension doesn't exist - create it via manager (with storage + datapack)
        var result = manager.createDimension(preset.getFolderName());

        if (result.success()) {
            return server.getLevel(levelKey);
        } else {
            return null;
        }
    }

    private String getWorldPortalColor(ServerLevel level) {
        if (level.getChunkSource().getGenerator() instanceof OTGFabricChunkGenerator gen) {
            return PortalConfigResolver.normalizeColor(gen.getPortalColor());
        }
        return "default";
    }

    // Portal frame validation
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

            // Find bottom
            BlockPos bottomPos = pos;
            while (bottomPos.getY() > level.getMinBuildHeight() && isEmpty(level.getBlockState(bottomPos.below()))) {
                bottomPos = bottomPos.below();
            }

            int distLeft = getDistanceToEdge(bottomPos, leftDir);
            if (distLeft > 0) {
                // bottomLeft should be the leftmost INTERIOR position (not on the wall)
                // distLeft is how far to the wall, so interior is at distLeft-1
                this.bottomLeft = bottomPos.relative(leftDir, distLeft - 1);
                // Width = distance from bottomLeft to right wall
                this.width = getDistanceToEdge(bottomLeft, rightDir);
                if (width < minWidth || width > maxWidth) {
                    this.bottomLeft = null;
                    this.width = 0;
                }
            } else {
                // Already at the left wall or no wall found
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

                    if (state.getBlock() instanceof OTGPortalBlock) {
                        portalBlockCount++;
                    }

                    // Check side frames
                    if (w == 0 && !isFrameBlock(level.getBlockState(checkPos.relative(leftDir)))) {
                        break outer;
                    }
                    if (w == width - 1 && !isFrameBlock(level.getBlockState(checkPos.relative(rightDir)))) {
                        break outer;
                    }
                }
                this.height = h + 1;
            }

            // Verify top frame
            for (int w = 0; w < this.width; w++) {
                if (!isFrameBlock(level.getBlockState(bottomLeft.relative(rightDir, w).above(height)))) {
                    this.height = 0;
                    break;
                }
            }

            return (height >= minHeight && height <= maxHeight) ? height : 0;
        }

        private boolean isEmpty(BlockState state) {
            return state.isAir() || state.getBlock() == Blocks.WATER || state.getBlock() instanceof OTGPortalBlock;
        }

        private boolean isFrameBlock(BlockState state) {
            return PortalConfigResolver.isFrameBlock(state, frameBlocks);
        }

        public boolean isValid() {
            return bottomLeft != null && width >= minWidth && width <= maxWidth && height >= minHeight && height <= maxHeight;
        }

        public void placePortalBlocks(String portalColor) {
            Block portalBlock = FabricPortalBlocks.getPortalBlock(portalColor);
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
        }
    }
}
