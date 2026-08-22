package example.world.blocks;

import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.geom.*;
import arc.math.Mathf;
import arc.scene.style.TextureRegionDrawable;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.Time;
import arc.util.io.*;
import mindustry.Vars;
import mindustry.entities.TargetPriority;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.type.*;
import mindustry.ui.Styles;
import mindustry.world.*;
import mindustry.world.blocks.storage.CoreBlock;
import mindustry.world.meta.*;

import static mindustry.Vars.*;

public class CoreUnloader extends Block {
    public int range = 250;
    public float transportTime = 1f;

    public CoreUnloader(String name) {
        super(name);
        update = true;
        solid = true;
        underBullets = true;
        itemCapacity = 50;
        configurable = true;
        hasItems = true;
        unloadable = true;
        separateItemCapacity = true;
        group = BlockGroup.transportation;
        noUpdateDisabled = true;
        allowDiagonal = true;
        copyConfig = true;
        allowConfigInventory = true;
        priority = TargetPriority.transport;
        delayLandingConfig = true;
    }

    @Override
    public void setBars() {
        super.setBars();
        removeBar("items");
    }

    @Override
    public boolean outputsItems() {
        return true;
    }

    @Override
    public void setStats() {
        super.setStats();
        if (transportTime != 0f) {
            stats.add(Stat.itemsMoved, 60f / transportTime, StatUnit.itemsSecond);
        }
        stats.add(Stat.linkRange, range, StatUnit.blocks);
    }

    @Override
    public void drawPlace(int x, int y, int rotation, boolean valid) {
        super.drawPlace(x, y, rotation, valid);
        Drawf.dashCircle(x, y, range * tilesize, Pal.accent);
    }

    public class CoreUnloaderBuild extends Building {
        public int link = -1;
        public float transportCounter = 0f;
        public boolean hadValidLink;
        public Seq<Item> selectedItems = new Seq<>();
        public int pullIndex = 0;
        public int nextItemIndex = 0;
        public int outputIndex = 0;
        public int pullAmount = 10;
        public int outputAmount = 10;
        public boolean linkValid(Tile other) {
            if (other == null || other.build == null) return false;
            Building b = other.build;
            if (!(b instanceof CoreBlock.CoreBuild)) return false;
            if (b.team != team) return false;
            float dx = other.x - tile.x, dy = other.y - tile.y;
            return dx * dx + dy * dy <= range * range;
        }

@Override
public void updateTile(){
    if(efficiency <= 0f || link == -1){
        transportCounter = 0f;
        return;
    }

    Tile other = world.tile(link);
    if(!linkValid(other)){
        transportCounter = 0f;
        return;
    }

    CoreBlock.CoreBuild core = (CoreBlock.CoreBuild)other.build;
    while(returnUnselected(core)){}
    if(selectedItems.isEmpty()){
        transportCounter = 0f;
        return;
    }

transportCounter += delta();

if(transportCounter >= transportTime){
    int amount = 0;
    while(amount < pullAmount){
        Item item = getNextItemFromCore(core);
        if(item == null){
            break;
        }

        core.items.remove(item, 1);
        items.add(item, 1);
        amount++;
    }
}
if(items.total() > 0 && !selectedItems.isEmpty()){
    int size = selectedItems.size;

    // 每 tick 最多输出 10 个
    for(int n = 0; n < outputAmount; n++){

        if(items.total() <= 0){
            break;
        }

        boolean output = false;

        // 从当前物品开始轮询
        for(int i = 0; i < size; i++){

            int idx = (outputIndex + i) % size;
            Item item = selectedItems.get(idx);

            if(items.get(item) <= 0){
                continue;
            }

            outputToAdjacent(item);
            outputIndex = (idx + 1) % size;
            output = true;
            break;
        }

        // 一个都输出不了，结束本 tick
        if(!output){
            break;
        }
    }
}
}

private void outputToAdjacent(Item item){
    if(item == null || items.get(item) <= 0) return;

    int tx = tile.x, ty = tile.y;
    int size = block.size;
    IntSeq positions = new IntSeq();
    for(int x = tx; x < tx + size; x++){
        positions.add(Point2.pack(x, ty + size));
        positions.add(Point2.pack(x, ty - 1));
    }
    for(int y = ty; y < ty + size; y++){
        positions.add(Point2.pack(tx - 1, y));
        positions.add(Point2.pack(tx + size, y));
    }

    for(int i = 0; i < positions.size; i++){
        int pos = positions.get(i);
        Tile targetTile = world.tile(Point2.x(pos), Point2.y(pos));
        if(targetTile == null || targetTile.build == null) continue;

        Building target = targetTile.build;
        if(target.team != team) continue;

        Object cfg = target.config();
        if(cfg instanceof Item targetItem){
            if(targetItem != item) continue;
        }

        if(target.acceptItem(this, item)){
            int amount = Math.min(items.get(item), 1);
            target.handleItem(this, item);
            items.remove(item, amount);
            return;
        }
    }
}

private Item getNextItemFromCore(CoreBlock.CoreBuild core){
    int size = selectedItems.size;
    for(int i = 0; i < size; i++){
        int idx = (pullIndex + i) % size;
        Item item = selectedItems.get(idx);
        if(items.get(item) < itemCapacity && core.items.has(item)){
            pullIndex = (idx + 1) % size;
            return item;
        }
    }

    pullIndex = 0;
    return null;
}

