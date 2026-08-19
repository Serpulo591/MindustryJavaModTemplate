package example.world.blocks;

import arc.graphics.Color;
import arc.math.Mathf;
import arc.math.geom.Point2;
import arc.struct.IntSeq;
import arc.struct.Seq;
import arc.util.Time;
import mindustry.core.Core;
import mindustry.gen.Building;
import mindustry.gen.Tile;
import mindustry.graphics.*;
import mindustry.type.Item;
import mindustry.ui.Bar;
import mindustry.world.Block;
import mindustry.world.TileEntity;
import mindustry.world.blocks.storage.BridgeRouter;
import mindustry.world.meta.Stat;
import mindustry.world.meta.StatUnit;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import static mindustry.Vars.content;
import static mindustry.Vars.world;

public class WarpBridge extends BridgeRouter {

    private static final Color POWER_LOSS_COLOR = Color.valueOf("f49fa680");
    private static final Color POWER_LOSS_INNER_COLOR = Color.valueOf("ec767859");
    private static final Color LINE_COLOR_OUTER = Color.valueOf("c0edf4");
    private static final Color LINE_COLOR_INNER = Color.valueOf("a1d7ecb3");
    private static final float LINE_WIDTH_OUTER = 1f;
    private static final float LINE_WIDTH_INNER = 4f;
    private static final float CAP_LINE_WIDTH = 1f;
    private static final Color ARROW_COLOR = Color.valueOf("c0edf4");
    private static final float ARROW_SIZE = 2.4f;
    private static final float ARROW_SPACING = 4f;
    private static final float ARROW_PERIOD = 0.4f;
    private static final float ARROW_TIME_SCL = 12.6f;
    private static final int LINE_INSET = 4;
    private static final float OUTER_EXTEND = 1.5f;
    private static final int LINE_INSET1 = 2;
    private static final int RANGE = 64;
    private static final float WARMUP_SPEED = 0.05f;
    private static final int LINK_LIMIT = 4;
    private static final int FRAME_DELAY = 1;

    private static final Seq<WarpBridgeBuild> activeBridges = new Seq<>();

    public WarpBridge(String name) {
        super(name);
        this.range = RANGE;
        this.itemCapacity = 40;
        this.configurable = true;
        this.hasItems = true;
    }

    @Override
    public void setStats() {
        super.setStats();
        stats.add(Stat.range, range / 8f, StatUnit.blocks);
        stats.add(Stat.powerConnections, LINK_LIMIT, StatUnit.none);
    }

    @Override
    public void setBars() {
        super.setBars();
        addBar("connections", (WarpBridgeBuild entity) ->
                new Bar(
                        () -> Core.bundle.format("bar.powerlines", entity.getLink().size, LINK_LIMIT),
                        () -> Pal.accent,
                        () -> (float) entity.getLink().size / LINK_LIMIT
                ));
    }

    @Override
    public void drawPlace(int x, int y, int rotation, boolean valid) {
        Drawf.dashCircle(x * 8, y * 8, range - 8, Pal.accent);
    }

    @Override
    public void config(Tile tile, Object value) {
        if (value instanceof IntSeq) {
            IntSeq seq = (IntSeq) value;
            Seq<Integer> newLinks = new Seq<>();
            for (int i = 0; i < seq.size; i += 2) {
                int dx = seq.get(i);
                int dy = seq.get(i + 1);
                int pos = Point2.pack(tile.x + dx, tile.y + dy);
                newLinks.add(pos);
            }
            WarpBridgeBuild build = (WarpBridgeBuild) tile.entity;
            build.setLink(newLinks);
        } else if (value instanceof Integer) {
            int pos = (Integer) value;
            WarpBridgeBuild build = (WarpBridgeBuild) tile.entity;
            Seq<Integer> links = build.getLink();
            if (links.contains(pos)) {
                links.remove(pos);
            } else {
                if (links.size >= LINK_LIMIT) return;
                links.add(pos);
                Building target = world.build(pos);
                if (target != null && target.block == this && target instanceof WarpBridgeBuild) {
                    WarpBridgeBuild targetBuild = (WarpBridgeBuild) target;
                    Seq<Integer> targetLinks = targetBuild.getLink();
                    if (targetLinks.contains(tile.pos())) {
                        targetLinks.remove(tile.pos());
                        targetBuild.setLink(targetLinks);
                    }
                }
            }
            build.setLink(links);
        }
    }

