package example.world.blocks;

import mindustry.world.blocks.storage.StorageBlock;
import arc.func.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.graphics.Color;
import arc.struct.IntSeq;
import arc.struct.Seq;
import arc.math.*;
import arc.math.geom.*;
import arc.struct.*;
import arc.util.*;
import arc.util.io.*;
import mindustry.core.*;
import mindustry.entities.*;
import mindustry.entities.units.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.input.*;
import mindustry.ui.Bar;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.meta.*;

import static mindustry.Vars.*;

public class BridgeRouter extends StorageBlock {
    public final int timerCheckMoved = timers ++;
    public int range;
    public float transportTime = 1f;
    public boolean fadeIn = true;
    public boolean moveArrows = true;
    public boolean pulse = false;
    public float arrowSpacing = 4f, arrowOffset = 2f, arrowPeriod = 0.4f;
    public float arrowTimeScl = 6.2f;
    public float bridgeWidth = 6.5f;
    
    public @Nullable BridgeRouterBuild lastBuild;

    public BridgeRouter(String name){
        super(name);
        update = true;
        solid = true;
        underBullets = true;
        hasPower = true;
        itemCapacity = 10;
        configurable = true;
        hasItems = true;
        unloadable = false;
        group = BlockGroup.transportation;
        noUpdateDisabled = true;
        allowDiagonal = false;
        copyConfig = false;
        allowConfigInventory = false;
        ignoreResizeConfig = true;
        priority = TargetPriority.transport;
        delayLandingConfig = true;
        config(Point2.class, (BridgeRouterBuild tile, Point2 i) -> tile.link = Point2.pack(i.x + tile.tileX(), i.y + tile.tileY()));
        config(Integer.class, (BridgeRouterBuild tile, Integer i) -> tile.link = i);
    }
    
    @Override
    public void setStats() {
        super.setStats();
        if(transportTime != 0f){
            stats.add(Stat.itemsMoved, 60f / transportTime, StatUnit.itemsSecond);
        }
    }
    
    @Override
    public void drawPlace(int x, int y, int rotation, boolean valid){
        super.drawPlace(x, y, rotation, valid);
        Drawf.dashCircle(x * tilesize, y * tilesize, range - tilesize, Pal.accent);
    }
    
    public boolean linkValid(Tile tile, Tile other){
        return linkValid(tile, other, true);
    }
    
    public boolean linkValid(Tile tile, Tile other, boolean checkDouble){
        if(other == null || tile == null || !positionsValid(tile.x, tile.y, other.x, other.y)) return false;

        return ((other.block() == tile.block() && tile.block() == this) || (!(tile.block() instanceof BridgeRouter) && other.block() == this))
            && (other.team() == tile.team() || tile.block() != this);
    }
    
    public boolean positionsValid(int x1, int y1, int x2, int y2){
        int dx = x1 - x2;
        int dy = y1 - y2;
        return (dx * dx + dy * dy) <= (range * range + 8);
    }
    
    @Override
    public void init(){
        super.init();
        updateClipRadius((range + 0.5f) * tilesize);
    }
    
    public class BridgeRouterBuild extends StorageBuild {
        public int link = -1;
        public IntSeq incoming = new IntSeq(false, 4);
        public float warmup;
        public float time = -8f, timeSpeed;
        public boolean wasMoved, moved, hadValidLink;
        public float transportCounter;
    
        @Override
        public void pickedUp() {
            link = -1;
        }
    
        @Override
        public void drawSelect() {
            if (linkValid(tile, world.tile(link))) {
                drawInput(world.tile(link));
            }
            incoming.each(pos -> drawInput(world.tile(pos)));
            Draw.reset();
        }
    
