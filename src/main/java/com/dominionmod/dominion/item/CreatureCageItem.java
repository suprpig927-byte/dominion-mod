package com.dominionmod.dominion.item;

import com.dominionmod.dominion.kingdom.Realm;
import com.dominionmod.dominion.registry.ModContent;
import java.util.List;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.TagValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * A Creature Cage: right-click your pet (or a small animal) to coax it in, carry it anywhere, then
 * right-click the ground to let it out again, collar, name, owner and all. Fits cats, dogs, otters,
 * parrots, foxes, rabbits, chickens, axolotls, frogs, armadillos, ocelots, turtles and allays, and
 * any other pet you've tamed (not horses: too big). Other people's pets won't go in.
 */
public class CreatureCageItem extends Item {
    private static final Set<EntityType<?>> SMALL = Set.of(EntityTypes.CAT, EntityTypes.WOLF, EntityTypes.PARROT, EntityTypes.FOX,
            EntityTypes.RABBIT, EntityTypes.CHICKEN, EntityTypes.AXOLOTL, EntityTypes.FROG, EntityTypes.ARMADILLO, EntityTypes.OCELOT,
            EntityTypes.TURTLE, EntityTypes.ALLAY);

    public CreatureCageItem(Properties p) {
        super(p);
    }

    public static boolean isFull(ItemStack s) {
        return s.has(DataComponents.CUSTOM_DATA) && s.get(DataComponents.CUSTOM_DATA).copyTag().contains("Mob");
    }

    /** Why this creature can't go in the cage (null = it can). */
    public static @Nullable String refusal(LivingEntity target, @Nullable Player player) {
        if (target instanceof Player || !(target instanceof Mob mob) || !target.isAlive()) return "That won't fit in a cage.";
        if (mob.isPassenger() || mob.isVehicle()) return "Get them down first.";
        if (target instanceof TamableAnimal t && t.isTame()) {
            if (player != null && !t.isOwnedBy(player) && !player.isCreative()) return "That's someone else's pet.";
            return null;
        }
        if (target.getType() == ModContent.OTTER.get() || SMALL.contains(target.getType())) return null;
        if (target instanceof net.minecraft.world.entity.animal.equine.AbstractHorse) return "A horse is far too big for a cage.";
        return "Only pets and small animals fit in a Creature Cage.";
    }

    /** Puts the creature in a cage and returns the full cage (the creature leaves the world). */
    public static ItemStack capture(LivingEntity target) {
        ServerLevel level = (ServerLevel) target.level();
        TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
        target.saveWithoutId(out);
        CompoundTag data = out.buildResult();
        data.remove("UUID");
        data.remove("leash");
        CompoundTag tag = new CompoundTag();
        tag.putString("Mob", BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()).toString());
        tag.put("Data", data);
        ItemStack cage = new ItemStack(ModContent.CREATURE_CAGE.get());
        cage.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        String kind = target.getType().getDescription().getString();
        String label = target.hasCustomName() ? target.getCustomName().getString() + " (" + kind + ")" : kind;
        cage.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("Holds: " + label).withStyle(ChatFormatting.GOLD),
                Component.literal("Right-click the ground to let it out").withStyle(ChatFormatting.GRAY))));
        if (target instanceof Mob m && m.isLeashed()) m.dropLeash();
        level.sendParticles(ParticleTypes.CLOUD, target.getX(), target.getY() + 0.4, target.getZ(), 10, 0.3, 0.3, 0.3, 0.02);
        level.playSound(null, target.blockPosition(), SoundEvents.CHAIN_PLACE, SoundSource.NEUTRAL, 1f, 1.2f);
        target.discard();
        return cage;
    }

    /** Lets the creature out at {@code at}. Returns it, or null if the cage was empty or it couldn't be made. */
    public static @Nullable Entity release(ServerLevel level, ItemStack cage, BlockPos at, float yaw) {
        if (!isFull(cage)) return null;
        CompoundTag tag = cage.get(DataComponents.CUSTOM_DATA).copyTag();
        Identifier id = Identifier.tryParse(tag.getStringOr("Mob", ""));
        if (id == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(id)) return null;
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(id);
        CompoundTag data = tag.getCompoundOrEmpty("Data");
        Entity e = EntityType.loadEntityRecursive(type, data, level, EntitySpawnReason.BUCKET, x -> {
            x.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, yaw, 0);
            return x;
        });
        if (e == null) return null;
        level.addFreshEntity(e);
        cage.remove(DataComponents.CUSTOM_DATA);
        cage.remove(DataComponents.LORE);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, e.getX(), e.getY() + 0.5, e.getZ(), 8, 0.3, 0.3, 0.3, 0);
        level.playSound(null, at, SoundEvents.CHAIN_BREAK, SoundSource.NEUTRAL, 1f, 1.3f);
        return e;
    }

    /** A player used a cage on a creature (called from the interaction event, before the creature's own handling). */
    public static InteractionResult tryCapture(Player player, ItemStack held, LivingEntity target) {
        if (player.level().isClientSide()) return InteractionResult.SUCCESS;
        if (isFull(held)) {
            player.sendOverlayMessage(Component.literal("This cage is full. Let its creature out first.").withStyle(ChatFormatting.GRAY));
            return InteractionResult.FAIL;
        }
        String no = refusal(target, player);
        if (no != null) {
            player.sendOverlayMessage(Component.literal(no).withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }
        ItemStack full = capture(target);
        held.shrink(1);
        if (!player.getInventory().add(full)) Realm.give(player, full);
        return InteractionResult.SUCCESS_SERVER;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        ItemStack stack = ctx.getItemInHand();
        if (!isFull(stack)) return InteractionResult.PASS;
        if (!(ctx.getLevel() instanceof ServerLevel level)) return InteractionResult.SUCCESS;
        BlockPos at = ctx.getClickedPos().relative(ctx.getClickedFace());
        Entity e = release(level, stack, at, ctx.getPlayer() == null ? 0 : ctx.getPlayer().getYRot() + 180);
        if (e == null && ctx.getPlayer() != null)
            ctx.getPlayer().sendOverlayMessage(Component.literal("The creature won't come out here.").withStyle(ChatFormatting.RED));
        return InteractionResult.SUCCESS_SERVER;
    }
}
