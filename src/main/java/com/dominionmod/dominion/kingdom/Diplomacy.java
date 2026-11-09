package com.dominionmod.dominion.kingdom;

import com.dominionmod.dominion.entity.Citizen;
import com.dominionmod.dominion.entity.Job;
import com.dominionmod.dominion.registry.ModContent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * How kingdoms stand with each other: at peace, at war, trading or allied. Rulers make war and
 * peace, propose trade and alliances, and demand or pay tribute from the Diplomacy tab of the Realm
 * screen. Kingdoms in the wild answer by their own lights (see {@link Rivals}), and send envoys
 * of their own; between two players' kingdoms the other ruler gets the offer to accept or refuse.
 */
public final class Diplomacy {
    private Diplomacy() {}

    public enum Relation {
        NEUTRAL("At peace", ChatFormatting.GRAY), WAR("At war", ChatFormatting.RED), TRADING("Trading", ChatFormatting.GOLD),
        ALLIED("Allied", ChatFormatting.GREEN);

        public final String label;
        public final ChatFormatting color;

        Relation(String label, ChatFormatting color) {
            this.label = label;
            this.color = color;
        }

        static Relation of(String s) {
            try {
                return valueOf(s);
            } catch (IllegalArgumentException e) {
                return NEUTRAL;
            }
        }
    }

    // ------------------------------------------------------------------ relations

    public static long day(ServerLevel level) {
        return level.getGameTime() / Citizen.DAY;
    }

    public static Relation relation(KingdomData d, @Nullable UUID a, @Nullable UUID b) {
        if (a == null || b == null || a.equals(b)) return Relation.NEUTRAL;
        KingdomData.Pact p = d.pact(a, b);
        return p == null ? Relation.NEUTRAL : Relation.of(p.state);
    }

    public static void set(ServerLevel level, Kingdom a, Kingdom b, Relation r) {
        KingdomData d = Realm.data(level);
        KingdomData.Pact p = d.pact(a.id, b.id);
        if (p == null) {
            p = new KingdomData.Pact(a.id, b.id, r.name(), day(level), 0);
            d.pacts.add(p);
        }
        p.state = r.name();
        p.since = day(level);
        d.markChanged();
    }

    /** Are these two sides (kingdom ids, as from Events.side) at war? */
    public static boolean hostile(@Nullable MinecraftServer server, @Nullable UUID a, @Nullable UUID b) {
        if (server == null || a == null || b == null || a.equals(b)) return false;
        return relation(KingdomData.get(server), a, b) == Relation.WAR;
    }

    /** The same kingdom, or allies. */
    public static boolean friendly(@Nullable MinecraftServer server, @Nullable UUID a, @Nullable UUID b) {
        if (a == null || b == null) return false;
        if (a.equals(b)) return true;
        return server != null && relation(KingdomData.get(server), a, b) == Relation.ALLIED;
    }

    public static boolean atWarWithAnyone(KingdomData d, Kingdom k) {
        for (KingdomData.Pact p : d.pacts) if (p.involves(k.id) && Relation.of(p.state) == Relation.WAR) return true;
        return false;
    }

    /** Soldiers a kingdom can put in the field: those on its rolls plus, for wild kingdoms, the levies not yet called up. */
    public static int strength(KingdomData d, Kingdom k) {
        int n = 0;
        for (int j : k.citizens.values()) if (Job.byId(j).soldier) n++;
        if (k.wild) n += d.rival(k).reserve;
        return n;
    }

    public static void declareWar(ServerLevel level, Kingdom by, Kingdom on, String why) {
        if (relation(Realm.data(level), by.id, on.id) == Relation.WAR) return;
        set(level, by, on, Relation.WAR);
        Component msg = Component.literal("WAR! ").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD).append(Realm.title(by))
                .append(Component.literal(" has declared war on ").withStyle(ChatFormatting.RED)).append(Realm.title(on))
                .append(Component.literal(why.isEmpty() ? "." : ": " + why).withStyle(ChatFormatting.RED));
        level.getServer().getPlayerList().broadcastSystemMessage(msg, false);
        for (ServerPlayer p : level.players()) {
            if (by.isMember(p.getUUID()) || on.isMember(p.getUUID()))
                level.playSound(null, p.blockPosition(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 1.5f, 0.8f);
        }
    }

