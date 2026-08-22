package example.world.meta;

import mindustry.world.meta.Stat;
import mindustry.world.meta.StatCat;

public class GlowStat extends Stat {

    public static final GlowStat bridgeConnections =
        new GlowStat("bridgeConnections", StatCat.blocks);

    public GlowStat(String name, StatCat category){
        super(name, category);
    }

    public GlowStat(String name){
        super(name);
    }
}