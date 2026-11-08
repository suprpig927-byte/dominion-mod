package com.dominionmod.dominion.build;

import com.dominionmod.dominion.kingdom.Kingdom;
import com.dominionmod.dominion.kingdom.KingdomData;
import com.dominionmod.dominion.kingdom.Progress;
import com.dominionmod.dominion.kingdom.Realm;
import com.dominionmod.dominion.kingdom.Research;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/** Finished buildings and the signs out front that upgrade them. */
public final class Buildings {
    private Buildings() {}

    /** A builder just finished a site: record the building (or its new tier) and put up its sign. */
    public static void finished(ServerLevel level, Kingdom k, Site site) {
        Structures.Structure s = Structures.get(site.structure);
        if (s == null || !s.upgradable()) return;
        Progress pr = Realm.data(level).progress(k);
        Building b = pr.building(site.building);
        if (b == null) {
            BlockPos sign = signSpot(level, s, site.origin, site.rotation);
            b = new Building(UUID.randomUUID(), s.id(), site.dim, site.origin, site.rotation, site.tier, sign);
            pr.buildings.add(b);
        } else {
            b.tier = Math.max(b.tier, site.tier);
            b.structure = site.structure;
        }
        placeSign(level, b);
        Realm.data(level).markChanged();
    }

    /** Just outside the entrance, one block to its right. */
    private static BlockPos signSpot(ServerLevel level, Structures.Structure s, BlockPos origin, int rot) {
        int ex = s.width() / 2, el = s.sink(), ez = s.depth() - 1;
        search:
        for (int layer = 0; layer < s.height(); layer++) {
            String[] rows = s.layers().get(layer);
            for (int z = rows.length - 1; z >= 0; z--) {
                int x = rows[z].indexOf('D');
                if (x < 0) x = rows[z].indexOf('g');
                if (x >= 0) {
                    ex = x;
                    ez = z;
                    el = layer;
                    break search;
                }
            }
        }
        BlockPos[] tries = {
                Plans.cell(s, origin, rot, ex + 1, el, ez + 1), Plans.cell(s, origin, rot, ex - 1, el, ez + 1),
                Plans.cell(s, origin, rot, ex + 2, el, ez + 1), Plans.cell(s, origin, rot, ex, el, ez + 2)};
        for (BlockPos p : tries) if (level.getBlockState(p).canBeReplaced() && !level.getBlockState(p.below()).canBeReplaced()) return p;
        return tries[0];
    }

    public static void placeSign(ServerLevel level, Building b) {
        Structures.Structure s = Structures.get(b.structure);
        if (s == null || !level.isLoaded(b.sign)) return;
        BlockState here = level.getBlockState(b.sign);
        if (!(here.getBlock() instanceof StandingSignBlock)) {
            if (!here.canBeReplaced()) return;
            BlockState sign = Blocks.OAK_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION, 0).rotate(Plans.rotation(b.rotation));
            level.setBlock(b.sign, sign, Block.UPDATE_ALL);
        }
        if (level.getBlockEntity(b.sign) instanceof SignBlockEntity be) {
            boolean top = b.tier >= Palette.ROYAL;
            SignText text = SignText.EMPTY.asMutable()
                    .setLine(0, Component.literal(s.name()).withStyle(ChatFormatting.BOLD))
                    .setLine(1, Component.literal(Palette.tierName(b.tier)))
                    .setLine(2, Component.literal(top ? "Fully upgraded" : "Sneak + right-click"))
                    .setLine(3, Component.literal(top ? "" : "to upgrade"))
                    .asImmutable();
            be.setText(text, SignTextSlot.FRONT);
            be.setWaxed(true);
            be.setChanged();
            BlockState st = level.getBlockState(b.sign);
            level.sendBlockUpdated(b.sign, st, st, Block.UPDATE_ALL);
        }
    }

    /** A player clicked a building's sign. Sneaking starts the upgrade. Returns the message to show. */
    public static Component clicked(ServerPlayer p, Kingdom k, Building b) {
        ServerLevel level = (ServerLevel) p.level();
        KingdomData d = Realm.data(level);
        Progress pr = d.progress(k);
        for (Site s : d.sites(k)) {
            if (b.id.equals(s.building)) {
                int total = Plans.steps(level, s, k).size();
                return Component.literal(b.name() + ": being rebuilt in " + Palette.tierName(s.tier) + ", "
                        + (100 * Math.min(s.cursor, total) / Math.max(1, total)) + "% done.").withStyle(ChatFormatting.YELLOW);
            }
        }
        int target = pr.maxTier();
        Structures.Structure st = Structures.get(b.structure);
        // A Builder's Hammer adds a second storey.
        if (p.getMainHandItem().is(com.dominionmod.dominion.registry.ModContent.BUILDERS_HAMMER.get())) {
            Structures.Structure up = Structures.storeyOf(b.structure);
            if (up == null) {
                return Component.literal(b.structure.endsWith("_2") ? b.name() + " already has an upper storey."
                        : "A " + b.name() + " can't take a second storey.").withStyle(ChatFormatting.GRAY);
            }
            if (!pr.researched.contains("storeys")) {
                return Component.literal("Research Upper Storeys at a Research Table to add a second storey.").withStyle(ChatFormatting.GRAY);
            }
            Site site = new Site(UUID.randomUUID(), k.id, up.id(), b.dim, b.origin, b.origin, b.rotation, List.of(), 0);
            site.tier = b.tier;
            site.building = b.id;
            d.addSite(site);
            return Component.literal("Your builders will add an upper storey to the " + b.name() + ", with two more beds.")
                    .withStyle(ChatFormatting.GREEN);
        }
        if (target <= b.tier) {
            if (b.tier >= Palette.ROYAL) return Component.literal(b.name() + " (Royal): it can't get any grander.").withStyle(ChatFormatting.GOLD);
            Research.Tech next = Research.get(Palette.TIER_RESEARCH[b.tier + 1]);
            return Component.literal(b.name() + " (" + Palette.tierName(b.tier) + "). Research "
                    + (next == null ? "the next tier" : next.name()) + " to upgrade it.").withStyle(ChatFormatting.GRAY);
        }
        if (!p.isShiftKeyDown()) {
            return Component.literal(b.name() + " (" + Palette.tierName(b.tier) + "). Sneak + right-click to rebuild it in "
                    + Palette.tierName(target) + ".").withStyle(ChatFormatting.YELLOW);
        }
        Site site = new Site(UUID.randomUUID(), k.id, b.structure, b.dim, b.origin, b.origin, b.rotation, List.of(), 0);
        site.tier = target;
        site.building = b.id;
        d.addSite(site);
        return Component.literal("Your builders will rebuild the " + b.name() + " in " + Palette.tierName(target)
                + ". Furniture and chests stay where they are; the old materials go to the storehouse.").withStyle(ChatFormatting.GREEN);
    }

    /** Engineers' repairs: a repair site rebuilds whatever is missing from a building at its own tier. */
    public static @Nullable Site repairSite(ServerLevel level, Kingdom k, Building b) {
        Site site = new Site(UUID.randomUUID(), k.id, b.structure, b.dim, b.origin, b.origin, b.rotation, List.of(), 0);
        site.tier = b.tier;
        site.building = b.id;
        int missing = 0;
        for (Plans.Step s : Plans.steps(level, site, k)) {
            if (s.isClear() || s.cost() == null) continue;
            if (level.isLoaded(s.pos()) && level.getBlockState(s.pos()).getBlock() != s.state().getBlock()) missing++;
        }
        Plans.forget(site.id);
        return missing == 0 ? null : site;
    }
}
