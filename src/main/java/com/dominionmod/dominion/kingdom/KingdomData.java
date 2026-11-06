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

    public static final Codec<KingdomData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Kingdom.CODEC.listOf().optionalFieldOf("kingdoms", List.of()).forGetter(d -> new ArrayList<>(d.kingdoms.values())),
            Claim.CODEC.listOf().optionalFieldOf("claims", List.of()).forGetter(KingdomData::claimList)
    ).apply(i, KingdomData::new));

    public static final SavedDataType<KingdomData> TYPE = new SavedDataType<>(DominionMod.id("kingdoms"), KingdomData::new, CODEC);

    private final Map<UUID, Kingdom> kingdoms = new LinkedHashMap<>();
    /** dimension id -> chunk -> kingdom */
    private final Map<String, Map<Long, UUID>> claims = new HashMap<>();

    public KingdomData() {}

    private KingdomData(List<Kingdom> list, List<Claim> claimList) {
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

    public void markChanged() {
        setDirty();
    }
}
