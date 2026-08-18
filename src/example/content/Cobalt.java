package example;

import mindustry.type.Item;

public class Cobalt {
    public static Item cobalt;

    public static void load() {
        cobalt = new Item("cobalt", Color.valueOf("3a6ea5")) {{
            hardness = 1;
            cost = 1;
            research = "core-origin";           // 对应 JSON 的 research
        }};
    }
}
