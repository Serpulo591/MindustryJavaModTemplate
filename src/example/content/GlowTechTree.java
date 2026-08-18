package example.content;

import mindustry.content.TechTree;
import mindustry.content.TechTree.TechNode;
import mindustry.ctype.UnlockableContent;

import static mindustry.content.Blocks.conveyor;   // 静态导入 conveyer

public class GlowTechTree {
    private static TechNode context = null;

    public static void load() {
        // 找到原版传送带的科技节点
        TechNode conveyorNode = TechTree.all.find(t -> t.content == conveyor);
        if (conveyorNode != null) {
            context = conveyorNode;
            // 添加你的传送带作为子节点
            node(GlowBlocks.conveyor1);
            // 可以继续添加更多节点
            // node(GlowBlocks.highCarbonConveyor);
            context = null;
        }
    }

    // 添加一个简单节点，无额外前置条件
    private static TechNode node(UnlockableContent content) {
        return new TechNode(context, content, content.researchRequirements());
    }
}
