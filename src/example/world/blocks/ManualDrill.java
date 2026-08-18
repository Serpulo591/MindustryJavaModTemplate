package example.world.blocks;

import mindustry.world.blocks.production.Drill;
import mindustry.world.meta.Category;
import mindustry.type.ItemStack;
import mindustry.content.Items;
import mindustry.content.Liquids;
import mindustry.gen.Sounds;
import mindustry.entities.effect.Fx;
import arc.math.Mathf;
import mindustry.world.blocks.production.Drill.DrillBuild;
import example.content.GlowItems;

import static mindustry.Vars.with;

public class ManualDrill extends Drill {

    public ManualDrill(String name) {
        super(name);

        // 基础属性
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

        // 自定义建筑逻辑
        buildType = () -> new ManualDrillBuild();
