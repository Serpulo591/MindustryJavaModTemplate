package example.content;

import mindustry.world.Block;
import mindustry.world.blocks.distribution.Conveyor;
import mindustry.world.blocks.production.Drill;
import mindustry.type.ItemStack;
import mindustry.content.Items;
import mindustry.content.Liquids;     // 改用 content 包
import example.content.GlowItems;

public class GlowBlocks {
    public static Block
        conveyor1,
        highCarbonConveyor,
        vectorDrill;

    public static void load() {
        conveyor1 = new Conveyor("conveyor1") {{
            requirements(Block.Category.distribution, new ItemStack[]{new ItemStack(GlowItems.cobalt, 1)});
            health = 45;
            speed = 0.06f;
            displayedSpeed = 8.4f;
            buildCostMultiplier = 2;
            researchCost = new ItemStack[]{new ItemStack(GlowItems.cobalt, 10)};
        }};

        highCarbonConveyor = new Conveyor("high-carbon-conveyor") {{
            requirements(Block.Category.distribution, new ItemStack[]{
                new ItemStack(GlowItems.cobalt, 3),
                new ItemStack(Items.titanium, 2),
                new ItemStack(GlowItems.highCarbonAlloy, 2)
            });
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
            requirements(Block.Category.production, new ItemStack[]{
                new ItemStack(GlowItems.cobalt, 12),
                new ItemStack(Items.tungsten, 8)
            });
            consumePower(0.5f);
            consumeLiquid(Liquids.water, 0.065f).boost();
        }};
    }
}
