!package example;

import arc.*;
import arc.util.*;
import mindustry.game.EventType.*;
import mindustry.mod.*;
import mindustry.ui.dialogs.*;

import example.content.GlowItems;
import example.content.GlowBlocks;


public class ExampleJavaMod extends Mod {
    public ExampleJavaMod() {
        Events.on(ClientLoadEvent.class, e -> {
            Time.run(10f, () -> {
                BaseDialog dialog = new BaseDialog("Welcome");
                dialog.cont.add("No Welcome");
                Time.run(100f, dialog::addCloseButton);
                dialog.show();
            });
        });
    }

    @Override
    public void loadContent() {
        GlowItems.load();
        GlowBlocks.load();
    }
}
