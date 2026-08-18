package example;   // ← 这一行必须加，和 ExampleJavaMod 在同一个包

import mindustry.type.Item;

public class ModItem {
    public static Item cobalt;

    public static void load() {
        cobalt = new Item("cobalt") {{
            hardness = 1;
            cost = 1;
        }};
    }
}