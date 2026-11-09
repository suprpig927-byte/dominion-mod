package com.dominionmod.dominion.kingdom;

import com.dominionmod.dominion.DominionMod;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;

/** All kingdoms and every claimed chunk, saved with the world. */
public final class KingdomData extends SavedData {
    record Claim(String dim, long chunk, UUID kingdom) {
        static final Codec<Claim> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("dim").forGetter(Claim::dim),
                Codec.LONG.fieldOf("chunk").forGetter(Claim::chunk),
                UUIDUtil.STRING_CODEC.fieldOf("kingdom").forGetter(Claim::kingdom)
        ).apply(i, Claim::new));
    }

    /** An orc village found in the wild: its War Totem, and whether it's been conquered. */
    public static final class OrcVillage {
        public final String dim;
        public final BlockPos totem;
        public boolean conquered;
        /** 0 = village, 1 = war camp, 2 = stronghold (palisade, watch platforms, a Warlord). */
        public int stage;
        public int days;
        public long lastDay = -1, lastRaid = -1;
        public boolean warlordSlain;

        public OrcVillage(String dim, BlockPos totem, boolean conquered) {
            this.dim = dim;
            this.totem = totem;
            this.conquered = conquered;
        }

        private OrcVillage(String dim, BlockPos totem, boolean conquered, int stage, int days, long lastDay, long lastRaid, boolean warlordSlain) {
            this(dim, totem, conquered);
            this.stage = stage;
            this.days = days;
            this.lastDay = lastDay;
            this.lastRaid = lastRaid;
            this.warlordSlain = warlordSlain;
        }

        static final Codec<OrcVillage> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("dim").forGetter(v -> v.dim),
                BlockPos.CODEC.fieldOf("totem").forGetter(v -> v.totem),
                Codec.BOOL.optionalFieldOf("conquered", false).forGetter(v -> v.conquered),
                Codec.INT.optionalFieldOf("stage", 0).forGetter(v -> v.stage),
                Codec.INT.optionalFieldOf("days", 0).forGetter(v -> v.days),
                Codec.LONG.optionalFieldOf("last_day", -1L).forGetter(v -> v.lastDay),
                Codec.LONG.optionalFieldOf("last_raid", -1L).forGetter(v -> v.lastRaid),
                Codec.BOOL.optionalFieldOf("warlord_slain", false).forGetter(v -> v.warlordSlain)
        ).apply(i, OrcVillage::new));

        public String title() {
            return stage >= 2 ? "orc stronghold" : stage == 1 ? "orc war camp" : "orc village";
        }
    }

    /** How two kingdoms stand with each other (unordered pair). Absent = neutral. */
    public static final class Pact {
        public final UUID a, b;
        public String state;
        public long since;
        /** Neither side's AI starts a war before this day (after peace is made). */
        public long truceUntil;

        public Pact(UUID a, UUID b, String state, long since, long truceUntil) {
            this.a = a;
            this.b = b;
            this.state = state;
            this.since = since;
            this.truceUntil = truceUntil;
        }

        static final Codec<Pact> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.STRING_CODEC.fieldOf("a").forGetter(p -> p.a),
                UUIDUtil.STRING_CODEC.fieldOf("b").forGetter(p -> p.b),
                Codec.STRING.fieldOf("state").forGetter(p -> p.state),
                Codec.LONG.optionalFieldOf("since", 0L).forGetter(p -> p.since),
                Codec.LONG.optionalFieldOf("truce", 0L).forGetter(p -> p.truceUntil)
        ).apply(i, Pact::new));

        public boolean involves(UUID k) { return a.equals(k) || b.equals(k); }
        public UUID other(UUID k) { return a.equals(k) ? b : a; }
    }

    /** A block knocked out of a kingdom's walls or buildings in a siege, waiting for a builder to put it back. */
    public record Ruin(UUID kingdom, String dim, BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        static final Codec<Ruin> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.STRING_CODEC.fieldOf("kingdom").forGetter(Ruin::kingdom),
                Codec.STRING.fieldOf("dim").forGetter(Ruin::dim),
                BlockPos.CODEC.fieldOf("pos").forGetter(Ruin::pos),
                net.minecraft.world.level.block.state.BlockState.CODEC.fieldOf("state").forGetter(Ruin::state)
        ).apply(i, Ruin::new));
    }

    public static final Codec<KingdomData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Kingdom.CODEC.listOf().optionalFieldOf("kingdoms", List.of()).forGetter(d -> new ArrayList<>(d.kingdoms.values())),
            Claim.CODEC.listOf().optionalFieldOf("claims", List.of()).forGetter(KingdomData::claimList),
            com.dominionmod.dominion.build.Site.CODEC.listOf().optionalFieldOf("sites", List.of()).forGetter(d -> new ArrayList<>(d.sites)),
            Progress.CODEC.listOf().optionalFieldOf("progress", List.of()).forGetter(d -> new ArrayList<>(d.progress.values())),
            Codec.LONG.listOf().optionalFieldOf("settled", List.of()).forGetter(d -> new ArrayList<>(d.settled)),
            OrcVillage.CODEC.listOf().optionalFieldOf("orc_villages", List.of()).forGetter(d -> new ArrayList<>(d.orcVillages)),
            Pact.CODEC.listOf().optionalFieldOf("pacts", List.of()).forGetter(d -> new ArrayList<>(d.pacts)),
            Rivals.Rival.CODEC.listOf().optionalFieldOf("rivals", List.of()).forGetter(d -> new ArrayList<>(d.rivals.values())),
            Armies.Army.CODEC.listOf().optionalFieldOf("armies", List.of()).forGetter(d -> new ArrayList<>(d.armies)),
            Ruin.CODEC.listOf().optionalFieldOf("ruins", List.of()).forGetter(d -> new ArrayList<>(d.ruins)),
            Caravans.Caravan.CODEC.listOf().optionalFieldOf("caravans", List.of()).forGetter(d -> new ArrayList<>(d.caravans)),
            OrcVillage.CODEC.listOf().optionalFieldOf("illager_camps", List.of()).forGetter(d -> new ArrayList<>(d.illagerCamps))
    ).apply(i, KingdomData::new));

    public static final SavedDataType<KingdomData> TYPE = new SavedDataType<>(DominionMod.id("kingdoms"), KingdomData::new, CODEC);

    private final Map<UUID, Kingdom> kingdoms = new LinkedHashMap<>();
    /** dimension id -> chunk -> kingdom */
    private final Map<String, Map<Long, UUID>> claims = new HashMap<>();

    public KingdomData() {}

    private final List<com.dominionmod.dominion.build.Site> sites = new ArrayList<>();

    private final Map<UUID, Progress> progress = new HashMap<>();

    /** Wild regions already looked at for settlements (see Settlements). */
    public final java.util.Set<Long> settled = new java.util.HashSet<>();
    public final List<OrcVillage> orcVillages = new ArrayList<>();
    public final List<Pact> pacts = new ArrayList<>();
    /** The AI state of every kingdom found in the wild. */
    public final Map<UUID, Rivals.Rival> rivals = new LinkedHashMap<>();
    /** Armies and raiding parties on the march. */
    public final List<Armies.Army> armies = new ArrayList<>();
    public final List<Ruin> ruins = new ArrayList<>();
    public final List<Caravans.Caravan> caravans = new ArrayList<>();
    /** Illager camps (same bookkeeping as orc villages: the "totem" is the Illager Standard). */
    public final List<OrcVillage> illagerCamps = new ArrayList<>();

    private KingdomData(List<Kingdom> list, List<Claim> claimList, List<com.dominionmod.dominion.build.Site> siteList, List<Progress> progressList,
                        List<Long> settledList, List<OrcVillage> villages, List<Pact> pactList, List<Rivals.Rival> rivalList, List<Armies.Army> armyList,
                        List<Ruin> ruinList, List<Caravans.Caravan> caravanList, List<OrcVillage> camps) {
        illagerCamps.addAll(camps);
        ruins.addAll(ruinList);
        caravans.addAll(caravanList);
        pacts.addAll(pactList);
        for (var r : rivalList) rivals.put(r.kingdom, r);
        armies.addAll(armyList);
        settled.addAll(settledList);
        orcVillages.addAll(villages);
        for (var site : siteList) sites.add(site);
        for (var pr : progressList) progress.put(pr.kingdom, pr);
        for (Kingdom k : list) kingdoms.put(k.id, k);
        for (Claim c : claimList) {
            if (kingdoms.containsKey(c.kingdom())) claims.computeIfAbsent(c.dim(), d -> new HashMap<>()).put(c.chunk(), c.kingdom());
        }
    }

    private List<Claim> claimList() {
        List<Claim> out = new ArrayList<>();
        claims.forEach((dim, m) -> m.forEach((chunk, k) -> out.add(new Claim(dim, chunk, k))));
        return out;
    }

    public static KingdomData get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    public static String dim(Level level) {
        return level.dimension().identifier().toString();
    }

    // ------------------------------------------------------------------ kingdoms

    public Collection<Kingdom> all() {
        return kingdoms.values();
    }

    public @Nullable Kingdom byId(@Nullable UUID id) {
        return id == null ? null : kingdoms.get(id);
    }

    public @Nullable Kingdom byPlayer(UUID player) {
        for (Kingdom k : kingdoms.values()) if (k.isMember(player)) return k;
        return null;
    }

    public @Nullable Kingdom byName(String name) {
        for (Kingdom k : kingdoms.values()) if (k.name.equalsIgnoreCase(name.trim())) return k;
        return null;
    }

    public void add(Kingdom k) {
        kingdoms.put(k.id, k);
        setDirty();
    }

    public void remove(Kingdom k) {
        kingdoms.remove(k.id);
        for (Map<Long, UUID> m : claims.values()) m.values().removeIf(k.id::equals);
        sites.removeIf(s -> s.kingdom.equals(k.id));
        progress.remove(k.id);
        pacts.removeIf(p -> p.involves(k.id));
        rivals.remove(k.id);
        armies.removeIf(a -> k.id.equals(a.kingdom) || k.id.equals(a.target));
        ruins.removeIf(r -> r.kingdom().equals(k.id));
        caravans.removeIf(c -> k.id.equals(c.kingdom) || k.id.equals(c.partner));
        setDirty();
    }

    // ------------------------------------------------------------------ claims

    public @Nullable UUID ownerId(Level level, ChunkPos chunk) {
        Map<Long, UUID> m = claims.get(dim(level));
        return m == null ? null : m.get(chunk.pack());
    }

    public @Nullable Kingdom ownerAt(Level level, ChunkPos chunk) {
        return byId(ownerId(level, chunk));
    }

    public @Nullable Kingdom ownerAt(Level level, BlockPos pos) {
        return ownerAt(level, ChunkPos.containing(pos));
    }

    public void claim(Level level, ChunkPos chunk, Kingdom k) {
        claims.computeIfAbsent(dim(level), d -> new HashMap<>()).put(chunk.pack(), k.id);
        setDirty();
    }

    public void unclaim(Level level, ChunkPos chunk) {
        Map<Long, UUID> m = claims.get(dim(level));
        if (m != null && m.remove(chunk.pack()) != null) setDirty();
    }

    public int claimCount(Kingdom k) {
        int n = 0;
        for (Map<Long, UUID> m : claims.values()) for (UUID id : m.values()) if (id.equals(k.id)) n++;
        return n;
    }

    /** Does the kingdom own a chunk that shares an edge with this one (same dimension)? */
    public boolean touches(Level level, ChunkPos chunk, Kingdom k) {
        Map<Long, UUID> m = claims.get(dim(level));
        if (m == null) return false;
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] d : dirs) {
            if (k.id.equals(m.get(ChunkPos.pack(chunk.x() + d[0], chunk.z() + d[1])))) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ construction sites

    public List<com.dominionmod.dominion.build.Site> sites(Kingdom k) {
        return sites.stream().filter(s -> s.kingdom.equals(k.id)).toList();
    }

    public com.dominionmod.dominion.build.@Nullable Site site(UUID id) {
        for (var s : sites) if (s.id.equals(id)) return s;
        return null;
    }

    public void addSite(com.dominionmod.dominion.build.Site s) {
        sites.add(s);
        setDirty();
    }

    public void removeSite(com.dominionmod.dominion.build.Site s) {
        sites.remove(s);
        com.dominionmod.dominion.build.Plans.forget(s.id);
        setDirty();
    }

    /** Research, buildings and storage labels for a kingdom. */
    public Progress progress(Kingdom k) {
        return progress.computeIfAbsent(k.id, Progress::new);
    }

    // ------------------------------------------------------------------ diplomacy

    public @Nullable Pact pact(UUID x, UUID y) {
        for (Pact p : pacts) if (p.involves(x) && p.involves(y) && !x.equals(y)) return p;
        return null;
    }

    public Rivals.Rival rival(Kingdom k) {
        return rivals.computeIfAbsent(k.id, Rivals.Rival::new);
    }

    /** All claimed chunks (packed) of a kingdom in one dimension. */
    public List<Long> claimsOf(Kingdom k, String dim) {
        List<Long> out = new ArrayList<>();
        Map<Long, UUID> m = claims.get(dim);
        if (m != null) m.forEach((c, id) -> { if (id.equals(k.id)) out.add(c); });
        return out;
    }

    public void markChanged() {
        setDirty();
    }
}
