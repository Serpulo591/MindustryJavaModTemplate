package mindustry.world.blocks.distribution;

import arc.graphics.Color;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.math.geom.*;
import arc.struct.Seq;
import arc.util.*;
import arc.util.io.*;
import mindustry.annotations.Annotations.*;
import mindustry.core.*;
import mindustry.entities.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.meta.*;
import mindustry.world.blocks.storage.StorageBlock;

import static mindustry.Vars.*;

public class BridgeRouter extends StorageBlock {
    // ----- 配置常量（与 JS 对应）-----
    public int range = 64;                 // 连接最大范围（像素）
    public int linkLimit = 4;              // 最大链接数
    public float warmupSpeed = 0.05f;      // 启动/停止平滑速度
    public float transportDelay = 60f;     // 物品传输延迟（tick）
    public float arrowTimeScl = 12.6f;     // 箭头动画速度
    public float arrowSpacing = 4f;
    public float arrowOffset = 2f;
    public float arrowPeriod = 0.4f;
    public float arrowSize = 2.4f;

    // 颜色（来自 JS）
    public static final Color POWER_LOSS_COLOR = Color.valueOf("f49fa680");
    public static final Color POWER_LOSS_INNER_COLOR = Color.valueOf("ec767859");
    public static final Color LINE_COLOR_OUTER = Color.valueOf("c0edf4");
    public static final Color LINE_COLOR_INNER = Color.valueOf("a1d7ecb3");
    public static final Color ARROW_COLOR = Color.valueOf("c0edf4");