    @Override
    public TileEntity createTileEntity(Tile tile) {
        return new WarpBridgeBuild(tile);
    }

    public class WarpBridgeBuild extends BridgeRouter.BridgeBuild {
        private Seq<Integer> links = new Seq<>();
        private float warmup = 0f;
        private float rotateSpeed = 0f;
        private int sendIndex = 0;
        private transient Seq<TransportItem> transport = new Seq<>();
        private float powerLoss = 0f;

        public WarpBridgeBuild(Tile tile) {
            super(tile);
            activeBridges.add(this);
        }

        public Seq<Integer> getLink() { return links; }
        public float getPowerLoss() { return powerLoss; }

        public void setLink(Seq<Integer> newLinks) {
            this.links = newLinks;
            if (links.size > LINK_LIMIT) links.truncate(LINK_LIMIT);
        }

        @Override
        public boolean shouldConsume() {
            return !links.isEmpty();
        }

        @Override
        public void updateEfficiency() {
            efficiency = Mathf.lerpDelta(efficiency, shouldConsume() ? 1f : 0f, WARMUP_SPEED);
        }

        @Override
        public boolean acceptItem(Building source, Item item) {
            if (items.total() >= block.itemCapacity) return false;
            return super.acceptItem(source, item);
        }

        @Override
        public void updateTile() {
            for (int i = transport.size - 1; i >= 0; i--) {
                TransportItem t = transport.get(i);
                if (--t.time <= 0) {
                    Building target = world.build(t.target);
                    if (target != null && target.team == team && target.block == WarpBridge.this) {
                        int accept = Math.min(t.amount, target.acceptStack(t.item, t.amount, this));
                        if (accept > 0) {
                            target.handleStack(t.item, accept, this);
                        }
                    }
                    transport.remove(i);
                }
            }

            boolean consValid = efficiency > 0;
            boolean itemSent = false;
            powerLoss = Mathf.lerpDelta(powerLoss, consValid ? 0f : 1f, 0.08f);

            if (links.isEmpty()) {
                if (items.total() > 0) {
                    for (Item item : content.items()) {
                        if (items.get(item) > 0) dump(item);
                    }
                }
                warmup = Mathf.lerpDelta(warmup, 0f, WARMUP_SPEED);
                rotateSpeed = Mathf.lerpDelta(rotateSpeed, 0f, WARMUP_SPEED);
                return;
            }

            if (!consValid) {
                warmup = Mathf.lerpDelta(warmup, 0f, WARMUP_SPEED);
                rotateSpeed = Mathf.lerpDelta(rotateSpeed, 0f, WARMUP_SPEED);
                return;
            }

            if (items.total() <= 0) {
                warmup = Mathf.lerpDelta(warmup, 0f, WARMUP_SPEED);
                return;
            }

            if (!links.isEmpty() && Time.time % FRAME_DELAY < 1) {
                for (int i = links.size - 1; i >= 0; i--) {
                    int pos = links.get(i);
                    Building target = world.build(pos);
                    if (!(target instanceof WarpBridgeBuild) || target.team != team || !within(target, range)) {
                        links.remove(i);
                    }
                }

                if (!links.isEmpty()) {
                    if (sendIndex >= links.size) sendIndex = 0;
                    int startIndex = sendIndex;
                    boolean sent = false;
                    do {
                        int pos = links.get(sendIndex);
                        Building target = world.build(pos);
                        if (target != null && target.team == team && target.block == WarpBridge.this && within(target, range)) {
                            for (Item item : content.items()) {
                                int amount = items.get(item);
                                if (amount <= 0) continue;
                                int accept = Math.min(amount, 1, target.acceptStack(item, 1, this));
                                if (accept > 0) {
                                    transport.add(new TransportItem(item, accept, target.pos(), 60));
                                    items.remove(item, accept);
                                    itemSent = true;
                                    sent = true;
                                    break;
                                }
                            }
                        }
                        sendIndex = (sendIndex + 1) % links.size;
                        if (sent) break;
                    } while (sendIndex != startIndex);
                }
            }

            warmup = Mathf.lerpDelta(warmup, links.isEmpty() ? 0f : 1f, WARMUP_SPEED);
            rotateSpeed = Mathf.lerpDelta(rotateSpeed, itemSent ? 1f : 0f, WARMUP_SPEED);
        }

