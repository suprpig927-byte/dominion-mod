package com.dominionmod.dominion.build;

import com.dominionmod.dominion.registry.ModContent;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Which block each layout character becomes, per building tier. Researching a tier lets you
 * build (and upgrade buildings) in its materials.
 */
public final class Palette {
    private Palette() {}

    public static final int THATCH = 0, TIMBER = 1, STONE = 2, BRICK = 3, ROYAL = 4;
    public static final String[] TIER_NAMES = {"Thatch", "Timber", "Stone", "Brick", "Royal"};
    /** The research that unlocks each tier (Thatch needs none). */
    public static final String[] TIER_RESEARCH = {null, "timber", "stone", "brick", "royal"};

    public static String tierName(int tier) {
        return TIER_NAMES[Math.max(0, Math.min(TIER_NAMES.length - 1, tier))];
    }

    private static BlockState pick(int tier, Block... byTier) {
        return byTier[Math.max(0, Math.min(byTier.length - 1, tier))].defaultBlockState();
    }

    private static BlockState stairs(int tier, Direction facing) {
        Block b = switch (tier) {
            case THATCH -> Blocks.MUD_BRICK_STAIRS;
            case TIMBER -> Blocks.SPRUCE_STAIRS;
            case STONE -> Blocks.DEEPSLATE_TILE_STAIRS;
            case BRICK -> Blocks.DARK_OAK_STAIRS;
            default -> Blocks.CUT_COPPER_STAIRS.waxed().unaffected();
        };
        return b.defaultBlockState().setValue(StairBlock.FACING, facing);
    }

