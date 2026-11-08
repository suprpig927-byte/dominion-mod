package com.dominionmod.dominion.registry;

import com.dominionmod.dominion.DominionMod;
import com.dominionmod.dominion.block.MarketBlock;
import com.dominionmod.dominion.block.TownHallBlock;
import com.dominionmod.dominion.entity.Citizen;
import com.dominionmod.dominion.item.CommandBatonItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.AttackRange;
import net.minecraft.world.item.component.BlocksAttacks;
import net.minecraft.world.item.component.Weapon;
import java.util.List;
import java.util.Optional;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Every block, item and entity in Dominion. */
public final class ModContent {
    private ModContent() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(DominionMod.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(DominionMod.MODID);
    public static final DeferredRegister.Entities ENTITIES = DeferredRegister.createEntities(DominionMod.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, DominionMod.MODID);

    // ---------------------------------------------------------------- entities

    public static final DeferredHolder<EntityType<?>, EntityType<Citizen>> CITIZEN = ENTITIES.registerEntityType("citizen",
            Citizen::new, MobCategory.MISC, b -> b.sized(0.6f, 1.95f).eyeHeight(1.62f).clientTrackingRange(10));

    public static final DeferredHolder<EntityType<?>, EntityType<com.dominionmod.dominion.entity.Bullet>> BULLET_ENTITY = ENTITIES.registerEntityType("bullet",
            com.dominionmod.dominion.entity.Bullet::new, MobCategory.MISC, b -> b.noLootTable().sized(0.15f, 0.15f).clientTrackingRange(6).updateInterval(2));

    public static final DeferredHolder<EntityType<?>, EntityType<com.dominionmod.dominion.entity.wild.Deer>> DEER = ENTITIES.registerEntityType("deer",
            com.dominionmod.dominion.entity.wild.Deer::new, MobCategory.CREATURE, b -> b.sized(0.9f, 1.5f).eyeHeight(1.35f).clientTrackingRange(10));
    public static final DeferredHolder<EntityType<?>, EntityType<com.dominionmod.dominion.entity.wild.Orc>> ORC = ENTITIES.registerEntityType("orc",
            com.dominionmod.dominion.entity.wild.Orc::new, MobCategory.MONSTER, b -> b.sized(0.6f, 1.95f).eyeHeight(1.62f).clientTrackingRange(10));
    public static final DeferredHolder<EntityType<?>, EntityType<com.dominionmod.dominion.entity.wild.Centaur>> CENTAUR = ENTITIES.registerEntityType("centaur",
            com.dominionmod.dominion.entity.wild.Centaur::new, MobCategory.CREATURE, b -> b.sized(1.2f, 2.3f).eyeHeight(2.05f).clientTrackingRange(10));
    public static final DeferredHolder<EntityType<?>, EntityType<com.dominionmod.dominion.entity.wild.BreedHorse>> CLYDESDALE = horse("clydesdale",
            com.dominionmod.dominion.entity.wild.BreedHorse.Breed.CLYDESDALE);
    public static final DeferredHolder<EntityType<?>, EntityType<com.dominionmod.dominion.entity.wild.BreedHorse>> MUSTANG = horse("mustang",
            com.dominionmod.dominion.entity.wild.BreedHorse.Breed.MUSTANG);
    public static final DeferredHolder<EntityType<?>, EntityType<com.dominionmod.dominion.entity.wild.BreedHorse>> SHETLAND_PONY = horse("shetland_pony",
            com.dominionmod.dominion.entity.wild.BreedHorse.Breed.SHETLAND);

    private static DeferredHolder<EntityType<?>, EntityType<com.dominionmod.dominion.entity.wild.BreedHorse>> horse(
            String id, com.dominionmod.dominion.entity.wild.BreedHorse.Breed breed) {
        return ENTITIES.registerEntityType(id, (type, level) -> new com.dominionmod.dominion.entity.wild.BreedHorse(type, level, breed),
                MobCategory.CREATURE, b -> b.sized(1.3964844f, 1.6f).eyeHeight(1.52f).passengerAttachments(breed == com.dominionmod.dominion.entity.wild.BreedHorse.Breed.SHETLAND ? 1.44375f - 3.0f * 1.1f / 16.0f : 1.44375f).clientTrackingRange(10));
    }

    // ---------------------------------------------------------------- blocks

    public static final DeferredBlock<TownHallBlock> TOWN_HALL = BLOCKS.registerBlock("town_hall", TownHallBlock::new,
            p -> p.mapColor(MapColor.WOOD).strength(4f, 1200f).sound(SoundType.WOOD));
    public static final DeferredBlock<MarketBlock> MARKET = BLOCKS.registerBlock("market", MarketBlock::new,
            p -> p.mapColor(MapColor.WOOD).strength(2.5f).sound(SoundType.WOOD));

    // ---------------------------------------------------------------- items

    public static final DeferredItem<Item> GOLD_COIN = ITEMS.registerSimpleItem("gold_coin", p -> p.stacksTo(99));
    public static final DeferredItem<CommandBatonItem> COMMAND_BATON = ITEMS.registerItem("command_baton", CommandBatonItem::new,
            p -> p.stacksTo(1).rarity(Rarity.UNCOMMON));
    public static final DeferredBlock<com.dominionmod.dominion.block.ResearchTableBlock> RESEARCH_TABLE = BLOCKS.registerBlock("research_table",
            com.dominionmod.dominion.block.ResearchTableBlock::new, p -> p.mapColor(MapColor.WOOD).strength(2.5f).sound(SoundType.WOOD).noOcclusion());
    public static final DeferredBlock<com.dominionmod.dominion.block.TrainingDeskBlock> TRAINING_DESK = BLOCKS.registerBlock("training_desk",
            com.dominionmod.dominion.block.TrainingDeskBlock::new, p -> p.mapColor(MapColor.WOOD).strength(2.5f).sound(SoundType.WOOD));
    public static final DeferredItem<BlockItem> RESEARCH_TABLE_ITEM = ITEMS.registerSimpleBlockItem("research_table", RESEARCH_TABLE);
    public static final DeferredItem<BlockItem> TRAINING_DESK_ITEM = ITEMS.registerSimpleBlockItem("training_desk", TRAINING_DESK);
    public static final DeferredItem<Item> BLACKSMITH_TONGS = ITEMS.registerSimpleItem("blacksmith_tongs", p -> p.stacksTo(1));
    public static final DeferredItem<com.dominionmod.dominion.item.StorageLabelItem> STORAGE_LABEL = ITEMS.registerItem("storage_label",
            com.dominionmod.dominion.item.StorageLabelItem::new, p -> p.stacksTo(16));
    public static final DeferredItem<Item> BUILDERS_HAMMER = ITEMS.registerSimpleItem("builders_hammer", p -> p.stacksTo(1));
    public static final DeferredItem<com.dominionmod.dominion.item.BlueprintItem> BLUEPRINT = ITEMS.registerItem("blueprint",
            com.dominionmod.dominion.item.BlueprintItem::new, p -> p.stacksTo(1));
    public static final DeferredItem<BlockItem> TOWN_HALL_ITEM = ITEMS.registerSimpleBlockItem("town_hall", TOWN_HALL);
    public static final DeferredItem<BlockItem> MARKET_ITEM = ITEMS.registerSimpleBlockItem("market", MARKET);

    public static final DeferredBlock<com.dominionmod.dominion.block.OrcTotemBlock> ORC_TOTEM = BLOCKS.registerBlock("orc_totem",
            com.dominionmod.dominion.block.OrcTotemBlock::new, p -> p.mapColor(MapColor.COLOR_BROWN).strength(-1f, 3600000f).sound(SoundType.WOOD));
    public static final DeferredItem<BlockItem> ORC_TOTEM_ITEM = ITEMS.registerSimpleBlockItem("orc_totem", ORC_TOTEM);
    public static final DeferredItem<com.dominionmod.dominion.item.SpellBookItem> SPELL_BOOK = ITEMS.registerItem("spell_book",
            com.dominionmod.dominion.item.SpellBookItem::new, p -> p.stacksTo(1).rarity(Rarity.RARE));
    public static final DeferredItem<com.dominionmod.dominion.item.AmmoPouchItem> AMMO_POUCH = ITEMS.registerItem("ammo_pouch",
            com.dominionmod.dominion.item.AmmoPouchItem::new, p -> p.stacksTo(1));
    public static final DeferredItem<Item> VENISON = ITEMS.registerSimpleItem("venison",
            p -> p.food(new net.minecraft.world.food.FoodProperties.Builder().nutrition(3).saturationModifier(0.3f).build()));
    public static final DeferredItem<Item> COOKED_VENISON = ITEMS.registerSimpleItem("cooked_venison",
            p -> p.food(new net.minecraft.world.food.FoodProperties.Builder().nutrition(8).saturationModifier(0.85f).build()));

    // ---------------------------------------------------------------- spawn eggs

    /** One spawn egg per mob the mod adds. Entity types register before items, so each egg can name its mob directly. */
    public static final java.util.Map<String, DeferredItem<net.minecraft.world.item.SpawnEggItem>> SPAWN_EGGS = new java.util.LinkedHashMap<>();

    static {
        for (String mob : List.of("citizen", "deer", "orc", "centaur", "clydesdale", "mustang", "shetland_pony"))
            SPAWN_EGGS.put(mob, ITEMS.registerItem(mob + "_spawn_egg", net.minecraft.world.item.SpawnEggItem::new, p -> p.spawnEgg(eggMob(mob))));
    }

    private static EntityType<?> eggMob(String mob) {
        return switch (mob) {
            case "citizen" -> CITIZEN.get();
            case "deer" -> DEER.get();
            case "orc" -> ORC.get();
            case "centaur" -> CENTAUR.get();
            case "clydesdale" -> CLYDESDALE.get();
            case "mustang" -> MUSTANG.get();
            case "shetland_pony" -> SHETLAND_PONY.get();
            default -> throw new IllegalArgumentException(mob);
        };
    }

    // ---------------------------------------------------------------- weapons

    private static Item.Properties reach(Item.Properties p, float reach) {
        return p.component(DataComponents.ATTACK_RANGE, new AttackRange(0.0f, reach, 0.0f, reach + 2.0f, 0.3f, 1.0f));
    }

    /** Longsword: a step up from the iron sword, still quick. */
    public static final DeferredItem<Item> LONGSWORD = ITEMS.registerItem("longsword", Item::new,
            p -> p.sword(ToolMaterial.IRON, 4.0f, -2.5f).durability(400));
    /** Greatsword: two-handed, slow, huge hits and a little extra reach. */
    public static final DeferredItem<Item> GREATSWORD = ITEMS.registerItem("greatsword", Item::new,
            p -> reach(p.sword(ToolMaterial.IRON, 7.0f, -3.0f).durability(520), 3.5f));
    /** Battle Axe: brutal chops that knock shields aside for a long time. */
    public static final DeferredItem<Item> BATTLE_AXE = ITEMS.registerItem("battle_axe", Item::new,
            p -> p.sword(ToolMaterial.IRON, 8.0f, -3.2f).durability(450).component(DataComponents.WEAPON, new Weapon(1, 5.0f)));
    /** Halberd: an axe-blade on a pole: long reach, heavy, beats shields. */
    public static final DeferredItem<Item> HALBERD = ITEMS.registerItem("halberd", Item::new,
            p -> reach(p.sword(ToolMaterial.IRON, 7.0f, -3.3f).durability(480).component(DataComponents.WEAPON, new Weapon(1, 3.0f)), 4.5f));
    /** Glaive: a curved blade on a pole: long reach, quicker than the halberd. */
    public static final DeferredItem<Item> GLAIVE = ITEMS.registerItem("glaive", Item::new,
            p -> reach(p.sword(ToolMaterial.IRON, 5.0f, -2.9f).durability(450), 4.5f));
    /** Flail: a spiked ball on a chain that swings around shields and stuns them. */
    public static final DeferredItem<Item> FLAIL = ITEMS.registerItem("flail", Item::new,
            p -> p.sword(ToolMaterial.IRON, 5.5f, -3.0f).durability(420).component(DataComponents.WEAPON, new Weapon(1, 2.5f)));
    /** Iron Shield: blocks like a shield, twice as tough, recovers faster when axed. */
    public static final DeferredItem<net.minecraft.world.item.ShieldItem> IRON_SHIELD = ITEMS.registerItem("iron_shield", net.minecraft.world.item.ShieldItem::new,
            p -> p.durability(700)
                    .repairable(Items.IRON_INGOT)
                    .equippableUnswappable(EquipmentSlot.OFFHAND)
                    .delayedComponent(DataComponents.BLOCKS_ATTACKS, ctx -> new BlocksAttacks(0.2f, 0.5f,
                            List.of(new BlocksAttacks.DamageReduction(100.0f, Optional.empty(), 0.0f, 1.0f)),
                            new BlocksAttacks.ItemDamageFunction(3.0f, 1.0f, 0.6f),
                            Optional.of(ctx.getOrThrow(DamageTypeTags.BYPASSES_SHIELD)),
                            Optional.of(SoundEvents.SHIELD_BLOCK), Optional.of(SoundEvents.SHIELD_BREAK)))
                    .component(DataComponents.BREAK_SOUND, SoundEvents.SHIELD_BREAK));
    public static final DeferredItem<Item> BULLET = ITEMS.registerSimpleItem("bullet", p -> p.stacksTo(64));
    /** Flintlock pistol: quick to load, short range. */
    public static final DeferredItem<com.dominionmod.dominion.item.FirearmItem> FLINTLOCK = ITEMS.registerItem("flintlock",
            p -> new com.dominionmod.dominion.item.FirearmItem(p, 25, 10.0f, 3.6f, 2.5f), p -> p.durability(250).stacksTo(1));
    /** Musket: slow to load, long range and a heavy ball. */
    public static final DeferredItem<com.dominionmod.dominion.item.FirearmItem> MUSKET = ITEMS.registerItem("musket",
            p -> new com.dominionmod.dominion.item.FirearmItem(p, 50, 16.0f, 5.0f, 0.8f), p -> p.durability(350).stacksTo(1));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("dominion", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.dominion"))
            .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
            .icon(() -> TOWN_HALL_ITEM.get().getDefaultInstance())
            .displayItems((params, out) -> {
                out.accept(TOWN_HALL_ITEM.get());
                out.accept(MARKET_ITEM.get());
                out.accept(COMMAND_BATON.get());
                out.accept(GOLD_COIN.get());
                out.accept(BUILDERS_HAMMER.get());
                out.accept(BLACKSMITH_TONGS.get());
                out.accept(STORAGE_LABEL.get());
                out.accept(RESEARCH_TABLE_ITEM.get());
                out.accept(TRAINING_DESK_ITEM.get());
                out.accept(LONGSWORD.get());
                out.accept(GREATSWORD.get());
                out.accept(BATTLE_AXE.get());
                out.accept(HALBERD.get());
                out.accept(GLAIVE.get());
                out.accept(FLAIL.get());
                out.accept(IRON_SHIELD.get());
                out.accept(FLINTLOCK.get());
                out.accept(MUSKET.get());
                out.accept(BULLET.get());
                out.accept(AMMO_POUCH.get());
                out.accept(SPELL_BOOK.get());
                out.accept(VENISON.get());
                out.accept(COOKED_VENISON.get());
                SPAWN_EGGS.values().forEach(egg -> out.accept(egg.get()));
            }).build());

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        ENTITIES.register(modBus);
        TABS.register(modBus);
        modBus.addListener((net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent e) -> {
            if (e.getTabKey() == net.minecraft.world.item.CreativeModeTabs.SPAWN_EGGS) SPAWN_EGGS.values().forEach(egg -> e.accept(egg.get()));
        });
        modBus.addListener((EntityAttributeCreationEvent e) -> {
            e.put(CITIZEN.get(), Citizen.createAttributes().build());
            e.put(DEER.get(), com.dominionmod.dominion.entity.wild.Deer.createAttributes().build());
            e.put(ORC.get(), com.dominionmod.dominion.entity.wild.Orc.createAttributes().build());
            e.put(CENTAUR.get(), com.dominionmod.dominion.entity.wild.Centaur.createAttributes().build());
            e.put(CLYDESDALE.get(), com.dominionmod.dominion.entity.wild.BreedHorse.createAttributes(com.dominionmod.dominion.entity.wild.BreedHorse.Breed.CLYDESDALE).build());
            e.put(MUSTANG.get(), com.dominionmod.dominion.entity.wild.BreedHorse.createAttributes(com.dominionmod.dominion.entity.wild.BreedHorse.Breed.MUSTANG).build());
            e.put(SHETLAND_PONY.get(), com.dominionmod.dominion.entity.wild.BreedHorse.createAttributes(com.dominionmod.dominion.entity.wild.BreedHorse.Breed.SHETLAND).build());
        });
    }
}
