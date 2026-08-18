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

        // 使用 Block.Category 替代无法导入的 Category
        requirements(Block.Category.distribution, 
            new ItemStack[]{new ItemStack(GlowItems.cobalt, 12), new ItemStack(Items.tungsten, 8)});
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
            // Fx 暂时注释（如果 Fx 仍报错可先保留，不影响钻头逻辑）
            // Fx.mineSmall.at(this.x, this.y);
        }
    }
}
