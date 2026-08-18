package example.content;

import arc.func.Boolf;
import arc.struct.Seq;
import mindustry.content.TechTree;
import mindustry.content.TechTree.TechNode;
import mindustry.ctype.UnlockableContent;
import mindustry.game.Objectives;
import mindustry.type.ItemStack;

public class GlowTechTree {
    private static TechNode context = null;
    public static Seq<TechNode> all = new Seq<>();
    public static Seq<TechNode> roots = new Seq<>();
    
    public static void load(){
        addToNext(Blocks.conveyor,() ->{
            node(GlowBlocks.conveyor1);
        });
    }
    
    public static void addToNext(UnlockableContent content, Runnable run){
        context = TechTree.all.find(t -> t.content == content);
        run.run();
    }
    
    public static TechNode nodeRoot(String name, UnlockableContent content, Runnable children){
        return nodeRoot(name, content, false, children);
    }

    public static TechNode nodeRoot(String name, UnlockableContent content, boolean requireUnlock, Runnable children){
        TechNode root = node(content, content.researchRequirements(), children);
        root.name = name;
        root.requiresUnlock = requireUnlock;
        roots.add(root);
        return root;
    }

    public static TechNode node(UnlockableContent content, Runnable children){
        return node(content, content.researchRequirements(), children);
    }

    public static TechNode node(UnlockableContent content, ItemStack[] requirements, Runnable children){
        return node(content, requirements, null, children);
    }

    public static TechNode node(UnlockableContent content, ItemStack[] requirements, Seq<Objective> objectives, Runnable children){
        TechNode node = new TechNode(context, content, requirements);
        if(objectives != null){
            node.objectives.addAll(objectives);
        }

        //insert missing sector parent dependencies
        if(context != null && context.content instanceof SectorPreset preset && !node.objectives.contains(o -> o instanceof SectorComplete sc && sc.preset == preset)){
            node.objectives.insert(0, new SectorComplete(preset));
        }

        TechNode prev = context;
        context = node;
        children.run();
        context = prev;

        return node;
    }

    public static TechNode node(UnlockableContent content, Seq<Objective> objectives, Runnable children){
        return node(content, content.researchRequirements(), objectives, children);
    }

    public static TechNode node(UnlockableContent block){
        return node(block, () -> {});
    }

    public static TechNode nodeProduce(UnlockableContent content, Seq<Objective> objectives, Runnable children){
        return node(content, content.researchRequirements(), objectives.add(new Produce(content)), children);
    }

    public static TechNode nodeProduce(UnlockableContent content, Runnable children){
        return nodeProduce(content, new Seq<>(), children);
    }
}