        @Override
        public void draw() {
            super.draw();
            drawConnections();
        }

        private void drawConnections() {
            float prevZ = Draw.z();
            Draw.z(Layer.block + 1);

            for (int i = 0; i < links.size; i++) {
                int pos = links.get(i);
                Building target = world.build(pos);
                if (!(target instanceof WarpBridgeBuild) || target.team != team) continue;

                float dx = target.x - x, dy = target.y - y;
                float length = Mathf.dst(dx, dy);
                if (length == 0) continue;
                float ux = dx / length, uy = dy / length;
                float nx = -uy, ny = ux;
                float offset = LINE_WIDTH_INNER / 2f;

                float insetX = ux * LINE_INSET, insetY = uy * LINE_INSET;
                float innerStartX = x + insetX, innerStartY = y + insetY;
                float innerEndX = target.x - insetX, innerEndY = target.y - insetY;

                float extendX = ux * OUTER_EXTEND, extendY = uy * OUTER_EXTEND;
                float outerStartX = innerStartX - extendX, outerStartY = innerStartY - extendY;
                float outerEndX = innerEndX + extendX, outerEndY = innerEndY + extendY;

                Draw.color(LINE_COLOR_OUTER.lerp(POWER_LOSS_COLOR, powerLoss));
                Lines.stroke(LINE_WIDTH_OUTER);
                Lines.line(outerStartX + nx * offset, outerStartY + ny * offset,
                           outerEndX + nx * offset, outerEndY + ny * offset);
                Lines.line(outerStartX - nx * offset, outerStartY - ny * offset,
                           outerEndX - nx * offset, outerEndY - ny * offset);

                Draw.color(LINE_COLOR_INNER.lerp(POWER_LOSS_INNER_COLOR, efficiency <= 0 ? 1f : 0f));
                Lines.stroke(LINE_WIDTH_INNER);
                Lines.line(innerStartX, innerStartY, innerEndX, innerEndY);

                Draw.color(LINE_COLOR_OUTER.lerp(POWER_LOSS_COLOR, powerLoss));
                Lines.stroke(CAP_LINE_WIDTH);
                Lines.line(outerStartX + nx * offset, outerStartY + ny * offset,
                           outerStartX - nx * offset, outerStartY - ny * offset);
                Lines.line(outerEndX + nx * offset, outerEndY + ny * offset,
                           outerEndX - nx * offset, outerEndY - ny * offset);

                drawFlowArrows(target, efficiency > 0, powerLoss, LINE_INSET);
            }

            Draw.z(prevZ);
        }

        private void drawFlowArrows(Building to, boolean powered, float powerLoss, int inset) {
            float dx = to.x - x, dy = to.y - y;
            float totalDist = Mathf.dst(dx, dy);
            if (totalDist <= 0) return;

            float normX = dx / totalDist, normY = dy / totalDist;
            float startX = x + normX * inset, startY = y + normY * inset;
            float endX = to.x - normX * inset, endY = to.y - normY * inset;
            float bridgeLength = Mathf.dst(endX - startX, endY - startY);

            int arrows = (int) (bridgeLength / ARROW_SPACING);
            if (arrows <= 0) return;

            float angle = Mathf.angle(dx, dy);

            for (int a = 0; a < arrows; a++) {
                float px = startX + normX * a * ARROW_SPACING;
                float py = startY + normY * a * ARROW_SPACING;
                float timeScl = powered ? ARROW_TIME_SCL : 4.2f;
                float alpha = Mathf.absin(a - Time.time / timeScl, ARROW_PERIOD, 1f);
                if (alpha <= 0.01f) continue;
                float finalAlpha = powered ? alpha : 0.5f;

                Draw.color(ARROW_COLOR.lerp(POWER_LOSS_COLOR, powerLoss), finalAlpha);
                float rad = angle * Mathf.degRad;
                Fill.tri(
                        px + Mathf.cos(rad) * ARROW_SIZE, py + Mathf.sin(rad) * ARROW_SIZE,
                        px + Mathf.cos(rad + Mathf.PI / 2) * ARROW_SIZE, py + Mathf.sin(rad + Mathf.PI / 2) * ARROW_SIZE,
                        px + Mathf.cos(rad - Mathf.PI / 2) * ARROW_SIZE, py + Mathf.sin(rad - Mathf.PI / 2) * ARROW_SIZE
                );
            }
        }