    public static void makePeace(ServerLevel level, Kingdom a, Kingdom b, int truceDays) {
        set(level, a, b, Relation.NEUTRAL);
        KingdomData.Pact p = Realm.data(level).pact(a.id, b.id);
        if (p != null) p.truceUntil = day(level) + truceDays;
        Armies.recall(level, a, b);
        level.getServer().getPlayerList().broadcastSystemMessage(Component.literal("Peace: ").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
                .append(Realm.title(a)).append(Component.literal(" and ").withStyle(ChatFormatting.GREEN)).append(Realm.title(b))
                .append(Component.literal(" have made peace.").withStyle(ChatFormatting.GREEN)), false);
    }

    // ------------------------------------------------------------------ coins

    public static int coinsAvailable(ServerLevel level, Kingdom k, @Nullable ServerPlayer payer) {
        int n = Storehouse.count(Storehouse.containers(level, k), s -> s.is(ModContent.GOLD_COIN.get()));
        if (payer != null) n += payer.getInventory().countItem(ModContent.GOLD_COIN.get());
        return n;
    }

    /** Takes coins from the storehouse, then the payer's purse. False (and nothing taken) if there aren't enough. */
    public static boolean pay(ServerLevel level, Kingdom k, @Nullable ServerPlayer payer, int amount) {
        if (coinsAvailable(level, k, payer) < amount) return false;
        int left = amount - Storehouse.take(Storehouse.containers(level, k), s -> s.is(ModContent.GOLD_COIN.get()), amount).getCount();
        if (left > 0 && payer != null) {
            var inv = payer.getInventory();
            for (int i = 0; i < inv.getContainerSize() && left > 0; i++) {
                ItemStack s = inv.getItem(i);
                if (!s.is(ModContent.GOLD_COIN.get())) continue;
                int t = Math.min(left, s.getCount());
                s.shrink(t);
                left -= t;
            }
        }
        return true;
    }

    /** Puts coins in a kingdom's storehouse (or the hands of an online member, or at the Town Hall). */
    public static void receive(ServerLevel level, Kingdom k, int amount) {
        if (amount <= 0) return;
        ItemStack left = Storehouse.insert(Storehouse.containers(level, k), new ItemStack(ModContent.GOLD_COIN.get(), amount));
        if (left.isEmpty()) return;
        for (ServerPlayer p : level.players()) {
            if (k.isMember(p.getUUID())) {
                Realm.give(p, left);
                return;
            }
        }
    }

    // ------------------------------------------------------------------ offers

    public enum Kind {
        TRIBUTE("demands tribute"), TRADE("proposes a trade agreement"), ALLIANCE("proposes an alliance"), PEACE("offers peace"),
        PEACE_FOR_GOLD("will make peace for gold");

        public final String verb;

        Kind(String verb) {
            this.verb = verb;
        }
    }

    public record Offer(int id, UUID from, UUID to, Kind kind, int coins, long expires) {}

    private static final Map<Integer, Offer> OFFERS = new HashMap<>();
    private static int nextId = 1;

    public static Offer newOffer(ServerLevel level, Kingdom from, Kingdom to, Kind kind, int coins) {
        Offer o = new Offer(nextId++, from.id, to.id, kind, coins, level.getGameTime() + 20 * 60 * 3);
        OFFERS.put(o.id(), o);
        return o;
    }

    public static @Nullable Offer offer(int id) {
        return OFFERS.get(id);
    }

