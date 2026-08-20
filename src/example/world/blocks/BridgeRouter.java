package example.world.blocks;

import arc.graphics.Color;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.struct.IntSeq;
import arc.util.*;
import arc.util.io.*;
import mindustry.entities.units.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.meta.*;
import static mindustry.Vars.*;

public class BridgeRouter extends Block {
    public final int timerCheckMoved = timers++;
    public int range = 5;
    public float transportTime = 1f;
    public float arrowSpacing = 4f;
    public float arrowPeriod = 0.8f;      // 增大使透明度变化更平滑
    public float arrowTimeScl = 4f;       // 流动速度，值小流动快
    private static final Color POWER_LOSS_COLOR = Color.valueOf("#f49fa680");
    private static final Color POWER_LOSS_INNER_COLOR = Color.valueOf("#ec767859");
    private static final Color LINE_COLOR_OUTER = Color.valueOf("#c0edf4");
    private static final Color LINE_COLOR_INNER = Color.valueOf("#a1d7ecb3");

    public BridgeRouter(String name) {
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
        if (transportTime != 0f) {
            stats.add(Stat.itemsMoved, 60f / transportTime, StatUnit.itemsSecond);
        }
    }

    @Override
    public void drawPlace(int x, int y, int rotation, boolean valid) {
        super.drawPlace(x, y, rotation, valid);
        Drawf.dashCircle(x, y, range * tilesize, Pal.accent);
    }

    public boolean linkValid(Tile tile, Tile other) {
        return linkValid(tile, other, true);
    }

    public boolean linkValid(Tile tile, Tile other, boolean checkDouble) {
        if (other == null || tile == null || !positionsValid(tile.x, tile.y, other.x, other.y)) return false;
        return ((other.block() == tile.block() && tile.block() == this) || (!(tile.block() instanceof BridgeRouter) && other.block() == this))
                && (other.team() == tile.team() || tile.block() != this);
    }

    public boolean positionsValid(int x1, int y1, int x2, int y2) {
        int dx = x1 - x2;
        int dy = y1 - y2;
        return (dx * dx + dy * dy) <= (range * range + tilesize);
    }

