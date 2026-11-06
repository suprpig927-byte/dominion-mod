package com.dominionmod.dominion.build;

import com.dominionmod.dominion.kingdom.Kingdom;
import com.dominionmod.dominion.kingdom.KingdomData;
import com.dominionmod.dominion.kingdom.Realm;
import com.dominionmod.dominion.registry.ModContent;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.ChunkPos;
import org.jspecify.annotations.Nullable;

/** Blueprints: picking a structure, placing it in the world and marking a construction site. */
public final class Construction {
    private Construction() {}

    public static final int MAX_WALL = 64;

    // ------------------------------------------------------------------ blueprint item data

    public static CompoundTag tag(ItemStack stack) {
        CustomData d = stack.get(DataComponents.CUSTOM_DATA);
        return d == null ? new CompoundTag() : d.copyTag();
    }

    public static void save(ItemStack stack, CompoundTag tag) {
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static @Nullable Structures.Structure structureOf(ItemStack stack) {
        return Structures.get(tag(stack).getStringOr("Structure", ""));
    }

    public static ItemStack blueprint(Structures.Structure s) {
        ItemStack stack = new ItemStack(ModContent.BLUEPRINT.get());
        CompoundTag t = new CompoundTag();
        t.putString("Structure", s.id());
        save(stack, t);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Blueprint: " + s.name()).withStyle(s2 -> s2.withItalic(false)));
        return stack;
    }

    public static Component giveBlueprint(ServerPlayer p, String id) {
        Kingdom k = Realm.of(p);
        if (k == null) return Realm.err("You need a kingdom first.");
        Structures.Structure s = Structures.get(id);
        if (s == null) return Realm.err("Unknown structure.");
        Realm.give(p, blueprint(s));
        return Component.literal("You take the blueprint for a " + s.name() + ". Right-click the ground with it to place it.")
                .withStyle(ChatFormatting.GREEN);
    }

    public static Component cancel(ServerPlayer p, String siteId) {
        Kingdom k = Realm.of(p);
        if (k == null) return Realm.err("You need a kingdom first.");
        KingdomData d = Realm.data((ServerLevel) p.level());
        Site s;
        try {
            s = d.site(UUID.fromString(siteId));
        } catch (IllegalArgumentException e) {
            s = null;
        }
        if (s == null || !s.kingdom.equals(k.id)) return Realm.err("No such construction site.");
        d.removeSite(s);
        return Component.literal("Cancelled the " + s.name() + ". What's already built stays.").withStyle(ChatFormatting.YELLOW);
    }

    // ------------------------------------------------------------------ placing

    /** Right-click with a blueprint. Returns the message to show. */
    public static Component use(ServerPlayer p, ItemStack stack, BlockPos clicked) {
        Structures.Structure s = structureOf(stack);
        if (s == null) return Realm.err("This blueprint is blank. Get one from the Build tab of your Realm screen (K).");
        Kingdom k = Realm.of(p);
        if (k == null) return Realm.err("You need a kingdom to build.");
        CompoundTag t = tag(stack);
        BlockPos pos = clicked.immutable();

        if (s.kind() == Structures.Kind.WALL) {
            if (p.isShiftKeyDown()) {
                t.remove("AX");
                t.remove("BX");
                save(stack, t);
                return Component.literal("Wall cleared. Click the first end of the wall.").withStyle(ChatFormatting.GRAY);
            }
            if (!t.contains("AX")) {
                put(t, "A", pos);
                save(stack, t);
                return Component.literal("Wall starts here. Now click where it should end.").withStyle(ChatFormatting.YELLOW);
            }
            BlockPos a = get(t, "A");
            if (t.contains("BX") && get(t, "B").equals(pos)) return confirmWall(p, stack, s, k, a, pos);
            int len = Plans.line(a, pos).size();
            if (len > MAX_WALL) return Realm.err("Walls can be at most " + MAX_WALL + " blocks per blueprint (that's " + len + ").");
            put(t, "B", pos);
            save(stack, t);
            return Component.literal("Wall of " + len + " blocks. Click the end again to confirm, or sneak + click to start over.")
                    .withStyle(ChatFormatting.YELLOW);
        }

        if (p.isShiftKeyDown()) {
            int rot = (t.getIntOr("Rot", Plans.rotationFacing(p.getDirection())) + 1) % 4;
            t.putInt("Rot", rot);
            if (!t.contains("X")) put(t, "", pos);
            save(stack, t);
            return Component.literal("Rotated. Right-click the marked spot to build here.").withStyle(ChatFormatting.GRAY);
        }
        if (t.contains("X") && get(t, "").equals(pos)) return confirmBuilding(p, stack, s, k, pos, t.getIntOr("Rot", 0));
        if (!t.contains("Rot")) t.putInt("Rot", Plans.rotationFacing(p.getDirection()));
        put(t, "", pos);
        save(stack, t);
        return Component.literal("Marked out a " + s.name() + " (" + s.width() + "x" + s.depth()
                + "). Right-click the same spot again to build, or sneak + right-click to rotate.").withStyle(ChatFormatting.YELLOW);
    }

    private static Component confirmBuilding(ServerPlayer p, ItemStack stack, Structures.Structure s, Kingdom k, BlockPos ground, int rot) {
        ServerLevel level = (ServerLevel) p.level();
        BlockPos[] c = Plans.corners(s, ground, rot);
        String bad = checkLand(level, k, c[0], c[1]);
        if (bad != null) return Realm.err(bad);
        Site site = new Site(UUID.randomUUID(), k.id, s.id(), KingdomData.dim(level), ground, ground, rot, List.of(), 0);
        return mark(p, stack, s, site);
    }

    private static Component confirmWall(ServerPlayer p, ItemStack stack, Structures.Structure s, Kingdom k, BlockPos a, BlockPos b) {
        ServerLevel level = (ServerLevel) p.level();
        List<BlockPos> cols = Plans.line(a, b);
        for (BlockPos col : cols) {
            String bad = checkLand(level, k, col, col);
            if (bad != null) return Realm.err(bad);
        }
        Site site = new Site(UUID.randomUUID(), k.id, s.id(), KingdomData.dim(level), a, b, 0, Plans.groundHeights(level, cols), 0);
        return mark(p, stack, s, site);
    }

    private static Component mark(ServerPlayer p, ItemStack stack, Structures.Structure s, Site site) {
        Realm.data((ServerLevel) p.level()).addSite(site);
        if (!p.isCreative()) stack.shrink(1);
        else save(stack, structureOnly(s));
        return Component.literal("Construction site marked for a " + s.name()
                + ". Builders will start as soon as the storehouse has the materials.").withStyle(ChatFormatting.GREEN);
    }

    private static CompoundTag structureOnly(Structures.Structure s) {
        CompoundTag t = new CompoundTag();
        t.putString("Structure", s.id());
        return t;
    }

    /** Is every chunk between the two corners ours, with no Town Hall inside? */
    private static @Nullable String checkLand(ServerLevel level, Kingdom k, BlockPos a, BlockPos b) {
        KingdomData d = Realm.data(level);
        int x0 = Math.min(a.getX(), b.getX()), x1 = Math.max(a.getX(), b.getX());
        int z0 = Math.min(a.getZ(), b.getZ()), z1 = Math.max(a.getZ(), b.getZ());
        for (int cx = x0 >> 4; cx <= x1 >> 4; cx++)
            for (int cz = z0 >> 4; cz <= z1 >> 4; cz++)
                if (d.ownerAt(level, new ChunkPos(cx, cz)) != k) return "It has to be built entirely on your own land.";
        if (k.hall.isPresent()) {
            BlockPos h = k.hall.get();
            if (h.getX() >= x0 - 1 && h.getX() <= x1 + 1 && h.getZ() >= z0 - 1 && h.getZ() <= z1 + 1)
                return "That's too close to your Town Hall.";
        }
        return null;
    }

    private static void put(CompoundTag t, String prefix, BlockPos p) {
        t.putInt(prefix + "X", p.getX());
        t.putInt(prefix + "Y", p.getY());
        t.putInt(prefix + "Z", p.getZ());
    }

    private static BlockPos get(CompoundTag t, String prefix) {
        return new BlockPos(t.getIntOr(prefix + "X", 0), t.getIntOr(prefix + "Y", 0), t.getIntOr(prefix + "Z", 0));
    }

    // ------------------------------------------------------------------ preview

    /** While a player holds a marked blueprint, outline where it will go. */
    public static void preview(ServerPlayer p, ItemStack stack, int color) {
        Structures.Structure s = structureOf(stack);
        if (s == null) return;
        CompoundTag t = tag(stack);
        ServerLevel level = (ServerLevel) p.level();
        DustParticleOptions dust = new DustParticleOptions(color, 1.0f);
        if (s.kind() == Structures.Kind.WALL) {
            if (!t.contains("AX")) return;
            BlockPos a = get(t, "A");
            BlockPos b = t.contains("BX") ? get(t, "B") : a;
            List<BlockPos> cols = Plans.line(a, b);
            List<Integer> ys = Plans.groundHeights(level, cols);
            for (int i = 0; i < cols.size(); i += 1) {
                BlockPos c = cols.get(i);
                for (int y = 0; y < s.height(); y += 2)
                    level.sendParticles(p, dust, true, false, c.getX() + 0.5, ys.get(i) + y + 0.5, c.getZ() + 0.5, 1, 0, 0, 0, 0);
            }
            return;
        }
        if (!t.contains("X")) return;
        BlockPos ground = get(t, "");
        int rot = t.getIntOr("Rot", 0);
        BlockPos[] c = Plans.corners(s, ground, rot);
        int x0 = Math.min(c[0].getX(), c[1].getX()), x1 = Math.max(c[0].getX(), c[1].getX()) + 1;
        int z0 = Math.min(c[0].getZ(), c[1].getZ()), z1 = Math.max(c[0].getZ(), c[1].getZ()) + 1;
        int y0 = ground.getY() + 1, y1 = y0 + s.height() - s.sink();
        for (double x = x0; x <= x1; x += 1) {
            for (double y : new double[]{y0, y1}) {
                level.sendParticles(p, dust, true, false, x, y, z0, 1, 0, 0, 0, 0);
                level.sendParticles(p, dust, true, false, x, y, z1, 1, 0, 0, 0, 0);
            }
        }
        for (double z = z0; z <= z1; z += 1) {
            for (double y : new double[]{y0, y1}) {
                level.sendParticles(p, dust, true, false, x0, y, z, 1, 0, 0, 0, 0);
                level.sendParticles(p, dust, true, false, x1, y, z, 1, 0, 0, 0, 0);
            }
        }
        for (double y = y0; y <= y1; y += 1) {
            level.sendParticles(p, dust, true, false, x0, y, z0, 1, 0, 0, 0, 0);
            level.sendParticles(p, dust, true, false, x1, y, z0, 1, 0, 0, 0, 0);
            level.sendParticles(p, dust, true, false, x0, y, z1, 1, 0, 0, 0, 0);
            level.sendParticles(p, dust, true, false, x1, y, z1, 1, 0, 0, 0, 0);
        }
        // The front edge (where the door goes) is marked in the secondary color.
        BlockPos fa = Plans.cell(s, ground, rot, 0, s.sink(), s.depth() - 1), fb = Plans.cell(s, ground, rot, s.width() - 1, s.sink(), s.depth() - 1);
        DustParticleOptions front = new DustParticleOptions(DyeColor.WHITE.getTextureDiffuseColor(), 1.4f);
        for (BlockPos q : Plans.line(fa, fb)) level.sendParticles(p, front, true, false, q.getX() + 0.5, y0 + 0.1, q.getZ() + 0.5, 1, 0, 0, 0, 0);
    }
}
