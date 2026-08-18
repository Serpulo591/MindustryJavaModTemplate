package example.world.blocks;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import arc.math.geom.Point2;
import arc.struct.Seq;
import arc.util.Time;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.Vars;
import mindustry.gen.Building;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.type.Item;
import mindustry.ui.Bar;
import mindustry.world.blocks.storage.StorageBlock;
import mindustry.world.meta.Stat;
import mindustry.world.meta.StatUnit;

import static mindustry.Vars.*;

public class BridgeRouter extends StorageBlock {
    // ---- 常量 ----
    private static final Color POWER_LOSS_COLOR = Color.valueOf("f49fa680");
    private static final Color POWER_LOSS_INNER_COLOR = Color.valueOf("ec767859");
    private static final Color LINE_COLOR_OUTER = Color.valueOf("c0edf4");
    private static final Color LINE_COLOR_INNER = Color.valueOf("a1d7ecb3");
    private static final Color ARROW_COLOR = Color.valueOf("c0edf4");
    private static final float LINE_WIDTH_OUTER = 1f;
    private static final float LINE_WIDTH_INNER = 4f;
    private static final float CAP_LINE_WIDTH = 1f;
    private static final float ARROW_SIZE = 2.4f;
    private static final float ARROW_SPACING = 4f;
    private static final float ARROW_PERIOD = 0.4f;
    private static final float ARROW_TIME_SCL = 12.6f;
    private static final float LINE_INSET = 4f;
    private static final float OUTER_EXTEND = 1.5f;
    private static final float LINE_INSET1 = 2f;
    private static final int LINK_LIMIT = 4;
    private static final int FRAME_DELAY = 1;

    public float range = 64f;
    public float warmupSpeed = 0.05f;

    // 全局活跃桥列表
    public static final Seq<BridgeBuild> activeBridges = new Seq<>();

    public BridgeRouter(String name) {
        super(name);
        update = true;
        solid = true;
        hasItems = true;
        configurable = true;

        // 配置序列化：接收相对坐标列表 (Seq)
        config(Seq.class, (BridgeBuild tile, Seq seq) -> {
            Seq<Integer> links = new Seq<>();
            for (int i = 0; i < seq.size; i += 2) {
                int x = ((Number) seq.get(i)).intValue();
                int y = ((Number) seq.get(i + 1)).intValue();
                int pos = Point2.pack(x + tile.tileX(), y + tile.tileY());
                links.add(pos);
            }
            tile.setLink(links);
        });

        // 配置单个连接点（添加/移除）
        config(Integer.class, (BridgeBuild tile, Integer pos) -> {
            Seq<Integer> links = tile.getLink();
            if (links.contains(pos)) {
                links.remove(pos);
            } else {
                if (links.size >= LINK_LIMIT) return;
                links.add(pos);
                Building target = world.build(pos);
                if (target instanceof BridgeBuild && target.block == tile.block) {
                    BridgeBuild other = (BridgeBuild) target;
                    Seq<Integer> otherLinks = other.getLink();
                    if (otherLinks.contains(tile.pos())) {
                        otherLinks.remove(tile.pos());
                        other.setLink(otherLinks);
                    }
                }
            }
            tile.setLink(links);
        });

        buildType = BridgeBuild::new;
    }

    @Override
    public void setStats() {
        super.setStats();
        stats.add(Stat.range, range / tilesize, StatUnit.blocks);
        stats.add(Stat.powerConnections, LINK_LIMIT, StatUnit.none);
    }

    @Override
    public void setBars() {
        super.setBars();
        addBar("connections", (BridgeBuild e) ->
                new Bar(
                        () -> Core.bundle.format("bar.powerlines", e.getLink().size, LINK_LIMIT),
                        () -> Pal.accent,
                        () -> (float) e.getLink().size / LINK_LIMIT
                )
        );
    }

    @Override
    public void drawPlace(int x, int y, int rotation, boolean valid) {
        Drawf.dashCircle(x * tilesize, y * tilesize, range - tilesize, Pal.accent);
    }

    @Override
    public boolean outputsItems() {
        return true;
    }

    // ============== 内部建筑类 ==============
    public class BridgeBuild extends StorageBuild {
        private Seq<Integer> links = new Seq<>();
        private float warmup = 0f;
        private float rotateSpeed = 0f;
        private int sendIndex = 0;
        private Seq<TransportData> transport = new Seq<>();
        private float powerLoss = 0f;

        private static class TransportData {
            Item item;
            int amount;
            int target;
            int time;
        }

