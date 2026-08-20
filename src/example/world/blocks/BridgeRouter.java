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
        allowDiagonal = true;
        copyConfig = false;
        allowConfigInventory = false;
        ignoreResizeConfig = true;
        priority = TargetPriority.transport;
        delayLandingConfig = true;

        // 单连接配置（兼容旧版）
        config(Point2.class, (BridgeRouterBuild tile, Point2 i) -> {
            tile.links.clear();
            tile.links.add(Point2.pack(i.x + tile.tileX(), i.y + tile.tileY()));
        });
        config(Integer.class, (BridgeRouterBuild tile, Integer i) -> {
            if(i == -1) {
                tile.links.clear();
            } else {
                tile.links.clear();
                tile.links.add(i);
            }
        });
        // 多连接配置（用于蓝图/批量）
        config(IntSeq.class, (BridgeRouterBuild tile, IntSeq seq) -> {
            tile.links.clear();
            for(int j = 0; j < seq.size; j += 2){
                int dx = seq.get(j);
                int dy = seq.get(j + 1);
                int pos = Point2.pack(dx + tile.tileX(), dy + tile.tileY());
                tile.links.add(pos);
            }
        });
    }

    @Override
    public void setStats() {
        super.setStats();
        if(transportTime != 0f){
            stats.add(Stat.itemsMoved, 10f / transportTime, StatUnit.itemsSecond);
        }
    }

    @Override
    public void drawPlace(int x, int y, int rotation, boolean valid){
        super.drawPlace(x, y, rotation, valid);
        Drawf.dashCircle(x, y, range * tilesize, Pal.accent);
    }

    public boolean linkValid(Tile tile, Tile other){
        if(other == tile) return false;
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

    // ==================== 内部类 ====================
    public class BridgeRouterBuild extends Building {
        // 主动连接列表（支持多个）
        public IntSeq links = new IntSeq();
        // 被动连接列表（指向本建筑的其他桥）
        public IntSeq incoming = new IntSeq(false, 4);
        public float warmup;
        public float time = -8f, timeSpeed;
        public boolean wasMoved, moved, hadValidLink;
        public float transportCounter;

        @Override
        public void pickedUp() {
            links.clear();
        }

        // ---------- 绘制单条桥间线（配置模式用） ----------
        private void drawInput(Tile other) {
            if (!linkValid(tile, other, false)) return;
            boolean linked = links.contains(other.pos());

            Tmp.v2.trns(tile.angleTo(other), 2f);
            float tx = tile.drawx(), ty = tile.drawy();
            float ox = other.drawx(), oy = other.drawy();
            float alpha = Math.abs((linked ? 100 : 0) - (Time.time * 2f) % 100f) / 100f;
            float x = Mathf.lerp(ox, tx, alpha);
            float y = Mathf.lerp(oy, ty, alpha);

            Tile otherLink = linked ? other : tile;
            float arrowDx = otherLink.drawx() - x;
            float arrowDy = otherLink.drawy() - y;
            float arrowAngle = Angles.angle(arrowDx, arrowDy);

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
            Draw.rect("bridge-arrow", x, y, arrowAngle);
            Draw.mixcol();
        }

        // ---------- 配置模式绘制 ----------
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
                        boolean linked = links.contains(other.pos());
                        // 如果其他建筑指向本建筑但本建筑未主动连接，跳过显示（避免干扰）
                        if(!linked && incoming.contains(other.pos())) continue;
                        Drawf.select(other.drawx(), other.drawy(),
                            other.block().size * tilesize / 2f + 2f + (linked ? 0f : Mathf.absin(Time.time, 4f, 1f)),
                            linked ? Pal.place : Pal.breakInvalid);
                    }
                }
            }

            // 绘制所有已连接的桥间线
            for(int i = 0; i < links.size; i++){
                Tile other = world.tile(links.get(i));
                if(other != null) drawInput(other);
            }
            // 绘制所有被动连接的桥间线
            incoming.each(pos -> {
                Tile other = world.tile(pos);
                if(other != null) drawInput(other);
            });

            Draw.reset();
        }

        // ---------- 配置点击交互 ----------
        @Override
        public boolean onConfigureBuildTapped(Building other) {
            if(other == this){
                links.clear();  // 点击自身清空所有连接
                return false;
            }

            if(other instanceof BridgeRouterBuild b && linkValid(tile, other.tile)){
                int pos = other.pos();
                if(links.contains(pos)){
                    links.removeValue(pos);  // 已连接则移除
                }else{
                    links.add(pos);          // 未连接则添加
                }
                return false;
            }
            return true;
        }

        // ---------- 清理 incoming ----------
        public void checkIncoming(){
            int idx = 0;
            while(idx < incoming.size){
                int i = incoming.items[idx];
                Tile other = world.tile(i);
                if(other == null || !linkValid(tile, other, false) || !((BridgeRouterBuild)other.build).links.contains(tile.pos())){
                    incoming.removeIndex(idx);
                    idx --;
                }
                idx ++;
            }
        }

        // ---------- 更新逻辑 ----------
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

            // 遍历所有主动连接，执行传输
            hadValidLink = false;
            for(int i = 0; i < links.size; i++){
                Tile other = world.tile(links.get(i));
                if(linkValid(tile, other)){
                    hadValidLink = true;
                    var targetBuild = (BridgeRouterBuild)other.build;
                    // 将本建筑加入目标的 incoming（用于反向显示）
                    if(!targetBuild.incoming.contains(tile.pos())){
                        targetBuild.incoming.add(tile.pos());
                    }
                    warmup = Mathf.approachDelta(warmup, 1f, 1f / 30f);
                    updateTransport(other.build);
                }else{
                    // 无效连接移除
                    links.removeIndex(i);
                    i--;
                }
            }

            if(!hadValidLink){
                doDump();
                warmup = 0f;
            }
        }

        public void doDump(){
            dumpAccumulate();
        }

        // ---------- 单次传输（对单个目标） ----------
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

        // ---------- 绘制主连接（遍历所有连接） ----------
        @Override
        public void draw(){
            super.draw();
            if(links.isEmpty()) return;

            for(int idx = 0; idx < links.size; idx++){
                Tile other = world.tile(links.get(idx));
                if(other == null || !linkValid(tile, other)) continue;
                drawConnection(other);
            }
        }

        // ---------- 绘制单条连接（抽取出独立方法） ----------
        private void drawConnection(Tile other){
            if(Mathf.zero(Renderer.bridgeOpacity)) return;

            float tx = tile.drawx(), ty = tile.drawy();
            float ox = other.drawx(), oy = other.drawy();

            float dx = ox - tx, dy = oy - ty;
            float length = Mathf.dst(dx, dy);
            if(length <= 0.001f) return;

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
            Draw.z(Layer.blockOver + 0.03f);

            Draw.color(outerColor);
            Lines.stroke(1f);

            Lines.line(outerStartX + nx * offset, outerStartY + ny * offset,
                       outerEndX + nx * offset, outerEndY + ny * offset);
            Lines.line(outerStartX - nx * offset, outerStartY - ny * offset,
                       outerEndX - nx * offset, outerEndY - ny * offset);

            // 端帽
            Lines.line(outerStartX + nx * offset, outerStartY + ny * offset,
                       outerStartX - nx * offset, outerStartY - ny * offset);
            Lines.line(outerEndX + nx * offset, outerEndY + ny * offset,
                       outerEndX - nx * offset, outerEndY - ny * offset);

            Draw.z(Layer.blockOver + 0.02f);
            Draw.color(innerColor);
            Lines.stroke(4f);
            Lines.line(innerStartX, innerStartY, innerEndX, innerEndY);

            // 箭头
            Draw.z(Layer.blockOver + 0.01f);
            float arrowLength = length - inset * 2f;
            int arrows = (int)(arrowLength / arrowSpacing);
            if(arrows > 0 && warmup > 0f){
                float angle = Angles.angle(dx, dy);
                float rad = angle * Mathf.degRad;
                Draw.color(outerColor);
                for(int a = 0; a < arrows; a++){
                    float dist = inset + a * arrowSpacing;
                    if(dist > length - inset - 3f) continue;
                    float px = tx + ux * dist, py = ty + uy * dist;
                    float timeFactor = (warmup > 0f) ? Time.time / arrowTimeScl : 0f;
                    float alpha = Mathf.absin(a - timeFactor, arrowPeriod, 1f);
                    if(alpha <= 0.01f) continue;
                    float displayAlpha = (warmup > 0f) ? alpha * warmup : alpha;
                    Draw.alpha(displayAlpha * Renderer.bridgeOpacity);
                    float size = 2.4f;
                    Fill.tri(
                        px + Mathf.cos(rad) * size, py + Mathf.sin(rad) * size,
                        px + Mathf.cos(rad + Mathf.PI * 0.5f) * size, py + Mathf.sin(rad + Mathf.PI * 0.5f) * size,
                        px + Mathf.cos(rad - Mathf.PI * 0.5f) * size, py + Mathf.sin(rad - Mathf.PI * 0.5f) * size
                    );
                }
            }
        }

        // ---------- 物品接受 ----------
        @Override
        public boolean acceptItem(Building source, Item item){
            if(links.size > 0 && source != this){
                return hasItems && team == source.team && items.total() < itemCapacity && checkAccept(source, world.tile(links.get(0)));
            }
            return false;
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
            return source instanceof BridgeRouterBuild && linkValid(source.tile, tile) && ((BridgeRouterBuild)source).links.contains(tile.pos());
        }

        // ---------- 倾倒 ----------
        @Override
        public boolean canDump(Building to, Item item){
            return checkDump(to);
        }

        protected boolean checkDump(Building to){
            Tile other = world.tile(links.isEmpty() ? -1 : links.get(0));
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

        // ---------- 配置/存档 ----------
        @Override
        public Point2 config(){
            if(links.size > 0){
                int pos = links.get(0);
                return Point2.unpack(pos).sub(tile.x, tile.y);
            }
            return new Point2(0, 0);
        }

        @Override
        public byte version(){
            return 2;
        }

        @Override
        public void write(Writes write){
            super.write(write);
            write.s(links.size);
            for(int i = 0; i < links.size; i++){
                write.i(links.get(i));
            }
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
            links.clear();
            if(revision >= 2){
                int size = read.s();
                for(int i = 0; i < size; i++){
                    links.add(read.i());
                }
            }else{
                // 旧版本单连接兼容
                int oldLink = read.i();
                if(oldLink != -1) links.add(oldLink);
            }
            warmup = read.f();
            byte incSize = read.b();
            incoming.clear();
            for(int i = 0; i < incSize; i++){
                incoming.add(read.i());
            }
            if(revision >= 1){
                wasMoved = moved = read.bool();
            }
        }
    }
}