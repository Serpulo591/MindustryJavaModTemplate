package example.world.blocks;

import mindustry.world.blocks.production.Drill;
import mindustry.type.ItemStack;
import mindustry.content.Items;
import mindustry.content.Liquids;
import mindustry.gen.Sounds;
import arc.math.Mathf;
import mindustry.world.blocks.production.Drill.DrillBuild;
import example.content.GlowItems;

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

        // 暂时用字符串代替 Category（后续可换）
        requirements(Category.distribution, ItemStack.with(GlowItems.cobalt, 12, Items.tungsten, 8));
        consumePower(0.5f);
        consumeLiquid(Liquids.water, 0.065f).boost();

        buildType = () -> new ManualDrillBuild();
    }

    public static class ManualDrillBuild extends DrillBuild {
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
            // 临时替换 Fx 为更基础的调用（或直接注释）
            // Fx.mineSmall.at(this.x, this.y);
            // 如果 Fx 仍报错，可以暂时用 Mindustry 自带的 Effect 库，或先注释
        }
    }
}
