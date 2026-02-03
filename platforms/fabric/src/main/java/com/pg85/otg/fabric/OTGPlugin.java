package com.pg85.otg.fabric;

import com.pg85.otg.OTG;
import com.pg85.otg.fabric.dimensions.FabricDimensionCommands;
import com.pg85.otg.fabric.dimensions.FabricDimensionManager;
import com.pg85.otg.fabric.portals.FabricPortalBlocks;
import com.pg85.otg.fabric.portals.PortalIgnitionHandler;
import com.pg85.otg.fabric.events.WorldSaveCallback;
import com.pg85.otg.fabric.gen.OTGFabricChunkGenerator;
import com.pg85.otg.fabric.materials.FabricMaterialReader;
import com.pg85.otg.fabric.util.FabricLogger;
import com.pg85.otg.util.OTGLog;
import com.pg85.otg.util.OTGMaterialReader;
import com.pg85.otg.util.logging.LogCategory;
import com.pg85.otg.util.logging.LogLevel;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.world.level.chunk.ChunkGenerator;

@SuppressWarnings("unused")
public class OTGPlugin implements ModInitializer {
	private static FabricDimensionManager dimensionManager;

	/**
	 * Returns the dimension manager singleton, or null if server not yet started.
	 */
	public static FabricDimensionManager getDimensionManager() {
		return dimensionManager;
	}

	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.
		OTGLog.setLogger(new FabricLogger());
		OTGLog.getLogger().log(LogLevel.INFO, LogCategory.MAIN, "OTG Engine starting");
		OTGMaterialReader.set(new FabricMaterialReader());
		OTG.startEngine(new FabricEngine());

		registerWorldSave();
		registerDimensionCommands();
		registerServerEvents();
		registerPortals();

		OTG.log("OTG Engine started, presets loaded");
	}

	void registerWorldSave() {
		WorldSaveCallback.EVENT.register((serverLevel) -> {
			ChunkGenerator chunkGenerator = serverLevel.getChunkSource().getGenerator();
			if (chunkGenerator instanceof OTGFabricChunkGenerator fabricChunkGenerator) {
				OTGLog.info(LogCategory.STRUCTURE_PLOTTING, "Saving structure cache for world " + fabricChunkGenerator.getPreset().getFolderName());
				fabricChunkGenerator.saveStructureCache();
			}
		});
	}

	void registerDimensionCommands() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			FabricDimensionCommands.register(dispatcher);
			OTGLog.info("Registered OTG dimension commands");
		});
	}

	void registerServerEvents() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			dimensionManager = new FabricDimensionManager();
			dimensionManager.initialize(server);
			FabricDimensionCommands.setManager(dimensionManager);
			OTGLog.info("OTG Dimension Manager initialized");
		});
	}

	void registerPortals() {
		FabricPortalBlocks.register();
		PortalIgnitionHandler.register();
		OTGLog.info("OTG Portal blocks and ignition handler registered");
	}
}
