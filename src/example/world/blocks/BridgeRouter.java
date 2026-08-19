package mindustry.world.blocks.distribution;

import arc.graphics.g2d.*;
import arc.math.*;
import arc.math.geom.*;
import arc.struct.*;
import arc.util.*;
import arc.util.io.*;
import mindustry.annotations.Annotations.*;
import mindustry.core.*;
import mindustry.entities.units.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.meta.*;

import static mindustry.Vars.*;

public class BridgeRouter extends Block {
    public int range;
    public float transportTime;
    public @Load("@-end") TextureRegion endRegion;
    public @Load("@-bridge") TextureRegion bridgeRegion;
    public @Load("@-arrow") TextureRegion arrowRegion;

    public boolean moveArrows = true;
    public float arrowSpacing = 4f, arrowOffset = 2f, arrowPeriod = 0.4f;
    public float arrowTimeScl = 6.2f;
    public float bridgeWidth = 6.5f;

    // for autolink
    public @Nullable BridgeRouterBuild lastBuild;

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

    private static int currentFindX, currentFindY;
    private static BuildPlan currentPlan;
    private static final Boolf<BuildPlan> planFinder = other -> other.block == currentPlan.block && currentPlan != other && currentFindX == other.x && currentFindY == other.y;

    @Override
    public void drawPlanConfigTop(BuildPlan plan, Eachable<BuildPlan> list) {
        if (plan.config instanceof Point2 p && (Math.abs(p.x) <= range && Math.abs(p.y) <= range && (p.x == 0 || p.y == 0))) {
            currentFindX = plan.x + p.x;
            currentFindY = plan.y + p.y;
            currentPlan = plan;
            var otherReq = findPlan(list, currentFindX, currentFindY, planFinder);
            if (otherReq != null) {
                drawBridge(plan, otherReq.drawx(), otherReq.drawy(), 0);
            }
        }
    }

    public void drawBridge(BuildPlan req, float ox, float oy, float flip) {
        if (Mathf.zero(Renderer.bridgeOpacity)) return;
        Draw.alpha(Renderer.bridgeOpacity);
        Lines.stroke(bridgeWidth);
        Tmp.v1.set(ox, oy).sub(req.drawx(), req.drawy()).setLength(tilesize / 2f);
        Lines.line(bridgeRegion,
                req.drawx() + Tmp.v1.x,
                req.drawy() + Tmp.v1.y,
                ox - Tmp.v1.x,
                oy - Tmp.v1.y, false);
        Draw.rect(arrowRegion,
                (req.drawx() + ox) / 2f,
                (req.drawy() + oy) / 2f,
                Angles.angle(req.drawx(), req.drawy(), ox, oy) + flip);
        Draw.reset();
    }

    @Override
    public void drawPlace(int x, int y, int rotation, boolean valid) {
        super.drawPlace(x, y, rotation, valid);
        Tile link = findLink(x, y);
        for (int i = 0; i < 4; i++) {
            Drawf.dashLine(Pal.placing,
                    x * tilesize + Geometry.d4[i].x * (tilesize / 2f + 2),
                    y * tilesize + Geometry.d4[i].y * (tilesize / 2f + 2),
                    x * tilesize + Geometry.d4[i].x * range * tilesize,
                    y * tilesize + Geometry.d4[i].y * range * tilesize);
        }
        Draw.reset();
        Draw.color(Pal.placing);
        Lines.stroke(1f);
        if (link != null && Math.abs(link.x - x) + Math.abs(link.y - y) > 1) {
            int rot = link.absoluteRelativeTo(x, y);
            float w = (link.x == x ? tilesize : Math.abs(link.x - x) * tilesize - tilesize);
            float h = (link.y == y ? tilesize : Math.abs(link.y - y) * tilesize - tilesize);
            Lines.rect((x + link.x) / 2f * tilesize - w / 2f,
                    (y + link.y) / 2f * tilesize - h / 2f, w, h);
            Draw.rect("bridge-arrow",
                    link.x * tilesize + Geometry.d4(rot).x * tilesize,
                    link.y * tilesize + Geometry.d4(rot).y * tilesize,
                    link.absoluteRelativeTo(x, y) * 90);
        }
        Draw.reset();
    }

