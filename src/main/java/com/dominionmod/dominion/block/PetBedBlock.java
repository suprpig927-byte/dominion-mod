package com.dominionmod.dominion.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A wicker basket with a quilted cushion. At night your tame cats, dogs, otters and parrots nearby
 * curl up in one (one pet to a bed) and heal while they sleep. Use any dye on it to recolour it.
 */
public class PetBedBlock extends Block {
    public static final EnumProperty<DyeColor> COLOR = EnumProperty.create("color", DyeColor.class);
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 5, 15);

    public PetBedBlock(Properties props) {
        super(props);
        registerDefaultState(this.stateDefinition.any().setValue(COLOR, DyeColor.RED).setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(COLOR, FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                          BlockHitResult hit) {
        DyeColor dye = stack.get(DataComponents.DYE);
        if (dye == null) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (state.getValue(COLOR) == dye) return InteractionResult.PASS;
        if (!level.isClientSide()) {
            level.setBlock(pos, state.setValue(COLOR, dye), Block.UPDATE_ALL);
            stack.consume(1, player);
            level.playSound(null, pos, SoundEvents.DYE_USE, SoundSource.BLOCKS, 1f, 1f);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide()) {
            String who = com.dominionmod.dominion.entity.PetBeds.sleeperName(level, pos);
            player.sendOverlayMessage(Component.literal(who == null ? "An empty pet bed. Tame pets nearby sleep here at night." : who + " sleeps here."));
        }
        return InteractionResult.SUCCESS;
    }
}
