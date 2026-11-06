package com.dominionmod.dominion.command;

import com.dominionmod.dominion.kingdom.Kingdom;
import com.dominionmod.dominion.kingdom.KingdomData;
import com.dominionmod.dominion.kingdom.Realm;
import com.dominionmod.dominion.network.Payloads;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** /dominion: land, membership and a few admin tools. */
public final class DominionCommand {
    private DominionCommand() {}

    /** player -> kingdom they've been invited to */
    private static final Map<UUID, UUID> INVITES = new HashMap<>();

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("dominion")
                .then(Commands.literal("claim").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    p.sendSystemMessage(Realm.claimHere(p));
                    return 1;
                }))
                .then(Commands.literal("unclaim").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    p.sendSystemMessage(Realm.unclaimHere(p));
                    return 1;
                }))
                .then(Commands.literal("realm").executes(ctx -> {
                    Payloads.sendRealm(ctx.getSource().getPlayerOrException(), true);
                    return 1;
                }))
                .then(Commands.literal("invite").then(Commands.argument("player", EntityArgument.player()).executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    ServerPlayer other = EntityArgument.getPlayer(ctx, "player");
                    Kingdom k = Realm.of(p);
                    if (k == null || !k.owner.equals(p.getUUID())) {
                        p.sendSystemMessage(Realm.err("Only a kingdom's ruler can invite people."));
                        return 0;
                    }
                    if (Realm.of(other) != null) {
                        p.sendSystemMessage(Realm.err(other.getName().getString() + " already belongs to a kingdom."));
                        return 0;
                    }
                    INVITES.put(other.getUUID(), k.id);
                    p.sendSystemMessage(Component.literal("Invited " + other.getName().getString() + ".").withStyle(ChatFormatting.GREEN));
                    other.sendSystemMessage(Component.literal(p.getName().getString() + " invites you to join ").withStyle(ChatFormatting.GOLD)
                            .append(Realm.title(k)).append(Component.literal(". Type /dominion join to accept.").withStyle(ChatFormatting.GOLD)));
                    return 1;
                })))
                .then(Commands.literal("join").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    KingdomData d = Realm.data((ServerLevel) p.level());
                    Kingdom k = d.byId(INVITES.remove(p.getUUID()));
                    if (k == null) {
                        p.sendSystemMessage(Realm.err("You don't have an invitation."));
                        return 0;
                    }
                    if (d.byPlayer(p.getUUID()) != null) {
                        p.sendSystemMessage(Realm.err("You already belong to a kingdom. /dominion leave first."));
                        return 0;
                    }
                    k.members.add(p.getUUID());
                    d.markChanged();
                    com.dominionmod.dominion.entity.Citizen.tellKingdom((ServerLevel) p.level(), k,
                            Component.literal(p.getName().getString() + " has joined the kingdom!").withStyle(ChatFormatting.GREEN));
                    return 1;
                }))
                .then(Commands.literal("leave").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    KingdomData d = Realm.data((ServerLevel) p.level());
                    Kingdom k = d.byPlayer(p.getUUID());
                    if (k == null) {
                        p.sendSystemMessage(Realm.err("You don't belong to a kingdom."));
                        return 0;
                    }
                    if (k.owner.equals(p.getUUID())) {
                        p.sendSystemMessage(Realm.err("A ruler can't abandon their kingdom (yet). Hand it over with /dominion crown <player>."));
                        return 0;
                    }
                    k.members.remove(p.getUUID());
                    d.markChanged();
                    p.sendSystemMessage(Component.literal("You left " + k.name + ".").withStyle(ChatFormatting.YELLOW));
                    return 1;
                }))
                .then(Commands.literal("crown").then(Commands.argument("player", EntityArgument.player()).executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    ServerPlayer other = EntityArgument.getPlayer(ctx, "player");
                    Kingdom k = Realm.of(p);
                    if (k == null || !k.owner.equals(p.getUUID()) || !k.isMember(other.getUUID())) {
                        p.sendSystemMessage(Realm.err("You can only crown a member of your own kingdom."));
                        return 0;
                    }
                    k.owner = other.getUUID();
                    Realm.data((ServerLevel) p.level()).markChanged();
                    com.dominionmod.dominion.entity.Citizen.tellKingdom((ServerLevel) p.level(), k,
                            Component.literal(other.getName().getString() + " now rules " + k.name + ".").withStyle(ChatFormatting.GOLD));
                    return 1;
                })))
                .then(Commands.literal("admin").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.literal("selftest").executes(ctx -> {
                            com.dominionmod.dominion.debug.SelfTest.start(ctx.getSource().getServer().overworld());
                            ctx.getSource().sendSuccess(() -> Component.literal("Self-test started at 4000, 4000. Results in the log in 30 seconds."), true);
                            return 1;
                        }))
                        .then(Commands.literal("influence").then(Commands.argument("amount", IntegerArgumentType.integer()).executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            Kingdom k = Realm.of(p);
                            if (k == null) return 0;
                            k.influence += IntegerArgumentType.getInteger(ctx, "amount");
                            Realm.data((ServerLevel) p.level()).markChanged();
                            ctx.getSource().sendSuccess(() -> Component.literal(String.format("%s now has %.0f Influence.", k.name, k.influence)), true);
                            return 1;
                        })))
                        .then(Commands.literal("disband").then(Commands.argument("kingdom", StringArgumentType.greedyString()).executes(ctx -> {
                            KingdomData d = KingdomData.get(ctx.getSource().getServer());
                            Kingdom k = d.byName(StringArgumentType.getString(ctx, "kingdom"));
                            if (k == null) {
                                ctx.getSource().sendFailure(Component.literal("No such kingdom."));
                                return 0;
                            }
                            d.remove(k);
                            ctx.getSource().sendSuccess(() -> Component.literal("Disbanded " + k.name + "."), true);
                            return 1;
                        }))))
                .then(Commands.literal("list").executes(ctx -> {
                    CommandSourceStack src = ctx.getSource();
                    KingdomData d = KingdomData.get(src.getServer());
                    if (d.all().isEmpty()) src.sendSuccess(() -> Component.literal("No kingdoms yet."), false);
                    for (Kingdom k : d.all()) {
                        src.sendSuccess(() -> Realm.title(k).append(Component.literal("  " + k.members.size() + " members, "
                                + k.citizens.size() + " citizens, " + d.claimCount(k) + " chunks").withStyle(ChatFormatting.GRAY)), false);
                    }
                    return 1;
                })));
    }
}
