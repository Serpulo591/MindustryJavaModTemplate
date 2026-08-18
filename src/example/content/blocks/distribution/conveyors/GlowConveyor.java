package example.content.blocks.distribution.conveyors;

import mindustry.type.Conveyor;

public class GlowItems {
    public static Conveyor
        cobalt,
        crudeSilicon,
        highCarbonAlloy;

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
    }
}