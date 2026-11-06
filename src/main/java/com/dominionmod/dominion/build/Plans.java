package com.dominionmod.dominion.build;

import com.dominionmod.dominion.kingdom.Kingdom;
import com.dominionmod.dominion.registry.ModContent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

/** Turns a structure + a place into an ordered list of blocks to clear and place. */
public final class Plans {
    private Plans() {}

    /**
     * One step of construction. {@code state == null} means "clear this block". {@code cost} is the
     * item it uses up (null = free). {@code companion} is a second block placed with it (a door's top, a bed's head).
     */
    public record Step(BlockPos pos, @Nullable BlockState state, @Nullable Item cost, @Nullable BlockPos companionPos,
                       @Nullable BlockState companion, boolean flag) {
        public boolean isClear() { return state == null; }
    }

    private static final Map<UUID, List<Step>> CACHE = new HashMap<>();

    public static List<Step> steps(Level level, Site site, Kingdom k) {
        return CACHE.computeIfAbsent(site.id, id -> build(level, site, k));
    }

    public static void forget(UUID site) {
        CACHE.remove(site);
    }

    public static Rotation rotation(int r) {
        return Rotation.values()[Math.floorMod(r, 4)];
    }

    /** Which way the front of a building faces when the player looks along {@code look}: the front faces them. */
    public static int rotationFacing(Direction look) {
        return switch (look) {
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> 0;
        };
    }

    // ------------------------------------------------------------------ building footprint

    /** World position of layout cell (x, layer, z) for a building placed at {@code ground}. */
    public static BlockPos cell(Structures.Structure s, BlockPos ground, int rot, int x, int layer, int z) {
        BlockPos local = new BlockPos(x - s.width() / 2, 0, z - s.depth() / 2).rotate(rotation(rot));
        return ground.above(1 + layer - s.sink()).offset(local.getX(), 0, local.getZ());
    }

    /** The two opposite corners of a building's footprint, at ground level. */
    public static BlockPos[] corners(Structures.Structure s, BlockPos ground, int rot) {
        BlockPos a = cell(s, ground, rot, 0, s.sink(), 0).atY(ground.getY() + 1);
        BlockPos b = cell(s, ground, rot, s.width() - 1, s.sink(), s.depth() - 1).atY(ground.getY() + 1);
        return new BlockPos[]{a, b};
    }

    // ------------------------------------------------------------------ walls

    /** The columns of a wall from a to b (straight or diagonal lines, one block thick). */
    public static List<BlockPos> line(BlockPos a, BlockPos b) {
        List<BlockPos> pts = new ArrayList<>();
        int x0 = a.getX(), z0 = a.getZ(), x1 = b.getX(), z1 = b.getZ();
        int dx = Math.abs(x1 - x0), dz = -Math.abs(z1 - z0), sx = x0 < x1 ? 1 : -1, sz = z0 < z1 ? 1 : -1;
        int err = dx + dz;
        while (true) {
            pts.add(new BlockPos(x0, a.getY(), z0));
            if (x0 == x1 && z0 == z1) break;
            int e2 = 2 * err;
            if (e2 >= dz) { err += dz; x0 += sx; }
            if (e2 <= dx) { err += dx; z0 += sz; }
        }
        // Fill diagonal steps so the wall has no gaps to slip through.
        List<BlockPos> out = new ArrayList<>();
        for (int i = 0; i < pts.size(); i++) {
            BlockPos p = pts.get(i);
            if (i > 0) {
                BlockPos q = pts.get(i - 1);
                if (q.getX() != p.getX() && q.getZ() != p.getZ()) out.add(new BlockPos(p.getX(), a.getY(), q.getZ()));
            }
            out.add(p);
        }
        return out;
    }

    /** Does this stack count as the item a step needs? Any wool or bed color will do. */
    public static boolean satisfies(Item cost, net.minecraft.world.item.ItemStack stack) {
        if (stack.is(cost)) return true;
        var costStack = new net.minecraft.world.item.ItemStack(cost);
        if (costStack.is(net.minecraft.tags.ItemTags.WOOL)) return stack.is(net.minecraft.tags.ItemTags.WOOL);
        if (costStack.is(net.minecraft.tags.ItemTags.BEDS)) return stack.is(net.minecraft.tags.ItemTags.BEDS);
        return false;
    }

