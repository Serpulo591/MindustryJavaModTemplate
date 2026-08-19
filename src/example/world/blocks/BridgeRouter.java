package example.world.blocks;

import arc.graphics.Color;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.math.geom.*;
import arc.struct.IntSeq;
import arc.struct.Seq;
import arc.util.*;
import arc.util.io.*;
import mindustry.core.Core;
import mindustry.entities.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.type.*;
import mindustry.ui.Bar;
import mindustry.world.*;
import mindustry.world.blocks.storage.StorageBlock;
import mindustry.world.meta.*;

import static mindustry.Vars.*;

public class BridgeRouter extends StorageBlock {
    // 配置常量（全部 static final）
    public static final int range = 64;
    public static final int linkLimit = 4;
    public static final float warmupSpeed = 0.05f;
    public static final float transportDelay = 60f;
    public static final float arrowTimeScl = 12.6f;
    public static final float arrowSpacing = 4f;
    public static final float arrowOffset = 2f;
    public static final float arrowPeriod = 0.4f;
    public static final float arrowSize = 2.4f;
    public static final int FRAME_DELAY = 1;
    public static final float LINE_INSET1 = 2f;
    public static final float LINE_INSET = 4f;
    public static final float OUTER_EXTEND = 1.5f;
    public static final float LINE_WIDTH_OUTER = 1f;
    public static final float LINE_WIDTH_INNER = 4f;
    public static final float CAP_LINE_WIDTH = 1f;

    public static final Color POWER_LOSS_COLOR = Color.valueOf("f49fa680");
    public static final Color POWER_LOSS_INNER_COLOR = Color.valueOf("ec767859");
    public static final Color LINE_COLOR_OUTER = Color.valueOf("c0edf4");
    public static final Color LINE_COLOR_INNER = Color.valueOf("a1d7ecb3");
    public static final Color ARROW_COLOR = Color.valueOf("c0edf4");

    public BridgeRouter(String name) {
        super(name);
        update = true;
        solid = true;
        hasItems = true;
        hasPower = true;
        configurable = true;
        saveConfig = true;
        itemCapacity = 30;
        group = BlockGroup.transportation;
        priority = TargetPriority.transport;
        envEnabled = Env.any;
        allowConfigInventory = false;
    }

    @Override
    public void setStats() {
        super.setStats();
        stats.add(Stat.range, range / tilesize, StatUnit.blocks);
        stats.add(Stat.powerConnections, linkLimit, StatUnit.none);
        stats.add(Stat.itemCapacity, itemCapacity, StatUnit.items);
    }

    @Override
    public void setBars() {
        super.setBars();
        addBar("connections", (BridgeRouterBuild e) ->
            new Bar(
                () -> Core.bundle.format("bar.powerlines", e.links.size, linkLimit),
                () -> Pal.accent,
                () -> (float) e.links.size / linkLimit
            )
        );
    }

    @Override
    public void drawPlace(int x, int y, int rotation, boolean valid) {
        super.drawPlace(x, y, rotation, valid);
        Drawf.dashCircle(x * tilesize, y * tilesize, range - tilesize, Pal.accent);
    }

    @Override
    public boolean outputsItems() {
        return true;
    }

    // config(IntSeq)
    public void config(IntSeq seq, BridgeRouterBuild tile) {
        Seq<Integer> newLinks = new Seq<>();
        for (int i = 0; i < seq.size; i += 2) {
            int x = seq.get(i) + tile.tileX();
            int y = seq.get(i + 1) + tile.tileY();
            int pos = Point2.pack(x, y);
            newLinks.add(pos);
        }
        tile.setLinks(newLinks);
    }

    // config(Integer)
    public void config(Integer value, BridgeRouterBuild tile) {
        int pos = value;
        Seq<Integer> links = tile.links;
        if (links.contains(pos)) {
            links.remove(pos);
            Building other = world.build(pos);
            if (other instanceof BridgeRouterBuild b && b.team == tile.team) {
                b.links.remove(tile.pos());
            }
        } else {
            if (links.size >= linkLimit) return;
            links.add(pos);
            Building other = world.build(pos);
            if (other instanceof BridgeRouterBuild b && b.team == tile.team) {
                b.links.remove(tile.pos());
            }
        }
        tile.setLinks(links);
    }

