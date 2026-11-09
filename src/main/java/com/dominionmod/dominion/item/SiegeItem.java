package com.dominionmod.dominion.item;

import com.dominionmod.dominion.entity.siege.SiegeEngine;
import com.dominionmod.dominion.kingdom.Kingdom;
import com.dominionmod.dominion.kingdom.KingdomData;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

/** A siege engine, knocked down for carrying: right-click the ground to set it up. */
public class SiegeItem extends Item {
    private final Supplier<EntityType<SiegeEngine>> type;
    private final String research;

    public SiegeItem(Properties props, Supplier<EntityType<SiegeEngine>> type, String research) {
        super(props);
        this.type = type;
        this.research = research;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (!(ctx.getLevel() instanceof ServerLevel level) || ctx.getPlayer() == null) return InteractionResult.SUCCESS;
        KingdomData d = KingdomData.get(level.getServer());
        Kingdom k = d.byPlayer(ctx.getPlayer().getUUID());
        if (k == null) {
            ctx.getPlayer().sendOverlayMessage(Component.literal("Found a kingdom first.").withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }
        if (!d.progress(k).has(research)) {
            var tech = com.dominionmod.dominion.kingdom.Research.get(research);
            ctx.getPlayer().sendOverlayMessage(Component.literal("Research " + (tech == null ? research : tech.name()) + " to field this.")
                    .withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }
        BlockPos at = ctx.getClickedPos().relative(ctx.getClickedFace());
        SiegeEngine e = type.get().create(level, EntitySpawnReason.SPAWN_ITEM_USE);
        if (e == null) return InteractionResult.FAIL;
        e.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, ctx.getPlayer().getYRot(), 0);
        e.setKingdom(k.id);
        level.addFreshEntity(e);
        if (!ctx.getPlayer().isCreative()) ctx.getItemInHand().shrink(1);
        ctx.getPlayer().sendOverlayMessage(Component.literal("The " + e.kind().title + " is ready. Right-click it to learn how to work it.")
                .withStyle(ChatFormatting.GOLD));
        return InteractionResult.SUCCESS_SERVER;
    }
}