    public static List<Integer> groundHeights(Level level, List<BlockPos> columns) {
        List<Integer> ys = new ArrayList<>();
        for (BlockPos c : columns) ys.add(level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, c.getX(), c.getZ()));
        return ys;
    }

    // ------------------------------------------------------------------ expansion

    private static List<Step> build(Level level, Site site, Kingdom k) {
        Structures.Structure s = Structures.get(site.structure);
        List<Step> clears = new ArrayList<>(), places = new ArrayList<>();
        if (s == null) return places;
        Rotation rot = rotation(site.rotation);
        if (s.kind() == Structures.Kind.WALL) {
            List<BlockPos> cols = line(site.origin, site.end);
            for (int i = 0; i < cols.size(); i++) {
                int ground = i < site.columns.size() ? site.columns.get(i) : site.origin.getY() + 1;
                for (int y = 0; y < s.height(); y++) {
                    char c = s.layers().get(y)[0].charAt(i % 2);
                    BlockPos p = new BlockPos(cols.get(i).getX(), ground + y, cols.get(i).getZ());
                    add(level, k, c, p, rot, clears, places);
                }
            }
        } else {
            for (int layer = 0; layer < s.height(); layer++) {
                String[] rows = s.layers().get(layer);
                for (int z = 0; z < rows.length; z++) {
                    for (int x = 0; x < rows[z].length(); x++) {
                        char c = rows[z].charAt(x);
                        BlockPos p = cell(s, site.origin, site.rotation, x, layer, z);
                        if (layer == 0 && s.sink() > 0 && c != ' ') supportBelow(level, p, places);
                        add(level, k, c, p, rot, clears, places);
                    }
                }
            }
        }
        // Clear from the top down, then build from the bottom up.
        clears.sort((a, b) -> Integer.compare(b.pos().getY(), a.pos().getY()));
        List<Step> out = new ArrayList<>(clears);
        out.addAll(places);
        return out;
    }

    /** Fill air under a foundation with dirt (free) so buildings on slopes don't float. */
    private static void supportBelow(Level level, BlockPos p, List<Step> places) {
        List<Step> fill = new ArrayList<>();
        BlockPos q = p.below();
        for (int i = 0; i < 6 && level.getBlockState(q).canBeReplaced(); i++, q = q.below()) {
            fill.add(0, new Step(q.immutable(), Blocks.DIRT.defaultBlockState(), null, null, null, false));
        }
        places.addAll(fill);
    }

    private static void add(Level level, Kingdom k, char c, BlockPos p, Rotation rot, List<Step> clears, List<Step> places) {
        BlockState state;
        Item cost = null;
        BlockPos compPos = null;
        BlockState comp = null;
        boolean flag = false;
        switch (c) {
            case ' ', 'u', 'h' -> { return; } // untouched, or placed together with their partner
            case '.' -> {
                clears.add(new Step(p.immutable(), null, null, null, null, false));
                return;
            }
            case 'C' -> state = Blocks.COBBLESTONE.defaultBlockState();
            case 'M' -> state = Blocks.STONE_BRICKS.defaultBlockState();
            case 'P' -> state = Blocks.OAK_PLANKS.defaultBlockState();
            case 'L' -> state = Blocks.OAK_LOG.defaultBlockState();
            case 'O' -> state = Blocks.SPRUCE_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
            case 'o' -> state = Blocks.SPRUCE_FENCE.defaultBlockState();
            case 's' -> state = Blocks.OAK_SLAB.defaultBlockState();
            case 'G' -> state = Blocks.GLASS_PANE.defaultBlockState();
            case 'F' -> state = Blocks.OAK_FENCE.defaultBlockState();
            case 'g' -> state = Blocks.OAK_FENCE_GATE.defaultBlockState().setValue(FenceGateBlock.FACING, Direction.SOUTH);
            case 'K' -> state = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH);
            case 'R' -> state = Blocks.BARREL.defaultBlockState();
            case 'Q' -> state = Blocks.CRAFTING_TABLE.defaultBlockState();
            case 'l' -> state = Blocks.LANTERN.defaultBlockState();
            case '>' -> state = Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(net.minecraft.world.level.block.StairBlock.FACING, Direction.EAST);
            case '<' -> state = Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(net.minecraft.world.level.block.StairBlock.FACING, Direction.WEST);
            case 'A' -> state = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH);
            case 'T' -> state = ModContent.MARKET.get().defaultBlockState();
            case 'X' -> state = Blocks.WOOL.pick(DyeColor.byId(k.primary)).defaultBlockState();
            case 'Z' -> state = Blocks.WOOL.pick(DyeColor.byId(k.secondary)).defaultBlockState();
            case 'f' -> state = Blocks.FARMLAND.defaultBlockState();
            case 'W' -> state = Blocks.WATER.defaultBlockState();
            case 'w' -> state = ((CropBlock) Blocks.WHEAT).getStateForAge(0);
            case 'N' -> {
                state = Blocks.BANNER.pick(DyeColor.byId(com.dominionmod.dominion.kingdom.Flags.base(
                        com.dominionmod.dominion.kingdom.Flags.fromBanner(k.flag)))).defaultBlockState();
                flag = true;
            }
            case 'D' -> {
                state = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.SOUTH).setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
                compPos = p.above();
                comp = state.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER).rotate(rot);
            }
            case 'b' -> {
                Block bed = Blocks.BED.pick(DyeColor.byId(k.primary));
                state = bed.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH).setValue(BedBlock.PART, BedPart.FOOT);
                compPos = p.relative(rot.rotate(Direction.NORTH));
                comp = state.setValue(BedBlock.PART, BedPart.HEAD).rotate(rot);
            }
            default -> { return; }
        }
        state = state.rotate(rot);
        boolean free = c == 'f' || c == 'W' || c == 'N';
        if (!free) cost = c == 'w' ? Items.WHEAT_SEEDS : state.getBlock().asItem();
        places.add(new Step(p.immutable(), state, cost, compPos == null ? null : compPos.immutable(), comp, flag));
    }

    /** Items a structure needs (for walls: per column). */
    public static Map<Item, Integer> materials(Structures.Structure s) {
        Map<Item, Integer> out = new LinkedHashMap<>();
        for (String[] rows : s.layers()) {
            for (String row : rows) {
                for (int i = 0; i < row.length(); i++) {
                    Item item = itemFor(row.charAt(i));
                    if (item != null) out.merge(item, 1, Integer::sum);
                }
            }
        }
        if (s.kind() == Structures.Kind.WALL) out.replaceAll((item, n) -> Math.max(1, n / 2));
        return out;
    }

    private static @Nullable Item itemFor(char c) {
        return switch (c) {
            case 'C' -> Items.COBBLESTONE;
            case 'M' -> Items.STONE_BRICKS;
            case 'P' -> Items.OAK_PLANKS;
            case 'L' -> Items.OAK_LOG;
            case 'O' -> Items.SPRUCE_LOG;
            case 'o' -> Items.SPRUCE_FENCE;
            case 's' -> Items.OAK_SLAB;
            case 'G' -> Items.GLASS_PANE;
            case 'F' -> Items.OAK_FENCE;
            case 'g' -> Items.OAK_FENCE_GATE;
            case 'K' -> Items.CHEST;
            case 'R' -> Items.BARREL;
            case 'Q' -> Items.CRAFTING_TABLE;
            case 'l' -> Items.LANTERN;
            case '>', '<' -> Items.SPRUCE_STAIRS;
            case 'A' -> Items.LADDER;
            case 'T' -> ModContent.MARKET_ITEM.get();
            case 'X', 'Z' -> Items.WOOL.pick(DyeColor.WHITE);
            case 'w' -> Items.WHEAT_SEEDS;
            case 'D' -> Items.OAK_DOOR;
            case 'b' -> Items.BED.pick(DyeColor.WHITE);
            default -> null;
        };
    }
}
