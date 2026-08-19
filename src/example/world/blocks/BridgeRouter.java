package example.world.blocks;

import arc.graphics.Color;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.math.geom.*;
import arc.struct.IntSeq;
import arc.struct.Seq;
import arc.util.*;
import arc.util.io.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.type.*;
import mindustry.ui.Bar;
import mindustry.world.*;
import mindustry.world.blocks.storage.StorageBlock;
import mindustry.world.meta.*;

import static mindustry.Vars.*;

public class BridgeRouter extends StorageBlock {
    // --- 常量（与 JS 完全一致） ---
    public static final int range = 64;
    public static final int linkLimit = 4;
    public static final float warmupSpeed = 0.05f;
    public static final float arrowTimeScl = 12.6f;
    public static final float arrowSpacing = 4f;
    public static final float arrowOffset = 2f;
    public static final float arrowPeriod = 0.4f;
    public static final float arrowSize = 2.4f;
    public static final int FRAME_DELAY = 1;
    public static final float LINE_INSET = 4f;
    public static final float OUTER_EXTEND = 1.5f;
    public static final float LINE_INSET1 = 2f;
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
        envEnabled = Env.any;
        allowConfigInventory = false;

        // config(IntSeq)
        config(IntSeq.class, (BridgeRouterBuild tile, IntSeq seq) -> {
            Seq<Integer> links = new Seq<>();
            for (int i = 0; i < seq.size; i += 2) {
                int x = seq.get(i) + tile.tileX();
                int y = seq.get(i + 1) + tile.tileY();
                int pos = Point2.pack(x, y);
                links.add(pos);
            }
            tile.setLink(links);
        });

        // config(Integer)
        config(Integer.class, (BridgeRouterBuild tile, Integer value) -> {
            int pos = value;
            Seq<Integer> links = new Seq<>(tile.getLink());
            Integer intObj = pos;
            if (links.contains(intObj)) {
                links.remove(intObj);
            } else {
                if (links.size >= linkLimit) return;
                links.add(intObj);
                Building targetBuild = world.build(pos);
                if (targetBuild != null && targetBuild.block == BridgeRouter.this) {
                    BridgeRouterBuild targetTile = (BridgeRouterBuild) targetBuild;
                    Seq<Integer> targetLinks = targetTile.getLink();
                    Integer myPos = tile.pos();
                    if (targetLinks.contains(myPos)) {
                        targetLinks.remove(myPos);
                        targetTile.setLink(targetLinks);
                    }
                }
            }
            tile.setLink(links);
        });
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
                () -> "Connections: " + e.getLink().size + "/" + linkLimit,
                () -> Pal.accent,
                () -> (float) e.getLink().size / linkLimit
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

    // --- 内部 Building 类 ---
    public class BridgeRouterBuild extends StorageBuild {
        private Seq<Integer> links = new Seq<>();
        private float warmup = 0f;
        private float rotateSpeed = 0f;
        private int sendIndex = 0;
        private Seq<TransportItem> transport = new Seq<>();
        private float powerLoss = 0f;

        private static class TransportItem {
            Item item;
            int amount;
            int target;
            float time;
        }

        public Seq<Integer> getLink() { return links; }
public void setLink(Seq<Integer> v) {
    links = new Seq<>();
    if (v != null) {
        for (int i = 0; i < v.size; i++) links.add(v.get(i));
    }
    if (links.size > linkLimit) links.truncate(linkLimit);
}
        public float getPowerLoss() { return powerLoss; }

        @Override
        public boolean shouldConsume() {
            return !getLink().isEmpty();
        }

        // 注意：父类没有这个方法，移除 @Override
        public void updateEfficiency() {
            this.efficiency = Mathf.lerpDelta(this.efficiency, shouldConsume() ? 1 : 0, warmupSpeed);
        }

        @Override
        public void created() {
            super.created();
            activeBridges.add(this);
        }

@Override
public void onRemoved() {
    super.onRemoved();
    // 先复制一份，避免遍历时修改列表导致崩溃
    Seq<BridgeRouterBuild> copy = new Seq<>(activeBridges);
    int myPos = pos();
    for (BridgeRouterBuild other : copy) {
        if (other == this) continue;
        Seq<Integer> otherLinks = other.getLink();
        if (otherLinks.contains(myPos)) {
            otherLinks.remove(myPos);
            other.setLink(otherLinks);
        }
    }
    activeBridges.remove(this);
}

