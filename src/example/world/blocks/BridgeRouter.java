package example.world.blocks;

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

public class BridgeRouter extends Block {
    public final int timerCheckMoved = timers ++;
    public int range = 5;
    public float transportTime = 1f;
    public float arrowSpacing = 4f, arrowPeriod = 0.4f;
    public float arrowTimeScl = 6.2f;
    private static final Color POWER_LOSS_COLOR = Color.valueOf("#f49fa680");
    private static final Color POWER_LOSS_INNER_COLOR = Color.valueOf("#ec767838");
    private static final Color LINE_COLOR_OUTER = Color.valueOf("#c0edf4");
    private static final Color LINE_COLOR_INNER = Color.valueOf("#a1d7ec80");

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
        Drawf.dashCircle(x, y, range * tilesize, Pal.accent);
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
        return (dx * dx + dy * dy) <= (range * range + tilesize);
    }
    
    @Override
    public void init(){
        super.init();
        updateClipRadius((range + 0.5f) * tilesize);
    }
    
    public class BridgeRouterBuild extends Building {
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
            int r = range + tilesize;
            for(int dx = -r; dx <= r; dx++){
                for(int dy = -r; dy <= r; dy++){
                    if(dx == 0 && dy == 0) continue;
                    if(dx*dx + dy*dy > r*r) continue;
                    Tile other = tile.nearby(dx, dy);
                    if(other == null) continue;
                    if(linkValid(tile, other)){
                        if(incoming.contains(other.pos()) && other.pos() != link) continue;
                        boolean linked = other.pos() == link;
                        Drawf.select(other.drawx(), other.drawy(),
                            other.block().size * tilesize / 2f + 2f + (linked ? 0f : Mathf.absin(Time.time, 4f, 1f)),
                            linked ? Pal.place : Pal.breakInvalid);
                    }
                }
            }
            if (linkValid(tile, world.tile(link))) {
                drawInput(world.tile(link));
            }
            incoming.each(pos -> drawInput(world.tile(pos)));
            Draw.reset();
        }
        
        @Override
        public boolean onConfigureBuildTapped(Building other){
            //reverse connection
            if(other instanceof BridgeRouterBuild b && b.link == pos()){
                configure(other.pos());
                other.configure(-1);
                return false;
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
            noSleep();
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

    float tx = tile.drawx();
    float ty = tile.drawy();

    float ox = other.drawx();
    float oy = other.drawy();

    float dx = ox - tx;
    float dy = oy - ty;
    float length = Mathf.dst(dx, dy);

    if(length <= 0.001f) return;

    //单位方向
    float ux = dx / length;
    float uy = dy / length;

    //法线方向
    float nx = -uy;
    float ny = ux;

    //========================================
    // 基础参数
    //========================================

    float offset = 2f;
    float inset = 4f;
    float extend = 1.5f;

    //========================================
    // 计算内线
    //========================================

    float innerStartX = tx + ux * inset;
    float innerStartY = ty + uy * inset;

    float innerEndX = ox - ux * inset;
    float innerEndY = oy - uy * inset;

    //========================================
    // 计算外线
    //========================================

    float outerStartX = innerStartX - ux * extend;
    float outerStartY = innerStartY - uy * extend;

    float outerEndX = innerEndX + ux * extend;
    float outerEndY = innerEndY + uy * extend;

    //========================================
    // Warmup / 颜色平滑过渡
    //========================================

    float warmup = hasPower ? this.warmup : 1f;

    // warmup:
    // 1 -> 正常
    // 0 -> 掉电
    float powerLoss = 1f - warmup;

    Color outerColor = Tmp.c1.set(LINE_COLOR_OUTER)
        .lerp(POWER_LOSS_COLOR, powerLoss);

    Color innerColor = Tmp.c2.set(LINE_COLOR_INNER)
        .lerp(POWER_LOSS_INNER_COLOR, powerLoss);

    // 统一线条透明度（受全局控制）
    Draw.alpha(Renderer.bridgeOpacity);
    Draw.z(Layer.blockOver);

    //========================================
    // 外层双线
    //========================================

    Draw.color(outerColor);
    Lines.stroke(1f);

    Lines.line(
        outerStartX + nx * offset,
        outerStartY + ny * offset,
        outerEndX + nx * offset,
        outerEndY + ny * offset
    );

    Lines.line(
        outerStartX - nx * offset,
        outerStartY - ny * offset,
        outerEndX - nx * offset,
        outerEndY - ny * offset
    );

    //========================================
    // 内部主线
    //========================================

    Draw.color(innerColor);
    Lines.stroke(4f);

    Lines.line(
        innerStartX,
        innerStartY,
        innerEndX,
        innerEndY
    );

    //========================================
    // 两端端帽
    //========================================

    Draw.color(outerColor);
    Lines.stroke(1f);

    //起点端帽
    Lines.line(
        outerStartX + nx * offset,
        outerStartY + ny * offset,
        outerStartX - nx * offset,
        outerStartY - ny * offset
    );

    //终点端帽
    Lines.line(
        outerEndX + nx * offset,
        outerEndY + ny * offset,
        outerEndX - nx * offset,
        outerEndY - ny * offset
    );

    //========================================
    // 流动箭头（未启用时静止）
    //========================================

    Draw.z(Layer.blockOver);
    float arrowLength = length - inset * 2f;
    int arrows = (int)(arrowLength / arrowSpacing);

    if(arrows > 0 && warmup > 0f){
        float angle = Angles.angle(dx, dy);
        float rad = angle * Mathf.degRad;

        Draw.color(outerColor);

        for(int a = 0; a < arrows; a++){

float dist = inset + a * arrowSpacing;

if(dist > length - inset - 3f){
    continue;
}

float px = tx + ux * dist;
float py = ty + uy * dist;

            // ★ 当 warmup <= 0.01 时，固定时间，使箭头静止 ★
            float timeFactor = (warmup > 0f) ? Time.time / arrowTimeScl : 0f;

            float alpha = Mathf.absin(
                a - timeFactor,
                arrowPeriod,
                1f
            );

            if(alpha <= 0.01f) continue;

            float displayAlpha = (warmup > 0f) ? alpha * warmup : alpha;
            Draw.alpha(displayAlpha * Renderer.bridgeOpacity);

            float size = 2.4f;

            Fill.tri(
                px + Mathf.cos(rad) * size,
                py + Mathf.sin(rad) * size,

                px + Mathf.cos(rad + Mathf.PI * 0.5f) * size,
                py + Mathf.sin(rad + Mathf.PI * 0.5f) * size,

                px + Mathf.cos(rad - Mathf.PI * 0.5f) * size,
                py + Mathf.sin(rad - Mathf.PI * 0.5f) * size
            );
        }
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