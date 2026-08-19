package mindustry.world.blocks.distribution;

import arc.graphics.g2d.*;
import mindustry.gen.*;
import mindustry.world.*;
import mindustry.world.meta.*;

public class BridgeRouter extends Block {
    // 唯一保留的方块属性 —— 连接范围（仅用于占位显示，实际无任何逻辑）
    public int range = 4;

    public BridgeRouter(String name) {
        super(name);
        update = false;          // 完全禁止逻辑更新
        solid = true;
        hasItems = false;        // 不存储任何物品
        configurable = false;    // 禁止玩家配置
        destructible = true;
        // 移除所有运输相关的属性（itemCapacity、transportTime等）
    }

    // 空的建造实体类，不执行任何操作
    public class BridgeRouterBuild extends Building {
        // 里面什么都没有，所有方法继承自 Building 且不做任何覆盖
    }
}