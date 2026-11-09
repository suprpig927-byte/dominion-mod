package com.dominionmod.dominion.client.render.siege;

import net.minecraft.client.renderer.entity.state.EntityRenderState;

public class SupplyCartRenderState extends EntityRenderState {
    public float yRot;
    /** Wheel turn, in radians, from how far the cart has rolled. */
    public float wheel;
    /** Where the cart should be drawn relative to its own (interpolated) position: right behind its pony. */
    public double offX, offY, offZ;
}
