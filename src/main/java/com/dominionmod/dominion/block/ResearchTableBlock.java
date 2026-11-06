package com.dominionmod.dominion.block;

import com.dominionmod.dominion.kingdom.Kingdom;
import com.dominionmod.dominion.kingdom.KingdomData;
import com.dominionmod.dominion.kingdom.Realm;
import com.dominionmod.dominion.network.Payloads;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/** Where the kingdom researches upgrades. Scholars working here speed research up. */
public class ResearchTableBlock extends Block {
    public ResearchTableBlock(Properties props) {
        super(props);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack stack) {
        super.setPlacedBy(level, pos, state, by, stack);
        if (level instanceof ServerLevel sl) register(sl, pos);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        super.onPlace(state, level, pos, old, moved);
        if (level instanceof ServerLevel sl && !old.is(this)) register(sl, pos);
    }

    /** Remember the table so Scholars can find it (whoever owns the land). */
    public static void register(ServerLevel level, BlockPos pos) {
        KingdomData d = Realm.data(level);
        Kingdom k = d.ownerAt(level, pos);
        if (k == null) return;
        var pr = d.progress(k);
        if (!pr.tables.contains(pos.asLong())) {
            pr.tables.add(pos.asLong());
            d.markChanged();
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.SUCCESS;
        register((ServerLevel) level, pos);
        Payloads.sendRealm(sp, true, "", Payloads.TAB_RESEARCH);
        return InteractionResult.SUCCESS_SERVER;
    }
}
