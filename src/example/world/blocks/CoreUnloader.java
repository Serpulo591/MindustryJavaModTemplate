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
    if(!linkValid(other)) return;

    float tx = tile.drawx();
    float ty = tile.drawy();
    float ox = other.drawx();
    float oy = other.drawy();

    Drawf.dashLine(Pal.accent, tx, ty, ox, oy);

    Drawf.square(ox, oy, other.block().size * tilesize / 2f + 2f, Pal.accent);
}

@Override
public void drawConfigure(){
    Drawf.circles(x, y, 9f, Pal.accent);
    Drawf.dashCircle(x, y, range * tilesize, Pal.accent);

    // 显示当前已连接的 Core
    if(link != -1){
        Tile other = world.tile(link);
        if(other != null && linkValid(other)){
            drawInput(other);
        }
    }

    // 显示范围内可以连接的 Core
    int radius = (int)(range / tilesize) + 1;

    for(int dx = -radius; dx <= radius; dx++){
        for(int dy = -radius; dy <= radius; dy++){
            Tile other = world.tile(tile.x + dx, tile.y + dy);

            if(other == null || other.build == null) continue;
            if(!(other.build instanceof CoreBlock.CoreBuild)) continue;
            if(other.build.team != team) continue;
            if(!linkValid(other)) continue;

            // 已连接的不再画红色候选框
            if(other.pos() == link){
                continue;
            }

            Drawf.select(
                other.drawx(),
                other.drawy(),
                other.block().size * tilesize / 2f + 2f +
                    Mathf.absin(Time.time, 4f, 1f),
                Pal.breakInvalid
            );
        }
    }

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