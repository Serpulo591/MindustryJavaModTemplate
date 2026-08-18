package example.world.blocks;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.math.Mathf;
import mindustry.gen.Sounds;
import mindustry.graphics.Pal;
import mindustry.world.blocks.production.Drill;
import mindustry.type.ItemStack;
import mindustry.content.Items;
import mindustry.content.Liquids;
import mindustry.world.blocks.production.Drill.DrillBuild;
import example.content.GlowItems;
import mindustry.world.meta.*;

import static mindustry.Vars.*;
import static mindustry.type.ItemStack.*;

public class ManualDrill extends Drill {

    public ManualDrill(String name) {
        super(name);

        researchCostMultiplier = 0.1f;
        size = 2;
        health = 300;
        tier = 100;
        drillTime = 180;
        canOverdrive = true;
        warmupSpeed = 30f;
        itemCapacity = 10;
        liquidCapacity = 38f;
        consumePower(0.5f);
        consumeLiquid(Liquids.water, 0.065f).boost();

        buildType = () -> new ManualDrillBuild();
    }

    public class ManualDrillBuild extends DrillBuild {
        public float boostLevel = 1f;
        public int emergencyTime = 0;
        public final float boostAdd = 1f;
        public final float boostMax = 20f;
        public final float boostRecover = 0.02f;

        @Override
        public void updateTile() {
            if (boostLevel > 1f) {
                boostLevel = Mathf.lerpDelta(boostLevel, 1f, boostRecover);
            }
            this.timeScale = boostLevel;

            if (emergencyTime > 0) {
                emergencyTime--;
                if (this.power.status <= 0) {
                    this.efficiency = 1f;
                }
            }

            super.updateTile();
        }

@Override
public void tapped() {
    boostLevel += boostAdd;
    if (boostLevel > boostMax) boostLevel = boostMax;

    if (this.power.status <= 0) {
        emergencyTime = 60;
    }

    Sounds.click.play();

    // 使用 Effect 播放爆炸特效
    Effect effect = new Effect(20f, e -> {
        Draw.color(Pal.accent);
        Fill.circle(e.x, e.y, 4f);
        Draw.color(Pal.ammo);
        Fill.circle(e.x - 3, e.y - 3, 2f);
        Draw.color(Color.valueOf("ffaa66"));
        Fill.circle(e.x + 3, e.y + 3, 2f);
    });
    effect.at(this.x, this.y);
}
    }
}
