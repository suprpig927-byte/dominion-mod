package com.dominionmod.dominion.block;

import com.dominionmod.dominion.kingdom.Kingdom;
import com.dominionmod.dominion.kingdom.KingdomData;
import com.dominionmod.dominion.kingdom.Realm;
import com.dominionmod.dominion.network.Payloads;
import com.dominionmod.dominion.registry.ModContent;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.Nullable;

/** The heart of a kingdom. Place it to found one; right-click it to open your Realm. */
public class TownHallBlock extends Block {
    public TownHallBlock(Properties props) {
        super(props);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack stack) {
        super.setPlacedBy(level, pos, state, by, stack);
        if (!(level instanceof ServerLevel sl) || !(by instanceof ServerPlayer p)) return;
        KingdomData d = Realm.data(sl);
        Kingdom mine = d.byPlayer(p.getUUID());
        Kingdom owner = d.ownerAt(sl, pos);
        if (mine == null) {
            if (owner != null) {
                refuse(sl, pos, p, "This land belongs to " + owner.name + ".");
                return;
            }
            PacketDistributor.sendToPlayer(p, new Payloads.OpenFounding(pos));
            return;
        }
        // Already in a kingdom: this can only replace a lost Town Hall, inside your own land.
        boolean hallStands = mine.hall.isPresent() && mine.hallDim.equals(KingdomData.dim(sl))
                && sl.isLoaded(mine.hall.get()) && sl.getBlockState(mine.hall.get()).is(this) && !mine.hall.get().equals(pos);
        if (hallStands || mine.hall.isPresent() && !mine.hallDim.equals(KingdomData.dim(sl))) {
            refuse(sl, pos, p, "Your kingdom already has a Town Hall.");
        } else if (owner != mine) {
            refuse(sl, pos, p, "A new Town Hall has to stand on your own land.");
        } else {
            mine.hall = Optional.of(pos.immutable());
            mine.hallDim = KingdomData.dim(sl);
            d.markChanged();
            Realm.raiseFlag(sl, pos, mine, p);
            p.sendSystemMessage(Component.literal("Your Town Hall stands again.").withStyle(ChatFormatting.GREEN));
        }
    }

    private void refuse(ServerLevel level, BlockPos pos, ServerPlayer p, String why) {
        level.removeBlock(pos, false);
        if (!p.isCreative()) Realm.give(p, new ItemStack(ModContent.TOWN_HALL_ITEM.get()));
        p.sendSystemMessage(Realm.err(why));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level instanceof ServerLevel sl) || !(player instanceof ServerPlayer p)) return InteractionResult.SUCCESS;
        KingdomData d = Realm.data(sl);
        Kingdom owner = d.ownerAt(sl, pos);
        if (owner == null && d.byPlayer(p.getUUID()) == null) {
            PacketDistributor.sendToPlayer(p, new Payloads.OpenFounding(pos));
        } else if (owner != null && owner.wild && owner.hall.isPresent() && owner.hall.get().equals(pos)) {
            com.dominionmod.dominion.kingdom.Settlements.tryConquer(p, owner, pos);
        } else if (owner != null && owner.isMember(p.getUUID())) {
            Payloads.sendRealm(p, true);
        } else if (owner != null) {
            p.sendSystemMessage(Component.literal("The Town Hall of ").withStyle(ChatFormatting.GRAY).append(Realm.title(owner)));
        }
        return InteractionResult.SUCCESS_SERVER;
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (level instanceof ServerLevel sl) {
            KingdomData d = Realm.data(sl);
            for (Kingdom k : d.all()) {
                if (k.hall.isPresent() && k.hall.get().equals(pos) && k.hallDim.equals(KingdomData.dim(sl))) {
                    k.hall = Optional.empty();
                    d.markChanged();
                    if (player instanceof ServerPlayer p) p.sendSystemMessage(Component.literal(
                            "The Town Hall of " + k.name + " has fallen. Place a new one on your land to restore it.").withStyle(ChatFormatting.RED));
                }
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    public static boolean isHallChunk(Kingdom k, Level level, ChunkPos chunk) {
        return k.hall.isPresent() && k.hallDim.equals(KingdomData.dim(level)) && ChunkPos.containing(k.hall.get()).equals(chunk);
    }
}
