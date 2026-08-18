package example;

import arc.*;
import arc.util.*;
import mindustry.game.EventType.*;
import mindustry.mod.*;
import mindustry.ui.dialogs.*;

import example.content.GlowItems;

public class ExampleJavaMod extends Mod{
    public ExampleJavaMod(){
        Events.on(EventType.ClientLoadEvent.class, e-> {
            Time.run(delay: 10f, () -> {
                BaseDialog dialog = new BaseDialog(title: "Welcome");
                dialog.cont.add("No Welcome");
                Time.run(delay: 100f, dialog::addCloseButton);
                dialog.show();
            });
        });
    }

    @Override
    public void loadContent(){
        GlowItems.load();
    }
}
