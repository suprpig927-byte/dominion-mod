package com.dominionmod.dominion.block;

import com.dominionmod.dominion.kingdom.Settlements;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** The War Totem at the heart of an orc village. Once the orcs are gone, right-click it to claim the village's land. */
public class OrcTotemBlock extends Block {
    public OrcTotemBlock(Properties props) {
        super(props);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer p) Settlements.tryClaimOrcVillage(p, pos);
        return InteractionResult.SUCCESS_SERVER;
    }
}
