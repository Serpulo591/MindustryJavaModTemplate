package example.content;

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

import static mindustry.Vars.*;
import static mindustry.type.ItemStack.*;

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
            researchCost = ItemStack.with(Items.cobalt, 10);
        }};

        highCarbonConveyor = new Conveyor("high-carbon-conveyor"){{
            requirements(Category.distribution, ItemStack.with(Items.cobalt, 3, Items.titanium, 2, Items.high-carbon-alloy, 2));
            health = 400;
            speed = 0.2036363636f;
            displayedSpeed = 28;
        }};
    }
}
