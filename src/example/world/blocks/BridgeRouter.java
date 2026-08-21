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

        // 配置：相对坐标（单个连接）
        config(Point2.class, (BridgeRouterBuild tile, Point2 i) -> {
            int pos = Point2.pack(i.x + tile.tileX(), i.y + tile.tileY());
            if (!tile.links.contains(pos) && tile.links.size < BridgeRouterBuild.LINK_LIMIT) {
                tile.links.add(pos);
            }
        });

        // 配置：IntSeq（多个连接，用于蓝图/存档）
        config(IntSeq.class, (BridgeRouterBuild tile, IntSeq seq) -> {
            tile.links.clear();
            for (int j = 0; j < seq.size; j += 2) {
                int dx = seq.get(j);
                int dy = seq.get(j + 1);
                int pos = Point2.pack(dx + tile.tileX(), dy + tile.tileY());
                if (tile.links.size < BridgeRouterBuild.LINK_LIMIT) {
                    tile.links.add(pos);
                }
            }
        });
    }

    @Override
    public void setStats() {
        super.setStats();
        if (transportTime != 0f) {
            stats.add(Stat.itemsMoved, 10f / transportTime, StatUnit.itemsSecond);
        }
        stats.add(Stat.powerConnections, BridgeRouterBuild.LINK_LIMIT, StatUnit.none);
    }

    @Override
    public void drawPlace(int x, int y, int rotation, boolean valid) {
        super.drawPlace(x, y, rotation, valid);
        Drawf.dashCircle(x, y, range * tilesize, Pal.accent);
    }

    public boolean linkValid(Tile tile, Tile other) {
        if (other == tile) return false;
        return linkValid(tile, other, true);
    }

    public boolean linkValid(Tile tile, Tile other, boolean checkDouble) {
        if (other == null || tile == null || !positionsValid(tile.x, tile.y, other.x, other.y)) return false;

        if (checkDouble && other.build instanceof BridgeRouterBuild b && b.links.contains(tile.pos())) {
            return false;
        }

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
        public static final int LINK_LIMIT = 4;

        public IntSeq links = new IntSeq(false, LINK_LIMIT);
        public IntSeq incoming = new IntSeq(false, LINK_LIMIT);
        public float warmup;
        public float time = -8f, timeSpeed;
        public boolean wasMoved, moved, hadValidLink;
        public float transportCounter;

        @Override
        public void pickedUp() {
            links.clear();
        }

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

        @Override
        public void drawConfigure() {
            Drawf.select(x, y, tile.block().size * tilesize / 2f + 2f, Pal.accent);
            Drawf.dashCircle(x, y, range * tilesize, Pal.accent);

            int r = range + tilesize;
            for (int dx = -r; dx <= r; dx++) {
                for (int dy = -r; dy <= r; dy++) {
                    if (dx == 0 && dy == 0) continue;
                    if (dx * dx + dy * dy > r * r) continue;
                    Tile other = tile.nearby(dx, dy);
                    if (other == null) continue;
                    if (linkValid(tile, other)) {
                        if (incoming.contains(other.pos()) && !links.contains(other.pos())) continue;
                        boolean linked = links.contains(other.pos());
                        Drawf.select(other.drawx(), other.drawy(),
                            other.block().size * tilesize / 2f + 2f + (linked ? 0f : Mathf.absin(Time.time, 4f, 1f)),
                            linked ? Pal.place : Pal.breakInvalid);
                    }
                }
            }

            // 画所有主动连接
            for (int i = 0; i < links.size; i++) {
                Tile other = world.tile(links.get(i));
                if (linkValid(tile, other)) {
                    drawInput(other);
                }
            }
            // 画所有指向本建筑的
            for (int i = 0; i < incoming.size; i++) {
                Tile other = world.tile(incoming.get(i));
                if (linkValid(tile, other)) {
                    drawInput(other);
                }
            }

            Draw.reset();
        }

        @Override
        public boolean onConfigureBuildTapped(Building other) {
            if (other == this) {
                links.clear();
                return false;
            }

            // 反向连接：如果 other 指向本建筑
            if (other instanceof BridgeRouterBuild b && b.links.contains(pos())) {
                b.links.removeValue(pos());
                links.removeValue(other.pos());
                return false;
            }

            if (linkValid(tile, other.tile)) {
                int targetPos = other.pos();
                if (links.contains(targetPos)) {
                    links.removeValue(targetPos);
                } else {
                    if (links.size < LINK_LIMIT) {
                        links.add(targetPos);
                    }
                }
                return false;
            }
            return true;
        }

        public void checkIncoming() {
            for (int i = incoming.size - 1; i >= 0; i--) {
                int pos = incoming.get(i);
                Tile other = world.tile(pos);
                if (!linkValid(tile, other, false) || !((BridgeRouterBuild) other.build).links.contains(tile.pos())) {
                    incoming.removeIndex(i);
                }
            }
        }

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

            // 检查所有连接的有效性
            hadValidLink = false;
            for (int i = links.size - 1; i >= 0; i--) {
                int pos = links.get(i);
                Tile other = world.tile(pos);
                if (!linkValid(tile, other)) {
                    links.removeIndex(i);
                } else {
                    hadValidLink = true;
                    var inc = ((BridgeRouterBuild) other.build).incoming;
                    int myPos = tile.pos();
                    if (!inc.contains(myPos)) {
                        inc.add(myPos);
                    }
                }
            }

            if (!hadValidLink) {
                doDump();
                warmup = 0f;
            } else {
                warmup = Mathf.approachDelta(warmup, efficiency, 1f / 30f);
                // 遍历所有链接传输物品
                for (int i = 0; i < links.size; i++) {
                    Tile other = world.tile(links.get(i));
                    if (linkValid(tile, other)) {
                        updateTransport(other.build);
                    }
                }
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

            for (int idx = 0; idx < links.size; idx++) {
                Tile other = world.tile(links.get(idx));
                if (!linkValid(tile, other)) continue;
                if (Mathf.zero(Renderer.bridgeOpacity)) continue;

                float tx = tile.drawx(), ty = tile.drawy();
                float ox = other.drawx(), oy = other.drawy();

                float dx = ox - tx, dy = oy - ty;
                float length = Mathf.dst(dx, dy);
                if (length <= 0.001f) continue;

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

                Lines.line(outerStartX + nx * offset, outerStartY + ny * offset,
                           outerStartX - nx * offset, outerStartY - ny * offset);
                Lines.line(outerEndX + nx * offset, outerEndY + ny * offset,
                           outerEndX - nx * offset, outerEndY - ny * offset);

                Draw.z(Layer.blockOver + 0.02f);
                Draw.color(innerColor);
                Lines.stroke(4f);
                Lines.line(innerStartX, innerStartY, innerEndX, innerEndY);

                Draw.z(Layer.blockOver + 0.01f);
                float arrowLength = length - inset * 2f;
                int arrows = (int) (arrowLength / arrowSpacing);

                if (arrows > 0 && warmup > 0f) {
                    float angle = Angles.angle(dx, dy);
                    float rad = angle * Mathf.degRad;
                    Draw.color(outerColor);

                    for (int a = 0; a < arrows; a++) {
                        float dist = inset + a * arrowSpacing;
                        if (dist > length - inset - 3f) continue;

                        float px = tx + ux * dist, py = ty + uy * dist;
                        float timeFactor = (warmup > 0f) ? Time.time / arrowTimeScl : 0f;
                        float alpha = Mathf.absin(a - timeFactor, arrowPeriod, 1f);
                        if (alpha <= 0.01f) continue;

                        Draw.alpha(alpha * warmup * Renderer.bridgeOpacity);
                        float size = 2.4f;
                        Fill.tri(
                            px + Mathf.cos(rad) * size, py + Mathf.sin(rad) * size,
                            px + Mathf.cos(rad + Mathf.PI * 0.5f) * size, py + Mathf.sin(rad + Mathf.PI * 0.5f) * size,
                            px + Mathf.cos(rad - Mathf.PI * 0.5f) * size, py + Mathf.sin(rad - Mathf.PI * 0.5f) * size
                        );
                    }
                }
            }

            Draw.reset();
        }

        @Override
        public boolean acceptItem(Building source, Item item) {
            return hasItems && team == source.team && items.total() < itemCapacity && checkAccept(source);
        }

        protected boolean checkAccept(Building source) {
            if (tile == null || linked(source)) return true;

            for (int i = 0; i < links.size; i++) {
                Tile linkTile = world.tile(links.get(i));
                if (linkValid(tile, linkTile)) {
                    int rel = relativeTo(linkTile);
                    var facing = Edges.getFacingEdge(source, this);
                    int rel2 = facing == null ? -1 : relativeTo(facing);
                    if (rel != rel2) return true;
                }
            }
            return false;
        }

        protected boolean linked(Building source) {
            return source instanceof BridgeRouterBuild b && b.links.contains(pos());
        }

        @Override
        public boolean canDump(Building to, Item item) {
            return checkDump(to);
        }

        protected boolean checkDump(Building to) {
            for (int i = 0; i < links.size; i++) {
                Tile other = world.tile(links.get(i));
                if (linkValid(tile, other)) {
                    int rel = relativeTo(other.x, other.y);
                    int rel2 = relativeTo(to.tileX(), to.tileY());
                    if (rel != rel2) return true;
                }
            }
            return false;
        }

        @Override
        public boolean shouldConsume() {
            return !links.isEmpty() && enabled;
        }

        @Override
        public IntSeq config() {
            IntSeq out = new IntSeq(links.size * 2);
            for (int i = 0; i < links.size; i++) {
                Point2 p = Point2.unpack(links.get(i)).sub(tile.x, tile.y);
                out.add(p.x, p.y);
            }
            return out;
        }

        @Override
        public byte version() {
            return 2;
        }

        @Override
        public void write(Writes write) {
            super.write(write);
            write.b(links.size);
            for (int i = 0; i < links.size; i++) {
                write.i(links.get(i));
            }
            write.f(warmup);
            write.b(incoming.size);
            for (int i = 0; i < incoming.size; i++) {
                write.i(incoming.get(i));
            }
            write.bool(wasMoved || moved);
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            links.clear();
            int linkCount = read.b();
            for (int i = 0; i < linkCount; i++) {
                links.add(read.i());
            }
            warmup = read.f();
            incoming.clear();
            int incomingCount = read.b();
            for (int i = 0; i < incomingCount; i++) {
                incoming.add(read.i());
            }
            if (revision >= 1) {
                wasMoved = moved = read.bool();
            }
        }
    }
}