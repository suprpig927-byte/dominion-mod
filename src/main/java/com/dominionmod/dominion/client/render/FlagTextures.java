package com.dominionmod.dominion.client.render;

import com.dominionmod.dominion.DominionMod;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.entity.BannerPatternLayers;

/**
 * Paints each kingdom's flag onto a tabard texture, once per design: the banner's base color and
 * every pattern layer are composited from the game's own banner textures, then stretched over the
 * tabard's front and back with a trim in the secondary color.
 */
public final class FlagTextures {
    private FlagTextures() {}

    /** The tabard texture is 64x32 in model units, painted at 4x. */
    private static final int S = 4, TW = 64 * S, TH = 32 * S;
    private static final Map<String, Identifier> CACHE = new HashMap<>();
    private static final Map<Identifier, NativeImage> SOURCES = new HashMap<>();
    private static int next;

    public static Identifier tabard(DyeColor base, BannerPatternLayers patterns, int primaryDye, int secondaryDye) {
        if (base == null) {
            base = DyeColor.byId(primaryDye);
            patterns = BannerPatternLayers.EMPTY;
        }
        StringBuilder key = new StringBuilder().append(base.getId()).append(':').append(secondaryDye);
        for (var l : patterns.layers()) key.append('/').append(l.pattern().value().assetId()).append('=').append(l.color().getId());
        DyeColor b = base;
        BannerPatternLayers p = patterns;
        return CACHE.computeIfAbsent(key.toString(), k -> paint(b, p, DyeColor.byId(secondaryDye)));
    }

    private static final Map<String, Identifier> PREVIEWS = new HashMap<>();

    /** A 20x40 picture of the flag itself, for big previews in screens. */
    public static Identifier preview(DyeColor base, BannerPatternLayers patterns) {
        StringBuilder key = new StringBuilder().append(base.getId());
        for (var l : patterns.layers()) key.append('/').append(l.pattern().value().assetId()).append('=').append(l.color().getId());
        return PREVIEWS.computeIfAbsent(key.toString(), k -> {
            int[] flag = composite(base, patterns);
            NativeImage img = new NativeImage(20, 40, true);
            for (int y = 0; y < 40; y++) for (int x = 0; x < 20; x++) img.setPixel(x, y, flag[y * 20 + x]);
            Identifier id = DominionMod.id("dynamic/flag_" + (next++));
            Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "dominion flag preview", img));
            return id;
        });
    }

    private static Identifier paint(DyeColor base, BannerPatternLayers patterns, DyeColor trimDye) {
        int[] flag = composite(base, patterns);
        NativeImage img = new NativeImage(TW, TH, true);
        int trim = 0xFF000000 | trimDye.getTextureDiffuseColor();
        int trimDark = darken(trim, 0.75f);

        // Chest box (texOffs 0,0; 8x12x6) and skirt box (texOffs 32,0; 8x6x6), front and back.
        panel(img, flag, 6, 6, 8, 12, 0, false, trim);
        panel(img, flag, 38, 6, 8, 6, 12, false, trim);
        panel(img, flag, 20, 6, 8, 12, 0, true, trim);
        panel(img, flag, 52, 6, 8, 6, 12, true, trim);
        // Shoulders: a trim-colored yoke with a neck hole.
        for (int y = 0; y < 6 * S; y++) {
            for (int x = 0; x < 8 * S; x++) {
                boolean neck = x >= 2 * S && x < 6 * S && y >= S && y < 5 * S;
                if (!neck) img.setPixel(6 * S + x, y, (x + y) % 5 == 0 ? trimDark : trim);
            }
        }
        // Bottom hem of the skirt.
        for (int x = 0; x < 8 * S; x++) {
            for (int dy = 0; dy < S; dy++) {
                img.setPixel(38 * S + x, (6 + 6) * S - 1 - dy, trimDark);
                img.setPixel(52 * S + x, (6 + 6) * S - 1 - dy, trimDark);
            }
        }
        Identifier id = DominionMod.id("dynamic/tabard_" + (next++));
        Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "dominion tabard", img));
        return id;
    }

    /**
     * Paints part of the flag onto one face. The chest shows the top 2/3 of the banner, the skirt the bottom third.
     * {@code rowOffset} is where this face starts in the 18-unit-tall tabard.
     */
    private static void panel(NativeImage img, int[] flag, int u, int v, int w, int h, int rowOffset, boolean mirror, int trim) {
        int pw = w * S, ph = h * S, total = 18 * S;
        for (int y = 0; y < ph; y++) {
            int fy = Math.min(39, (rowOffset * S + y) * 40 / total);
            for (int x = 0; x < pw; x++) {
                int fx = Math.min(19, (mirror ? pw - 1 - x : x) * 20 / pw);
                int c = flag[fy * 20 + fx];
                boolean edge = x < 2 || x >= pw - 2;
                img.setPixel(u * S + x, v * S + y, edge ? trim : c);
            }
        }
    }

    /** The 20x40 face of a banner: base color plus every layer, from the vanilla banner textures. */
    private static int[] composite(DyeColor base, BannerPatternLayers patterns) {
        int[] out = new int[20 * 40];
        layer(out, Identifier.withDefaultNamespace("textures/entity/banner/base.png"), base, true);
        for (var l : patterns.layers()) {
            Identifier asset = l.pattern().value().assetId();
            layer(out, asset.withPath(p -> "textures/entity/banner/" + p + ".png"), l.color(), false);
        }
        return out;
    }

    private static void layer(int[] out, Identifier texture, DyeColor dye, boolean opaque) {
        NativeImage src = source(texture);
        int col = dye.getTextureDiffuseColor();
        float cr = ((col >> 16) & 255) / 255f, cg = ((col >> 8) & 255) / 255f, cb = (col & 255) / 255f;
        for (int y = 0; y < 40; y++) {
            for (int x = 0; x < 20; x++) {
                int px = src == null ? (opaque ? 0xFFFFFFFF : 0) : src.getPixel(1 + x, 1 + y);
                float a = opaque ? 1f : ((px >>> 24) & 255) / 255f;
                if (a <= 0) continue;
                float g = ((px >> 16) & 255) / 255f;
                int r = (int) (g * cr * 255), gg = (int) (g * cg * 255), bb = (int) (g * cb * 255);
                int dst = out[y * 20 + x];
                int nr = (int) (r * a + ((dst >> 16) & 255) * (1 - a));
                int ng = (int) (gg * a + ((dst >> 8) & 255) * (1 - a));
                int nb = (int) (bb * a + (dst & 255) * (1 - a));
                out[y * 20 + x] = 0xFF000000 | nr << 16 | ng << 8 | nb;
            }
        }
    }

    private static NativeImage source(Identifier texture) {
        return SOURCES.computeIfAbsent(texture, t -> {
            try {
                var res = Minecraft.getInstance().getResourceManager().getResource(t);
                if (res.isEmpty()) return null;
                try (InputStream in = res.get().open()) {
                    return NativeImage.read(in);
                }
            } catch (Exception e) {
                DominionMod.LOGGER.warn("Couldn't read banner texture {}", t, e);
                return null;
            }
        });
    }

    private static int darken(int argb, float f) {
        int r = (int) (((argb >> 16) & 255) * f), g = (int) (((argb >> 8) & 255) * f), b = (int) ((argb & 255) * f);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }
}