    public boolean linkValid(Tile tile, Tile other) {
        return linkValid(tile, other, true);
    }

    public boolean linkValid(Tile tile, Tile other, boolean checkDouble) {
        if (other == null || tile == null || !positionsValid(tile.x, tile.y, other.x, other.y)) return false;
        return ((other.block() == tile.block() && tile.block() == this) || (!(tile.block() instanceof BridgeRouter) && other.block() == this))
                && (other.team() == tile.team() || tile.block() != this)
                && (!checkDouble || ((BridgeRouterBuild) other.build).link != tile.pos());
    }

    public boolean positionsValid(int x1, int y1, int x2, int y2) {
        if (x1 == x2) {
            return Math.abs(y1 - y2) <= range;
        } else if (y1 == y2) {
            return Math.abs(x1 - x2) <= range;
        } else {
            return false;
        }
    }

    public Tile findLink(int x, int y) {
        Tile tile = world.tile(x, y);
        if (tile != null && lastBuild != null && linkValid(tile, lastBuild.tile) && lastBuild.tile != tile && lastBuild.link == -1) {
            return lastBuild.tile;
        }
        return null;
    }

    @Override
    public void init() {
        super.init();
        updateClipRadius((range + 0.5f) * tilesize);
    }

    @Override
    public void handlePlacementLine(Seq<BuildPlan> plans) {
        for (int i = 0; i < plans.size - 1; i++) {
            var cur = plans.get(i);
            var next = plans.get(i + 1);
            if (positionsValid(cur.x, cur.y, next.x, next.y)) {
                cur.config = new Point2(next.x - cur.x, next.y - cur.y);
            }
        }
    }

    @Override
    public void changePlacementPath(Seq<Point2> points, int rotation) {
        Placement.calculateNodes(points, this, rotation, (point, other) -> Math.max(Math.abs(point.x - other.x), Math.abs(point.y - other.y)) <= range);
    }

    public class BridgeRouterBuild extends Building {
        public int link = -1;
        public float warmup;
        public float time = -8f;
        public float transportCounter;

        @Override
        public void pickedUp() {
            link = -1;
        }

        @Override
        public void playerPlaced(Object config) {
            super.playerPlaced(config);
            Tile link = findLink(tile.x, tile.y);
            if (linkValid(tile, link) && this.link != link.pos() && !proximity.contains(link.build)) {
                link.build.configure(tile.pos());
            }
            lastBuild = this;
        }

        @Override
        public void drawSelect() {
            if (linkValid(tile, world.tile(link))) {
                drawInput(world.tile(link));
            }
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
            Draw.rect(arrowRegion, x, y, rel * 90);
            Draw.mixcol();
        }

        @Override
        public void drawConfigure() {
            Drawf.select(x, y, tile.block().size * tilesize / 2f + 2f, Pal.accent);
            for (int i = 1; i <= range; i++) {
                for (int j = 0; j < 4; j++) {
                    Tile other = tile.nearby(Geometry.d4[j].x * i, Geometry.d4[j].y * i);
                    if (linkValid(tile, other)) {
                        boolean linked = other.pos() == link;
                        Drawf.select(other.drawx(), other.drawy(),
                                other.block().size * tilesize / 2f + 2f + (linked ? 0f : Mathf.absin(Time.time, 4f, 1f)),
                                linked ? Pal.place : Pal.breakInvalid);
                    }
                }
            }
        }

