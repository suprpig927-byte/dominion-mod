package com.dominionmod.dominion.client.render.siege;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

public class SiegeRenderState extends LivingEntityRenderState {
    /** Ticks since the last swing or shot (large = idle). */
    public float sinceAction = 1000;
    public float pitch;
    public float wheel;
}
