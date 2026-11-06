package com.dominionmod.dominion.block;

import com.dominionmod.dominion.command.Orders;
import com.dominionmod.dominion.entity.Citizen;
import com.dominionmod.dominion.kingdom.Kingdom;
import com.dominionmod.dominion.kingdom.Realm;
import com.dominionmod.dominion.network.Payloads;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.network.PacketDistributor;

/** The University's Training Desk: bring citizens here (selected with the baton) to train them in a class. */
public class TrainingDeskBlock extends Block {
    public static final int RANGE = 12;

    public TrainingDeskBlock(Properties props) {
        super(props);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.SUCCESS;
        Kingdom k = Realm.of(sp);
        if (k == null) return InteractionResult.PASS;
        if (!Realm.data((ServerLevel) level).progress(k).has("university")) {
            sp.sendSystemMessage(Realm.err("Research Higher Learning at a Research Table to train classes."));
            return InteractionResult.SUCCESS_SERVER;
        }
        List<Citizen> near = Orders.selectedCitizens(sp).stream()
                .filter(c -> c.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos)) < RANGE * RANGE && !c.isChild() && !c.job().isClass()).toList();
        if (near.isEmpty()) {
            sp.sendSystemMessage(Component.literal("Select the citizens to train with the Command Baton, bring them within " + RANGE
                    + " blocks of this desk, then click it again.").withStyle(ChatFormatting.YELLOW));
            return InteractionResult.SUCCESS_SERVER;
        }
        PacketDistributor.sendToPlayer(sp, new Payloads.OpenTraining(pos, near.stream().map(Citizen::baseName).toList()));
        return InteractionResult.SUCCESS_SERVER;
    }
}