    /**
     * The block for a layout character (before rotation), or null if the character isn't a block
     * (air, untouched, or the second half of a door/bed).
     */
    public static @Nullable BlockState state(char c, int tier, int primary, int secondary) {
        return switch (c) {
            // structure
            case 'C' -> pick(tier, Blocks.PACKED_MUD, Blocks.COBBLESTONE, Blocks.STONE_BRICKS, Blocks.STONE_BRICKS, Blocks.POLISHED_DEEPSLATE);
            case 'M' -> pick(tier, Blocks.MUD_BRICKS, Blocks.COBBLESTONE, Blocks.STONE_BRICKS, Blocks.STONE_BRICKS, Blocks.DEEPSLATE_TILES);
            case 'W' -> pick(tier, Blocks.PACKED_MUD, Blocks.OAK_PLANKS, Blocks.COBBLESTONE, Blocks.BRICKS, Blocks.DEEPSLATE_BRICKS);
            case 'L' -> {
                BlockState s = pick(tier, Blocks.OAK_LOG, Blocks.OAK_LOG, Blocks.STRIPPED_SPRUCE_LOG, Blocks.CHISELED_STONE_BRICKS,
                        Blocks.POLISHED_DEEPSLATE);
                yield s.hasProperty(RotatedPillarBlock.AXIS) ? s.setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y) : s;
            }
            case 'P' -> pick(tier, Blocks.OAK_PLANKS, Blocks.OAK_PLANKS, Blocks.SPRUCE_PLANKS, Blocks.DARK_OAK_PLANKS, Blocks.DARK_OAK_PLANKS);
            case 'G' -> pick(tier, Blocks.OAK_FENCE, Blocks.GLASS_PANE, Blocks.GLASS_PANE, Blocks.GLASS_PANE, Blocks.GLASS_PANE);
            case 'F' -> pick(tier, Blocks.OAK_FENCE, Blocks.OAK_FENCE, Blocks.SPRUCE_FENCE, Blocks.DARK_OAK_FENCE, Blocks.DARK_OAK_FENCE);
            case '>' -> tier == THATCH ? Blocks.HAY_BLOCK.defaultBlockState() : stairs(tier, Direction.EAST);
            case '<' -> tier == THATCH ? Blocks.HAY_BLOCK.defaultBlockState() : stairs(tier, Direction.WEST);
            case 'r' -> pick(tier, Blocks.HAY_BLOCK, Blocks.SPRUCE_PLANKS, Blocks.DEEPSLATE_TILES, Blocks.DARK_OAK_PLANKS,
                    Blocks.CUT_COPPER.waxed().unaffected());
            case 'l' -> tier == THATCH ? Blocks.TORCH.defaultBlockState() : Blocks.LANTERN.defaultBlockState();
            case 'n' -> tier == THATCH ? Blocks.AIR.defaultBlockState() : Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
            case 'k' -> tier >= BRICK ? Blocks.CARPET.pick(DyeColor.byId(primary)).defaultBlockState() : Blocks.AIR.defaultBlockState();
            // potted flowers stand on tables or sills (never on the floor), a different flower per tier
            case 'p' -> pick(tier, Blocks.POTTED_FERN, Blocks.POTTED_POPPY, Blocks.POTTED_BLUE_ORCHID, Blocks.POTTED_RED_TULIP,
                    Blocks.POTTED_ALLIUM);
            // interior furniture
            case 't' -> {
                BlockState slab = pick(tier, Blocks.OAK_SLAB, Blocks.SPRUCE_SLAB, Blocks.SPRUCE_SLAB, Blocks.DARK_OAK_SLAB, Blocks.DARK_OAK_SLAB);
                yield slab.setValue(net.minecraft.world.level.block.SlabBlock.TYPE, net.minecraft.world.level.block.state.properties.SlabType.TOP);
            }
            case '5', '6', '7', '8' -> {
                Direction d = c == '5' ? Direction.NORTH : c == '6' ? Direction.EAST : c == '7' ? Direction.SOUTH : Direction.WEST;
                Block wood = switch (tier) {
                    case THATCH -> Blocks.OAK_STAIRS;
                    case TIMBER, STONE -> Blocks.SPRUCE_STAIRS;
                    default -> Blocks.DARK_OAK_STAIRS;
                };
                yield wood.defaultBlockState().setValue(StairBlock.FACING, d);
            }
            case 'i' -> Blocks.CANDLE.defaultBlockState().setValue(net.minecraft.world.level.block.CandleBlock.CANDLES, 3)
                    .setValue(net.minecraft.world.level.block.CandleBlock.LIT, true);
            case 'a' -> Blocks.LANTERN.defaultBlockState();
            case 'm' -> Blocks.SMOKER.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH);
            case 'V' -> Blocks.WATER_CAULDRON.defaultBlockState().setValue(net.minecraft.world.level.block.LayeredCauldronBlock.LEVEL, 3);
            case 'j' -> pick(tier, Blocks.OAK_SHELF, Blocks.SPRUCE_SHELF, Blocks.SPRUCE_SHELF, Blocks.DARK_OAK_SHELF, Blocks.DARK_OAK_SHELF)
                    .setValue(net.minecraft.world.level.block.ShelfBlock.FACING, Direction.SOUTH);
            case 'I' -> Blocks.DECORATED_POT.defaultBlockState();
            case '0' -> tier >= TIMBER ? Blocks.CARPET.pick(DyeColor.byId(secondary)).defaultBlockState() : Blocks.AIR.defaultBlockState();
            case '9', ':', ';' -> Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING,
                    c == '9' ? Direction.NORTH : c == ':' ? Direction.EAST : Direction.WEST);
            case 's' -> Blocks.OAK_SLAB.defaultBlockState();
            case 'O' -> Blocks.SPRUCE_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
            case 'o' -> Blocks.SPRUCE_FENCE.defaultBlockState();
            // furniture
            case 'g' -> Blocks.OAK_FENCE_GATE.defaultBlockState().setValue(FenceGateBlock.FACING, Direction.SOUTH);
            case 'K' -> Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH);
            case 'R' -> Blocks.BARREL.defaultBlockState();
            case 'Q' -> Blocks.CRAFTING_TABLE.defaultBlockState();
            case 'A' -> Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH);
            case 'B' -> Blocks.BOOKSHELF.defaultBlockState();
            case 'E' -> Blocks.LECTERN.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH);
            case 'Y' -> Blocks.ANVIL.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING, Direction.EAST);
            case 'U' -> Blocks.BLAST_FURNACE.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH);
            case 'S' -> Blocks.SMITHING_TABLE.defaultBlockState();
            case 'c' -> Blocks.CAMPFIRE.defaultBlockState();
            case 'H' -> ModContent.RESEARCH_TABLE.get().defaultBlockState();
            case 'J' -> ModContent.TRAINING_DESK.get().defaultBlockState();
            case 'T' -> ModContent.MARKET.get().defaultBlockState();
            case 'X' -> Blocks.WOOL.pick(DyeColor.byId(primary)).defaultBlockState();
            case 'Z' -> Blocks.WOOL.pick(DyeColor.byId(secondary)).defaultBlockState();
            // decorations
            case '1', '2', '3', '4' -> {
                Direction d = c == '1' ? Direction.NORTH : c == '2' ? Direction.EAST : c == '3' ? Direction.SOUTH : Direction.WEST;
                Block shutter = switch (tier) {
                    case THATCH, TIMBER -> Blocks.SPRUCE_TRAPDOOR;
                    case STONE -> Blocks.OAK_TRAPDOOR;
                    default -> Blocks.DARK_OAK_TRAPDOOR;
                };
                yield shutter.defaultBlockState().setValue(net.minecraft.world.level.block.TrapDoorBlock.FACING, d)
                        .setValue(net.minecraft.world.level.block.TrapDoorBlock.OPEN, true);
            }
            case 'x', 'z' -> {
                BlockState log = pick(tier, Blocks.OAK_LOG, Blocks.SPRUCE_LOG, Blocks.SPRUCE_LOG, Blocks.DARK_OAK_LOG, Blocks.POLISHED_BASALT);
                yield log.setValue(RotatedPillarBlock.AXIS, c == 'x' ? Direction.Axis.X : Direction.Axis.Z);
            }
            case '[', ']', 'w' -> {
                Block wood = switch (tier) {
                    case THATCH -> Blocks.OAK_STAIRS;
                    case TIMBER, STONE -> Blocks.SPRUCE_STAIRS;
                    default -> Blocks.DARK_OAK_STAIRS;
                };
                Direction d = c == '[' ? Direction.EAST : c == ']' ? Direction.WEST : Direction.NORTH;
                yield wood.defaultBlockState().setValue(StairBlock.FACING, d)
                        .setValue(StairBlock.HALF, c == 'w' ? net.minecraft.world.level.block.state.properties.Half.BOTTOM
                                : net.minecraft.world.level.block.state.properties.Half.TOP);
            }
            case '%' -> Blocks.WOOL.pick(DyeColor.BROWN).defaultBlockState();
            case '#' -> Blocks.COARSE_DIRT.defaultBlockState();
            case '@' -> Blocks.SKELETON_SKULL.defaultBlockState();
            case '=' -> Blocks.HAY_BLOCK.defaultBlockState();
            case '!' -> Blocks.BONE_BLOCK.defaultBlockState();
            case '&' -> com.dominionmod.dominion.registry.ModContent.ORC_TOTEM.get().defaultBlockState();
            case 'v' -> Blocks.FLOWERING_AZALEA_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true);
            case 'd' -> Blocks.DIRT_PATH.defaultBlockState();
            case 'e' -> Blocks.CAMPFIRE.defaultBlockState();
            // farming
            case 'f' -> Blocks.FARMLAND.defaultBlockState();
            case '~' -> Blocks.WATER.defaultBlockState();
            case 'y' -> ((CropBlock) Blocks.WHEAT).getStateForAge(0);
            default -> null;
        };
    }

    /** Characters whose block is placed for free (no material needed). */
    public static boolean free(char c) {
        return c == 'f' || c == '~' || c == 'N' || (c >= '1' && c <= '4') || c == 'v' || c == 'd' || c == 'e' || c == '[' || c == ']' || c == 'w'
                // interior trimmings: builders bring these along with the furniture
                || c == 'p' || c == 'i' || c == 'a' || c == 'I' || c == 'V' || c == 'j';
    }
}