    @Override
    public void init() {
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

        // ---------- 冻结相关 ----------
        public boolean frozen = false;
        public float frozenTimeFactor = 0f;
        public float frozenWarmup = 1f;

        @Override
        public void pickedUp() {
            link = -1;
        }

        // ... (drawInput, drawConfigure, onConfigureBuildTapped, checkIncoming 保持不变，省略以节省篇幅) ...
        // 这些方法您已有，完全保留不动，此处略写

        @Override
        public void updateTile() {
            noSleep();
            if (timer(timerCheckMoved, 30f)) {
                wasMoved = moved;
                moved = false;
            }

            timeSpeed = Mathf.approachDelta(timeSpeed, wasMoved ? 1f : 0f, 1f / 60f);
            time += timeSpeed * delta();
            checkIncoming();
            Tile other = world.tile(link);
            hadValidLink = linkValid(tile, other);
            if (!hadValidLink) {
                doDump();
                warmup = 0f;
            } else {
                var inc = ((BridgeRouterBuild) other.build).incoming;
                int pos = tile.pos();
                if (!inc.contains(pos)) {
                    inc.add(pos);
                }
                warmup = Mathf.approachDelta(warmup, efficiency, 1f / 30f);
                updateTransport(other.build);
            }

            // ---------- 冻结逻辑 ----------
            boolean hasItems = items.total() > 0 && hadValidLink && enabled;
            if (hasItems) {
                frozen = false;
            } else if (!frozen) {
                frozen = true;
                frozenTimeFactor = Time.time / arrowTimeScl;
                frozenWarmup = warmup;
            }
        }

        public void doDump() {
            dumpAccumulate();
        }

        public void updateTransport(Building other) {
            transportCounter += edelta();
            while (transportCounter >= transportTime) {
                Item item = items.take();
                if (item != null && other.acceptItem(this, item)) {
                    other.handleItem(this, item);
                    moved = true;
                } else if (item != null) {
                    items.add(item, 1);
                    items.undoFlow(item);
                }
                transportCounter -= transportTime;
            }
        }

        @Override
        public void draw() {
            super.draw();

            Tile other = world.tile(link);
            if (!linkValid(tile, other)) return;
            if (Mathf.zero(Renderer.bridgeOpacity)) return;

            float tx = tile.drawx(), ty = tile.drawy();
            float ox = other.drawx(), oy = other.drawy();
            float dx = ox - tx, dy = oy - ty;
            float length = Mathf.dst(dx, dy);
            if (length <= 0.001f) return;

            float ux = dx / length, uy = dy / length;
            float nx = -uy, ny = ux;

            float offset = 2f, inset = 4f, extend = 1.5f;

            float innerStartX = tx + ux * inset, innerStartY = ty + uy * inset;
            float innerEndX = ox - ux * inset, innerEndY = oy - uy * inset;
            float outerStartX = innerStartX - ux * extend, outerStartY = innerStartY - uy * extend;
            float outerEndX = innerEndX + ux * extend, outerEndY = innerEndY + uy * extend;

            float warmup = hasPower ? this.warmup : 1f;
            float powerLoss = 1f - warmup;

            Color outerColor = Tmp.c1.set(LINE_COLOR_OUTER).lerp(POWER_LOSS_COLOR, powerLoss);
            Color innerColor = Tmp.c2.set(LINE_COLOR_INNER).lerp(POWER_LOSS_INNER_COLOR, powerLoss);

            Draw.alpha(Renderer.bridgeOpacity);

            // ----- 绘制线条 -----
            Draw.color(outerColor);
            Lines.stroke(1f);
            Lines.line(outerStartX + nx * offset, outerStartY + ny * offset, outerEndX + nx * offset, outerEndY + ny * offset);
            Lines.line(outerStartX - nx * offset, outerStartY - ny * offset, outerEndX - nx * offset, outerEndY - ny * offset);

            Draw.color(innerColor);
            Lines.stroke(4f);
            Lines.line(innerStartX, innerStartY, innerEndX, innerEndY);

            Draw.color(outerColor);
            Lines.stroke(1f);
            Lines.line(outerStartX + nx * offset, outerStartY + ny * offset, outerStartX - nx * offset, outerStartY - ny * offset);
            Lines.line(outerEndX + nx * offset, outerEndY + ny * offset, outerEndX - nx * offset, outerEndY - ny * offset);

            // ----- 流动箭头（透明度波浪，位置固定） -----
            int arrows = (int) (length / arrowSpacing);
            if (arrows > 0) {
                boolean hasItems = items.total() > 0 && hadValidLink && enabled;

                // 有物品用实时值，无物品用冻结值
                float timeFactor = hasItems ? Time.time / arrowTimeScl : frozenTimeFactor;
                float effectiveWarmup = hasItems ? warmup : frozenWarmup;

                float angle = Angles.angle(dx, dy);
                float rad = angle * Mathf.degRad;

                Draw.color(outerColor);

                for (int a = 0; a < arrows; a++) {
                    float px = tx + ux * (inset + a * arrowSpacing);
                    float py = ty + uy * (inset + a * arrowSpacing);

                    // 透明度正弦波，每个箭头相位不同，整体随时间移动，形成流动感
                    float alpha = Mathf.absin(a - timeFactor, arrowPeriod, 1f);
                    if (alpha <= 0.01f) continue;

                    float displayAlpha = alpha * effectiveWarmup;
                    Draw.alpha(displayAlpha * Renderer.bridgeOpacity);

                    float size = 2.4f;
                    Fill.tri(
                            px + Mathf.cos(rad) * size, py + Mathf.sin(rad) * size,
                            px + Mathf.cos(rad + Mathf.PI * 0.5f) * size, py + Mathf.sin(rad + Mathf.PI * 0.5f) * size,
                            px + Mathf.cos(rad - Mathf.PI * 0.5f) * size, py + Mathf.sin(rad - Mathf.PI * 0.5f) * size
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