        @Override
        public void drawConfigure() {
            super.drawConfigure();
            float pulse = Mathf.absin(Time.time, 4f, 1f);
            Draw.color(Pal.accent);
            Lines.stroke(1f);
            Drawf.select(x, y, tile.block().size * 8 / 2f + 2, Pal.accent);

            for (int pos : links) {
                Building target = world.build(pos);
                if (target != null && target.block == WarpBridge.this && target.team == team) {
                    Drawf.select(target.x, target.y, target.block.size * 8 / 2f + 2, Pal.place);
                }
            }

            int myPos = pos();
            for (WarpBridgeBuild other : activeBridges) {
                if (other == this || other.team != team || !within(other, range)) continue;
                boolean connected = links.contains(other.pos());
                if (!connected && other.getLink().contains(myPos)) {
                    Drawf.select(other.x, other.y, other.block.size * 8 / 2f + 2 + pulse, Pal.breakInvalid);
                }
            }

            // 绘制灰色底线条
            Draw.z(Layer.block + 1);
            for (int pos : links) {
                Building target = world.build(pos);
                if (target == null) continue;
                float dx = target.x - x, dy = target.y - y;
                float dist = Mathf.dst(dx, dy);
                if (dist == 0) continue;
                float ux = dx / dist, uy = dy / dist;
                float sx = x + ux * LINE_INSET1, sy = y + uy * LINE_INSET1;
                float ex = target.x - ux * LINE_INSET1, ey = target.y - uy * LINE_INSET1;
                Draw.color(Color.valueOf("454545"));
                Lines.stroke(2.5f);
                Lines.line(sx, sy, ex, ey);
            }

            for (WarpBridgeBuild other : activeBridges) {
                if (other == this || other.team != team) continue;
                if (!other.getLink().contains(myPos)) continue;
                if (links.contains(other.pos())) continue;
                float dx = other.x - x, dy = other.y - y;
                float dist = Mathf.dst(dx, dy);
                if (dist == 0) continue;
                float ux = dx / dist, uy = dy / dist;
                float sx = other.x + ux * LINE_INSET1, sy = other.y + uy * LINE_INSET1;
                float ex = x - ux * LINE_INSET1, ey = y - uy * LINE_INSET1;
                Draw.color(Color.valueOf("454545"));
                Lines.stroke(2.5f);
                Lines.line(sx, sy, ex, ey);
            }

            Draw.z(Layer.block + 1);
            for (int pos : links) {
                Building target = world.build(pos);
                if (target == null) continue;
                Drawf.square(target.x, target.y, 1, Pal.place);
            }

            for (WarpBridgeBuild other : activeBridges) {
                if (other == this || other.team != team || !within(other, range)) continue;
                if (!other.getLink().contains(myPos)) continue;
                if (links.contains(other.pos())) continue;
                Drawf.square(other.x, other.y, 1, Pal.accent);
            }

            Draw.z(Layer.block + 1);
            for (int pos : links) {
                Building target = world.build(pos);
                if (target == null) continue;
                float dx = target.x - x, dy = target.y - y;
                float dist = Mathf.dst(dx, dy);
                if (dist == 0) continue;
                float ux = dx / dist, uy = dy / dist;
                float sx = x + ux * LINE_INSET1, sy = y + uy * LINE_INSET1;
                float ex = target.x - ux * LINE_INSET1, ey = target.y - uy * LINE_INSET1;
                Draw.color(Color.valueOf("662fff"));
                Lines.stroke(1f);
                Lines.line(sx, sy, ex, ey);
            }

            for (WarpBridgeBuild other : activeBridges) {
                if (other == this || other.team != team) continue;
                if (!other.getLink().contains(myPos)) continue;
                if (links.contains(other.pos())) continue;
                float dx = other.x - x, dy = other.y - y;
                float dist = Mathf.dst(dx, dy);
                if (dist == 0) continue;
                float ux = dx / dist, uy = dy / dist;
                float sx = other.x + ux * LINE_INSET1, sy = other.y + uy * LINE_INSET1;
                float ex = x - ux * LINE_INSET1, ey = y - uy * LINE_INSET1;
                Draw.color(Color.valueOf("ffd16d"));
                Lines.stroke(1f);
                Lines.line(sx, sy, ex, ey);
            }

            Draw.z(Layer.block + 1);
            for (int pos : links) {
                Building target = world.build(pos);
                if (target == null) continue;
                drawMovingArrow(this, target, Pal.place, LINE_INSET1 - 2);
            }
            for (WarpBridgeBuild other : activeBridges) {
                if (other == this || other.team != team) continue;
                if (!other.getLink().contains(myPos)) continue;
                if (links.contains(other.pos())) continue;
                drawMovingArrow(other, this, Pal.accent, LINE_INSET1 - 2);
            }

            Drawf.dashCircle(x, y, range - 8, Pal.accent);
        }

