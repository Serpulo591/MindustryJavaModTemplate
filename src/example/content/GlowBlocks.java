package example.content;

import arc.graphics.*;
import arc.math.*;
import arc.struct.*;
import arc.func.Prov;   // 新增
import mindustry.*;
import mindustry.entities.*;
import mindustry.entities.abilities.*;
import mindustry.entities.bullet.*;
import mindustry.entities.effect.*;
import mindustry.entities.part.DrawPart.*;
import mindustry.entities.part.*;
import mindustry.entities.pattern.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.type.*;
import mindustry.type.unit.*;
import mindustry.world.*;
import mindustry.world.blocks.*;
import mindustry.world.blocks.campaign.*;
import mindustry.world.blocks.defense.*;
import mindustry.world.blocks.defense.turrets.*;
import mindustry.world.blocks.distribution.*;
import mindustry.world.blocks.environment.*;
import mindustry.world.blocks.heat.*;
import mindustry.world.blocks.legacy.*;
import mindustry.world.blocks.liquid.*;
import mindustry.world.blocks.logic.*;
import mindustry.world.blocks.payloads.*;
import mindustry.world.blocks.power.*;
import mindustry.world.blocks.production.*;
import mindustry.world.blocks.sandbox.*;
import mindustry.world.blocks.storage.*;
import mindustry.world.blocks.units.*;
import mindustry.world.consumers.*;
import mindustry.world.draw.*;
import mindustry.world.meta.*;
import mindustry.content.Items;
import mindustry.content.Liquids;

import static mindustry.Vars.*;
import static mindustry.type.ItemStack.*;

public class GlowBlocks {
    public static Block
        conveyor1,
        highCarbonConveyor,
        vectorDrill;

    public static void load() {
        conveyor1 = new Conveyor("conveyor1") {{
            requirements(Category.distribution, with(GlowItems.cobalt, 1));
            health = 45;
            speed = 0.06f;
            displayedSpeed = 8.4f;
            buildCostMultiplier = 2;
            researchCost = with(GlowItems.cobalt, 10);
        }};

        highCarbonConveyor = new Conveyor("high-carbon-conveyor") {{
            requirements(Category.distribution, with(GlowItems.cobalt, 3, Items.titanium, 2, GlowItems.highCarbonAlloy, 2));
            health = 400;
            speed = 0.2036363636f;
            displayedSpeed = 28;
        }};

        vectorDrill = new Drill("vector-drill") {{
            researchCostMultiplier = 0.1f;
            size = 2;
            health = 300;
            tier = 100;
            drillTime = 180;
            canOverdrive = true;
            warmupSpeed = 30f;
            itemCapacity = 10;
            liquidCapacity = 38f;
            requirements(Category.production, with(GlowItems.cobalt, 12, Items.tungsten, 8));
            consumePower(0.5f);
            consumeLiquid(Liquids.water, 0.065f).boost();

            // ★★★ 挂载自定义逻辑 ★★★
            buildType = () -> new VectorDrillBuild();
        }};
    }

    // ====== 自定义钻头建筑逻辑（JS 转 Java） ======
    public static class VectorDrillBuild extends Drill.DrillBuild {
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
            Fx.mineSmall.at(this.x, this.y);
        }
    }
}