    // ----- 构造 -----
    public BridgeRouter(String name) {
        super(name);
        update = true;
        solid = true;
        hasItems = true;
        hasPower = true;
        configurable = true;
        saveConfig = true;
        itemCapacity = 30;                // 可调整
        group = BlockGroup.transportation;
        priority = TargetPriority.transport;
        envEnabled = Env.any;
        // 禁用默认配置文件（我们使用自定义配置）
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
        // 显示当前连接数
        addBar("connections", (BridgeRouterBuild e) ->
            new Bar(
                () -> Core.bundle.format("bar.powerlines", e.links.size, linkLimit),
                () -> Pal.accent,
                () -> (float)e.links.size / linkLimit
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

    // ----- 配置（支持 IntSeq 和 Integer）-----
    // 注意：Mindustry 的 config 方法通过反射调用，参数类型需匹配

    // 配置为 IntSeq（相对坐标列表）
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

    // 配置为 Integer（点击目标切换连接）
    public void config(Integer value, BridgeRouterBuild tile) {
        int pos = value;
        Seq<Integer> links = tile.links;
        if (links.contains(pos)) {
            links.removeValue(pos, true);
            // 同时断开对方对自己的连接（可选）
            Building other = world.build(pos);
            if (other instanceof BridgeRouterBuild b && b.team == tile.team) {
                b.links.removeValue(tile.pos(), true);
            }
        } else {
            if (links.size >= linkLimit) return;
            links.add(pos);
            // 若对方也有此方块的链接，则移除对方的反向链接（避免双向重复）
            Building other = world.build(pos);
            if (other instanceof BridgeRouterBuild b && b.team == tile.team) {
                b.links.removeValue(tile.pos(), true);
            }
        }
        tile.setLinks(links);
    }

    // ----- 内部 Building 类 -----
    public class BridgeRouterBuild extends StorageBuild {
        public Seq<Integer> links = new Seq<>();    // 存储链接的目标坐标（打包的 int）
        public float warmup = 0f;
        public float powerLoss = 0f;

        // 传输中的物品记录（用于延迟到达）
        private static class TransportItem {
            Item item;
            int amount;
            int targetPos;
            float time;       // 剩余到达时间
        }
        private final Seq<TransportItem> transportQueue = new Seq<>();

        @Override
        public void created() {
            super.created();
            // 注册到全局绘制列表（用于连接线绘制）
            activeBridges.add(this);
        }

        @Override
        public void onRemoved() {
            super.onRemoved();
            activeBridges.removeValue(this, true);
        }

        public void setLinks(Seq<Integer> newLinks) {
            this.links = newLinks;
            if (this.links.size > linkLimit) this.links.truncate(linkLimit);
        }

        // 检查链接是否有效
        private boolean linkValid(int pos) {
            if (pos == -1) return false;
            Building target = world.build(pos);
            return target != null && target.team == team && target.block == blockType && within(target, range);
        }

        @Override
        public boolean shouldConsume() {
            return !links.isEmpty() && enabled;
        }

        @Override
        public void updateTile() {
            super.updateTile();

            // 1. 处理传输队列（延迟送达）
            for (int i = transportQueue.size - 1; i >= 0; i--) {
                TransportItem t = transportQueue.get(i);
                t.time -= edelta();
                if (t.time <= 0) {
                    Building target = world.build(t.targetPos);
                    if (target != null && target.team == team && target.block == blockType) {
                        int accept = Math.min(t.amount, target.acceptStack(t.item, t.amount, this));
                        if (accept > 0) {
                            target.handleStack(t.item, accept, this);
                        }
                    }
                    transportQueue.remove(i);
                }
            }

            // 2. 更新 warmup / powerLoss
            boolean hasLinks = !links.isEmpty();
            boolean consValid = efficiency > 0 && hasLinks;
            powerLoss = Mathf.lerpDelta(powerLoss, consValid ? 0 : 1, 0.08f);
            warmup = Mathf.lerpDelta(warmup, consValid ? 1 : 0, warmupSpeed);

            if (!consValid) {
                // 若不能工作，但仍有物品则 dump
                if (items.total() > 0) {
                    for (Item item : content.items()) {
                        if (items.get(item) > 0) dump(item);
                    }
                }
                return;
            }

            // 3. 定期向每个链接发送物品（每 FRAME_DELAY 帧一次）
            if (Time.time % FRAME_DELAY < 1) { // FRAME_DELAY = 1，即每帧都尝试
                // 清理无效链接
                for (int i = links.size - 1; i >= 0; i--) {
                    int pos = links.get(i);
                    if (!linkValid(pos)) {
                        links.remove(i);
                    }
                }

                if (links.isEmpty()) return;

                // 轮询发送物品到各个链接
                for (int idx = 0; idx < links.size; idx++) {
                    int pos = links.get(idx);
                    Building target = world.build(pos);
                    if (target == null || target.team != team || target.block != blockType || !within(target, range))
                        continue;

                    // 尝试发送任何物品（一次最多 1 个）
                    for (Item item : content.items()) {
                        int amount = items.get(item);
                        if (amount <= 0) continue;
                        int accept = Math.min(amount, 1, target.acceptStack(item, 1, this));
                        if (accept > 0) {
                            TransportItem t = new TransportItem();
                            t.item = item;
                            t.amount = accept;
                            t.targetPos = pos;
                            t.time = transportDelay;   // 延迟 tick
                            transportQueue.add(t);
                            items.remove(item, accept);
                            break; // 每帧每个链接只发一个物品
                        }
                    }
                }
            }
        }

        @Override
        public boolean acceptItem(Building source, Item item) {
            if (items.total() >= itemCapacity) return false;
            // 接受来自任何方向，但限制不能来自自己（通常不会）
            return super.acceptItem(source, item);
        }

        // ----- 绘制 -----
        @Override
        public void draw() {
            super.draw();
            // 主要绘制由全局 Draw 事件完成，这里只画方块自身
        }

        // ----- 配置绘制（选中时）-----
        @Override
        public void drawConfigure() {
            // 绘制范围圈
            Drawf.dashCircle(x, y, range - tilesize, Pal.accent);

            // 高亮当前链接的目标
            float pulse = Mathf.absin(Time.time, 4f, 1f);
            for (int pos : links) {
                if (linkValid(pos)) {
                    Building target = world.build(pos);
                    if (target != null) {
                        Drawf.select(target.x, target.y, target.block.size * tilesize / 2f + 2f, Pal.place);
                    }
                }
            }

            // 显示范围内可连接但尚未连接的同类方块（可选）
            for (Building other : activeBridges) {
                if (other == this || other.team != team || !within(other, range)) continue;
                if (other.block == blockType) {
                    // 若已被自己或对方连接，则用不同颜色
                    boolean connected = links.contains(other.pos()) || other.<BridgeRouterBuild>as().links.contains(pos());
                    Color color = connected ? Pal.place : Pal.breakInvalid;
                    float extra = connected ? 0f : pulse;
                    Drawf.select(other.x, other.y, other.block.size * tilesize / 2f + 2f + extra, color);
                }
            }

            // 绘制虚线连接线
            Draw.z(Layer.block + 1);
            for (int pos : links) {
                if (linkValid(pos)) {
                    Building target = world.build(pos);
                    if (target == null) continue;
                    drawConnectionLine(this, target, Pal.accent, 1f);
                }
            }
            // 绘制由其他桥指向自己的连线（若对方已连接但自己未反向连接，显示提示）
            for (Building other : activeBridges) {
                if (other == this || other.team != team) continue;
                if (other.block == blockType && other.<BridgeRouterBuild>as().links.contains(pos())) {
                    if (!links.contains(other.pos())) {
                        drawConnectionLine(other, this, Pal.orange, 1f);
                    }
                }
            }

            // 绘制流动箭头
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

        // 绘制单条连接线（带外发光效果，简化版）
        private void drawConnectionLine(Building from, Building to, Color color, float alpha) {
            float dx = to.x - from.x, dy = to.y - from.y;
            float dist = Mathf.dst(dx, dy);
            if (dist == 0) return;
            float ux = dx / dist, uy = dy / dist;
            float inset = LINE_INSET1; // 从方块边缘开始

            float sx = from.x + ux * inset;
            float sy = from.y + uy * inset;
            float ex = to.x - ux * inset;
            float ey = to.y - uy * inset;

            Draw.color(color, alpha);
            Lines.stroke(2f);
            Lines.line(sx, sy, ex, ey);
            Draw.color();
        }

        // 绘制流动箭头（在 drawConfigure 中调用）
        private void drawMovingArrow(Building from, Building to, Color color) {
            float dx = to.x - from.x, dy = to.y - from.y;
            float dist = Mathf.dst(dx, dy);
            if (dist == 0) return;
            float ux = dx / dist, uy = dy / dist;
            float inset = LINE_INSET1;
            float startX = from.x + ux * inset;
            float startY = from.y + uy * inset;
            float endX = to.x - ux * inset;
            float endY = to.y - uy * inset;
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

        // 点击配置：切换链接
        @Override
        public boolean onConfigureBuildTapped(Building other) {
            if (other == this) {
                // 点击自己：清空所有链接
                links.clear();
                return false;
            }
            if (other != null && other.team == team && other.block == blockType && within(other, range)) {
                // 配置为 other 的位置（会触发 config(Integer)）
                configure(other.pos());
                return false;
            }
            return true;
        }

        // 返回配置数据（用于存档）
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
            // 队列状态不保存（简单起见）
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

        // 获取 powerLoss 供绘制使用
        public float getPowerLoss() { return powerLoss; }
    }

    // ----- 全局绘制（连接线 & 箭头）-----
    private static final Seq<BridgeRouterBuild> activeBridges = new Seq<>();

    static {
        Events.on(EventType.DrawEvent.class, e -> {
            if (e.type == EventType.DrawEventType.draw) {
                drawAllBridges();
            }
        });
    }

    private static void drawAllBridges() {
        if (activeBridges.isEmpty()) return;
        float prevZ = Draw.z();
        Draw.z(Layer.block + 1);

        for (BridgeRouterBuild bridge : activeBridges) {
            if (!bridge.isValid() || world.build(bridge.pos()) != bridge) {
                activeBridges.removeValue(bridge, true);
                continue;
            }
            if (bridge.links.isEmpty()) continue;

            for (int pos : bridge.links) {
                Building target = world.build(pos);
                if (target == null || target.team != bridge.team || target.block != bridge.blockType) {
                    // 无效链接会在 update 中清理，这里跳过
                    continue;
                }

                // 绘制带能量损耗颜色的连接线
                float loss = bridge.getPowerLoss();
                Color outer = LINE_COLOR_OUTER.lerp(POWER_LOSS_COLOR, loss);
                Color inner = LINE_COLOR_INNER.lerp(POWER_LOSS_INNER_COLOR, bridge.efficiency <= 0 ? 1 : 0);

                float dx = target.x - bridge.x, dy = target.y - bridge.y;
                float len = Mathf.dst(dx, dy);
                if (len == 0) continue;
                float ux = dx / len, uy = dy / len;
                float nx = -uy, ny = ux;
                float halfWidth = LINE_WIDTH_INNER / 2f;

                // 端点缩进
                float inset = LINE_INSET;
                float sx = bridge.x + ux * inset;
                float sy = bridge.y + uy * inset;
                float ex = target.x - ux * inset;
                float ey = target.y - uy * inset;

                // 延长线
                float ext = OUTER_EXTEND;
                float osx = sx - ux * ext, osy = sy - uy * ext;
                float oex = ex + ux * ext, oey = ey + uy * ext;

                // 外线（双线）
                Draw.color(outer);
                Lines.stroke(LINE_WIDTH_OUTER);
                Lines.line(osx + nx * halfWidth, osy + ny * halfWidth, oex + nx * halfWidth, oey + ny * halfWidth);
                Lines.line(osx - nx * halfWidth, osy - ny * halfWidth, oex - nx * halfWidth, oey - ny * halfWidth);

                // 内线
                Draw.color(inner);
                Lines.stroke(LINE_WIDTH_INNER);
                Lines.line(sx, sy, ex, ey);

                // 端点封口
                Draw.color(outer);
                Lines.stroke(CAP_LINE_WIDTH);
                Lines.line(osx + nx * halfWidth, osy + ny * halfWidth, osx - nx * halfWidth, osy - ny * halfWidth);
                Lines.line(oex + nx * halfWidth, oey + ny * halfWidth, oex - nx * halfWidth, oey - ny * halfWidth);

                // 流动箭头
                drawFlowArrows(bridge, target, bridge.efficiency > 0, loss);
            }
        }
        Draw.z(prevZ);
    }

    // 画箭头（沿连接线多个）
    private static void drawFlowArrows(Building from, Building to, boolean powered, float loss) {
        float dx = to.x - from.x, dy = to.y - from.y;
        float totalDist = Mathf.dst(dx, dy);
        if (totalDist <= 0) return;
        float ux = dx / totalDist, uy = dy / totalDist;
        float inset = LINE_INSET;
        float startX = from.x + ux * inset;
        float startY = from.y + uy * inset;
        float endX = to.x - ux * inset;
        float endY = to.y - uy * inset;
        float bridgeLen = Mathf.dst(endX - startX, endY - startY);

        int arrows = (int)(bridgeLen / ARROW_SPACING);
        if (arrows <= 0) return;

        float angle = Mathf.angle(dx, dy);
        float rad = angle * Mathf.degRad;

        for (int a = 0; a < arrows; a++) {
            float px = startX + ux * a * ARROW_SPACING;
            float py = startY + uy * a * ARROW_SPACING;
            float timeScl = powered ? ARROW_TIME_SCL : 4.2f;
            float alpha = Mathf.absin(a - Time.time / timeScl, ARROW_PERIOD, 1f);
            if (alpha <= 0.01f) continue;
            float finalAlpha = powered ? alpha : 0.5f;

            Color color = ARROW_COLOR.lerp(POWER_LOSS_COLOR, loss);
            Draw.color(color, finalAlpha);
            Fill.tri(
                px + Mathf.cos(rad) * ARROW_SIZE, py + Mathf.sin(rad) * ARROW_SIZE,
                px + Mathf.cos(rad + Mathf.PI * 0.5f) * ARROW_SIZE, py + Mathf.sin(rad + Mathf.PI * 0.5f) * ARROW_SIZE,
                px + Mathf.cos(rad - Mathf.PI * 0.5f) * ARROW_SIZE, py + Mathf.sin(rad - Mathf.PI * 0.5f) * ARROW_SIZE
            );
        }
    }
}