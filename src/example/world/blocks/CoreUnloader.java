package example.world.blocks;

import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.Mathf;
import arc.util.io.*;
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
        public void updateTile() {
            Tile other = world.tile(link);
            hadValidLink = linkValid(other);

            if (hadValidLink) {
                CoreBlock.CoreBuild core = (CoreBlock.CoreBuild) other.build;
                transportCounter += delta();

                while (transportCounter >= transportTime) {
                    Item item = null;
                    for (Item i : content.items()) {
                        if (core.items.has(i)) {
                            core.items.remove(i, 1);
                            item = i;
                            break;
                        }
                    }
                    if (item != null) {
                        items.add(item, 1);
                        dumpAccumulate();
                    } else {
                        transportCounter = 0f;
                        break;
                    }
                    transportCounter -= transportTime;
                }
            } else {
                transportCounter = 0f;
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
        public void configure(Object value) {
            if (value instanceof Integer) {
                link = (Integer) value;
            } else {
                link = -1;
            }
        }

        @Override
        public Object config() {
            return link;
        }

        @Override
        public boolean onConfigureBuildTapped(Building other) {
            if (other instanceof CoreBlock.CoreBuild && other.team == team) {
                float dst = Mathf.dst(other.tileX(), other.tileY(), tileX(), tileY());
                if (dst <= range) {
                    configure(other.pos());
                    return false;
                }
            }
            return true;
        }

private void drawInput(Tile other){
    if(!linkValid(tile, other, false)) return;

    float tx = tile.drawx();
    float ty = tile.drawy();
    float ox = other.drawx();
    float oy = other.drawy();

    Drawf.dashLine(Pal.accent, tx, ty, ox, oy);

    Drawf.square(ox, oy, other.block().size * tilesize / 2f + 2f, Pal.accent);
}

        @Override
        public void drawConfigure(){
            Drawf.Circle(x, y, tilesize, Pal.accent);
            Drawf.dashCircle(x, y, range * tilesize, Pal.accent);
            int r = range + tilesize;
            for(int dx = -r; dx <= r; dx++){
                for(int dy = -r; dy <= r; dy++){
                    if(dx == 0 && dy == 0) continue;
                    if(dx*dx + dy*dy > r*r) continue;
                    Tile other = tile.nearby(dx, dy);
                    if(other == null) continue;
                    if(linkValid(tile, other)){
                        boolean linked = links.contains(other.pos());
                        if(!linked && incoming.contains(other.pos())) continue;
                        Drawf.select(other.drawx(), other.drawy(),
                            other.block().size * tilesize / 2f + 2f + (linked ? 0f : Mathf.absin(Time.time, 4f, 1f)),
                            linked ? Pal.place : Pal.breakInvalid);
                    }
                }
            }

            for(int i = 0; i < links.size; i++){
                Tile other = world.tile(links.get(i));
                if(other != null) drawInput(other);
            }
            incoming.each(pos -> {
                Tile other = world.tile(pos);
                if(other != null) drawInput(other);
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