        @Override
        public boolean acceptItem(Building source, Item item) {
            return false;
        }

@Override
public void configure(Object value){
    if(value instanceof Integer i){
        link = i;
    }else{
        link = -1;
    }

    transportCounter = 0f;
}

        @Override
        public Object config() {
            return link;
        }

@Override
public void buildConfiguration(Table table){
    table.clear();
    table.top();
    Table cont = new Table();
    cont.top();
    cont.defaults().size(40);
    int column = 0;
    for(Item item : content.items()){
        if(!item.unlockedNow()) continue;
        if(!item.isOnPlanet(Vars.state.getPlanet())) continue;
        if(item.isHidden()) continue;
        ImageButton button = cont.button(
            Tex.whiteui,
            Styles.clearNoneTogglei,
            Mathf.clamp(item.selectionSize, 0, 40),
            () -> {}
        ).tooltip(item.localizedName).get();
        button.getStyle().imageUp =
            new TextureRegionDrawable(item.uiIcon);
        button.changed(() -> {
            if(button.isChecked()){
                if(!selectedItems.contains(item)){
                    selectedItems.add(item);
                }
            }else{
                selectedItems.remove(item);
            }
        });
        button.update(() -> {
            button.setChecked(selectedItems.contains(item));
        });
        column++;
        if(column >= 4){
            cont.row();
            column = 0;
        }
    }
    Table main = new Table();
    main.background(Styles.black6);
    ScrollPane pane = new ScrollPane(cont, Styles.smallPane);
    pane.setScrollingDisabled(true, false);
    pane.setOverscroll(false, false);
    main.add(pane)
        .maxHeight(40 * 5)
        .growX();
    table.add(main).growX();
}

private boolean isSelected(Item item){
    return selectedItems.contains(item);
}

private boolean returnUnselected(CoreBlock.CoreBuild core){
    for(Item item : content.items()){
        if(isSelected(item)) continue;

        int amount = items.get(item);
        if(amount <= 0) continue;

        if(core.acceptItem(this, item)){
            core.handleItem(this, item);
            items.remove(item, 1);
            return true;
        }
    }

    return false;
}

@Override
public boolean onConfigureBuildTapped(Building other){

    if(other == this){

        if(selectedItems.size == 0){

            for(Item item : content.items()){
                if(!item.unlockedNow()) continue;
                if(item.isHidden()) continue;

                selectedItems.add(item);
            }

        }else{

            selectedItems.clear();
        }

        return false;
    }

    if(other instanceof CoreBlock.CoreBuild && other.team == team){

        if(dst(other) <= range){

            if(link == other.pos()){
                configure(-1);
            }else{
                configure(other.pos());
            }

            return false;
        }
    }

    return true;
}

private void drawInput(Tile other){
    if(!linkValid(other)) return;
    float tx = tile.drawx();
    float ty = tile.drawy();
    float ox = other.drawx();
    float oy = other.drawy();
    Drawf.dashLine(Pal.accent, tx, ty, ox, oy);
    Drawf.select(ox, oy, other.block().size * tilesize / 2f + 2f, Pal.place);
    Drawf.square(ox, oy, 2f, Pal.accent);
}

@Override
public void drawConfigure(){
    float sin = Mathf.absin(Time.time, 4f, 1f);
    Lines.stroke(1f);
    Drawf.circles(x, y, 9f + sin, Pal.accent);
    Drawf.dashCircle(x, y, range * tilesize, Pal.accent);

    if(link != -1){
        Tile other = world.tile(link);
        if(other != null && linkValid(other)){
            drawInput(other);
        }
    }
    Groups.build.each(b -> {
        if(!(b instanceof CoreBlock.CoreBuild)) return;
        if(b.team != team) return;
        if(!within(b, range)) return;
        if(b.pos() == link) return;
        Drawf.select(b.x, b.y, b.block.size * tilesize / 2f + 2f + sin, Pal.remove);
    });
    Draw.reset();
}

@Override
public void write(Writes write){
    super.write(write);
    write.i(link);
    write.s(selectedItems.size);
    for(Item item : selectedItems){
        write.i(item.id);
    }
}

@Override
public void read(Reads read, byte revision){
    super.read(read, revision);
    link = read.i();
    selectedItems.clear();
    int size = read.s();
    for(int i = 0; i < size; i++){
        Item item = content.item(read.i());
        if(item != null){
            selectedItems.add(item);
        }
    }
}
    }
}