package com.dominionmod.dominion.command;

import com.dominionmod.dominion.entity.Citizen;
import com.dominionmod.dominion.kingdom.Kingdom;
import com.dominionmod.dominion.kingdom.Realm;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;

/** The Command Baton's memory: who each player has selected, and which order right-clicking the air gives. */
public final class Orders {
    private Orders() {}

    public enum Mode {
        RALLY("Rally", "fall in behind you"),
        HOLD("Hold", "hold where they stand"),
        DUTY("Return to duty", "go back to their posts"),
        CLEAR("Clear selection", "");

        public final String label, desc;

        Mode(String label, String desc) {
            this.label = label;
            this.desc = desc;
        }
    }

    private static final class State {
        final Set<UUID> selected = new LinkedHashSet<>();
        Mode mode = Mode.RALLY;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    private static State state(ServerPlayer p) {
        return STATES.computeIfAbsent(p.getUUID(), u -> new State());
    }

    public static Set<UUID> selected(ServerPlayer p) {
        return state(p).selected;
    }

    /** Loaded, living, selected citizens that still belong to the player's kingdom. */
    public static List<Citizen> selectedCitizens(ServerPlayer p) {
        Kingdom k = Realm.of(p);
        List<Citizen> out = new ArrayList<>();
        var it = state(p).selected.iterator();
        while (it.hasNext()) {
            UUID id = it.next();
            if (p.level().getEntity(id) instanceof Citizen c && c.isAlive() && k != null && c.sameKingdom(k.id)) out.add(c);
            else if (p.level().getEntity(id) == null && Realm.findCitizen((ServerLevel) p.level(), id) == null) it.remove();
        }
        out.sort(Comparator.comparing(c -> c.getUUID()));
        return out;
    }

    public static void toggle(ServerPlayer p, Citizen c) {
        State s = state(p);
        if (!s.selected.remove(c.getUUID())) {
            s.selected.add(c.getUUID());
            p.sendOverlayMessage(Component.literal("Selected " + c.baseName() + " the " + c.job().title + " (" + s.selected.size() + " selected)")
                    .withStyle(ChatFormatting.GREEN));
            ping(p, c);
        } else {
            p.sendOverlayMessage(Component.literal("Deselected " + c.baseName() + " (" + s.selected.size() + " selected)").withStyle(ChatFormatting.GRAY));
        }
    }

    public static void selectAllSoldiersNear(ServerPlayer p) {
        Kingdom k = Realm.of(p);
        if (k == null) return;
        State s = state(p);
        int n = 0;
        for (Citizen c : p.level().getEntitiesOfClass(Citizen.class, p.getBoundingBox().inflate(24),
                c -> c.sameKingdom(k.id) && c.job().soldier)) {
            s.selected.add(c.getUUID());
            ping(p, c);
            n++;
        }
        p.sendOverlayMessage(Component.literal("Selected " + n + " soldiers nearby (" + s.selected.size() + " selected)").withStyle(ChatFormatting.GREEN));
    }

    public static void cycleMode(ServerPlayer p) {
        State s = state(p);
        s.mode = Mode.values()[(s.mode.ordinal() + 1) % Mode.values().length];
        p.sendOverlayMessage(Component.literal("Baton order: ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(s.mode.label).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(s.mode == Mode.CLEAR ? "" : "  (right-click the air to order your selection to " + s.mode.desc + ")")
                        .withStyle(ChatFormatting.GRAY)));
    }

