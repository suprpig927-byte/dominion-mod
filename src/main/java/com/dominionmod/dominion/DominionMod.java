package com.dominionmod.dominion;

import com.dominionmod.dominion.command.DominionCommand;
import com.dominionmod.dominion.event.Events;
import com.dominionmod.dominion.network.Payloads;
import com.dominionmod.dominion.registry.ModContent;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

/** Dominion: found a kingdom, recruit villagers, put them to work, raise an army and claim the land. */
@Mod(DominionMod.MODID)
public class DominionMod {
    public static final String MODID = "dominion";
    public static final Logger LOGGER = LogUtils.getLogger();

    public DominionMod(IEventBus modBus) {
        ModContent.register(modBus);
        modBus.addListener(Payloads::register);
        Events.register(NeoForge.EVENT_BUS);
        NeoForge.EVENT_BUS.addListener(DominionCommand::register);
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }
}
