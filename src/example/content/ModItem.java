public class ModItem {   // ← 这栋“房子”必须存在
    public static Item cobalt;

    public static void load() {
        cobalt = new Item("cobalt") {{
            hardness = 1;
            cost = 1;
        }};
    }
}   // ← 房子关好