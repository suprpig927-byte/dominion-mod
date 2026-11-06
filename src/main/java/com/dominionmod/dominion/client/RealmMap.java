package com.dominionmod.dominion.client;

import com.dominionmod.dominion.DominionMod;
import com.dominionmod.dominion.network.Payloads;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.material.MapColor;

/** The Realm screen's live map: a texture repainted from each {@link Payloads.MapData} the server sends. */
public final class RealmMap {
    private RealmMap() {}

    public static final Identifier ID = DominionMod.id("dynamic/realm_map");
    private static DynamicTexture texture;
    private static int size;
    public static int x0, z0;
    public static boolean ready;

    public static void receive(Payloads.MapData data) {
        int n = data.size();
        if (texture == null || size != n) {
            if (texture != null) texture.close();
            texture = new DynamicTexture(() -> "dominion realm map", n, n, true);
            Minecraft.getInstance().getTextureManager().register(ID, texture);
            size = n;
        }
        NativeImage img = texture.getPixels();
        if (img == null) return;
        byte[] c = data.colors();
        for (int z = 0; z < n; z++) {
            for (int x = 0; x < n; x++) {
                int packed = c[z * n + x] & 0xFF;
                int argb = packed == 0 ? 0xFF15181A : MapColor.getColorFromPackedId(packed);
                if ((argb >>> 24) == 0) argb = 0xFF15181A;
                img.setPixel(x, z, argb);
            }
        }
        texture.upload();
        x0 = data.x0();
        z0 = data.z0();
        ready = true;
    }

    public static int size() {
        return size;
    }
}
