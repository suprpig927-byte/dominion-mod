package com.dominionmod.dominion.client.render.siege;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/**
 * The battering ram, trebuchet and cannon share one model class: whatever parts each mesh has
 * (wheels, the swinging ram, the throwing arm, the barrel) are animated if present.
 */
public class SiegeModel extends EntityModel<SiegeRenderState> {
    private final ModelPart[] wheels;
    private final @Nullable ModelPart ram, arm, sling, barrel;

    public SiegeModel(ModelPart root) {
        super(root);
        java.util.List<ModelPart> w = new java.util.ArrayList<>();
        for (String n : new String[]{"left_wheel", "right_wheel", "wheel_fl", "wheel_fr", "wheel_bl", "wheel_br"}) if (root.hasChild(n)) w.add(root.getChild(n));
        this.wheels = w.toArray(new ModelPart[0]);
        ModelPart body = root.hasChild("body") ? root.getChild("body") : null;
        this.ram = body != null && body.hasChild("ram") ? body.getChild("ram") : null;
        ModelPart base = root.hasChild("base") ? root.getChild("base") : null;
        this.arm = base != null ? base.getChild("arm") : null;
        this.sling = arm != null ? arm.getChild("sling") : null;
        ModelPart carriage = root.hasChild("carriage") ? root.getChild("carriage") : null;
        this.barrel = carriage != null ? carriage.getChild("barrel") : null;
    }

    @Override
    public void setupAnim(SiegeRenderState s) {
        super.setupAnim(s);
        for (ModelPart w : wheels) w.xRot = s.wheel;
        float t = s.sinceAction;
        if (ram != null) {
            // draw back, then slam forward
            float swing = t < 6 ? -t / 6f * 6f : t < 10 ? -6f + (t - 6) / 4f * 12f : t < 20 ? 6f - (t - 10) / 10f * 6f : 0f;
            ram.z += swing;
        }
        if (arm != null) {
            // the arm whips up and over, then is winched back down
            float a = t < 8 ? t / 8f * 2.4f : t < 60 ? 2.4f - (t - 8) / 52f * 2.4f : 0f;
            arm.xRot = -0.35f + a;
            if (sling != null) sling.xRot = -arm.xRot * 0.8f;
        }
        if (barrel != null) {
            barrel.xRot = -s.pitch * Mth.DEG_TO_RAD;
            if (t < 6) barrel.z += t < 2 ? t * 0.8f : (6 - t) * 0.4f;
        }
    }
}