    public class BridgeRouterBuild extends StorageBuild {
        public Seq<Integer> links = new Seq<>();
        public float warmup = 0f;
        public float powerLoss = 0f;

        private static class TransportItem {
            Item item;
            int amount;
            int targetPos;
            float time;
        }
        private final Seq<TransportItem> transportQueue = new Seq<>();

        @Override
        public void created() {
            super.created();
            activeBridges.add(this);
        }

        @Override
        public void onRemoved() {
            super.onRemoved();
            activeBridges.remove(this);
        }

        public void setLinks(Seq<Integer> newLinks) {
            this.links = newLinks;
            if (this.links.size > linkLimit) this.links.truncate(linkLimit);
        }

        private boolean linkValid(int pos) {
            if (pos == -1) return false;
            Building target = world.build(pos);
            return target != null && target.team == team && target.block == BridgeRouter.this && within(target, range);
        }

        @Override
        public boolean shouldConsume() {
            return !links.isEmpty() && enabled;
        }

        @Override
        public void updateTile() {
            super.updateTile();

            for (int i = transportQueue.size - 1; i >= 0; i--) {
                TransportItem t = transportQueue.get(i);
                t.time -= edelta();
                if (t.time <= 0) {
                    Building target = world.build(t.targetPos);
                    if (target != null && target.team == team && target.block == BridgeRouter.this) {
                        int accept = Math.min(t.amount, target.acceptStack(t.item, t.amount, this));
                        if (accept > 0) {
                            target.handleStack(t.item, accept, this);
                        }
                    }
                    transportQueue.remove(i);
                }
            }

            boolean hasLinks = !links.isEmpty();
            boolean consValid = efficiency > 0 && hasLinks;
            powerLoss = Mathf.lerpDelta(powerLoss, consValid ? 0 : 1, 0.08f);
            warmup = Mathf.lerpDelta(warmup, consValid ? 1 : 0, warmupSpeed);

            if (!consValid) {
                if (items.total() > 0) {
                    for (Item item : content.items()) {
                        if (items.get(item) > 0) dump(item);
                    }
                }
                return;
            }

            if (Time.time % FRAME_DELAY < 1) {
                for (int i = links.size - 1; i >= 0; i--) {
                    int pos = links.get(i);
                    if (!linkValid(pos)) {
                        links.remove(i);
                    }
                }

                if (links.isEmpty()) return;

                for (int idx = 0; idx < links.size; idx++) {
                    int pos = links.get(idx);
                    Building target = world.build(pos);
                    if (target == null || target.team != team || target.block != BridgeRouter.this || !within(target, range))
                        continue;

                    for (Item item : content.items()) {
                        int amount = items.get(item);
                        if (amount <= 0) continue;
                        int accept = Math.min(amount, 1);
                        accept = Math.min(accept, target.acceptStack(item, accept, this));
                        if (accept > 0) {
                            TransportItem t = new TransportItem();
                            t.item = item;
                            t.amount = accept;
                            t.targetPos = pos;
                            t.time = transportDelay;
                            transportQueue.add(t);
                            items.remove(item, accept);
                            break;
                        }
                    }
                }
            }
        }

        @Override
        public boolean acceptItem(Building source, Item item) {
            if (items.total() >= itemCapacity) return false;
            return super.acceptItem(source, item);
        }

