package com.pg85.otg.shared.commands;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public final class OTGCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher,
                                CommandBuildContext buildContext,
                                Commands.CommandSelection selection) {
        var otg = Commands.literal("otg")
                .requires(src -> src.hasPermission(2))
                .then(BiomeCommand.register())
                .then(TpCommand.register())
                .then(MapCommand.register())
                .then(PresetCommand.register())
                .then(SettingsCommand.register())
                .then(PackCommand.register());
        DimensionCommands.register(otg);
        dispatcher.register(otg);
    }

    private OTGCommand() {}
}
