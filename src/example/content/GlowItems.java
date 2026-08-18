package example.content;

import mindustry.type.Item;

public class item {
    public static Item 
        cobalt,
        crudeSilicon,
        highCarbonAlloy;

    public static void load(){
        cobalt = new Item(name: "cobalt", Color.valueOf("3a6ea5")){{
            hardness = 1;
            cost = 1;
            alwaysUnlocked = true;
        }};
        
        crudeSilicon = new Item(name: "crude-silicon", Color.valueOf("676c70"){{
            buildable = false;
            alwaysUnlocked = false;
        }};
        
        highCarbonAlloy = new Item(name: "high-carbon-alloy", Color.valueOf("4a5a6a")){{
            radioactivity = 0.3f;
            cost = 3;
            alwaysUnlocked = false;
        }};
    }
}
