package com.dominionmod.dominion.block;

import com.dominionmod.dominion.kingdom.IllagerEmpire;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** The Illager Standard at the heart of an Illager camp. Once the camp is cleared, right-click it to claim the land and free the captives. */
public class IllagerStandardBlock extends Block {
    public IllagerStandardBlock(Properties props) {
        super(props);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer p) IllagerEmpire.tryClaim(p, pos);
        return InteractionResult.SUCCESS_SERVER;
    }
}
