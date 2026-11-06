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
            case STONE -> Blocks.STONE_BRICK_STAIRS;
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
                BlockState s = pick(tier, Blocks.STRIPPED_OAK_LOG, Blocks.OAK_LOG, Blocks.STRIPPED_SPRUCE_LOG, Blocks.CHISELED_STONE_BRICKS,
                        Blocks.POLISHED_DEEPSLATE);
                yield s.hasProperty(RotatedPillarBlock.AXIS) ? s.setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y) : s;
            }
            case 'P' -> pick(tier, Blocks.OAK_PLANKS, Blocks.OAK_PLANKS, Blocks.SPRUCE_PLANKS, Blocks.DARK_OAK_PLANKS, Blocks.DARK_OAK_PLANKS);
            case 'G' -> pick(tier, Blocks.OAK_FENCE, Blocks.GLASS_PANE, Blocks.GLASS_PANE, Blocks.GLASS_PANE, Blocks.GLASS_PANE);
            case 'F' -> pick(tier, Blocks.OAK_FENCE, Blocks.OAK_FENCE, Blocks.SPRUCE_FENCE, Blocks.DARK_OAK_FENCE, Blocks.DARK_OAK_FENCE);
            case '>' -> tier == THATCH ? Blocks.HAY_BLOCK.defaultBlockState() : stairs(tier, Direction.EAST);
            case '<' -> tier == THATCH ? Blocks.HAY_BLOCK.defaultBlockState() : stairs(tier, Direction.WEST);
            case 'r' -> pick(tier, Blocks.HAY_BLOCK, Blocks.SPRUCE_PLANKS, Blocks.STONE_BRICKS, Blocks.DARK_OAK_PLANKS,
                    Blocks.CUT_COPPER.waxed().unaffected());
            case 'l' -> tier == THATCH ? Blocks.TORCH.defaultBlockState() : Blocks.LANTERN.defaultBlockState();
            case 'n' -> tier == THATCH ? Blocks.AIR.defaultBlockState() : Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
            case 'k' -> tier >= BRICK ? Blocks.CARPET.pick(DyeColor.byId(primary)).defaultBlockState() : Blocks.AIR.defaultBlockState();
            case 'p' -> tier >= TIMBER ? Blocks.POTTED_POPPY.defaultBlockState() : Blocks.AIR.defaultBlockState();
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
            // farming
            case 'f' -> Blocks.FARMLAND.defaultBlockState();
            case '~' -> Blocks.WATER.defaultBlockState();
            case 'y' -> ((CropBlock) Blocks.WHEAT).getStateForAge(0);
            default -> null;
        };
    }

    /** Characters whose block is placed for free (no material needed). */
    public static boolean free(char c) {
        return c == 'f' || c == '~' || c == 'N';
    }
}
