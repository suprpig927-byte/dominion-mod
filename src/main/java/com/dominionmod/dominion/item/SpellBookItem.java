package com.dominionmod.dominion.item;

import com.dominionmod.dominion.magic.Spell;
import com.dominionmod.dominion.magic.Spells;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/** A wizard's Spell Book. Right-click to cast the chosen spell; sneak + right-click to turn to the next one. Spells cost experience. */
public class SpellBookItem extends Item {
    public SpellBookItem(Properties props) {
        super(props);
    }

    public static Spell spell(ItemStack book) {
        CustomData data = book.get(DataComponents.CUSTOM_DATA);
        return Spell.byId(data == null ? 0 : data.copyTag().getIntOr("Spell", 0));
    }

    private static void setSpell(ItemStack book, Spell s) {
        CustomData.update(DataComponents.CUSTOM_DATA, book, t -> t.putInt("Spell", s.ordinal()));
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack book = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.SUCCESS;
        if (sp.isShiftKeyDown()) {
            Spell next = Spell.byId(spell(book).ordinal() + 1);
            setSpell(book, next);
            sp.sendOverlayMessage(Component.literal("Spell: ").withStyle(ChatFormatting.LIGHT_PURPLE)
                    .append(Component.literal(next.title).withStyle(s -> s.withColor(next.color)))
                    .append(Component.literal("  (" + next.cost + " XP) " + next.description).withStyle(ChatFormatting.GRAY)));
            level.playSound(null, sp.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0f, 1.0f);
            return InteractionResult.SUCCESS_SERVER;
        }
        Spells.playerCast(sp, spell(book));
        sp.swingForAttack(hand);
        return InteractionResult.SUCCESS_SERVER;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        Spell s = spell(stack);
        out.accept(Component.literal("Spell: ").withStyle(ChatFormatting.GRAY).append(Component.literal(s.title).withStyle(st -> st.withColor(s.color))));
        out.accept(Component.literal(s.description + " (" + s.cost + " XP)").withStyle(ChatFormatting.DARK_GRAY));
        out.accept(Component.literal("Sneak + right-click to turn the page").withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }
}
