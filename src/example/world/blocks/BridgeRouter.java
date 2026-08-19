package mindustry.world.blocks.defense;

import arc.Graphics.*;
import arc.Graphics.Cursor.*;
import arc.audio.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.math.geom.*;
import arc.struct.*;
import arc.util.*;
import arc.util.io.*;
import mindustry.annotations.Annotations.*;
import mindustry.content.*;
import mindustry.entities.*;
import mindustry.entities.units.*;
import mindustry.gen.*;
import mindustry.logic.*;

import static mindustry.Vars.*;

public class BridgeRouter extends Wall{
    protected final static Rect rect = new Rect();
    protected final static Queue<BridgeRouterBuild> BridgeRouterQueue = new Queue<>();

    public final int timerToggle = timers++;
    public Effect openfx = Fx.Dooropen;
    public Effect closefx = Fx.Doorclose;
    public Sound BridgeRouterSound = Sounds.Door;
    public boolean chainEffect = false;
    public @Load("@-open") TextureRegion openRegion;

    public BridgeRouter(String name){
        super(name);
        solid = false;
        solidifes = true;
        consumesTap = true;

        config(Boolean.class, (BridgeRouterBuild base, Boolean open) -> {
            if(!world.isGenerating()){
                BridgeRouterSound.at(base);
                base.effect();
            }

            BridgeRouterQueue.clear();
            BridgeRouterQueue.add(base);

            for(BridgeRouterBuild entity : base.chained.isEmpty() ? BridgeRouterQueue : base.chained){
                //skip BridgeRouters with things in them
                if((Units.anyEntities(entity.tile) && !open) || entity.open == open){
                    continue;
                }

                if(chainEffect) entity.effect();
                entity.open = open;
                entity.recache();
                if(!world.isGenerating()) pathfinder.updateTile(entity.tile);
            }
        });
    }

    @Override
    public TextureRegion getPlanRegion(BuildPlan plan, Eachable<BuildPlan> list){
        return plan.config == Boolean.TRUE ? openRegion : region;
    }

    public class BridgeRouterBuild extends Building{
        public boolean open = false;
        public Seq<BridgeRouterBuild> chained = new Seq<>();

        @Override
        public void onProximityAdded(){
            super.onProximityAdded();
            updateChained();
        }

        @Override
        public void onProximityRemoved(){
            super.onProximityRemoved();

            for(Building b : proximity){
                if(b instanceof BridgeRouterBuild d){
                    d.updateChained();
                }
            }
        }

        @Override
        public double sense(LAccess sensor){
            if(sensor == LAccess.enabled) return open ? 1 : 0;
            return super.sense(sensor);
        }

        @Override
        public void control(LAccess type, double p1, double p2, double p3, double p4){
            if(type == LAccess.enabled){
                boolean shouldOpen = !Mathf.zero(p1);

                if(net.client() || open == shouldOpen || (Units.anyEntities(tile) && !shouldOpen) || !origin().timer(timerToggle, 80f)){
                    return;
                }

                configureAny(shouldOpen);
            }
        }

        public BridgeRouterBuild origin(){
            return chained.isEmpty() ? this : chained.first();
        }

        public void effect(){
            (open ? closefx : openfx).at(this, size);
        }

        public void updateChained(){
            chained = new Seq<>();
            BridgeRouterQueue.clear();
            BridgeRouterQueue.add(this);

            while(!BridgeRouterQueue.isEmpty()){
                var next = BridgeRouterQueue.removeLast();
                chained.add(next);

                for(var b : next.proximity){
                    if(b instanceof BridgeRouterBuild d && d.chained != chained){
                        d.chained = chained;
                        BridgeRouterQueue.addFirst(d);
                    }
                }
            }
        }

        @Override
        public void draw(){
            Draw.rect(open ? openRegion : region, x, y);
        }

        @Override
        public Cursor getCursor(){
            return interactable(player.team()) ? SystemCursor.hand : SystemCursor.arrow;
        }

        @Override
        public boolean checkSolid(){
            return !open;
        }

        @Override
        public void tapped(){
            if((Units.anyEntities(tile) && open) || !origin().timer(timerToggle, 60f)){
                return;
            }

            configure(!open);
        }

        @Override
        public Boolean config(){
            return open;
        }

        @Override
        public void write(Writes write){
            super.write(write);
            write.bool(open);
        }

        @Override
        public void read(Reads read, byte revision){
            super.read(read, revision);
            open = read.bool();
        }
    }

}
