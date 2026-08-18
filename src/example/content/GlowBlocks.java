package example.content;

import mindustry.content.Item;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.world.Block;
import mindustry.world.GlowBlocks.production.Conveyor;

public class GlowBlocks {
    public static Block
        conveyor1,
        highCarbonConveyor;

    public static void load() {
        conveyor1 = new Conveyor("conveyor1"){{
            requirements(Category.distribution, ItemStack.with(Items.cobalt, 1));
            health = 45;
            speed = 0.06f;
            displayedSpeed = 8.4f;
            buildCostMultiplier = 2;
            researchCost = with(Items.cobalt, 10);
        }};

        highCarbonConveyor = new Conveyor("high-carbon-conveyor"){{
            requirements(Category.distribution, with(Items.cobalt, 3, Items.titanium, 2, Items.high-carbon-alloy, 2));
            health = 400;
            speed = 0.2036363636f;
            displayedSpeed = 28;
        }};
    }
}
