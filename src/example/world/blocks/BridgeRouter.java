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
    public int maxLinks = 4; // 最大连接数

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
        // 配置使用 IntSeq 存储多个连接
        config(IntSeq.class, (BridgeRouterBuild tile, IntSeq seq) -> {
            tile.links = seq;
            // 同步更新目标的 incoming
            tile.syncIncoming();
        });
    }

    @Override
    public void setStats() {
        super.setStats();
        if(transportTime != 0f){
            stats.add(Stat.itemsMoved, 10f / transportTime, StatUnit.itemsSecond);
        }
        stats.add(Stat.powerConnections, maxLinks, StatUnit.none);
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

    public class BridgeRouterBuild extends Building {
        public IntSeq links = new IntSeq(false, 4);
        public IntSeq incoming = new IntSeq(false, 4);
        public float warmup;
        public float time = -8f, timeSpeed;
        public boolean wasMoved, moved, hadValidLink;
        public float transportCounter;
        public int sendIndex = 0; // 轮询发送索引

        @Override
        public void pickedUp() {
            links.clear();
            incoming.clear();
        }

        // 同步 incoming：将自己的坐标加入所有 link 目标的 incoming，并从不是自己的目标中移除
        public void syncIncoming(){
            // 先清除所有目标的 incoming 中自己的记录
            for(int i = 0; i < links.size; i++){
                int pos = links.get(i);
                Tile t = world.tile(pos);
                if(t != null && t.build instanceof BridgeRouterBuild other){
                    other.incoming.removeValue(tile.pos());
                }
            }
            // 再重新添加
            for(int i = 0; i < links.size; i++){
                int pos = links.get(i);
                Tile t = world.tile(pos);
                if(t != null && t.build instanceof BridgeRouterBuild other){
                    if(!other.incoming.contains(tile.pos())){
                        other.incoming.add(tile.pos());
                    }
                }
            }
        }

        private void drawInput(Tile other) {
            if (!linkValid(tile, other, false)) return;
            // 画单条连接线（与原来一致）
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
                        // 如果该格子指向本建筑（incoming）且本建筑未主动连接它，则跳过红框
                        if(incoming.contains(other.pos()) && !links.contains(other.pos())) continue;
                        boolean linked = links.contains(other.pos());
                        Drawf.select(other.drawx(), other.drawy(),
                            other.block().size * tilesize / 2f + 2f + (linked ? 0f : Mathf.absin(Time.time, 4f, 1f)),
                            linked ? Pal.place : Pal.breakInvalid);
                    }
                }
            }
            // 画所有主动连接的线
            for(int i = 0; i < links.size; i++){
                Tile t = world.tile(links.get(i));
                if(t != null) drawInput(t);
            }
            // 画所有被动连接的线
            incoming.each(pos -> {
                Tile t = world.tile(pos);
                if(t != null) drawInput(t);
            });
            Draw.reset();
        }

        @Override
        public boolean onConfigureBuildTapped(Building other){
            if(other == this){
                // 点击自己：清空所有连接
                configure(new IntSeq()); // 清空
                return false;
            }

            if(!(other instanceof BridgeRouterBuild)) return true;

            BridgeRouterBuild target = (BridgeRouterBuild) other;

            // 如果目标指向自己（被动），且自己未连接它，忽略点击（阻止反向跳转）
            if(target.links.contains(tile.pos()) && !links.contains(target.tile.pos())){
                return false;
            }

            // 切换连接
            int targetPos = target.tile.pos();
            if(links.contains(targetPos)){
                // 移除连接
                links.removeValue(targetPos);
                // 从目标的 incoming 中移除自己
                target.incoming.removeValue(tile.pos());
            } else {
                if(links.size >= maxLinks) return false; // 达到上限
                links.add(targetPos);
                // 将自己加入目标的 incoming
                if(!target.incoming.contains(tile.pos())){
                    target.incoming.add(tile.pos());
                }
            }
            // 同步
            configure(links); // 触发保存
            return false;
        }

        public void checkIncoming(){
            int idx = 0;
            while(idx < incoming.size){
                int i = incoming.get(idx);
                Tile other = world.tile(i);
                if(other == null || !linkValid(tile, other, false) || !((BridgeRouterBuild)other.build).links.contains(tile.pos())){
                    incoming.removeIndex(idx);
                } else {
                    idx++;
                }
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

            // 清理无效连接
            for(int i = links.size - 1; i >= 0; i--){
                Tile other = world.tile(links.get(i));
                if(other == null || !linkValid(tile, other)){
                    links.removeIndex(i);
                }
            }

            hadValidLink = !links.isEmpty();

            if(!hadValidLink){
                doDump();
                warmup = 0f;
                return;
            }

            // 同步 incoming（确保所有目标都有自己的坐标）
            for(int i = 0; i < links.size; i++){
                Tile other = world.tile(links.get(i));
                if(other != null && other.build instanceof BridgeRouterBuild target){
                    if(!target.incoming.contains(tile.pos())){
                        target.incoming.add(tile.pos());
                    }
                }
            }

            warmup = Mathf.approachDelta(warmup, efficiency, 1f / 30f);

            // 轮询发送
            if(!links.isEmpty() && efficiency > 0){
                if(sendIndex >= links.size) sendIndex = 0;
                int start = sendIndex;
                boolean sent = false;
                do {
                    int pos = links.get(sendIndex);
                    Tile targetTile = world.tile(pos);
                    if(targetTile != null && targetTile.build instanceof BridgeRouterBuild target){
                        if(linkValid(tile, targetTile)){
                            // 尝试发送物品
                            if(items.total() > 0){
                                Item item = items.first();
                                int amount = 1;
                                if(target.items.total() < target.block.itemCapacity && target.acceptItem(this, item)){
                                    target.handleItem(this, item);
                                    items.remove(item, amount);
                                    moved = true;
                                    sent = true;
                                }
                            }
                        }
                    }
                    sendIndex = (sendIndex + 1) % links.size;
                    if(sent || items.total() == 0) break;
                } while(sendIndex != start);
            }
        }

        public void doDump(){
            dumpAccumulate();
        }

        @Override
        public void draw(){
            super.draw();

            if(links.isEmpty()) return;
            if(Mathf.zero(Renderer.bridgeOpacity)) return;

            // 遍历所有连接绘制
            for(int linkIdx = 0; linkIdx < links.size; linkIdx++){
                int pos = links.get(linkIdx);
                Tile other = world.tile(pos);
                if(other == null || !linkValid(tile, other)) continue;

                float tx = tile.drawx();
                float ty = tile.drawy();
                float ox = other.drawx();
                float oy = other.drawy();
                float dx = ox - tx;
                float dy = oy - ty;
                float length = Mathf.dst(dx, dy);
                if(length <= 0.001f) continue;

                float ux = dx / length;
                float uy = dy / length;
                float nx = -uy;
                float ny = ux;

                float offset = 2f;
                float inset = 4f;
                float extend = 1.5f;

                float innerStartX = tx + ux * inset;
                float innerStartY = ty + uy * inset;
                float innerEndX = ox - ux * inset;
                float innerEndY = oy - uy * inset;

                float outerStartX = innerStartX - ux * extend;
                float outerStartY = innerStartY - uy * extend;
                float outerEndX = innerEndX + ux * extend;
                float outerEndY = innerEndY + uy * extend;

                float warmup = hasPower ? this.warmup : 1f;
                float powerLoss = 1f - warmup;

                Color outerColor = Tmp.c1.set(LINE_COLOR_OUTER).lerp(POWER_LOSS_COLOR, powerLoss);
                Color innerColor = Tmp.c2.set(LINE_COLOR_INNER).lerp(POWER_LOSS_INNER_COLOR, powerLoss);

                Draw.alpha(Renderer.bridgeOpacity);
                Draw.z(Layer.blockOver + 0.03f);

                Draw.color(outerColor);
                Lines.stroke(1f);
                Lines.line(outerStartX + nx * offset, outerStartY + ny * offset, outerEndX + nx * offset, outerEndY + ny * offset);
                Lines.line(outerStartX - nx * offset, outerStartY - ny * offset, outerEndX - nx * offset, outerEndY - ny * offset);

                Draw.color(outerColor);
                Lines.stroke(1f);
                Lines.line(outerStartX + nx * offset, outerStartY + ny * offset, outerStartX - nx * offset, outerStartY - ny * offset);
                Lines.line(outerEndX + nx * offset, outerEndY + ny * offset, outerEndX - nx * offset, outerEndY - ny * offset);

                Draw.z(Layer.blockOver + 0.02f);
                Draw.color(innerColor);
                Lines.stroke(4f);
                Lines.line(innerStartX, innerStartY, innerEndX, innerEndY);

                // 流动箭头
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
                        float px = tx + ux * dist;
                        float py = ty + uy * dist;
                        float timeFactor = Time.time / arrowTimeScl;
                        float alpha = Mathf.absin(a - timeFactor, arrowPeriod, 1f);
                        if(alpha <= 0.01f) continue;
                        Draw.alpha(alpha * warmup * Renderer.bridgeOpacity);
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
            }
            Draw.reset();
        }

        @Override
        public boolean acceptItem(Building source, Item item){
            // 只有当连接非空且源头是连接的建筑之一时才接受？
            // 原版允许从任何有效源接受，但只发给自己的 links
            return hasItems && team == source.team && items.total() < itemCapacity && checkAccept(source, world.tile(links.isEmpty() ? -1 : links.get(0)));
        }

        protected boolean checkAccept(Building source, Tile link){
            // 简化版，只检查链接是否有效
            if(tile == null || linked(source)) return true;
            if(link != null && linkValid(tile, link)){
                return true;
            }
            return false;
        }

        protected boolean linked(Building source){
            if(!(source instanceof BridgeRouterBuild)) return false;
            BridgeRouterBuild other = (BridgeRouterBuild) source;
            return other.links.contains(tile.pos());
        }

        @Override
        public boolean canDump(Building to, Item item){
            return checkDump(to);
        }

        protected boolean checkDump(Building to){
            // 允许向任何有效的目标倾倒（用于物品溢出）
            return true;
        }

        @Override
        public boolean shouldConsume(){
            return !links.isEmpty() && enabled;
        }

        @Override
        public IntSeq config(){
            return links;
        }

        @Override
        public byte version(){
            return 2; // 更新版本号
        }

        @Override
        public void write(Writes write){
            super.write(write);
            write.i(links.size);
            for(int i = 0; i < links.size; i++){
                write.i(links.get(i));
            }
            write.f(warmup);
            write.b(incoming.size);
            for(int i = 0; i < incoming.size; i++){
                write.i(incoming.get(i));
            }
            write.bool(wasMoved || moved);
        }

        @Override
        public void read(Reads read, byte revision){
            super.read(read, revision);
            links.clear();
            int size = read.i();
            if(size > maxLinks) size = maxLinks;
            for(int i = 0; i < size; i++){
                links.add(read.i());
            }
            warmup = read.f();
            incoming.clear();
            int incSize = read.b();
            for(int i = 0; i < incSize; i++){
                incoming.add(read.i());
            }
            if(revision >= 1){
                wasMoved = moved = read.bool();
            }
        }
    }
}