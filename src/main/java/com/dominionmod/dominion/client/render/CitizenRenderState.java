package com.dominionmod.dominion.client.render;

import com.dominionmod.dominion.entity.Job;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;

public class CitizenRenderState extends HumanoidRenderState {
    /** Kingdom colors, as ARGB tints. */
    public int primary = 0xFFB02E26, secondary = 0xFFFED83D;
    public int look;
    public Job job = Job.UNASSIGNED;
    public boolean isAggressive;
    /** The kingdom flag shown on soldiers' tabards (null = no flag). */
    public net.minecraft.world.item.DyeColor flagBase;
    public net.minecraft.world.level.block.entity.BannerPatternLayers flagPatterns =
            net.minecraft.world.level.block.entity.BannerPatternLayers.EMPTY;
}
