package com.dominionmod.dominion.kingdom;

import com.dominionmod.dominion.block.TrainingDeskBlock;
import com.dominionmod.dominion.command.Orders;
import com.dominionmod.dominion.entity.Citizen;
import com.dominionmod.dominion.entity.Job;
import com.dominionmod.dominion.registry.ModContent;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Items;

/** Research (paid from the storehouse, sped up by Scholars) and University training. */
public final class Scholarship {
    private Scholarship() {}

    // ------------------------------------------------------------------ research

    public static Component start(ServerPlayer p, String id) {
        ServerLevel level = (ServerLevel) p.level();
        Kingdom k = Realm.of(p);
        if (k == null) return Realm.err("You need a kingdom first.");
        if (!k.owner.equals(p.getUUID())) return Realm.err("Only the ruler can choose what to research.");
        Research.Tech t = Research.get(id);
        if (t == null) return Realm.err("Unknown research.");
        KingdomData d = Realm.data(level);
        Progress pr = d.progress(k);
        if (pr.has(t.id())) return Realm.err(t.name() + " is already researched.");
        if (!pr.current.isEmpty()) {
            Research.Tech cur = Research.get(pr.current);
            return Realm.err("You're already researching " + (cur == null ? pr.current : cur.name()) + ".");
        }
        for (String req : t.requires()) {
            if (!pr.has(req)) {
                Research.Tech r = Research.get(req);
                return Realm.err("Research " + (r == null ? req : r.name()) + " first.");
            }
        }
        var store = Storehouse.containers(level, k);
        int coins = Storehouse.count(store, s -> s.is(ModContent.GOLD_COIN.get()));
        int lapis = Storehouse.count(store, s -> s.is(Items.LAPIS_LAZULI));
        if (coins < t.coins() || lapis < t.lapis()) {
            return Realm.err("The storehouse needs " + t.coins() + " Gold Coins and " + t.lapis() + " Lapis Lazuli (it has " + coins + " and " + lapis + ").");
        }
        takeAll(store, s -> s.is(ModContent.GOLD_COIN.get()), t.coins());
        takeAll(store, s -> s.is(Items.LAPIS_LAZULI), t.lapis());
        pr.current = t.id();
        pr.work = 0;
        d.markChanged();
        return Component.literal("Research begun: " + t.name() + ". Scholars at a Research Table make it go much faster.").withStyle(ChatFormatting.GREEN);
    }

    private static void takeAll(List<net.minecraft.world.Container> store, java.util.function.Predicate<net.minecraft.world.item.ItemStack> f, int n) {
        while (n > 0) {
            var got = Storehouse.take(store, f, n);
            if (got.isEmpty()) break;
            n -= got.getCount();
        }
    }

    /** Every second: research moves forward (1 per tick, plus more for each Scholar at work). */
    public static void tick(ServerLevel level) {
        KingdomData d = Realm.data(level);
        long now = level.getGameTime();
        for (Kingdom k : d.all()) {
            Progress pr = d.progress(k);
            if (pr.current.isEmpty()) continue;
            Research.Tech t = Research.get(pr.current);
            if (t == null) {
                pr.current = "";
                continue;
            }
            pr.work += 20 * (1 + Research.PER_SCHOLAR * pr.scholarsWorking(now));
            if (pr.work >= t.work()) {
                pr.researched.add(t.id());
                pr.current = "";
                pr.work = 0;
                Citizen.tellKingdom(level, k, Component.literal("Research complete: " + t.name() + "! ").withStyle(ChatFormatting.GOLD)
                        .append(Component.literal(t.unlocks()).withStyle(ChatFormatting.YELLOW)));
                for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
                    if (k.isMember(p.getUUID())) p.level().playSound(null, p.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8f, 1.2f);
                }
            }
            d.markChanged();
        }
    }

    // ------------------------------------------------------------------ University classes

    public record Course(Job job, int coins, int lapis, String about) {}

    public static final List<Course> COURSES = List.of(
            new Course(Job.WIZARD, 25, 8, "Casts lines of biting fangs at enemies from a distance."),
            new Course(Job.BARBARIAN, 15, 0, "40 health, hits hard, and goes berserk when wounded."),
            new Course(Job.MONK, 15, 4, "Fights with bare hands; heals and shields allies nearby."),
            new Course(Job.ENGINEER, 20, 4, "Builds twice as fast and repairs damaged buildings."));

    public static Course course(Job j) {
        for (Course c : COURSES) if (c.job() == j) return c;
        return null;
    }

    /** arg: "JOB@x,y,z" (the desk). Trains every selected citizen standing near the desk. */
    public static Component train(ServerPlayer p, String arg) {
        ServerLevel level = (ServerLevel) p.level();
        Kingdom k = Realm.of(p);
        if (k == null) return Realm.err("You need a kingdom first.");
        Progress pr = Realm.data(level).progress(k);
        if (!pr.has("university")) return Realm.err("Research Higher Learning first.");
        String[] parts = arg.split("@");
        Job job;
        BlockPos desk;
        try {
            job = Job.valueOf(parts[0]);
            String[] xyz = parts[1].split(",");
            desk = new BlockPos(Integer.parseInt(xyz[0]), Integer.parseInt(xyz[1]), Integer.parseInt(xyz[2]));
        } catch (RuntimeException e) {
            return Realm.err("Couldn't read that choice.");
        }
        Course course = course(job);
        if (course == null) return Realm.err("That isn't a class.");
        if (!level.getBlockState(desk).is(ModContent.TRAINING_DESK.get())) return Realm.err("The Training Desk is gone.");
        var store = Storehouse.containers(level, k);
        int trained = 0;
        for (Citizen c : Orders.selectedCitizens(p)) {
            if (c.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(desk)) > TrainingDeskBlock.RANGE * TrainingDeskBlock.RANGE || c.isChild() || c.job().isClass()) continue;
            if (Storehouse.count(store, s -> s.is(ModContent.GOLD_COIN.get())) < course.coins()
                    || Storehouse.count(store, s -> s.is(Items.LAPIS_LAZULI)) < course.lapis()) {
                if (trained == 0) return Realm.err("Training a " + job.title + " costs " + course.coins() + " Gold Coins"
                        + (course.lapis() > 0 ? " and " + course.lapis() + " Lapis Lazuli" : "") + " from the storehouse.");
                break;
            }
            takeAll(store, s -> s.is(ModContent.GOLD_COIN.get()), course.coins());
            takeAll(store, s -> s.is(Items.LAPIS_LAZULI), course.lapis());
            c.train(job);
            level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, c.getX(), c.getY() + 1, c.getZ(), 30, 0.4, 0.8, 0.4, 0.2);
            trained++;
        }
        if (trained == 0) return Realm.err("None of your selected citizens are at the desk (or they're already trained).");
        level.playSound(null, desk, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 1f, 0.8f);
        return Component.literal(trained + (trained == 1 ? " citizen is" : " citizens are") + " now " + job.title + (trained == 1 ? "" : "s") + ".")
                .withStyle(ChatFormatting.GREEN);
    }
}