    /** Tells a kingdom's members about an offer, with buttons to accept or refuse it. */
    public static void present(ServerLevel level, Offer o, String preface) {
        KingdomData d = Realm.data(level);
        Kingdom from = d.byId(o.from()), to = d.byId(o.to());
        if (from == null || to == null) return;
        String terms = switch (o.kind()) {
            case TRIBUTE -> " Pay " + o.coins() + " Gold Coins, or face war.";
            case TRADE -> " Caravans would carry goods between your towns, and both crowns earn from the trade.";
            case ALLIANCE -> " Allies' soldiers never fight each other, and come to each other's aid.";
            case PEACE -> " The war would end, with a truce of five days.";
            case PEACE_FOR_GOLD -> " Pay " + o.coins() + " Gold Coins and the war ends, with a truce of five days.";
        };
        MutableComponent msg = Component.literal(preface).withStyle(ChatFormatting.GOLD).append(Realm.title(from))
                .append(Component.literal(" " + o.kind().verb + "." + terms + " ").withStyle(ChatFormatting.YELLOW))
                .append(button("[Accept]", ChatFormatting.GREEN, "/dominion diplomacy accept " + o.id(), "Accept the offer"))
                .append(Component.literal(" "))
                .append(button("[Refuse]", ChatFormatting.RED, "/dominion diplomacy refuse " + o.id(),
                        o.kind() == Kind.TRIBUTE ? "Refuse, and go to war" : "Refuse the offer"));
        Citizen.tellKingdom(level, to, msg);
    }

    private static MutableComponent button(String text, ChatFormatting color, String command, String hover) {
        return Component.literal(text).withStyle(s -> s.withColor(color).withBold(true)
                .withClickEvent(new ClickEvent.RunCommand(command)).withHoverEvent(new HoverEvent.ShowText(Component.literal(hover))));
    }

    /** A ruler answers an offer made to their kingdom. */
    public static Component respond(ServerPlayer p, int id, boolean accept) {
        ServerLevel level = (ServerLevel) p.level();
        KingdomData d = Realm.data(level);
        Offer o = OFFERS.get(id);
        Kingdom mine = d.byPlayer(p.getUUID());
        if (o == null || mine == null || !mine.id.equals(o.to())) return Realm.err("That offer is no longer on the table.");
        if (!mine.owner.equals(p.getUUID())) return Realm.err("Only your ruler can answer that.");
        if (level.getGameTime() > o.expires()) {
            OFFERS.remove(id);
            if (o.kind() == Kind.TRIBUTE) {
                Kingdom from = d.byId(o.from());
                if (from != null) declareWar(level, from, mine, "the tribute went unpaid");
            }
            return Realm.err("You took too long to answer.");
        }
        Kingdom from = d.byId(o.from());
        if (from == null) {
            OFFERS.remove(id);
            return Realm.err("That kingdom is no more.");
        }
        if (!accept) {
            OFFERS.remove(id);
            if (o.kind() == Kind.TRIBUTE) {
                declareWar(level, from, mine, "the tribute was refused");
                return Component.literal("You refused to pay. " + from.name + " marches to war!").withStyle(ChatFormatting.RED);
            }
            Citizen.tellKingdom(level, from, Component.literal(mine.name + " refused your offer.").withStyle(ChatFormatting.GRAY));
            return Component.literal("You refused.").withStyle(ChatFormatting.GRAY);
        }
        switch (o.kind()) {
            case TRIBUTE, PEACE_FOR_GOLD -> {
                if (!pay(level, mine, p, o.coins())) return Realm.err("You don't have " + o.coins() + " Gold Coins (storehouse and purse).");
                receive(level, from, o.coins());
                OFFERS.remove(id);
                if (o.kind() == Kind.PEACE_FOR_GOLD) makePeace(level, from, mine, 5);
                else {
                    KingdomData.Pact pact = d.pact(from.id, mine.id);
                    if (pact == null) set(level, from, mine, Relation.NEUTRAL);
                    pact = d.pact(from.id, mine.id);
                    if (pact != null) pact.truceUntil = day(level) + 4;
                }
                return Component.literal("You paid " + o.coins() + " Gold Coins to " + from.name + ".").withStyle(ChatFormatting.YELLOW);
            }
            case TRADE -> set(level, from, mine, Relation.TRADING);
            case ALLIANCE -> set(level, from, mine, Relation.ALLIED);
            case PEACE -> makePeace(level, from, mine, 5);
        }
        OFFERS.remove(id);
        Citizen.tellKingdom(level, from, Component.literal(mine.name + " accepted your offer.").withStyle(ChatFormatting.GREEN));
        return Component.literal("Agreed with " + from.name + ": " + relation(d, from.id, mine.id).label + ".").withStyle(ChatFormatting.GREEN);
    }

