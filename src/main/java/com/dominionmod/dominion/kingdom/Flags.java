package com.dominionmod.dominion.kingdom;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.BannerPatternLayers;

/**
 * A kingdom flag travels between client and server as a short string:
 * {@code "<base dye>;<pattern id>#<dye>,<pattern id>#<dye>,..."}.
 */
public final class Flags {
    private Flags() {}

    public static final int MAX_LAYERS = 8;

    public record Layer(String pattern, int dye) {}

    public static String encode(int base, List<Layer> layers) {
        StringBuilder sb = new StringBuilder().append(base).append(';');
        for (int i = 0; i < layers.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(layers.get(i).pattern()).append('#').append(layers.get(i).dye());
        }
        return sb.toString();
    }

    public static int base(String flag) {
        try {
            return Math.floorMod(Integer.parseInt(flag.substring(0, flag.indexOf(';'))), 16);
        } catch (RuntimeException e) {
            return 0;
        }
    }

    public static List<Layer> layers(String flag) {
        List<Layer> out = new ArrayList<>();
        int semi = flag.indexOf(';');
        if (semi < 0 || semi == flag.length() - 1) return out;
        for (String part : flag.substring(semi + 1).split(",")) {
            int hash = part.lastIndexOf('#');
            if (hash <= 0) continue;
            try {
                out.add(new Layer(part.substring(0, hash), Math.floorMod(Integer.parseInt(part.substring(hash + 1)), 16)));
            } catch (NumberFormatException ignored) {
            }
            if (out.size() >= MAX_LAYERS) break;
        }
        return out;
    }

    /** Turns a flag string into a banner item carrying the design. */
    public static ItemStack toBanner(String flag, HolderLookup.Provider registries) {
        DyeColor base = DyeColor.byId(base(flag));
        ItemStack stack = new ItemStack(Items.BANNER.pick(base));
        HolderLookup.RegistryLookup<BannerPattern> patterns = registries.lookupOrThrow(Registries.BANNER_PATTERN);
        List<BannerPatternLayers.Layer> list = new ArrayList<>();
        for (Layer l : layers(flag)) {
            Identifier id = Identifier.tryParse(l.pattern());
            if (id == null) continue;
            patterns.get(ResourceKey.create(Registries.BANNER_PATTERN, id))
                    .ifPresent(h -> list.add(new BannerPatternLayers.Layer(h, DyeColor.byId(l.dye()))));
        }
        if (!list.isEmpty()) stack.set(DataComponents.BANNER_PATTERNS, new BannerPatternLayers(list));
        return stack;
    }

    /** The other way: a banner item (e.g. the kingdom's stored flag) as a flag string. */
    public static String fromBanner(ItemStack banner) {
        int base = banner.getItem() instanceof BannerItem b ? b.getColor().getId() : 0;
        List<Layer> out = new ArrayList<>();
        BannerPatternLayers layers = banner.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY);
        for (BannerPatternLayers.Layer l : layers.layers()) {
            Holder<BannerPattern> h = l.pattern();
            h.unwrapKey().ifPresent(k -> out.add(new Layer(k.identifier().toString(), l.color().getId())));
        }
        return encode(base, out);
    }
}