        private void drawMovingArrow(Building from, Building to, Color color, int inset) {
            float dx = to.x - from.x, dy = to.y - from.y;
            float dist = Mathf.dst(dx, dy);
            if (dist <= 0) return;
            float ux = dx / dist, uy = dy / dist;
            float startX = from.x + ux * inset, startY = from.y + uy * inset;
            float endX = to.x - ux * inset, endY = to.y - uy * inset;
            float bridgeLength = Mathf.dst(endX - startX, endY - startY);

            float speed = 0.02f;
            float progress = (Time.time * speed) % 1f;
            float px = startX + ux * bridgeLength * progress;
            float py = startY + uy * bridgeLength * progress;
            float angle = Mathf.angle(dx, dy);
            float rad = angle * Mathf.degRad;
            float arrowSize = 2.5f;
            Draw.color(color);
            Fill.tri(
                    px + Mathf.cos(rad) * arrowSize, py + Mathf.sin(rad) * arrowSize,
                    px + Mathf.cos(rad + Mathf.PI / 2) * arrowSize, py + Mathf.sin(rad + Mathf.PI / 2) * arrowSize,
                    px + Mathf.cos(rad - Mathf.PI / 2) * arrowSize, py + Mathf.sin(rad - Mathf.PI / 2) * arrowSize
            );
        }

        @Override
        public boolean onConfigureBuildTapped(Building other) {
            if (this == other) {
                links.clear();
                return false;
            }
            if (dst(other) <= range && other.team == team && other.block == WarpBridge.this) {
                configure(other.pos());
                return false;
            }
            return true;
        }

        @Override
        public Object config() {
            IntSeq out = new IntSeq(links.size * 2);
            for (int pos : links) {
                Point2 p = Point2.unpack(pos).sub(tile.x, tile.y);
                out.add(p.x, p.y);
            }
            return out;
        }

        @Override
        public void write(DataOutput write) throws IOException {
            super.write(write);
            write.writeInt(links.size);
            for (int pos : links) {
                write.writeInt(pos);
            }
        }

        @Override
        public void read(DataInput read, byte revision) throws IOException {
            super.read(read, revision);
            links.clear();
            int size = read.readInt();
            if (size > LINK_LIMIT) size = LINK_LIMIT;
            for (int i = 0; i < size; i++) {
                links.add(read.readInt());
            }
            transport.clear();
        }

        @Override
        public byte version() {
            return 1;
        }

        @Override
        public void onRemoved() {
            activeBridges.remove(this);
        }

        private class TransportItem {
            Item item;
            int amount;
            int target;
            int time;

            TransportItem(Item item, int amount, int target, int time) {
                this.item = item;
                this.amount = amount;
                this.target = target;
                this.time = time;
            }
        }
    }
}