        private void drawInput(Tile other) {
            if (!linkValid(tile, other, false)) return;
            boolean linked = other.pos() == link;
    
            Tmp.v2.trns(tile.angleTo(other), 2f);
            float tx = tile.drawx(), ty = tile.drawy();
            float ox = other.drawx(), oy = other.drawy();
            float alpha = Math.abs((linked ? 100 : 0) - (Time.time * 2f) % 100f) / 100f;
            float x = Mathf.lerp(ox, tx, alpha);
            float y = Mathf.lerp(oy, ty, alpha);
    
            Tile otherLink = linked ? other : tile;
            int rel = (linked ? tile : other).absoluteRelativeTo(otherLink.x, otherLink.y);
    
            Draw.color(Pal.gray);
            Lines.stroke(2.5f);
            Lines.square(ox, oy, 2f, 45f);
            Lines.stroke(2.5f);
            Lines.line(tx + Tmp.v2.x, ty + Tmp.v2.y, ox - Tmp.v2.x, oy - Tmp.v2.y);
    
            float color = (linked ? Pal.place : Pal.accent).toFloatBits();
            Draw.color(color);
            Lines.stroke(1f);
            Lines.line(tx + Tmp.v2.x, ty + Tmp.v2.y, ox - Tmp.v2.x, oy - Tmp.v2.y);
            Lines.square(ox, oy, 2f, 45f);
            Draw.mixcol(color);
            Draw.color();
            Draw.rect("bridge-arrow", x, y, rel * 90);
            Draw.mixcol();
        }
        
        @Override
        public void drawConfigure(){
            Drawf.select(x, y, tile.block().size * tilesize / 2f + 2f, Pal.accent);
            Drawf.dashCircle(x, y, range * tilesize, Pal.accent);
            int r = range;
            for(int dx = -r; dx <= r; dx++){
                for(int dy = -r; dy <= r; dy++){
                    if(dx == 0 && dy == 0) continue;
                    if(dx*dx + dy*dy > r*r) continue;
                    Tile other = tile.nearby(dx, dy);
                    if(other == null) continue;
                    if(linkValid(tile, other)){
                        boolean linked = other.pos() == link;
                        Drawf.select(other.drawx(), other.drawy(),
                            other.block().size * tilesize / 2f + 2f + (linked ? 0f : Mathf.absin(Time.time, 4f, 1f)),
                            linked ? Pal.place : Pal.breakInvalid);
                    }
                }
            }
        }
        
        @Override
        public boolean onConfigureBuildTapped(Building other){
            //reverse connection
            if(other instanceof BridgeRouterBuild b && b.link == pos()){
                configure(other.pos());
                other.configure(-1);
                return true;
            }

            if(linkValid(tile, other.tile)){
                if(link == other.pos()){
                    configure(-1);
                }else{
                    configure(other.pos());
                }
                return false;
            }
            return true;
        }
        
        public void checkIncoming(){
            int idx = 0;
            while(idx < incoming.size){
                int i = incoming.items[idx];
                Tile other = world.tile(i);
                if(!linkValid(tile, other, false) || ((BridgeRouterBuild)other.build).link != tile.pos()){
                    incoming.removeIndex(idx);
                    idx --;
                }
                idx ++;
            }
        }
        
        @Override
        public void updateTile(){
            if(timer(timerCheckMoved, 30f)){
                wasMoved = moved;
                moved = false;
            }

            timeSpeed = Mathf.approachDelta(timeSpeed, wasMoved ? 1f : 0f, 1f / 60f);

            time += timeSpeed * delta();

            checkIncoming();

            Tile other = world.tile(link);
            hadValidLink = linkValid(tile, other);

            if(!hadValidLink){
                doDump();
                warmup = 0f;
            }else{
                var inc = ((BridgeRouterBuild)other.build).incoming;
                int pos = tile.pos();
                if(!inc.contains(pos)){
                    inc.add(pos);
                }

                warmup = Mathf.approachDelta(warmup, efficiency, 1f / 30f);
                updateTransport(other.build);
            }
        }
        
        public void doDump(){
             dumpAccumulate();
        }
        
        public void updateTransport(Building other){
            transportCounter += edelta();
            while(transportCounter >= transportTime){
                Item item = items.take();
                if(item != null && other.acceptItem(this, item)){
                    other.handleItem(this, item);
                    moved = true;
                }else if(item != null){
                    items.add(item, 1);
                    items.undoFlow(item);
                }
                transportCounter -= transportTime;
            }
        }
        
