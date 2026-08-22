package example.world.blocks;

import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.Mathf;
import arc.util.io.*;
import arc.util.Time;
import mindustry.entities.TargetPriority;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.blocks.storage.CoreBlock;
import mindustry.world.meta.*;

import static mindustry.Vars.*;

public class CoreUnloader extends Block {
    public int range = 250;
    public float transportTime = 1f;

    public CoreUnloader(String name) {
        super(name);
        update = true;
        solid = true;
        underBullets = true;
        itemCapacity = 50;
        configurable = true;
        hasItems = true;
        unloadable = true;
        group = BlockGroup.transportation;
        noUpdateDisabled = true;
        allowDiagonal = true;
        copyConfig = true;
        allowConfigInventory = true;
        priority = TargetPriority.transport;
        delayLandingConfig = true;
    }

    @Override
    public void setBars() {
        super.setBars();
        removeBar("items");
    }

    @Override
    public boolean outputsItems() {
        return true;
    }

    @Override
    public void setStats() {
        super.setStats();
        if (transportTime != 0f) {
            stats.add(Stat.itemsMoved, 60f / transportTime, StatUnit.itemsSecond);
        }
        stats.add(Stat.linkRange, range, StatUnit.blocks);
    }

    @Override
    public void drawPlace(int x, int y, int rotation, boolean valid) {
        super.drawPlace(x, y, rotation, valid);
        Drawf.dashCircle(x, y, range * tilesize, Pal.accent);
    }

    public class CoreUnloaderBuild extends Building {
        public int link = -1;
        public float transportCounter = 0f;
        public boolean hadValidLink;

        public boolean linkValid(Tile other) {
            if (other == null || other.build == null) return false;
            Building b = other.build;
            if (!(b instanceof CoreBlock.CoreBuild)) return false;
            if (b.team != team) return false;
            float dx = other.x - tile.x, dy = other.y - tile.y;
            return dx * dx + dy * dy <= range * range;
        }

@Override
public void updateTile(){
    if(efficiency <= 0f){
        transportCounter = 0f;
        return;
    }

    if(link == -1){
        transportCounter = 0f;
        return;
    }

    if(items.total() >= itemCapacity){
        transportCounter = 0f;
        return;
    }

    Tile other = world.tile(link);

    if(!linkValid(other)){
        transportCounter = 0f;
        return;
    }

    CoreBlock.CoreBuild core = (CoreBlock.CoreBuild)other.build;

    transportCounter += delta();

    while(transportCounter >= transportTime){

        if(items.total() >= itemCapacity){
            transportCounter = 0f;
            break;
        }

        Item item = null;

        for(Item i : content.items()){
            if(core.items.has(i)){
                item = i;
                break;
            }
        }

        if(item == null){
            transportCounter = 0f;
            break;
        }

        core.items.remove(item, 1);
        items.add(item, 1);

        transportCounter -= transportTime;

        dumpAccumulate();
    }
}

        @Override
        public boolean acceptItem(Building source, Item item) {
            return false;
        }

        @Override
        public boolean canDump(Building to, Item item) {
            return true;
        }

@Override
public void configure(Object value){
    if(value instanceof Integer i){
        link = i;
    }else{
        link = -1;
    }

    transportCounter = 0f;
}

        @Override
        public Object config() {
            return link;
        }

@Override
public boolean onConfigureBuildTapped(Building other){
    if(other instanceof CoreBlock.CoreBuild && other.team == team){
        if(dst(other) <= range){

            if(link == other.pos()){
                configure(-1);
            }else{
                configure(other.pos());
            }

            return false;
        }
    }

    return true;
}

private void drawInput(Tile other){
    if(!linkValid(other)) return;
    float tx = tile.drawx();
    float ty = tile.drawy();
    float ox = other.drawx();
    float oy = other.drawy();
    Drawf.dashLine(Pal.accent, tx, ty, ox, oy);
    Drawf.select(ox, oy, other.block().size * tilesize / 2f + 2f, Pal.place);
    Drawf.square(ox, oy, 2f, Pal.accent);
}

@Override
public void drawConfigure(){
    float sin = Mathf.absin(Time.time, 4f, 1f);
    Lines.stroke(1f);
    Drawf.circles(x, y, 9f + sin, Pal.accent);
    Drawf.dashCircle(x, y, range * tilesize, Pal.accent);

    if(link != -1){
        Tile other = world.tile(link);
        if(other != null && linkValid(other)){
            drawInput(other);
        }
    }
    Groups.build.each(b -> {
        if(!(b instanceof CoreBlock.CoreBuild)) return;
        if(b.team != team) return;
        if(!within(b, range)) return;
        if(b.pos() == link) return;
        Drawf.select( b.x, b.y, b.block.size * tilesize / 2f + 2f, Pal.breakInvalid);
    });
    Draw.reset();
}

        @Override
        public void write(Writes write) {
            super.write(write);
            write.i(link);
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            link = read.i();
        }
    }
}