package example.content;

import example.world.blocks.BridgeRouter;
import example.world.blocks.ManualDrill;
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

public class GlowBlocks {
    public static Block
        conveyor1,
        highCarbonConveyor,
        warpBridge,
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

        warpBridge = new BridgeRouter("warp-bridge") {{
            size = 1;
            health = 350;
            hasItems = true;
            hasPower = false;
            itemCapacity = 10;
            requirements(Category.distribution, with(GlowItems.cobalt, 12, Items.tungsten, 8));
        }};
        
        //占位符
        
        vectorDrill = new ManualDrill("vector-drill") {{
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
        }};
    }
}