    /** Right-click on nothing: give the current order. */
    public static void giveOrder(ServerPlayer p) {
        State s = state(p);
        if (s.mode == Mode.CLEAR) {
            s.selected.clear();
            p.sendOverlayMessage(Component.literal("Selection cleared.").withStyle(ChatFormatting.GRAY));
            return;
        }
        List<Citizen> sel = selectedCitizens(p);
        if (sel.isEmpty()) {
            p.sendOverlayMessage(Component.literal("Nobody selected. Right-click a citizen, or sneak + right-click one to select every soldier nearby.")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        switch (s.mode) {
            case RALLY -> {
                List<Citizen> soldiers = sel.stream().filter(c -> c.job().soldier).toList();
                for (int i = 0; i < soldiers.size(); i++) soldiers.get(i).rally(p.getUUID(), i, soldiers.size());
                horn(p);
                p.sendOverlayMessage(Component.literal(soldiers.size() + " soldiers rally to you!").withStyle(ChatFormatting.GOLD));
            }
            case HOLD -> {
                for (Citizen c : sel) c.hold(c.blockPosition());
                p.sendOverlayMessage(Component.literal(sel.size() + " hold their ground.").withStyle(ChatFormatting.YELLOW));
            }
            case DUTY -> {
                for (Citizen c : sel) c.returnToDuty();
                p.sendOverlayMessage(Component.literal(sel.size() + " return to their posts.").withStyle(ChatFormatting.YELLOW));
            }
            default -> {}
        }
    }

    /** Right-click a block: soldiers march there and hold it; workers make it their new work spot. */
    public static void orderTo(ServerPlayer p, BlockPos pos) {
        List<Citizen> sel = selectedCitizens(p);
        if (sel.isEmpty()) {
            p.sendOverlayMessage(Component.literal("Nobody selected.").withStyle(ChatFormatting.RED));
            return;
        }
        BlockPos stand = pos.above();
        List<Citizen> soldiers = sel.stream().filter(c -> c.job().soldier).toList();
        int w = (int) Math.ceil(Math.sqrt(soldiers.size()));
        for (int i = 0; i < soldiers.size(); i++) {
            int dx = i % Math.max(1, w) - w / 2, dz = i / Math.max(1, w) - w / 2;
            soldiers.get(i).hold(stand.offset(dx * 2, 0, dz * 2));
        }
        int workers = 0;
        for (Citizen c : sel) {
            if (!c.job().soldier) {
                c.setPost(stand);
                c.returnToDuty();
                workers++;
            }
        }
        ((ServerLevel) p.level()).sendParticles(p, ParticleTypes.HAPPY_VILLAGER, true, false, stand.getX() + 0.5, stand.getY() + 0.2,
                stand.getZ() + 0.5, 12, 0.6, 0.2, 0.6, 0);
        String msg = (soldiers.isEmpty() ? "" : soldiers.size() + " soldiers march there and hold. ")
                + (workers == 0 ? "" : workers + " workers will work there now.");
        p.sendOverlayMessage(Component.literal(msg.trim()).withStyle(ChatFormatting.YELLOW));
    }

    /** Right-click an enemy: charge! */
    public static void charge(ServerPlayer p, LivingEntity target) {
        List<Citizen> soldiers = selectedCitizens(p).stream().filter(c -> c.job().soldier).toList();
        if (soldiers.isEmpty()) {
            p.sendOverlayMessage(Component.literal("No soldiers selected.").withStyle(ChatFormatting.RED));
            return;
        }
        for (Citizen c : soldiers) {
            if (c.order() == com.dominionmod.dominion.entity.Order.HOLD) c.returnToDuty();
            c.setTarget(target);
        }
        horn(p);
        p.sendOverlayMessage(Component.literal("CHARGE! " + soldiers.size() + " soldiers attack the " + target.getName().getString() + ".")
                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
    }

    private static void horn(ServerPlayer p) {
        p.level().playSound(null, p.blockPosition(), SoundEvents.RAID_HORN.value(), SoundSource.PLAYERS, 0.6f, 1.4f);
    }

    private static void ping(ServerPlayer p, Citizen c) {
        ((ServerLevel) p.level()).sendParticles(p, ParticleTypes.HAPPY_VILLAGER, true, false, c.getX(), c.getY() + 2.3, c.getZ(), 4, 0.2, 0.1, 0.2, 0);
    }

    /** Every second, players holding the baton see markers over who they've selected. */
    public static void showSelection(ServerPlayer p) {
        for (Citizen c : selectedCitizens(p)) {
            ((ServerLevel) p.level()).sendParticles(p, ParticleTypes.HAPPY_VILLAGER, true, false, c.getX(), c.getY() + 2.3, c.getZ(), 1, 0.05, 0.05, 0.05, 0);
        }
    }

    public static void forget(UUID player) {
        STATES.remove(player);
    }
}
