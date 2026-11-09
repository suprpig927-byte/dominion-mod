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
                .then(Commands.literal("diplomacy")
                        .then(Commands.literal("accept").then(Commands.argument("id", IntegerArgumentType.integer()).executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            p.sendSystemMessage(com.dominionmod.dominion.kingdom.Diplomacy.respond(p, IntegerArgumentType.getInteger(ctx, "id"), true));
                            return 1;
                        })))
                        .then(Commands.literal("refuse").then(Commands.argument("id", IntegerArgumentType.integer()).executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            p.sendSystemMessage(com.dominionmod.dominion.kingdom.Diplomacy.respond(p, IntegerArgumentType.getInteger(ctx, "id"), false));
                            return 1;
                        })))
                        .then(Commands.argument("action", StringArgumentType.word())
                                .suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
                                        new String[]{"war", "peace", "trade", "alliance", "tribute", "break"}, b))
                                .then(Commands.argument("kingdom", StringArgumentType.greedyString()).executes(ctx -> {
                                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                                    Kingdom other = KingdomData.get(ctx.getSource().getServer()).byName(StringArgumentType.getString(ctx, "kingdom"));
                                    if (other == null) {
                                        p.sendSystemMessage(Realm.err("No kingdom by that name."));
                                        return 0;
                                    }
                                    p.sendSystemMessage(com.dominionmod.dominion.kingdom.Diplomacy.act(p, StringArgumentType.getString(ctx, "action"), other.id));
                                    return 1;
                                }))))
                .then(Commands.literal("caravan").then(Commands.literal("guards").then(Commands.argument("count", IntegerArgumentType.integer(0, 6))
                        .executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            Kingdom k = Realm.of(p);
                            if (k == null || !k.owner.equals(p.getUUID())) {
                                p.sendSystemMessage(Realm.err("Only your ruler hires caravan guards."));
                                return 0;
                            }
                            KingdomData d = Realm.data((ServerLevel) p.level());
                            d.progress(k).caravanGuards = IntegerArgumentType.getInteger(ctx, "count");
                            d.markChanged();
                            p.sendSystemMessage(Component.literal("Each caravan will now take " + d.progress(k).caravanGuards + " hired guards (2 coins each a trip).")
                                    .withStyle(ChatFormatting.GOLD));
                            return 1;
                        }))))
                .then(Commands.literal("admin").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.literal("war").then(Commands.argument("kingdom", StringArgumentType.greedyString()).executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            ServerLevel level = (ServerLevel) p.level();
                            KingdomData d = Realm.data(level);
                            Kingdom mine = d.byPlayer(p.getUUID()), other = d.byName(StringArgumentType.getString(ctx, "kingdom"));
                            if (mine == null || other == null) {
                                ctx.getSource().sendFailure(Component.literal("Need your own kingdom and a kingdom by that name."));
                                return 0;
                            }
                            com.dominionmod.dominion.kingdom.Diplomacy.declareWar(level, other, mine, "by admin command");
                            var army = com.dominionmod.dominion.kingdom.Armies.raise(level, other, mine);
                            ctx.getSource().sendSuccess(() -> Component.literal(army == null ? "War declared." : "War declared; an army marches."), true);
                            return 1;
                        })))
                        .then(Commands.literal("envoy").then(Commands.argument("kind", StringArgumentType.word())
                                .suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(new String[]{"tribute", "trade", "alliance", "peace"}, b))
                                .then(Commands.argument("kingdom", StringArgumentType.greedyString()).executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            ServerLevel level = (ServerLevel) p.level();
                            KingdomData d = Realm.data(level);
                            Kingdom mine = d.byPlayer(p.getUUID()), other = d.byName(StringArgumentType.getString(ctx, "kingdom"));
                            if (mine == null || other == null) return 0;
                            var kind = com.dominionmod.dominion.kingdom.Diplomacy.Kind.valueOf(StringArgumentType.getString(ctx, "kind").toUpperCase());
                            com.dominionmod.dominion.kingdom.Rivals.envoy(level, other, mine, com.dominionmod.dominion.kingdom.Diplomacy.newOffer(level, other, mine, kind,
                                    kind == com.dominionmod.dominion.kingdom.Diplomacy.Kind.TRIBUTE ? 20 : 0));
                            return 1;
                        }))))
                        .then(Commands.literal("armies").executes(ctx -> {
                            ServerLevel level = ctx.getSource().getLevel();
                            for (String line : com.dominionmod.dominion.kingdom.Armies.describe(level)) ctx.getSource().sendSuccess(() -> Component.literal(line), false);
                            for (String line : com.dominionmod.dominion.kingdom.Caravans.describe(level)) ctx.getSource().sendSuccess(() -> Component.literal(line), false);
                            return 1;
                        }))
                        .then(Commands.literal("raid").executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            ServerLevel level = (ServerLevel) p.level();
                            Kingdom mine = Realm.of(p);
                            if (mine == null || mine.hall.isEmpty()) return 0;
                            var look = p.getLookAngle();
                            var from = mine.hall.get().offset((int) (look.x * 120), 0, (int) (look.z * 120));
                            com.dominionmod.dominion.kingdom.Armies.raid(level, from, mine, 6, false);
                            return 1;
                        }))
                        .then(Commands.literal("stronghold").executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            ServerLevel level = (ServerLevel) p.level();
                            KingdomData d = Realm.data(level);
                            KingdomData.OrcVillage best = null;
                            for (var v : d.orcVillages) if (!v.conquered && (best == null || v.totem.distSqr(p.blockPosition()) < best.totem.distSqr(p.blockPosition()))) best = v;
                            if (best == null) {
                                ctx.getSource().sendFailure(Component.literal("No orc village known. /dominion admin settle orc first."));
                                return 0;
                            }
                            com.dominionmod.dominion.kingdom.OrcWarbands.fastForward(level, best);
                            ctx.getSource().sendSuccess(() -> Component.literal("The orc village has grown into a stronghold."), true);
                            return 1;
                        }))
                        .then(Commands.literal("settle").then(Commands.argument("kind", com.mojang.brigadier.arguments.StringArgumentType.word())
                                .suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(new String[]{"orc", "town", "kingdom"}, b))
                                .executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            String kind = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "kind");
                            var type = switch (kind) {
                                case "orc" -> com.dominionmod.dominion.kingdom.Settlements.Type.ORC_VILLAGE;
                                case "kingdom" -> com.dominionmod.dominion.kingdom.Settlements.Type.WILD_KINGDOM;
                                default -> com.dominionmod.dominion.kingdom.Settlements.Type.WILD_TOWN;
                            };
                            var look = p.getLookAngle();
                            int dist = type.radius + 8;
                            int x = p.getBlockX() + (int) (look.x * dist), z = p.getBlockZ() + (int) (look.z * dist);
                            var level = (net.minecraft.server.level.ServerLevel) p.level();
                            var center = new net.minecraft.core.BlockPos(x, level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1, z);
                            com.dominionmod.dominion.kingdom.Settlements.generateNow(level, type, center, level.getRandom());
                            ctx.getSource().sendSuccess(() -> Component.literal("Raised a " + kind + " at " + center.toShortString()), true);
                            return 1;
                        })))
                        .then(Commands.literal("selftest").executes(ctx -> {
                            com.dominionmod.dominion.debug.SelfTest.start(ctx.getSource().getServer().overworld());
                            ctx.getSource().sendSuccess(() -> Component.literal("Self-test started at 4000, 4000. Results in the log in two minutes."), true);
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
