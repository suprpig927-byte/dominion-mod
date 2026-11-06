package com.dominionmod.dominion.client.render;

import com.dominionmod.dominion.entity.Job;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.entity.BannerPatternLayers;

public class CitizenRenderState extends HumanoidRenderState {
    /** Kingdom colors, as ARGB tints, and their dye ids. */
    public int primary = 0xFFB02E26, secondary = 0xFFFED83D;
    public int primaryDye = 14, secondaryDye = 4;
    public Job job = Job.UNASSIGNED;
    public boolean isAggressive;
    /** Which biome the villager came from (vanilla villager type, e.g. "plains", "desert"). */
    public String villagerType = "plains";
    /** The kingdom flag (null = none). */
    public DyeColor flagBase;
    public BannerPatternLayers flagPatterns = BannerPatternLayers.EMPTY;
}