        public Seq<Integer> getLink() { return links == null ? new Seq<>() : links; }
        public void setLink(Seq<Integer> v) {
            if (v == null) links = new Seq<>();
            else links = v;
            if (links.size > LINK_LIMIT) links.truncate(LINK_LIMIT);
        }
        public float getPowerLoss() { return powerLoss; }

        @Override
        public void created() {
            activeBridges.add(this);
        }

        @Override
        public void onRemoved() {
            activeBridges.remove(this);
        }

        @Override
        public boolean shouldConsume() {
            return !links.isEmpty();
        }

        @Override
        public boolean acceptItem(Building source, Item item) {
            if (items.total() >= itemCapacity) return false;
            return super.acceptItem(source, item);
        }

        @Override
        public void updateTile() {
            efficiency = Mathf.lerpDelta(efficiency, shouldConsume() ? 1f : 0f, warmupSpeed);

            // 处理运输中的物品
            for (int i = transport.size - 1; i >= 0; i--) {
                TransportData t = transport.get(i);
                if (--t.time <= 0) {
                    Building target = world.build(t.target);
                    if (target instanceof BridgeBuild && target.team == team && target.block == block) {
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
                warmup = Mathf.lerpDelta(warmup, 0f, warmupSpeed);
                rotateSpeed = Mathf.lerpDelta(rotateSpeed, 0f, warmupSpeed);
                return;
            }

            if (!consValid) {
                warmup = Mathf.lerpDelta(warmup, 0f, warmupSpeed);
                rotateSpeed = Mathf.lerpDelta(rotateSpeed, 0f, warmupSpeed);
                return;
            }

            if (items.total() <= 0) {
                warmup = Mathf.lerpDelta(warmup, 0f, warmupSpeed);
                return;
            }

            if (Time.time % FRAME_DELAY < 1) {
                // 清理无效连接
                for (int i = links.size - 1; i >= 0; i--) {
                    Building target = world.build(links.get(i));
                    if (!(target instanceof BridgeBuild) || target.team != team || target.block != block || !within(target, range)) {
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
                        if (target instanceof BridgeBuild && target.team == team && target.block == block && within(target, range)) {
                            for (Item item : content.items()) {
                                int amount = items.get(item);
                                if (amount <= 0) continue;
                                int accept = Math.min(amount, target.acceptStack(item, 1, this));
                                if (accept > 0) {
                                    TransportData t = new TransportData();
                                    t.item = item;
                                    t.amount = accept;
                                    t.target = target.pos();
                                    t.time = 60;
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

            warmup = Mathf.lerpDelta(warmup, links.isEmpty() ? 0f : 1f, warmupSpeed);
            rotateSpeed = Mathf.lerpDelta(rotateSpeed, itemSent ? 1f : 0f, warmupSpeed);
        }
        // ========== 绘制方法（全局静态） ==========
public static void drawAllBridges() {
    float prevZ = Draw.z();
    Draw.z(Layer.block + 1);

    for (int i = activeBridges.size - 1; i >= 0; i--) {
        BridgeBuild bridge = activeBridges.get(i);
        if (!bridge.isValid() || world.build(bridge.pos()) != bridge) {
            activeBridges.remove(i);
            continue;
        }
        Seq<Integer> links = bridge.getLink();
        if (links.isEmpty()) continue;

        for (int j = links.size - 1; j >= 0; j--) {
            int pos = links.get(j);
            Building target = world.build(pos);
            if (!(target instanceof BridgeBuild) || target.team != bridge.team || target.block != bridge.block) {
                links.remove(j);
                continue;
            }

            float dx = target.x - bridge.x;
            float dy = target.y - bridge.y;
            float length = Mathf.dst(dx, dy);
            if (length == 0) continue;

            float ux = dx / length;
            float uy = dy / length;
            float nx = -uy;
            float ny = ux;
            float offset = LINE_WIDTH_INNER / 2f;

            float insetX = ux * LINE_INSET;
            float insetY = uy * LINE_INSET;
            float innerStartX = bridge.x + insetX;
            float innerStartY = bridge.y + insetY;
            float innerEndX = target.x - insetX;
            float innerEndY = target.y - insetY;

            float extendX = ux * OUTER_EXTEND;
            float extendY = uy * OUTER_EXTEND;
            float outerStartX = innerStartX - extendX;
            float outerStartY = innerStartY - extendY;
            float outerEndX = innerEndX + extendX;
            float outerEndY = innerEndY + extendY;

            Color outerColor = LINE_COLOR_OUTER.lerp(POWER_LOSS_COLOR, bridge.getPowerLoss());
            Draw.color(outerColor);
            Lines.stroke(LINE_WIDTH_OUTER);
            Lines.line(outerStartX + nx * offset, outerStartY + ny * offset,
                       outerEndX + nx * offset, outerEndY + ny * offset);
            Lines.line(outerStartX - nx * offset, outerStartY - ny * offset,
                       outerEndX - nx * offset, outerEndY - ny * offset);

            Color innerColor = LINE_COLOR_INNER.lerp(POWER_LOSS_INNER_COLOR,
                    bridge.efficiency <= 0 ? 1f : 0f);
            Draw.color(innerColor);
            Lines.stroke(LINE_WIDTH_INNER);
            Lines.line(innerStartX, innerStartY, innerEndX, innerEndY);

            Draw.color(outerColor);
            Lines.stroke(CAP_LINE_WIDTH);
            Lines.line(outerStartX + nx * offset, outerStartY + ny * offset,
                       outerStartX - nx * offset, outerStartY - ny * offset);
            Lines.line(outerEndX + nx * offset, outerEndY + ny * offset,
                       outerEndX - nx * offset, outerEndY - ny * offset);

            drawFlowArrowsWithInset(bridge, target, bridge.efficiency > 0,
                                    bridge.getPowerLoss(), LINE_INSET);
        }
    }
    Draw.z(prevZ);
}

private static void drawFlowArrowsWithInset(BridgeBuild from, Building to, boolean powered,
                                            float powerLoss, float inset) {
    float dx = to.x - from.x;
    float dy = to.y - from.y;
    float totalDist = Mathf.dst(dx, dy);
    if (totalDist <= 0) return;

    float normX = dx / totalDist;
    float normY = dy / totalDist;
    float startX = from.x + normX * inset;
    float startY = from.y + normY * inset;
    float endX = to.x - normX * inset;
    float endY = to.y - normY * inset;
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

        Color arrowColor = ARROW_COLOR.lerp(POWER_LOSS_COLOR, powerLoss);
        Draw.color(arrowColor, finalAlpha);
        float rad = angle * Mathf.degRad;
        Fill.tri(
                px + Mathf.cos(rad) * ARROW_SIZE, py + Mathf.sin(rad) * ARROW_SIZE,
                px + Mathf.cos(rad + Mathf.PI * 0.5f) * ARROW_SIZE, py + Mathf.sin(rad + Mathf.PI * 0.5f) * ARROW_SIZE,
                px + Mathf.cos(rad - Mathf.PI * 0.5f) * ARROW_SIZE, py + Mathf.sin(rad - Mathf.PI * 0.5f) * ARROW_SIZE
        );
    }
}

private static void drawMovingArrow(Building from, Building to, Color color, float inset) {
    float dx = to.x - from.x;
    float dy = to.y - from.y;
    float dist = Mathf.dst(dx, dy);
    if (dist <= 0) return;

    float normX = dx / dist;
    float normY = dy / dist;
    float startX = from.x + normX * inset;
    float startY = from.y + normY * inset;
    float endX = to.x - normX * inset;
    float endY = to.y - normY * inset;
    float bridgeLength = Mathf.dst(endX - startX, endY - startY);
    float speed = 0.02f;
    float progress = (Time.time * speed) % 1.0f;
    float px = startX + normX * bridgeLength * progress;
    float py = startY + normY * bridgeLength * progress;
    float angle = Mathf.angle(dx, dy);
    float rad = angle * Mathf.degRad;
    float arrowSize = 2.5f;

    Draw.color(color);
    Fill.tri(
            px + Mathf.cos(rad) * arrowSize, py + Mathf.sin(rad) * arrowSize,
            px + Mathf.cos(rad + Mathf.PI * 0.5f) * arrowSize, py + Mathf.sin(rad + Mathf.PI * 0.5f) * arrowSize,
            px + Mathf.cos(rad - Mathf.PI * 0.5f) * arrowSize, py + Mathf.sin(rad - Mathf.PI * 0.5f) * arrowSize
    );
}
        // ========== 配置模式绘制 ==========
        @Override
        public void drawConfigure() {
            float pulse = Mathf.absin(Time.time, 4f, 1f);
            Draw.color(Pal.accent);
            Lines.stroke(1f);
            Drawf.select(x, y, tile.block().size * tilesize / 2f + 2f, Pal.accent);

            // 绘制已连接目标
            for (int i = 0; i < links.size; i++) {
                int pos = links.get(i);
                Building target = world.build(pos);
                if (target instanceof BridgeBuild && target.team == team && within(target, range)) {
                    Drawf.select(target.x, target.y, target.block.size * tilesize / 2f + 2f, Pal.place);
                }
            }

            // 显示可连接但未连接的目标
            int myPos = pos();
            for (BridgeBuild other : activeBridges) {
                if (other == this || other.team != team || !within(other, range)) continue;
                boolean connected = links.contains(other.pos()) || other.getLink().contains(myPos);
                if (!connected) {
                    Drawf.select(other.x, other.y, other.block.size * tilesize / 2f + 2f + pulse, Pal.breakInvalid);
                }
            }

            // 配置辅助线（灰色粗线）
            Draw.z(Layer.block + 1);
            for (int i = 0; i < links.size; i++) {
                int pos = links.get(i);
                Building target = world.build(pos);
                if (target instanceof BridgeBuild && target.team == team && within(target, range)) {
                    drawConfigLine(this, target, Color.valueOf("#454545"), 2.5f);
                }
            }
            for (BridgeBuild other : activeBridges) {
                if (other == this || other.team != team) continue;
                if (other.getLink().contains(myPos) && !links.contains(other.pos())) {
                    drawConfigLine(other, this, Color.valueOf("#454545"), 2.5f);
                }
            }

            // 彩色指示线（紫色/金色）
            Draw.z(Layer.block + 1);
            for (int i = 0; i < links.size; i++) {
                int pos = links.get(i);
                Building target = world.build(pos);
                if (target instanceof BridgeBuild && target.team == team && within(target, range)) {
                    drawConfigLine(this, target, Color.valueOf("#662fff"), 1f);
                }
            }
            for (BridgeBuild other : activeBridges) {
                if (other == this || other.team != team) continue;
                if (other.getLink().contains(myPos) && !links.contains(other.pos())) {
                    drawConfigLine(other, this, Color.valueOf("#ffd16d"), 1f);
                }
            }

            // 配置模式下的移动箭头
            Draw.z(Layer.block + 1);
            for (int i = 0; i < links.size; i++) {
                int pos = links.get(i);
                Building target = world.build(pos);
                if (target instanceof BridgeBuild && target.team == team && within(target, range)) {
                    drawMovingArrow(this, target, Pal.place, LINE_INSET1 - 2);
                }
            }
            for (BridgeBuild other : activeBridges) {
                if (other == this || other.team != team) continue;
                if (other.getLink().contains(myPos) && !links.contains(other.pos())) {
                    drawMovingArrow(other, this, Pal.accent, LINE_INSET1 - 2);
                }
            }

            Drawf.dashCircle(x, y, range - tilesize, Pal.accent);
        }

        private void drawConfigLine(Building from, Building to, Color color, float width) {
            float dx = to.x - from.x;
            float dy = to.y - from.y;
            float dist = Mathf.dst(dx, dy);
            if (dist == 0) return;
            float ux = dx / dist;
            float uy = dy / dist;
            float sx = from.x + ux * LINE_INSET1;
            float sy = from.y + uy * LINE_INSET1;
            float ex = to.x - ux * LINE_INSET1;
            float ey = to.y - uy * LINE_INSET1;
            Draw.color(color);
            Lines.stroke(width);
            Lines.line(sx, sy, ex, ey);
        }

        // ========== 配置交互 ==========
        @Override
        public boolean onConfigureBuildTapped(Building other) {
            if (this == other) {
                links.clear();
                return false;
            }
            if (other instanceof BridgeBuild && other.team == team && other.block == block &&
                    within(other, range)) {
                configure(other.pos());
                return false;
            }
            return true;
        }

        @Override
        public Object config() {
            Seq<Integer> out = new Seq<>(links.size * 2);
            for (int i = 0; i < links.size; i++) {
                Point2 p = Point2.unpack(links.get(i)).sub(tile.x, tile.y);
                out.add(p.x);
                out.add(p.y);
            }
            return out;
        }

        @Override
        public void write(Writes write) {
            super.write(write);
            write.s(links.size);
            for (int i = 0; i < links.size; i++) write.i(links.get(i));
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            links = new Seq<>();
            int size = read.s();
            if (size > LINK_LIMIT) size = LINK_LIMIT;
            for (int i = 0; i < size; i++) links.add(read.i());
            transport = new Seq<>();
        }

        @Override
        public byte version() { return 1; }
    }
}