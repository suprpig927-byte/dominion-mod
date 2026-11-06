package com.dominionmod.dominion.item;

import com.dominionmod.dominion.kingdom.Kingdom;
import com.dominionmod.dominion.kingdom.KingdomData;
import com.dominionmod.dominion.kingdom.Realm;
import com.dominionmod.dominion.kingdom.StorageCategory;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.ChestType;

/**
 * Click a storehouse chest or barrel to cycle what it's for: Food, Farming, Wood, Mining,
 * Building, Armory, Coins &amp; Research, or Anything. Workers only put matching items in a labeled chest.
 */
public class StorageLabelItem extends Item {
    public StorageLabelItem(Item.Properties props) {
        super(props);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (!(ctx.getPlayer() instanceof ServerPlayer p) || !(ctx.getLevel() instanceof ServerLevel level)) return InteractionResult.SUCCESS;
        BlockPos pos = ctx.getClickedPos();
        var be = level.getBlockEntity(pos);
        if (!(be instanceof ChestBlockEntity) && !(be instanceof BarrelBlockEntity)) {
            p.sendOverlayMessage(Realm.err("Use the label on a chest or barrel."));
            return InteractionResult.FAIL;
        }
        KingdomData d = Realm.data(level);
        Kingdom k = d.ownerAt(level, pos);
        if (k == null || !k.isMember(p.getUUID())) {
            p.sendOverlayMessage(Realm.err("You can only label chests on your own land."));
            return InteractionResult.FAIL;
        }
        var labels = d.progress(k).labels;
        StorageCategory next = StorageCategory.byName(labels.get(pos.asLong())).next();
        BlockPos other = otherHalf(level, pos);
        for (BlockPos q : other == null ? new BlockPos[]{pos} : new BlockPos[]{pos, other}) {
            if (next == StorageCategory.ANY) labels.remove(q.asLong());
            else labels.put(q.asLong(), next.name());
        }
        d.markChanged();
        p.sendOverlayMessage(Component.literal("Storage: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(next.title).withStyle(ChatFormatting.GOLD))
                .append(Component.literal(next == StorageCategory.ANY ? "  (takes anything)" : "  (click again to change)").withStyle(ChatFormatting.DARK_GRAY)));
        return InteractionResult.SUCCESS_SERVER;
    }

    /** The other half of a double chest, if any. */
    public static BlockPos otherHalf(ServerLevel level, BlockPos pos) {
        var st = level.getBlockState(pos);
        if (!(st.getBlock() instanceof ChestBlock) || st.getValue(ChestBlock.TYPE) == ChestType.SINGLE) return null;
        return pos.relative(ChestBlock.getConnectedDirection(st));
    }
}