    // ------------------------------------------------------------------ the ruler's own moves

    /**
     * A ruler acts toward another kingdom: war, peace, trade, alliance, tribute (demand), or
     * break (end a trade agreement or alliance). Wild kingdoms answer at once; other players' kingdoms get an offer.
     */
    public static Component act(ServerPlayer p, String verb, UUID otherId) {
        ServerLevel level = (ServerLevel) p.level();
        KingdomData d = Realm.data(level);
        Kingdom mine = d.byPlayer(p.getUUID());
        Kingdom other = d.byId(otherId);
        if (mine == null) return Realm.err("You don't belong to a kingdom.");
        if (!mine.owner.equals(p.getUUID())) return Realm.err("Only your ruler can deal with other kingdoms.");
        if (other == null || other == mine) return Realm.err("No such kingdom.");
        Relation now = relation(d, mine.id, other.id);
        int mineStr = strength(d, mine), theirs = strength(d, other);
        switch (verb) {
            case "war" -> {
                if (now == Relation.WAR) return Realm.err("You are already at war with " + other.name + ".");
                declareWar(level, mine, other, "by order of " + p.getName().getString());
                if (other.wild) Rivals.onWarDeclared(level, other, mine);
                return Component.literal("Your banners are raised against " + other.name + ".").withStyle(ChatFormatting.RED);
            }
            case "break" -> {
                if (now != Relation.TRADING && now != Relation.ALLIED) return Realm.err("You have no agreement to break.");
                set(level, mine, other, Relation.NEUTRAL);
                Citizen.tellKingdom(level, other, Component.literal(mine.name + " has ended its agreement with you.").withStyle(ChatFormatting.GRAY));
                return Component.literal("You ended your agreement with " + other.name + ".").withStyle(ChatFormatting.YELLOW);
            }
            default -> {}
        }
        if (!other.wild) {
            Kind kind = switch (verb) {
                case "peace" -> now == Relation.WAR ? Kind.PEACE : null;
                case "trade" -> now == Relation.NEUTRAL ? Kind.TRADE : null;
                case "alliance" -> now == Relation.NEUTRAL || now == Relation.TRADING ? Kind.ALLIANCE : null;
                default -> null;
            };
            if (kind == null) return Realm.err("That isn't something you can propose right now.");
            present(level, newOffer(level, mine, other, kind, 0), "Word from abroad: ");
            return Component.literal("Your envoys carry the offer to " + other.name + ".").withStyle(ChatFormatting.YELLOW);
        }
        return Rivals.answer(p, mine, other, verb, now, mineStr, theirs);
    }

    // ------------------------------------------------------------------ the Diplomacy tab

    /** One row per other kingdom: id|name|dye|relation|soldiers|distance|wild|truce days left */
    public static List<String> rows(ServerLevel level, Kingdom mine) {
        KingdomData d = Realm.data(level);
        List<String[]> rows = new ArrayList<>();
        long today = day(level);
        for (Kingdom k : d.all()) {
            if (k == mine) continue;
            int dist = -1;
            if (k.hall.isPresent() && mine.hall.isPresent() && k.hallDim.equals(mine.hallDim))
                dist = (int) Math.sqrt(k.hall.get().distSqr(mine.hall.get()));
            KingdomData.Pact pact = d.pact(mine.id, k.id);
            long truce = pact == null ? 0 : Math.max(0, pact.truceUntil - today);
            rows.add(new String[]{k.id.toString(), k.name, String.valueOf(k.primary), relation(d, mine.id, k.id).name(),
                    String.valueOf(strength(d, k)), String.valueOf(dist), String.valueOf(k.wild), String.valueOf(truce)});
        }
        rows.sort((a, b) -> Integer.compare(a[5].equals("-1") ? Integer.MAX_VALUE : Integer.parseInt(a[5]),
                b[5].equals("-1") ? Integer.MAX_VALUE : Integer.parseInt(b[5])));
        List<String> out = new ArrayList<>();
        for (String[] r : rows) out.add(String.join("|", r));
        return out;
    }
}
