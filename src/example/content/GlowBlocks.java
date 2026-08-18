package example.content;

import mindustry.world.Block;

public class GlowBlocks {
    public static Block
        conveyor1,
        highCarbonConveyor,
        vectorDrill;

    public static void load() {
        conveyor1 = new Conveyor("conveyor1") {{
            requirements(Category.distribution, with(Items.cobalt, 1));
            health = 45;
            speed = 0.06f;
            displayedSpeed = 8.4f;
            buildCostMultiplier = 2;
            researchCost = with(Items.cobalt, 10);
        }};

        highCarbonConveyor = new Conveyor("high-carbon-conveyor") {{
            requirements(Category.distribution, with(Items.cobalt, 3, Items.titanium, 2, Items.high-carbon-alloy, 2));
            health = 400;
            speed = 0.2036363636f;
            displayedSpeed = 28;
        }};
        
        //占位符
        
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
            requirements(Category.production, with(Items.cobalt, 12, Items.tungsten, 8));
            consumePower(0.5f);
            consumeLiquid(Liquids.water, 0.065f).boost();
        }};
    }
}
