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

    /** Server -> client: open the kingdom founding screen for the Town Hall at {@code pos}. */
    public record OpenFounding(BlockPos pos) implements CustomPacketPayload {
        public static final Type<OpenFounding> TYPE = new Type<>(DominionMod.id("open_founding"));
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenFounding> CODEC = StreamCodec.of(
                (RegistryFriendlyByteBuf buf, OpenFounding p) -> buf.writeBlockPos(p.pos), buf -> new OpenFounding(buf.readBlockPos()));

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Client -> server: found the kingdom. Colors are dye ids (0-15). */
    public record FoundKingdom(BlockPos pos, String name, int primary, int secondary) implements CustomPacketPayload {
        public static final Type<FoundKingdom> TYPE = new Type<>(DominionMod.id("found_kingdom"));
        public static final StreamCodec<RegistryFriendlyByteBuf, FoundKingdom> CODEC = StreamCodec.of(
                (RegistryFriendlyByteBuf buf, FoundKingdom p) -> {
                    buf.writeBlockPos(p.pos);
                    buf.writeUtf(p.name, 64);
                    buf.writeVarInt(p.primary);
                    buf.writeVarInt(p.secondary);
                },
                buf -> new FoundKingdom(buf.readBlockPos(), buf.readUtf(64), buf.readVarInt(), buf.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Client -> server: a Realm screen button (or opening the screen, which asks for fresh info). */
    public record RealmAction(int action) implements CustomPacketPayload {
        public static final int REFRESH = 0, CLAIM = 1, UNCLAIM = 2, TOGGLE_PEACEFUL = 3, OPEN = 4;
        public static final Type<RealmAction> TYPE = new Type<>(DominionMod.id("realm_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, RealmAction> CODEC = StreamCodec.of(
                (RegistryFriendlyByteBuf buf, RealmAction p) -> buf.writeVarInt(p.action), buf -> new RealmAction(buf.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /**
     * Server -> client: everything the Realm screen shows. {@code map} holds one entry per chunk
     * around the player, row by row: -1 = wild, 0-15 = another kingdom's dye color, 100-115 = yours.
     */
    public record RealmInfo(boolean open, boolean has, String name, int primary, int secondary, double influence, double daily,
                            int claimCost, int chunks, int beds, List<Integer> jobs, int coins, int food, String here,
                            boolean peaceful, List<Integer> map, String message) implements CustomPacketPayload {
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
                },
                buf -> new RealmInfo(buf.readBoolean(), buf.readBoolean(), buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readDouble(),
                        buf.readDouble(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), readInts(buf), buf.readVarInt(), buf.readVarInt(),
                        buf.readUtf(), buf.readBoolean(), readInts(buf), buf.readUtf()));

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }

        public int citizens() {
            int n = 0;
            for (int j : jobs) n += j;
            return n;
        }
    }

    private static void writeInts(RegistryFriendlyByteBuf buf, List<Integer> list) {
        buf.writeVarInt(list.size());
        for (int i : list) buf.writeVarInt(i + 1);
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

        r.playToServer(FoundKingdom.TYPE, FoundKingdom.CODEC, (p, ctx) -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            String err = Realm.found(sp, p.pos(), p.name(), p.primary(), p.secondary());
            if (err != null) {
                sp.sendSystemMessage(Realm.err(err));
                PacketDistributor.sendToPlayer(sp, new OpenFounding(p.pos()));
            } else {
                sendRealm(sp, true);
            }
        });

        r.playToServer(RealmAction.TYPE, RealmAction.CODEC, (p, ctx) -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
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
                default -> {}
            }
            sendRealm(sp, p.action() == RealmAction.OPEN, msg);
        });
    }

    public static void sendRealm(ServerPlayer p, boolean open) {
        sendRealm(p, open, "");
    }

    public static void sendRealm(ServerPlayer p, boolean open, String message) {
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
            PacketDistributor.sendToPlayer(p, new RealmInfo(open, false, "", 0, 0, 0, 0, 0, 0, 0, List.of(), 0, 0, hereName, false, map, message));
            return;
        }
        List<Integer> jobs = new ArrayList<>();
        for (Job j : Job.values()) jobs.add(k.countJob(j.ordinal()));
        var store = Storehouse.containers(level, k);
        int coins = Storehouse.count(store, s -> s.is(ModContent.GOLD_COIN.get()));
        int food = Storehouse.count(store, s -> s.has(DataComponents.FOOD));
        PacketDistributor.sendToPlayer(p, new RealmInfo(open, true, k.name, k.primary, k.secondary, k.influence, Realm.dailyInfluence(k),
                Realm.claimCost(d, k), d.claimCount(k), Realm.beds(level, k), jobs, coins, food, hereName, k.peaceful, map, message));
    }
}
