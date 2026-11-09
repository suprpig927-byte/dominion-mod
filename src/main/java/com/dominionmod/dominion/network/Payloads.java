package com.dominionmod.dominion.network;

import com.dominionmod.dominion.DominionMod;
import com.dominionmod.dominion.entity.Job;
import com.dominionmod.dominion.kingdom.Kingdom;
import com.dominionmod.dominion.kingdom.KingdomData;
import com.dominionmod.dominion.kingdom.Realm;
import com.dominionmod.dominion.kingdom.Storehouse;
import com.dominionmod.dominion.registry.ModContent;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class Payloads {
    private Payloads() {}

    public static final int MAP_RADIUS = 5; // an 11 x 11 chunk map
    public static final int TAB_KEEP = -1, TAB_OVERVIEW = 0, TAB_RESEARCH = 3;

    /** Server -> client: open the class picker for citizens standing at a Training Desk. */
    public record OpenTraining(BlockPos desk, List<String> names) implements CustomPacketPayload {
        public static final Type<OpenTraining> TYPE = new Type<>(DominionMod.id("open_training"));
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenTraining> CODEC = StreamCodec.of(
                (RegistryFriendlyByteBuf buf, OpenTraining p) -> {
                    buf.writeBlockPos(p.desk);
                    writeStrings(buf, p.names);
                }, buf -> new OpenTraining(buf.readBlockPos(), readStrings(buf)));

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Server -> client: open the kingdom founding screen for the Town Hall at {@code pos}. */
    /** Server -> client: the Market screen. rows: key|base|now|bonus|carried|stock ; caravans: one line each. */
    public record OpenMarket(BlockPos pos, List<String> rows, int guards, List<String> caravans, boolean owner, int coins)
            implements CustomPacketPayload {
        public static final Type<OpenMarket> TYPE = new Type<>(DominionMod.id("open_market"));
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenMarket> CODEC = StreamCodec.of(
                (RegistryFriendlyByteBuf buf, OpenMarket p) -> {
                    buf.writeBlockPos(p.pos);
                    writeStrings(buf, p.rows);
                    buf.writeVarInt(p.guards);
                    writeStrings(buf, p.caravans);
                    buf.writeBoolean(p.owner);
                    buf.writeVarInt(p.coins);
                }, buf -> new OpenMarket(buf.readBlockPos(), readStrings(buf), buf.readVarInt(), readStrings(buf), buf.readBoolean(), buf.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record OpenFounding(BlockPos pos) implements CustomPacketPayload {
        public static final Type<OpenFounding> TYPE = new Type<>(DominionMod.id("open_founding"));
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenFounding> CODEC = StreamCodec.of(
                (RegistryFriendlyByteBuf buf, OpenFounding p) -> buf.writeBlockPos(p.pos), buf -> new OpenFounding(buf.readBlockPos()));

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Client -> server: found the kingdom. Colors are dye ids (0-15). */
    public record FoundKingdom(BlockPos pos, String name, int primary, int secondary, String flag) implements CustomPacketPayload {
        public static final Type<FoundKingdom> TYPE = new Type<>(DominionMod.id("found_kingdom"));
        public static final StreamCodec<RegistryFriendlyByteBuf, FoundKingdom> CODEC = StreamCodec.of(
                (RegistryFriendlyByteBuf buf, FoundKingdom p) -> {
                    buf.writeBlockPos(p.pos);
                    buf.writeUtf(p.name, 64);
                    buf.writeVarInt(p.primary);
                    buf.writeVarInt(p.secondary);
                    buf.writeUtf(p.flag, 2048);
                },
                buf -> new FoundKingdom(buf.readBlockPos(), buf.readUtf(64), buf.readVarInt(), buf.readVarInt(), buf.readUtf(2048)));

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Client -> server: a Realm screen button (or opening the screen, which asks for fresh info). */
    public record RealmAction(int action, String arg) implements CustomPacketPayload {
        public static final int REFRESH = 0, CLAIM = 1, UNCLAIM = 2, TOGGLE_PEACEFUL = 3, OPEN = 4, BLUEPRINT = 5, CANCEL_SITE = 6, SET_FLAG = 7, MAP = 8,
                START_RESEARCH = 9, TRAIN = 10, DIPLOMACY = 11, TOGGLE_SHOP = 12, MARKET_SELL = 13, MARKET_GUARDS = 14;

        public RealmAction(int action) {
            this(action, "");
        }
        public static final Type<RealmAction> TYPE = new Type<>(DominionMod.id("realm_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, RealmAction> CODEC = StreamCodec.of(
                (RegistryFriendlyByteBuf buf, RealmAction p) -> {
                    buf.writeVarInt(p.action);
                    buf.writeUtf(p.arg, 2048);
                }, buf -> new RealmAction(buf.readVarInt(), buf.readUtf(2048)));

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /**
     * Server -> client: everything the Realm screen shows. {@code map} holds one entry per chunk
     * around the player, row by row: -1 = wild, 0-15 = another kingdom's dye color, 100-115 = yours.
     */
    public record RealmInfo(boolean open, boolean has, String name, int primary, int secondary, double influence, double daily,
                            int claimCost, int chunks, int beds, List<Integer> jobs, int coins, int food, String here,
                            boolean peaceful, List<Integer> map, String message, boolean ruler, String flag,
                            List<String> roster, List<String> sites, int openTab, List<String> research, int scholars, List<String> diplomacy,
                            List<String> industry, List<String> defense, List<String> stock)
            implements CustomPacketPayload {
        public static final Type<RealmInfo> TYPE = new Type<>(DominionMod.id("realm_info"));
        public static final StreamCodec<RegistryFriendlyByteBuf, RealmInfo> CODEC = StreamCodec.of(
                (RegistryFriendlyByteBuf buf, RealmInfo p) -> {
                    buf.writeBoolean(p.open);
                    buf.writeBoolean(p.has);
                    buf.writeUtf(p.name);
                    buf.writeVarInt(p.primary);
                    buf.writeVarInt(p.secondary);
                    buf.writeDouble(p.influence);
                    buf.writeDouble(p.daily);
                    buf.writeVarInt(p.claimCost);
                    buf.writeVarInt(p.chunks);
                    buf.writeVarInt(p.beds);
                    writeInts(buf, p.jobs);
                    buf.writeVarInt(p.coins);
                    buf.writeVarInt(p.food);
                    buf.writeUtf(p.here);
                    buf.writeBoolean(p.peaceful);
                    writeInts(buf, p.map);
                    buf.writeUtf(p.message);
                    buf.writeBoolean(p.ruler);
                    buf.writeUtf(p.flag, 2048);
                    writeStrings(buf, p.roster);
                    writeStrings(buf, p.sites);
                    buf.writeVarInt(p.openTab + 1);
                    writeStrings(buf, p.research);
                    buf.writeVarInt(p.scholars);
                    writeStrings(buf, p.diplomacy);
                    writeStrings(buf, p.industry);
                    writeStrings(buf, p.defense);
                    writeStrings(buf, p.stock);
                },
                buf -> new RealmInfo(buf.readBoolean(), buf.readBoolean(), buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readDouble(),
                        buf.readDouble(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), readInts(buf), buf.readVarInt(), buf.readVarInt(),
                        buf.readUtf(), buf.readBoolean(), readInts(buf), buf.readUtf(), buf.readBoolean(), buf.readUtf(2048),
                        readStrings(buf), readStrings(buf), buf.readVarInt() - 1, readStrings(buf), buf.readVarInt(), readStrings(buf),
                        readStrings(buf), readStrings(buf), readStrings(buf)));

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }

        public int citizens() {
            int n = 0;
            for (int j : jobs) n += j;
            return n;
        }
    }

    /**
     * Server -> client: a top-down picture of the land around the player, one pixel per block,
     * as vanilla map color ids (0 = not loaded). {@code x0, z0} is the north-west corner.
     */
    public record MapData(int x0, int z0, int size, byte[] colors) implements CustomPacketPayload {
        public static final Type<MapData> TYPE = new Type<>(DominionMod.id("map_data"));
        public static final StreamCodec<RegistryFriendlyByteBuf, MapData> CODEC = StreamCodec.of(
                (RegistryFriendlyByteBuf buf, MapData p) -> {
                    buf.writeInt(p.x0);
                    buf.writeInt(p.z0);
                    buf.writeVarInt(p.size);
                    buf.writeByteArray(p.colors);
                }, buf -> new MapData(buf.readInt(), buf.readInt(), buf.readVarInt(), buf.readByteArray()));

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static void sendMap(ServerPlayer p) {
        ServerLevel level = (ServerLevel) p.level();
        ChunkPos center = ChunkPos.containing(p.blockPosition());
        int n = (MAP_RADIUS * 2 + 1) * 16;
        int x0 = (center.x() - MAP_RADIUS) * 16, z0 = (center.z() - MAP_RADIUS) * 16;
        int[] heights = new int[n * (n + 1)];
        net.minecraft.world.level.block.state.BlockState[] tops = new net.minecraft.world.level.block.state.BlockState[n * (n + 1)];
        // Row 0 is one block north of the map, for shading the first real row.
        net.minecraft.core.BlockPos.MutableBlockPos pos = new net.minecraft.core.BlockPos.MutableBlockPos();
        for (int row = 0; row <= n; row++) {
            int z = z0 - 1 + row;
            for (int col = 0; col < n; col++) {
                int x = x0 + col;
                var chunk = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
                int i = row * n + col;
                if (chunk == null) {
                    heights[i] = Integer.MIN_VALUE;
                    continue;
                }
                int h = chunk.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, x & 15, z & 15);
                var state = chunk.getBlockState(pos.set(x, h, z));
                // See through blocks that have no map color (glass, barriers) to what's beneath.
                for (int k = 0; k < 4 && state.getMapColor(level, pos) == net.minecraft.world.level.material.MapColor.NONE && h > level.getMinY(); k++) {
                    h--;
                    state = chunk.getBlockState(pos.set(x, h, z));
                }
                heights[i] = h;
                tops[i] = state;
            }
        }
        byte[] colors = new byte[n * n];
        for (int row = 1; row <= n; row++) {
            int z = z0 - 1 + row;
            for (int col = 0; col < n; col++) {
                int i = row * n + col;
                if (heights[i] == Integer.MIN_VALUE || tops[i] == null) continue;
                int x = x0 + col;
                var state = tops[i];
                var color = state.getMapColor(level, pos.set(x, heights[i], z));
                net.minecraft.world.level.material.MapColor.Brightness b;
                if (!state.getFluidState().isEmpty()) {
                    // Deeper water is darker.
                    var chunk = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
                    int depth = 0;
                    while (depth < 10 && chunk != null && !chunk.getBlockState(pos.set(x, heights[i] - depth - 1, z)).getFluidState().isEmpty()) depth++;
                    b = depth < 2 ? net.minecraft.world.level.material.MapColor.Brightness.HIGH
                            : depth < 5 ? net.minecraft.world.level.material.MapColor.Brightness.NORMAL
                            : net.minecraft.world.level.material.MapColor.Brightness.LOW;
                } else {
                    int north = heights[i - n];
                    if (north == Integer.MIN_VALUE || north == heights[i]) b = net.minecraft.world.level.material.MapColor.Brightness.NORMAL;
                    else b = heights[i] > north ? net.minecraft.world.level.material.MapColor.Brightness.HIGH
                            : net.minecraft.world.level.material.MapColor.Brightness.LOW;
                }
                colors[(row - 1) * n + col] = color.getPackedId(b);
            }
        }
        PacketDistributor.sendToPlayer(p, new MapData(x0, z0, n, colors));
    }

    private static void writeInts(RegistryFriendlyByteBuf buf, List<Integer> list) {
        buf.writeVarInt(list.size());
        for (int i : list) buf.writeVarInt(i + 1);
    }

    private static void writeStrings(RegistryFriendlyByteBuf buf, List<String> list) {
        buf.writeVarInt(list.size());
        for (String s : list) buf.writeUtf(s, 512);
    }

    private static List<String> readStrings(RegistryFriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<String> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) out.add(buf.readUtf(512));
        return out;
    }

    private static List<Integer> readInts(RegistryFriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<Integer> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) out.add(buf.readVarInt() - 1);
        return out;
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1");

        r.playToClient(OpenFounding.TYPE, OpenFounding.CODEC, (p, ctx) ->
                com.dominionmod.dominion.client.FoundingScreen.open(p.pos()));
        r.playToClient(RealmInfo.TYPE, RealmInfo.CODEC, (p, ctx) ->
                com.dominionmod.dominion.client.RealmScreen.receive(p));
        r.playToClient(OpenTraining.TYPE, OpenTraining.CODEC, (p, ctx) ->
                com.dominionmod.dominion.client.TrainingScreen.open(p));
        r.playToClient(OpenMarket.TYPE, OpenMarket.CODEC, (p, ctx) ->
                com.dominionmod.dominion.client.MarketScreen.open(p));
        r.playToClient(MapData.TYPE, MapData.CODEC, (p, ctx) ->
                com.dominionmod.dominion.client.RealmMap.receive(p));

        r.playToServer(FoundKingdom.TYPE, FoundKingdom.CODEC, (p, ctx) -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            String err = Realm.found(sp, p.pos(), p.name(), p.primary(), p.secondary(), p.flag());
            if (err != null) {
                sp.sendSystemMessage(Realm.err(err));
                PacketDistributor.sendToPlayer(sp, new OpenFounding(p.pos()));
            } else {
                sendRealm(sp, true);
            }
        });

        r.playToServer(RealmAction.TYPE, RealmAction.CODEC, (p, ctx) -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            if (p.action() == RealmAction.MAP) {
                sendMap(sp);
                return;
            }
            String msg = "";
            switch (p.action()) {
                case RealmAction.CLAIM -> msg = Realm.claimHere(sp).getString();
                case RealmAction.UNCLAIM -> msg = Realm.unclaimHere(sp).getString();
                case RealmAction.TOGGLE_PEACEFUL -> {
                    Kingdom k = Realm.of(sp);
                    if (k != null && k.owner.equals(sp.getUUID())) {
                        k.peaceful = !k.peaceful;
                        Realm.data((ServerLevel) sp.level()).markChanged();
                        msg = k.peaceful ? "Monsters will no longer spawn on your land." : "Monsters can spawn on your land again.";
                    } else {
                        msg = "Only the ruler can change that.";
                    }
                }
                case RealmAction.SET_FLAG -> msg = Realm.setFlag(sp, p.arg()).getString();
                case RealmAction.START_RESEARCH -> msg = com.dominionmod.dominion.kingdom.Scholarship.start(sp, p.arg()).getString();
                case RealmAction.TRAIN -> msg = com.dominionmod.dominion.kingdom.Scholarship.train(sp, p.arg()).getString();
                case RealmAction.BLUEPRINT -> msg = com.dominionmod.dominion.build.Construction.giveBlueprint(sp, p.arg()).getString();
                case RealmAction.CANCEL_SITE -> msg = com.dominionmod.dominion.build.Construction.cancel(sp, p.arg()).getString();
                case RealmAction.TOGGLE_SHOP -> {
                    Kingdom k = Realm.of(sp);
                    msg = k == null || !k.owner.equals(sp.getUUID()) ? "Only the ruler can do that."
                            : com.dominionmod.dominion.kingdom.Industry.toggle((ServerLevel) sp.level(), k, p.arg()).getString();
                }
                case RealmAction.MARKET_SELL -> {
                    com.dominionmod.dominion.block.MarketBlock.sellFromScreen(sp, p.arg());
                    return;
                }
                case RealmAction.MARKET_GUARDS -> {
                    com.dominionmod.dominion.block.MarketBlock.guardsFromScreen(sp, p.arg());
                    return;
                }
                case RealmAction.DIPLOMACY -> {
                    String[] f = p.arg().split("\\|", 2);
                    try {
                        msg = f.length < 2 ? "" : com.dominionmod.dominion.kingdom.Diplomacy.act(sp, f[0], java.util.UUID.fromString(f[1])).getString();
                    } catch (IllegalArgumentException e) {
                        msg = "No such kingdom.";
                    }
                }
                default -> {}
            }
            if (p.action() == RealmAction.TRAIN) {
                sp.sendSystemMessage(net.minecraft.network.chat.Component.literal(msg).withStyle(net.minecraft.ChatFormatting.YELLOW));
                return;
            }
            sendRealm(sp, p.action() == RealmAction.OPEN, msg, TAB_KEEP);
        });
    }

    /** Everything in the storehouse, as item id|count (biggest first, at most 160 kinds). */
    private static List<String> stockRows(List<net.minecraft.world.Container> store) {
        java.util.Map<net.minecraft.world.item.Item, Integer> counts = new java.util.HashMap<>();
        for (var c : store) for (int i = 0; i < c.getContainerSize(); i++) {
            var s = c.getItem(i);
            if (!s.isEmpty()) counts.merge(s.getItem(), s.getCount(), Integer::sum);
        }
        List<String> out = new ArrayList<>();
        counts.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(160)
                .forEach(e -> out.add(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(e.getKey()) + "|" + e.getValue()));
        return out;
    }

    public static void sendRealm(ServerPlayer p, boolean open) {
        sendRealm(p, open, "");
    }

    public static void sendRealm(ServerPlayer p, boolean open, String message) {
        sendRealm(p, open, message, TAB_KEEP);
    }

    public static void sendRealm(ServerPlayer p, boolean open, String message, int tab) {
        sendMap(p);
        ServerLevel level = (ServerLevel) p.level();
        KingdomData d = Realm.data(level);
        Kingdom k = d.byPlayer(p.getUUID());
        ChunkPos center = ChunkPos.containing(p.blockPosition());
        List<Integer> map = new ArrayList<>();
        for (int dz = -MAP_RADIUS; dz <= MAP_RADIUS; dz++) {
            for (int dx = -MAP_RADIUS; dx <= MAP_RADIUS; dx++) {
                Kingdom o = d.ownerAt(level, new ChunkPos(center.x() + dx, center.z() + dz));
                map.add(o == null ? -1 : o == k ? 100 + o.primary : o.primary);
            }
        }
        Kingdom here = d.ownerAt(level, center);
        String hereName = here == null ? "" : here.name;
        if (k == null) {
            PacketDistributor.sendToPlayer(p, new RealmInfo(open, false, "", 0, 0, 0, 0, 0, 0, 0, List.of(), 0, 0, hereName, false, map, message,
                    false, "", List.of(), List.of(), tab, List.of(), 0, List.of(), List.of(), List.of(), List.of()));
            return;
        }
        List<Integer> jobs = new ArrayList<>();
        for (Job j : Job.values()) jobs.add(k.countJob(j.ordinal()));
        var store = Storehouse.containers(level, k);
        int coins = Storehouse.count(store, s -> s.is(ModContent.GOLD_COIN.get()));
        int food = Storehouse.count(store, s -> s.has(DataComponents.FOOD));
        // Roster: name|job|bed|workplace|soldier
        var prog = d.progress(k);
        java.util.Map<java.util.UUID, String> works = new java.util.HashMap<>();
        for (var b : prog.buildings) for (var w : b.workers) works.put(w, b.name());
        List<String> roster = new ArrayList<>();
        for (var e : k.citizens.entrySet()) {
            var bed = k.bedOf(e.getKey());
            Job j = Job.byId(e.getValue());
            roster.add(k.names.getOrDefault(e.getKey(), "?") + "|" + j.title + "|"
                    + (bed == null ? "no bed" : bed.getX() + " " + bed.getY() + " " + bed.getZ()) + "|" + works.getOrDefault(e.getKey(), "") + "|" + j.soldier);
        }
        roster.sort(String::compareToIgnoreCase);
        // Sites: id|name|done|total|status
        List<String> sites = new ArrayList<>();
        for (var site : d.sites(k)) {
            int total = site.dim.equals(KingdomData.dim(level)) ? com.dominionmod.dominion.build.Plans.steps(level, site, k).size() : 0;
            sites.add(site.id + "|" + site.name() + "|" + Math.min(site.cursor, total) + "|" + total + "|"
                    + (site.status.isEmpty() ? "Waiting for a builder" : site.status) + "|" + site.origin.getX() + " " + site.origin.getZ());
        }
        // Research: id|status|percent  (status: done, current, open, locked)
        var pr = d.progress(k);
        List<String> research = new ArrayList<>();
        for (var t : com.dominionmod.dominion.kingdom.Research.all()) {
            String status = pr.has(t.id()) ? "done" : t.id().equals(pr.current) ? "current"
                    : t.requires().stream().allMatch(pr::has) ? "open" : "locked";
            int pct = t.id().equals(pr.current) ? (int) Math.min(99, 100 * pr.work / t.work()) : status.equals("done") ? 100 : 0;
            research.add(t.id() + "|" + status + "|" + pct);
        }
        PacketDistributor.sendToPlayer(p, new RealmInfo(open, true, k.name, k.primary, k.secondary, k.influence, Realm.dailyInfluence(d, k),
                Realm.claimCost(d, k), d.claimCount(k), Realm.beds(level, k), jobs, coins, food, hereName, k.peaceful, map, message,
                k.owner.equals(p.getUUID()), com.dominionmod.dominion.kingdom.Flags.fromBanner(k.flag), roster, sites, tab, research,
                pr.scholarsWorking(level.getGameTime()), com.dominionmod.dominion.kingdom.Diplomacy.rows(level, k),
                com.dominionmod.dominion.kingdom.Industry.rows(level, k), com.dominionmod.dominion.kingdom.Watchtowers.rows(level, k), stockRows(store)));
    }
}
