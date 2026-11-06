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

    // ---------------------------------------------------------------- blocks

    public static final DeferredBlock<TownHallBlock> TOWN_HALL = BLOCKS.registerBlock("town_hall", TownHallBlock::new,
            p -> p.mapColor(MapColor.WOOD).strength(4f, 1200f).sound(SoundType.WOOD));
    public static final DeferredBlock<MarketBlock> MARKET = BLOCKS.registerBlock("market", MarketBlock::new,
            p -> p.mapColor(MapColor.WOOD).strength(2.5f).sound(SoundType.WOOD));

    // ---------------------------------------------------------------- items

    public static final DeferredItem<Item> GOLD_COIN = ITEMS.registerSimpleItem("gold_coin", p -> p.stacksTo(99));
    public static final DeferredItem<CommandBatonItem> COMMAND_BATON = ITEMS.registerItem("command_baton", CommandBatonItem::new,
            p -> p.stacksTo(1).rarity(Rarity.UNCOMMON));
    public static final DeferredItem<BlockItem> TOWN_HALL_ITEM = ITEMS.registerSimpleBlockItem("town_hall", TOWN_HALL);
    public static final DeferredItem<BlockItem> MARKET_ITEM = ITEMS.registerSimpleBlockItem("market", MARKET);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("dominion", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.dominion"))
            .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
            .icon(() -> TOWN_HALL_ITEM.get().getDefaultInstance())
            .displayItems((params, out) -> {
                out.accept(TOWN_HALL_ITEM.get());
                out.accept(MARKET_ITEM.get());
                out.accept(COMMAND_BATON.get());
                out.accept(GOLD_COIN.get());
            }).build());

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        ENTITIES.register(modBus);
        TABS.register(modBus);
        modBus.addListener((EntityAttributeCreationEvent e) -> e.put(CITIZEN.get(), Citizen.createAttributes().build()));
    }
}