        @Override
        public boolean onConfigureBuildTapped(Building other) {
            if (other instanceof BridgeRouterBuild b && b.link == pos()) {
                configure(other.pos());
                other.configure(-1);
                return true;
            }
            if (linkValid(tile, other.tile)) {
                if (link == other.pos()) {
                    configure(-1);
                } else {
                    configure(other.pos());
                }
                return false;
            }
            return true;
        }

        @Override
        public void updateTile() {
            time += delta();
            Tile other = world.tile(link);
            boolean hadValidLink = linkValid(tile, other);

            if (!hadValidLink) {
                doDump();
                warmup = 0f;
            } else {
                warmup = Mathf.approachDelta(warmup, efficiency, 1f / 30f);
                updateTransport(other.build);
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
            Draw.z(Layer.power);
            Tile other = world.tile(link);
            if (!linkValid(tile, other)) return;
            if (Mathf.zero(Renderer.bridgeOpacity)) return;

            int i = relativeTo(other.x, other.y);
            float warmup = hasPower ? this.warmup : 1f;
            Draw.alpha(warmup * Renderer.bridgeOpacity);

            Draw.rect(endRegion, x, y, i * 90 + 90);
            Draw.rect(endRegion, other.drawx(), other.drawy(), i * 90 + 270);

            Lines.stroke(bridgeWidth);
            Tmp.v1.set(x, y).sub(other.worldx(), other.worldy()).setLength(tilesize / 2f).scl(-1f);
            Lines.line(bridgeRegion,
                    x + Tmp.v1.x,
                    y + Tmp.v1.y,
                    other.worldx() - Tmp.v1.x,
                    other.worldy() - Tmp.v1.y, false);

            int dist = Math.max(Math.abs(other.x - tile.x), Math.abs(other.y - tile.y)) - 1;
            Draw.color();
            if (Lod.l1 && moveArrows) {
                int arrows = (int) (dist * tilesize / arrowSpacing), dx = Geometry.d4x(i), dy = Geometry.d4y(i);
                for (int a = 0; a < arrows; a++) {
                    Draw.alpha(Mathf.absin(a - time / arrowTimeScl, arrowPeriod, 1f) * warmup * Renderer.bridgeOpacity * Lod.alpha1);
                    Draw.rect(arrowRegion,
                            x + dx * (tilesize / 2f + a * arrowSpacing + arrowOffset),
                            y + dy * (tilesize / 2f + a * arrowSpacing + arrowOffset),
                            i * 90f);
                }
            }
            Draw.reset();
        }

        @Override
        public boolean acceptItem(Building source, Item item) {
            return hasItems && team == source.team && items.total() < itemCapacity && checkAccept(source, world.tile(link));
        }

        @Override
        public boolean canDumpLiquid(Building to, Liquid liquid) {
            return checkDump(to);
        }

        @Override
        public boolean acceptLiquid(Building source, Liquid liquid) {
            return hasLiquids && team == source.team &&
                    (liquids.current() == liquid || liquids.get(liquids.current()) < 0.2f) &&
                    checkAccept(source, world.tile(link));
        }

        protected boolean checkAccept(Building source, Tile link) {
            if (tile == null) return false;
            if (linked(source)) return true;
            return linkValid(tile, link);
        }

        protected boolean linked(Building source) {
            return source instanceof BridgeRouterBuild && linkValid(source.tile, tile) && ((BridgeRouterBuild) source).link == pos();
        }

        @Override
        public boolean canDump(Building to, Item item) {
            return checkDump(to);
        }

        protected boolean checkDump(Building to) {
            return true;
        }

        @Override
        public boolean shouldConsume() {
            return linkValid(tile, world.tile(link)) && enabled;
        }

        @Override
        public Point2 config() {
            return Point2.unpack(link).sub(tile.x, tile.y);
        }

        @Override
        public byte version() {
            return 1;
        }

        @Override
        public void write(Writes write) {
            super.write(write);
            write.i(link);
            write.f(warmup);
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            link = read.i();
            warmup = read.f();
        }
    }
}