        @Override
        public void draw(){
            super.draw();
        
            Tile other = world.tile(link);
            if(!linkValid(tile, other)) return;
            if(Mathf.zero(Renderer.bridgeOpacity)) return;
        
            float tx = tile.drawx(), ty = tile.drawy();
            float ox = other.drawx(), oy = other.drawy();
        
            float dx = ox - tx, dy = oy - ty;
            float len = Mathf.dst(dx, dy);
            if(len < 1f) return;
            float nx = dx / len, ny = dy / len;
        
            float px = -ny, py = nx;
        
            float inset = tilesize / 2f;
            float startX = tx + nx * inset, startY = ty + ny * inset;
            float endX = ox - nx * inset, endY = oy - ny * inset;
        
            // ---- 1. 绘制两条平行外线 ----
            float offset = 2.5f;
            Draw.color(Pal.gray);
            Lines.stroke(2.5f);
            Lines.line(startX + px * offset, startY + py * offset,
                       endX + px * offset, endY + py * offset);
            Lines.line(startX - px * offset, startY - py * offset,
                       endX - px * offset, endY - py * offset);
        
            // ---- 2. 绘制内线（效率低时变色） ----
            float warmup = hasPower ? this.warmup : 1f;
            Draw.color(warmup < 0.5f ? Pal.ammo : Pal.accent);
            Lines.stroke(1f);
            Lines.line(startX, startY, endX, endY);
        
            // ---- 3. 绘制两端端帽 ----
            Draw.color(Pal.accent);
            Lines.stroke(2f);
            float capLen = 4f;
            Lines.line(startX + px * capLen, startY + py * capLen,
                       startX - px * capLen, startY - py * capLen);
            Lines.line(endX + px * capLen, endY + py * capLen,
                       endX - px * capLen, endY - py * capLen);
        
            // ---- 4. 绘制流动箭头 ----
            Draw.color(Pal.accent);
            int arrows = Math.max(1, (int)(len / arrowSpacing));
            for(int i = 0; i < arrows; i++){
                float progress = (i / (float)arrows + time / arrowTimeScl) % 1f;
                float alpha = Mathf.absin(progress * arrows - time / arrowTimeScl, arrowPeriod, 1f);
                Draw.alpha(alpha * warmup * Renderer.bridgeOpacity);
                float ax = Mathf.lerp(startX, endX, progress);
                float ay = Mathf.lerp(startY, endY, progress);
                Fill.circle(ax, ay, 2f);
            }
        
            Draw.reset();
        }
        
        @Override
        public boolean acceptItem(Building source, Item item){
            return hasItems && team == source.team && items.total() < itemCapacity && checkAccept(source, world.tile(link));
        }
        
        protected boolean checkAccept(Building source, Tile link){
            if(tile == null || linked(source)) return true;

            if(linkValid(tile, link)){
                int rel = relativeTo(link);
                var facing = Edges.getFacingEdge(source, this);
                int rel2 = facing == null ? -1 : relativeTo(facing);
                return rel != rel2;
            }

            return false;
        }
        
        protected boolean linked(Building source){
            return source instanceof BridgeRouterBuild && linkValid(source.tile, tile) && ((BridgeRouterBuild)source).link == pos();
        }
        
        @Override
        public boolean canDump(Building to, Item item){
            return checkDump(to);
        }
        
        protected boolean checkDump(Building to){
            Tile other = world.tile(link);
            if(!linkValid(tile, other)){
                Tile edge = Edges.getFacingEdge(to.tile, tile);
                int i = relativeTo(edge.x, edge.y);

                for(int j = 0; j < incoming.size; j++){
                    int v = incoming.items[j];
                    if(relativeTo(Point2.x(v), Point2.y(v)) == i){
                        return false;
                    }
                }
                return true;
            }

            int rel = relativeTo(other.x, other.y);
            int rel2 = relativeTo(to.tileX(), to.tileY());

            return rel != rel2;
        }
        
        @Override
        public boolean shouldConsume(){
            return hadValidLink && enabled;
        }

        @Override
        public Point2 config(){
            return Point2.unpack(link).sub(tile.x, tile.y);
        }

        @Override
        public byte version(){
            return 1;
        }
        
        @Override
        public void write(Writes write){
            super.write(write);
            write.i(link);
            write.f(warmup);
            write.b(incoming.size);

            for(int i = 0; i < incoming.size; i++){
                write.i(incoming.items[i]);
            }

            write.bool(wasMoved || moved);
        }
        
        @Override
        public void read(Reads read, byte revision){
            super.read(read, revision);
            link = read.i();
            warmup = read.f();
            byte links = read.b();
            for(int i = 0; i < links; i++){
                incoming.add(read.i());
            }

            if(revision >= 1){
                wasMoved = moved = read.bool();
            }
        }
    }
}