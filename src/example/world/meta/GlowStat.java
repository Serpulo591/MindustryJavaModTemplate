package example.world.meta;

import arc.*;
import arc.struct.*;

import java.util.*;

public class GlowStat implements Comparable<GlowStat>{
    public static final Seq<GlowStat> all = new Seq<>();

    public static final GlowStat
        bridgeConnections = new GlowStat("bridgeConnections", StatCat.crafting);

    public final StatCat category;
    public final String name;
    public final int id;

    public GlowStat(String name, StatCat category){
        this.category = category;
        this.name = name;
        id = all.size;
        all.add(this);
    }

    public GlowStat(String name){
        this(name, StatCat.general);
    }

    public String localized(){
        return Core.bundle.get("stat." + name.toLowerCase(Locale.ROOT));
    }

    @Override
    public String toString(){
        return name;
    }

    @Override
    public int compareTo(GlowStat o){
        return id - o.id;
    }
}