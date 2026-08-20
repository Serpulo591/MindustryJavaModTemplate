package example.world.blocks;

import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.math.geom.*;
import arc.struct.*;
import arc.util.*;
import arc.util.io.*;
import mindustry.core.*;
import mindustry.entities.*;
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
    public float arrowSpacing = 4f, arrowPeriod = 0.4f;
    public float arrowTimeScl = 6.2f;
    private static final Color POWER_LOSS_COLOR = Color.valueOf("#f49fa680");
    private static final Color POWER_LOSS_INNER_COLOR = Color.valueOf("#ec767838");
    private static final Color LINE_COLOR_OUTER = Color.valueOf("#c0edf4");
    private static final Color LINE_COLOR_INNER = Color.valueOf("#a1d7ec80");
    public static final int LINK_LIMIT = 4;

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
        allowDiagonal = true;
        copyConfig = false;
        allowConfigInventory = false;
        ignoreResizeConfig = true;
        priority = TargetPriority.transport;
        delayLandingConfig = true;

        config(Point2.class, (BridgeRouterBuild tile, Point2 i) -> {
            int pos = Point2.pack(i.x + tile.tileX(), i.y + tile.tileY());
            if (!tile.links.contains(pos) && tile.links.size < LINK_LIMIT) {
                tile.links.add(pos);
            }
        });

        config(Integer.class, (BridgeRouterBuild tile, Integer i) -> {
            if (i == -1) {
                tile.links.clear();
            } else if (!tile.links.contains(i) && tile.links.size < LINK_LIMIT) {
                tile.links.add(i);
            }
        });

        config(IntSeq.class, (BridgeRouterBuild tile, IntSeq seq) -> {
            tile.links.clear();
            for (int j = 0; j < seq.size; j += 2) {
                int x = seq.get(j);
                int y = seq.get(j + 1);
                int pos = Point2.pack(x + tile.tileX(), y + tile.tileY());
                if (tile.links.size < LINK_LIMIT) {
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
        stats.add(Stat.powerConnections, LINK_LIMIT, StatUnit.none);
    }

    @Override
    public void drawPlace(int x, int y, int rotation, boolean valid) {
        super.drawPlace(x, y, rotation, valid);
        Drawf.dashCircle(x * tilesize + tilesize / 2f, y * tilesize + tilesize / 2f, range * tilesize, Pal.accent);
    }

    public boolean linkValid(Tile tile, Tile other) {
        if (other == tile) return false;
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
        public IntSeq links = new IntSeq(false, LINK_LIMIT);
        public IntSeq incoming = new IntSeq(false, 4);
        public float warmup;
        public float time = -8f, timeSpeed;
        public boolean wasMoved, moved, hadValidLink;
        public float transportCounter;
        public int sendIndex = 0;

        @Override
        public void pickedUp() {
            links.clear();
        }

        private void drawBridgeTo(Tile other) {
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

        private void drawConnectionLine(Tile target) {
            if (!linkValid(tile, target)) return;

            float tx = tile.drawx(), ty = tile.drawy();
            float ox = target.drawx(), oy = target.drawy();
            float dx = ox - tx, dy = oy - ty;
            float length = Mathf.dst(dx, dy);
            if (length <= 0.001f) return;

            float ux = dx / length, uy = dy / length;
            float nx = -uy, ny = ux;
            float offset = 2f, inset = 4f, extend = 1.5f;

            float innerStartX = tx + ux * inset;
            float innerStartY = ty + uy * inset;
            float innerEndX = ox - ux * inset;
            float innerEndY = oy - uy * inset;
            float outerStartX = innerStartX - ux * extend;
            float outerStartY = innerStartY - uy * extend;
            float outerEndX = innerEndX + ux * extend;
            float outerEndY = innerEndY + uy * extend;

            float warmupVal = hasPower ? this.warmup : 1f;
            float powerLoss = 1f - warmupVal;

            Color outerColor = Tmp.c1.set(LINE_COLOR_OUTER).lerp(POWER_LOSS_COLOR, powerLoss);
            Color innerColor = Tmp.c2.set(LINE_COLOR_INNER).lerp(POWER_LOSS_INNER_COLOR, powerLoss);

            Draw.alpha(Renderer.bridgeOpacity);
            Draw.z(Layer.blockOver + 0.03f);

            Draw.color(outerColor);
            Lines.stroke(1f);
            Lines.line(outerStartX + nx * offset, outerStartY + ny * offset, outerEndX + nx * offset, outerEndY + ny * offset);
            Lines.line(outerStartX - nx * offset, outerStartY - ny * offset, outerEndX - nx * offset, outerEndY - ny * offset);
            Lines.line(outerStartX + nx * offset, outerStartY + ny * offset, outerStartX - nx * offset, outerStartY - ny * offset);
            Lines.line(outerEndX + nx * offset, outerEndY + ny * offset, outerEndX - nx * offset, outerEndY - ny * offset);

            Draw.z(Layer.blockOver + 0.02f);
            Draw.color(innerColor);
            Lines.stroke(4f);
            Lines.line(innerStartX, innerStartY, innerEndX, innerEndY);

            Draw.z(Layer.blockOver + 0.01f);
            float arrowLength = length - inset * 2f;
            int arrows = (int) (arrowLength / arrowSpacing);
            if (arrows > 0 && warmupVal > 0f) {
                float angle = Angles.angle(dx, dy);
                float rad = angle * Mathf.degRad;
                Draw.color(outerColor);
                for (int a = 0; a < arrows; a++) {
                    float dist = inset + a * arrowSpacing;
                    if (dist > length - inset - 3f) continue;
                    float px = tx + ux * dist;
                    float py = ty + uy * dist;
                    float timeFactor = (warmupVal > 0f) ? Time.time / arrowTimeScl : 0f;
                    float alphaVal = Mathf.absin(a - timeFactor, arrowPeriod, 1f);
                    if (alphaVal <= 0.01f) continue;
                    float displayAlpha = (warmupVal > 0f) ? alphaVal * warmupVal : alphaVal;
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
        public void drawSelect() {
            for (int i = 0; i < links.size; i++) {
                Tile other = world.tile(links.get(i));
                if (linkValid(tile, other)) {
                    drawBridgeTo(other);
                }
            }
            incoming.each(pos -> {
                Tile other = world.tile(pos);
                if (linkValid(tile, other)) {
                    drawBridgeTo(other);
                }
            });
            Draw.reset();
        }

        @Override
        public void drawConfigure() {
            Drawf.select(x, y, tile.block().size * tilesize / 2f + 2f, Pal.accent);
            Drawf.dashCircle(x, y, range * tilesize, Pal.accent);

            // 显示已连接的
            for (int i = 0; i < links.size; i++) {
                Tile other = world.tile(links.get(i));
                if (linkValid(tile, other)) {
                    Drawf.select(other.drawx(), other.drawy(),
                            other.block().size * tilesize / 2f + 2f, Pal.place);
                }
            }

            // 显示所有可连但未连接的目标
            int r = range;
            for (int dx = -r; dx <= r; dx++) {
                for (int dy = -r; dy <= r; dy++) {
                    if (dx == 0 && dy == 0) continue;
                    if (dx * dx + dy * dy > r * r) continue;
                    Tile other = tile.nearby(dx, dy);
                    if (other == null) continue;
                    if (linkValid(tile, other) && !links.contains(other.pos()) && !incoming.contains(other.pos())) {
                        Drawf.select(other.drawx(), other.drawy(),
                                other.block().size * tilesize / 2f + 2f + Mathf.absin(Time.time, 4f, 1f),
                                Pal.breakInvalid);
                    }
                }
            }

            // 显示桥间线
            for (int i = 0; i < links.size; i++) {
                Tile other = world.tile(links.get(i));
                if (linkValid(tile, other)) {
                    drawBridgeTo(other);
                }
            }
            incoming.each(pos -> {
                Tile other = world.tile(pos);
                if (linkValid(tile, other) && !links.contains(other.pos())) {
                    drawBridgeTo(other);
                }
            });
            Draw.reset();
        }

        @Override
        public boolean onConfigureBuildTapped(Building other) {
            if (other == this) {
                links.clear();
                return false;
            }

            if (other instanceof BridgeRouterBuild b) {
                int pos = other.pos();
                if (links.contains(pos)) {
                    links.removeValue(pos);
                    if (b.links.contains(tile.pos())) {
                        b.links.removeValue(tile.pos());
                    }
                    return false;
                } else if (links.size < LINK_LIMIT && linkValid(tile, other.tile)) {
                    links.add(pos);
                    if (!b.links.contains(tile.pos())) {
                        b.links.add(tile.pos());
                    }
                    return false;
                }
            }
            return true;
        }

        public void checkIncoming() {
            int idx = 0;
            while (idx < incoming.size) {
                int i = incoming.get(idx);
                Tile other = world.tile(i);
                if (!linkValid(tile, other, false) || !(other.build instanceof BridgeRouterBuild) || !((BridgeRouterBuild) other.build).links.contains(tile.pos())) {
                    incoming.removeIndex(idx);
                } else {
                    idx++;
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

            // 清理无效连接
            for (int i = links.size - 1; i >= 0; i--) {
                Tile other = world.tile(links.get(i));
                if (!linkValid(tile, other)) {
                    links.removeIndex(i);
                }
            }

            hadValidLink = links.size > 0;

            if (!hadValidLink) {
                doDump();
                warmup = 0f;
            } else {
                // 同步双向连接
                for (int i = 0; i < links.size; i++) {
                    Tile other = world.tile(links.get(i));
                    if (other != null && other.build instanceof BridgeRouterBuild) {
                        var b = (BridgeRouterBuild) other.build;
                        if (!b.links.contains(tile.pos())) {
                            b.links.add(tile.pos());
                        }
                    }
                }

                warmup = Mathf.approachDelta(warmup, efficiency, 1f / 30f);

                // 轮询传输
                if (!links.isEmpty()) {
                    if (sendIndex >= links.size) sendIndex = 0;
                    int targetPos = links.get(sendIndex);
                    Tile targetTile = world.tile(targetPos);
                    if (linkValid(tile, targetTile) && targetTile.build != null) {
                        updateTransport(targetTile.build);
                    }
                    sendIndex = (sendIndex + 1) % links.size;
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
            for (int i = 0; i < links.size; i++) {
                Tile other = world.tile(links.get(i));
                if (linkValid(tile, other)) {
                    drawConnectionLine(other);
                }
            }
            Draw.reset();
        }

        @Override
        public boolean acceptItem(Building source, Item item) {
            return hasItems && team == source.team && items.total() < itemCapacity && checkAccept(source, world.tile(links.size > 0 ? links.get(0) : -1));
        }

        protected boolean checkAccept(Building source, Tile linkTile) {
            if (tile == null || linked(source)) return true;
            if (linkTile == null) return false;
            if (linkValid(tile, linkTile)) {
                int rel = relativeTo(linkTile);
                var facing = Edges.getFacingEdge(source, this);
                int rel2 = facing == null ? -1 : relativeTo(facing);
                return rel != rel2;
            }
            return false;
        }

        protected boolean linked(Building source) {
            if (!(source instanceof BridgeRouterBuild)) return false;
            return ((BridgeRouterBuild) source).links.contains(tile.pos()) || links.contains(source.pos());
        }

        @Override
        public boolean canDump(Building to, Item item) {
            return checkDump(to);
        }

        protected boolean checkDump(Building to) {
            if (links.size == 0) return true;
            Tile firstLink = world.tile(links.get(0));
            if (!linkValid(tile, firstLink)) {
                Tile edge = Edges.getFacingEdge(to.tile, tile);
                int i = relativeTo(edge.x, edge.y);
                for (int j = 0; j < incoming.size; j++) {
                    int v = incoming.get(j);
                    if (relativeTo(Point2.x(v), Point2.y(v)) == i) {
                        return false;
                    }
                }
                return true;
            }
            int rel = relativeTo(firstLink.x, firstLink.y);
            int rel2 = relativeTo(to.tileX(), to.tileY());
            return rel != rel2;
        }

        @Override
        public boolean shouldConsume() {
            return hadValidLink && enabled;
        }

        @Override
        public Point2 config() {
            if (links.size == 0) return new Point2(0, 0);
            int pos = links.get(0);
            return Point2.unpack(pos).sub(tile.x, tile.y);
        }

        @Override
        public byte version() {
            return 2;
        }

        @Override
        public void write(Writes write) {
            super.write(write);
            write.s(links.size);
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
            int linkCount = read.s();
            if (linkCount > LINK_LIMIT) linkCount = LINK_LIMIT;
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