        @Override
        public boolean acceptItem(Building source, Item item) {
            if (items.total() >= this.block.itemCapacity) return false;
            return super.acceptItem(source, item);
        }

        @Override
        public void updateTile() {
            // 处理运输队列
            for (int i = transport.size - 1; i >= 0; i--) {
                TransportItem t = transport.get(i);
                t.time -= edelta();
                if (t.time <= 0) {
                    Building target = world.build(t.target);
                    if (target != null && target.team == team && target.block == BridgeRouter.this) {
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
            powerLoss = Mathf.lerpDelta(powerLoss, consValid ? 0 : 1, 0.08f);

            if (links.isEmpty()) {
                if (items.total() > 0) {
                    for (Item item : content.items()) {
                        if (items.get(item) > 0) dump(item);
                    }
                }
                warmup = Mathf.lerpDelta(warmup, 0, warmupSpeed);
                rotateSpeed = Mathf.lerpDelta(rotateSpeed, 0, warmupSpeed);
            }

            if (!consValid) {
                warmup = Mathf.lerpDelta(warmup, 0, warmupSpeed);
                rotateSpeed = Mathf.lerpDelta(rotateSpeed, 0, warmupSpeed);
                return;
            }

            if (items.total() <= 0) {
                warmup = Mathf.lerpDelta(warmup, 0, warmupSpeed);
                return;
            }

            if (!links.isEmpty() && Time.time % FRAME_DELAY < 1) {
                // 清理无效链接
                int i = links.size;
                while (i-- > 0) {
                    Building target = world.build(links.get(i));
                    if (target == null || target.team != team || target.block != BridgeRouter.this || !within(target, range)) {
                        links.remove(i);
                    }
                }

                if (!links.isEmpty()) {
                    if (sendIndex >= links.size) sendIndex = 0;
                    int startIndex = sendIndex;
                    boolean sent = false;
                    do {
                        Building target = world.build(links.get(sendIndex));
                        if (target != null && target.team == team && target.block == BridgeRouter.this && within(target, range)) {
                            for (Item item : content.items()) {
                                int amount = items.get(item);
                                if (amount <= 0) continue;
                                // 修正 Math.min 三参数问题
                                int acceptAmount = Math.min(amount, 1);
                                int accept = target.acceptStack(item, acceptAmount, this);
                                if (accept > 0) {
                                    TransportItem t = new TransportItem();
                                    t.item = item;
                                    t.amount = accept;
                                    t.target = target.pos();
                                    t.time = 60f;
                                    transport.add(t);
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

            warmup = Mathf.lerpDelta(warmup, links.isEmpty() ? 0 : 1, warmupSpeed);
            rotateSpeed = Mathf.lerpDelta(rotateSpeed, itemSent ? 1 : 0, warmupSpeed);
        }

        // --- 绘制所有链接（在 draw() 中调用）---
        @Override
        public void draw() {
            super.draw();
            Draw.z(Layer.block + 1);
            // 绘制所有有效链接（包括自己的和指向自己的）
            drawAllLinks();
            Draw.z(Layer.block);
        }

        private void drawAllLinks() {
            if (activeBridges.isEmpty()) return;

            // 1. 绘制自己的链接
            for (int pos : links) {
                Building target = world.build(pos);
                if (target == null || target.team != team || target.block != BridgeRouter.this) continue;
                drawLinkLine(this, target);
            }

        // 绘制单条连接线（含箭头）
        private void drawLinkLine(Building from, Building to) {
            float loss = getPowerLoss();
            Color outer = LINE_COLOR_OUTER.lerp(POWER_LOSS_COLOR, loss);
            Color inner = LINE_COLOR_INNER.lerp(POWER_LOSS_INNER_COLOR, efficiency <= 0 ? 1 : 0);

            float dx = to.x - from.x, dy = to.y - from.y;
            float length = Mathf.dst(dx, dy);
            if (length == 0) return;

            float ux = dx / length, uy = dy / length;
            float nx = -uy, ny = ux;
            float offset = LINE_WIDTH_INNER / 2f;

            float insetX = ux * LINE_INSET, insetY = uy * LINE_INSET;
            float innerStartX = from.x + insetX, innerStartY = from.y + insetY;
            float innerEndX = to.x - insetX, innerEndY = to.y - insetY;

            float extendX = ux * OUTER_EXTEND, extendY = uy * OUTER_EXTEND;
            float outerStartX = innerStartX - extendX, outerStartY = innerStartY - extendY;
            float outerEndX = innerEndX + extendX, outerEndY = innerEndY + extendY;

            Draw.color(outer);
            Lines.stroke(LINE_WIDTH_OUTER);
            Lines.line(outerStartX + nx * offset, outerStartY + ny * offset, outerEndX + nx * offset, outerEndY + ny * offset);
            Lines.line(outerStartX - nx * offset, outerStartY - ny * offset, outerEndX - nx * offset, outerEndY - ny * offset);

            Draw.color(inner);
            Lines.stroke(LINE_WIDTH_INNER);
            Lines.line(innerStartX, innerStartY, innerEndX, innerEndY);

            Draw.color(outer);
            Lines.stroke(CAP_LINE_WIDTH);
            Lines.line(outerStartX + nx * offset, outerStartY + ny * offset, outerStartX - nx * offset, outerStartY - ny * offset);
            Lines.line(outerEndX + nx * offset, outerEndY + ny * offset, outerEndX - nx * offset, outerEndY - ny * offset);

            // 流动箭头
            drawFlowArrows(from, to, efficiency > 0, loss);
        }

        private void drawFlowArrows(Building from, Building to, boolean powered, float loss) {
            float dx = to.x - from.x, dy = to.y - from.y;
            float totalDist = Mathf.dst(dx, dy);
            if (totalDist <= 0) return;

            float normX = dx / totalDist, normY = dy / totalDist;
            float startX = from.x + normX * LINE_INSET, startY = from.y + normY * LINE_INSET;
            float endX = to.x - normX * LINE_INSET, endY = to.y - normY * LINE_INSET;
            float bridgeLength = Mathf.dst(endX - startX, endY - startY);

            int arrows = (int) Math.floor(bridgeLength / arrowSpacing);
            if (arrows <= 0) return;

            float angle = Mathf.angle(dx, dy);
            float rad = angle * Mathf.degRad;

            for (int a = 0; a < arrows; a++) {
                float px = startX + normX * a * arrowSpacing;
                float py = startY + normY * a * arrowSpacing;
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

        // --- 配置绘制（保持不变） ---
        @Override
        public void drawConfigure() {
            float pulse = Mathf.absin(Time.time, 4f, 1f);
            Draw.color(Pal.accent);
            Lines.stroke(1f);
            Drawf.select(x, y, tile.block().size * tilesize / 2f + 2f, Pal.accent);

            for (int pos : links) {
                if (linkValid(this, pos)) {
                    Building target = world.build(pos);
                    if (target == null) continue;
                    Drawf.select(target.x, target.y, target.block.size * tilesize / 2f + 2f, Pal.place);
                }
            }

            Integer myPosInt = pos();
            for (BridgeRouterBuild other : activeBridges) {
                if (!other.isValid() || other == this || other.team != team || !within(other, range)) continue;
                boolean connected = false;
                for (int p : links) {
                    if (p == other.pos()) { connected = true; break; }
                }
                if (!connected && other.getLink().contains(myPosInt)) {
                    connected = true;
                }
                if (!connected) {
                    Drawf.select(other.x, other.y, other.block.size * tilesize / 2f + 2f + pulse, Pal.breakInvalid);
                }
            }

            Draw.z(Layer.block + 1);
            // 灰色粗线（自己的链接）
            for (int pos : links) {
                if (linkValid(this, pos)) {
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
            }
            // 灰色粗线（指向自己的链接）
            for (BridgeRouterBuild other : activeBridges) {
                if (!other.isValid() || other == this || other.team != team) continue;
                if (!other.getLink().contains(myPosInt)) continue;
                if (links.contains(other.pos())) continue;
                float dx = x - other.x, dy = y - other.y;
                float dist = Mathf.dst(dx, dy);
                if (dist == 0) continue;
                float ux = dx / dist, uy = dy / dist;
                float sx = other.x + ux * LINE_INSET1, sy = other.y + uy * LINE_INSET1;
                float ex = x - ux * LINE_INSET1, ey = y - uy * LINE_INSET1;
                Draw.color(Color.valueOf("454545"));
                Lines.stroke(2.5f);
                Lines.line(sx, sy, ex, ey);
            }

            // 小方块标记
            for (int pos : links) {
                if (linkValid(this, pos)) {
                    Building target = world.build(pos);
                    if (target == null) continue;
                    Drawf.square(target.x, target.y, 1f, Pal.place);
                }
            }
            for (BridgeRouterBuild other : activeBridges) {
                if (!other.isValid() || other == this || other.team != team || !within(other, range)) continue;
                if (!other.getLink().contains(myPosInt)) continue;
                if (links.contains(other.pos())) continue;
                Drawf.square(other.x, other.y, 1f, Pal.accent);
            }

            // 彩色细线（紫色和黄色）
            for (int pos : links) {
                if (linkValid(this, pos)) {
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
            }
            for (BridgeRouterBuild other : activeBridges) {
                if (!other.isValid() || other == this || other.team != team) continue;
                if (!other.getLink().contains(myPosInt)) continue;
                if (links.contains(other.pos())) continue;
                float dx = x - other.x, dy = y - other.y;
                float dist = Mathf.dst(dx, dy);
                if (dist == 0) continue;
                float ux = dx / dist, uy = dy / dist;
                float sx = other.x + ux * LINE_INSET1, sy = other.y + uy * LINE_INSET1;
                float ex = x - ux * LINE_INSET1, ey = y - uy * LINE_INSET1;
                Draw.color(Color.valueOf("ffd16d"));
                Lines.stroke(1f);
                Lines.line(sx, sy, ex, ey);
            }

            // 流动箭头（配置模式下也显示）
            for (int pos : links) {
                if (linkValid(this, pos)) {
                    Building target = world.build(pos);
                    if (target == null) continue;
                    drawMovingArrow(this, target, Pal.place, LINE_INSET1 - 2);
                }
            }
            for (BridgeRouterBuild other : activeBridges) {
                if (!other.isValid() || other == this || other.team != team) continue;
                if (!other.getLink().contains(myPosInt)) continue;
                if (links.contains(other.pos())) continue;
                drawMovingArrow(other, this, Pal.accent, LINE_INSET1 - 2);
            }

            Drawf.dashCircle(x, y, range - tilesize, Pal.accent);
        }

        private boolean linkValid(BridgeRouterBuild the, int pos) {
            if (pos == -1) return false;
            Building target = world.build(pos);
            return target != null && target.team == the.team && the.within(target, range) && target.block == BridgeRouter.this;
        }

        private void drawMovingArrow(Building from, Building to, Color color, float inset) {
            float dx = to.x - from.x, dy = to.y - from.y;
            float dist = Mathf.dst(dx, dy);
            if (dist <= 0) return;

            float normX = dx / dist, normY = dy / dist;
            float startX = from.x + normX * inset, startY = from.y + normY * inset;
            float endX = to.x - normX * inset, endY = to.y - normY * inset;
            float bridgeLength = Mathf.dst(endX - startX, endY - startY);

            float speed = 0.02f;
            float progress = (Time.time * speed) % 1f;

            float px = startX + normX * bridgeLength * progress;
            float py = startY + normY * bridgeLength * progress;

            float angle = Mathf.angle(dx, dy);
            float rad = angle * Mathf.degRad;
            float arrowSizeLocal = 2.5f;

            Draw.color(color);
            Fill.tri(
                px + Mathf.cos(rad) * arrowSizeLocal, py + Mathf.sin(rad) * arrowSizeLocal,
                px + Mathf.cos(rad + Mathf.PI * 0.5f) * arrowSizeLocal, py + Mathf.sin(rad + Mathf.PI * 0.5f) * arrowSizeLocal,
                px + Mathf.cos(rad - Mathf.PI * 0.5f) * arrowSizeLocal, py + Mathf.sin(rad - Mathf.PI * 0.5f) * arrowSizeLocal
            );
        }

        @Override
        public boolean onConfigureBuildTapped(Building other) {
            if (other == this) {
                setLink(new Seq<>());
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
            links = new Seq<>();
            int size = read.s();
            if (size > linkLimit) size = linkLimit;
            for (int i = 0; i < size; i++) {
                links.add(read.i());
            }
            transport = new Seq<>();
        }

        @Override
        public byte version() {
            return 1;
        }
    }

    // 静态活跃桥列表（用于绘制）
    private static final Seq<BridgeRouterBuild> activeBridges = new Seq<>();
}