        @Override
        public void drawConfigure() {
            Drawf.dashCircle(x, y, range - tilesize, Pal.accent);

            float pulse = Mathf.absin(Time.time, 4f, 1f);
            for (int pos : links) {
                if (linkValid(pos)) {
                    Building target = world.build(pos);
                    if (target != null) {
                        Drawf.select(target.x, target.y, target.block.size * tilesize / 2f + 2f, Pal.place);
                    }
                }
            }

            for (Building other : activeBridges) {
                if (other == this || other.team != team || !within(other, range)) continue;
                if (other.block == BridgeRouter.this) {
                    boolean connected = links.contains(other.pos()) || other.<BridgeRouterBuild>as().links.contains(pos());
                    Color color = connected ? Pal.place : Pal.breakInvalid;
                    float extra = connected ? 0f : pulse;
                    Drawf.select(other.x, other.y, other.block.size * tilesize / 2f + 2f + extra, color);
                }
            }

            Draw.z(Layer.block + 1);
            for (int pos : links) {
                if (linkValid(pos)) {
                    Building target = world.build(pos);
                    if (target != null) {
                        drawConnectionLine(this, target, Pal.accent, 1f);
                    }
                }
            }
            for (Building other : activeBridges) {
                if (other == this || other.team != team) continue;
                if (other.block == BridgeRouter.this && other.<BridgeRouterBuild>as().links.contains(pos())) {
                    if (!links.contains(other.pos())) {
                        drawConnectionLine(other, this, Pal.accent, 1f);
                    }
                }
            }

            for (int pos : links) {
                if (linkValid(pos)) {
                    Building target = world.build(pos);
                    if (target != null) {
                        drawMovingArrow(this, target, Pal.place);
                    }
                }
            }
            Draw.reset();
        }

        private void drawConnectionLine(Building from, Building to, Color color, float alpha) {
            float dx = to.x - from.x, dy = to.y - from.y;
            float dist = Mathf.dst(dx, dy);
            if (dist == 0) return;
            float ux = dx / dist, uy = dy / dist;
            float sx = from.x + ux * LINE_INSET1;
            float sy = from.y + uy * LINE_INSET1;
            float ex = to.x - ux * LINE_INSET1;
            float ey = to.y - uy * LINE_INSET1;
            Draw.color(color, alpha);
            Lines.stroke(2f);
            Lines.line(sx, sy, ex, ey);
            Draw.color();
        }

        private void drawMovingArrow(Building from, Building to, Color color) {
            float dx = to.x - from.x, dy = to.y - from.y;
            float dist = Mathf.dst(dx, dy);
            if (dist == 0) return;
            float ux = dx / dist, uy = dy / dist;
            float startX = from.x + ux * LINE_INSET1;
            float startY = from.y + uy * LINE_INSET1;
            float endX = to.x - ux * LINE_INSET1;
            float endY = to.y - uy * LINE_INSET1;
            float bridgeLen = Mathf.dst(endX - startX, endY - startY);
            float speed = 0.02f;
            float progress = (Time.time * speed) % 1.0f;
            float px = startX + ux * bridgeLen * progress;
            float py = startY + uy * bridgeLen * progress;
            float angle = Mathf.angle(dx, dy);
            float rad = angle * Mathf.degRad;
            Draw.color(color);
            Fill.tri(
                px + Mathf.cos(rad) * 3f, py + Mathf.sin(rad) * 3f,
                px + Mathf.cos(rad + Mathf.PI * 0.5f) * 3f, py + Mathf.sin(rad + Mathf.PI * 0.5f) * 3f,
                px + Mathf.cos(rad - Mathf.PI * 0.5f) * 3f, py + Mathf.sin(rad - Mathf.PI * 0.5f) * 3f
            );
        }

        @Override
        public boolean onConfigureBuildTapped(Building other) {
            if (other == this) {
                links.clear();
                return false;
            }
            if (other != null && other.team == team && other.block == BridgeRouter.this && within(other, range)) {
                configure(other.pos());
                return false;
            }
            return true;
        }

        @Override
        public Object config() {
            IntSeq out = new IntSeq(links.size * 2);
            for (int pos : links) {
                Point2 p = Point2.unpack(pos);
                out.add(p.x - tileX(), p.y - tileY());
            }
            return out;
        }

        @Override
        public void write(Writes write) {
            super.write(write);
            write.s(links.size);
            for (int pos : links) {
                write.i(pos);
            }
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            links.clear();
            int size = read.s();
            if (size > linkLimit) size = linkLimit;
            for (int i = 0; i < size; i++) {
                links.add(read.i());
            }
            transportQueue.clear();
        }

        @Override
        public byte version() {
            return 1;
        }

        public float getPowerLoss() {
            return powerLoss;
        }
    }

    private static final Seq<BridgeRouterBuild> activeBridges = new Seq<>();

