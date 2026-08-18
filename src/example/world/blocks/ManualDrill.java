package example.world.blocks;

// 完全复制 GlowBlocks 的导入列表
import arc.graphics.*;
import arc.math.*;
import arc.struct.*;
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

// 导入你的自定义物品
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
        // 现在 Category 和 with 都有了
        requirements(Category.production, with(GlowItems.cobalt, 12, Items.tungsten, 8));
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

            // 点击特效：用 Draw 和 Fill，不用任何外部类
            Draw.color(Pal.accent);
            Fill.circle(this.x, this.y, 3f);
            Draw.color(Pal.ammo);
            Fill.circle(this.x - 2, this.y - 2, 2f);
        }
    }
}
