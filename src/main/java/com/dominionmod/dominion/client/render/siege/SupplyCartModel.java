package com.dominionmod.dominion.client.render.siege;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;

/** A two-wheeled covered supply wagon with shafts for a pony. */
public class SupplyCartModel extends EntityModel<SupplyCartRenderState> {
    private final ModelPart left, right;

    public SupplyCartModel(ModelPart root) {
        super(root);
        this.left = root.getChild("left_wheel");
        this.right = root.getChild("right_wheel");
    }

    @Override
    public void setupAnim(SupplyCartRenderState s) {
        super.setupAnim(s);
        left.xRot = s.wheel;
        right.xRot = s.wheel;
    }
}