    static {
        Events.run(Trigger.draw, () -> {
            if (activeBridges.isEmpty()) return;
            float prevZ = Draw.z();
            Draw.z(Layer.block + 1);

            for (BridgeRouterBuild bridge : activeBridges) {
                if (!bridge.isValid() || world.build(bridge.pos()) != bridge) {
                    activeBridges.remove(bridge);
                    continue;
                }
                if (bridge.links.isEmpty()) continue;

                for (int pos : bridge.links) {
                    Building target = world.build(pos);
                    if (target == null || target.team != bridge.team || target.block != bridge.block) continue;

                    float loss = bridge.getPowerLoss();
                    Color outer = LINE_COLOR_OUTER.lerp(POWER_LOSS_COLOR, loss);
                    Color inner = LINE_COLOR_INNER.lerp(POWER_LOSS_INNER_COLOR, bridge.efficiency <= 0 ? 1 : 0);

                    float dx = target.x - bridge.x, dy = target.y - bridge.y;
                    float len = Mathf.dst(dx, dy);
                    if (len == 0) continue;
                    float ux = dx / len, uy = dy / len;
                    float nx = -uy, ny = ux;
                    float halfWidth = LINE_WIDTH_INNER / 2f;

                    float inset = LINE_INSET;
                    float sx = bridge.x + ux * inset;
                    float sy = bridge.y + uy * inset;
                    float ex = target.x - ux * inset;
                    float ey = target.y - uy * inset;

                    float ext = OUTER_EXTEND;
                    float osx = sx - ux * ext, osy = sy - uy * ext;
                    float oex = ex + ux * ext, oey = ey + uy * ext;

                    Draw.color(outer);
                    Lines.stroke(LINE_WIDTH_OUTER);
                    Lines.line(osx + nx * halfWidth, osy + ny * halfWidth, oex + nx * halfWidth, oey + ny * halfWidth);
                    Lines.line(osx - nx * halfWidth, osy - ny * halfWidth, oex - nx * halfWidth, oey - ny * halfWidth);

                    Draw.color(inner);
                    Lines.stroke(LINE_WIDTH_INNER);
                    Lines.line(sx, sy, ex, ey);

                    Draw.color(outer);
                    Lines.stroke(CAP_LINE_WIDTH);
                    Lines.line(osx + nx * halfWidth, osy + ny * halfWidth, osx - nx * halfWidth, osy - ny * halfWidth);
                    Lines.line(oex + nx * halfWidth, oey + ny * halfWidth, oex - nx * halfWidth, oey - ny * halfWidth);

                    float startX = bridge.x + ux * inset;
                    float startY = bridge.y + uy * inset;
                    float endX = target.x - ux * inset;
                    float endY = target.y - uy * inset;
                    float bridgeLen = Mathf.dst(endX - startX, endY - startY);
                    int arrows = (int) (bridgeLen / arrowSpacing);
                    if (arrows > 0) {
                        float angle = Mathf.angle(dx, dy);
                        float rad = angle * Mathf.degRad;
                        boolean powered = bridge.efficiency > 0;
                        for (int a = 0; a < arrows; a++) {
                            float px = startX + ux * a * arrowSpacing;
                            float py = startY + uy * a * arrowSpacing;
                            float timeScl = powered ? arrowTimeScl : 4.2f;
                            float alpha = Mathf.absin(a - Time.time / timeScl, arrowPeriod, 1f);
                            if (alpha <= 0.01f) continue;
                            float finalAlpha = powered ? alpha : 0.5f;
                            Color color = ARROW_COLOR.lerp(POWER_LOSS_COLOR, loss);
                            Draw.color(color, finalAlpha);
                            Fill.tri(
                                px + Mathf.cos(rad) * arrowSize, py + Mathf.sin(rad) * arrowSize,
                                px + Mathf.cos(rad + Mathf.PI * 0.5f) * arrowSize, py + Mathf.sin(rad + Mathf.PI * 0.5f) * arrowSize,
                                px + Mathf.cos(rad - Mathf.PI * 0.5f) * arrowSize, py + Mathf.sin(rad - Mathf.PI * 0.5f) * arrowSize
                            );
                        }
                    }
                }
            }
            Draw.z(prevZ);
        });
    }
}