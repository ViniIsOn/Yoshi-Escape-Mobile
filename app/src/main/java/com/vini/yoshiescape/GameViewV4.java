package com.vini.yoshiescape;

import android.content.Context;
import android.graphics.*;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.ToneGenerator;

public class GameViewV4 extends GameView {
    private final Context ctx4;
    private MediaPlayer bgm;
    private int currentTrack=0;
    private boolean bgmLoop=false;
    private boolean appPaused=false;
    private int loopStartMs=0;
    private float yGrace4=0f;
    private boolean storyIntro=true;
    private int storyPage=0;
    private int mapId=0;
    private boolean mapSelect=false;
    private int unlockedMap=0;
    private boolean courseComplete=false;
    private boolean returnPressed=false;
    private float yoshiVy=0f;
    private boolean yoshiGrounded=true;
    private Bitmap hqPanel1,hqPanel2,marioSmw;
    private float transitionAlpha=1f;
    private float scenePulse=0f;
    private float titleCard=0f;
    private int lastPolishState=-1;
    
    private android.content.SharedPreferences prefs;

    public GameViewV4(Context c){
        super(c);
        ctx4=c.getApplicationContext();
        prefs=c.getSharedPreferences("yoshi_escape_save",Context.MODE_PRIVATE);
        unlockedMap=Math.max(0,Math.min(5,prefs.getInt("unlocked_map_v11",0)));
        hqPanel1=BitmapFactory.decodeResource(getResources(),R.drawable.hq_panel_1);
        hqPanel2=BitmapFactory.decodeResource(getResources(),R.drawable.hq_panel_2);
        marioSmw=BitmapFactory.decodeResource(getResources(),R.drawable.mario_smw_1);
        playTrack(R.raw.escape_menu,true);
    }

    private void stopTrack(){
        currentTrack=0;
        bgmLoop=false;
        if(bgm!=null){
            try{bgm.setOnCompletionListener(null);}catch(Exception ignored){}
            try{if(bgm.isPlaying())bgm.stop();}catch(Exception ignored){}
            try{bgm.reset();}catch(Exception ignored){}
            try{bgm.release();}catch(Exception ignored){}
            bgm=null;
        }
    }

    private int loopPoint(int res){
        // Skip the intro on repeats so looping sounds like a continuation.
        if(res==R.raw.escape_menu)return 4200;
        if(res==R.raw.run_you_fool)return 6100;
        if(res==R.raw.escape_final_v2)return 4300;
        if(res==R.raw.escape_lap2)return 3100;
        if(res==R.raw.die)return 5200;
        return 0;
    }

    private void playTrack(int res,boolean loop){
        if(!sound||ctx4==null)return;
        if(bgm!=null && currentTrack==res){
            bgmLoop=loop;
            try{
                bgm.setLooping(loop);
                if(!appPaused&&!bgm.isPlaying())bgm.start();
            }catch(Exception ignored){}
            return;
        }
        stopTrack();
        currentTrack=res;
        bgmLoop=loop;
        try{
            AudioAttributes attrs=new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build();
            bgm=MediaPlayer.create(ctx4,res,attrs,AudioManager.AUDIO_SESSION_ID_GENERATE);
            if(bgm!=null){
                bgm.setLooping(loop);
                bgm.setVolume(.72f,.72f);
                bgm.setOnErrorListener((mp,what,extra)->{
                    stopTrack();
                    return true;
                });
                if(!appPaused)bgm.start();
            }
        }catch(Exception ignored){
            stopTrack();
        }
    }

    public void pauseMusic(){
        appPaused=true;
        if(bgm!=null){
            try{if(bgm.isPlaying())bgm.pause();}catch(Exception ignored){}
        }
    }

    public void resumeMusic(){
        appPaused=false;
        if(sound&&bgm!=null){
            try{if(!bgm.isPlaying())bgm.start();}catch(Exception ignored){}
        }else if(sound&&state==MENU){
            playTrack(R.raw.escape_menu,true);
        }
    }

    @Override void menu(){
        super.menu();
        if(ctx4!=null)playTrack(R.raw.escape_menu,true);
    }

    @Override void beginLap(int l,boolean left){
        super.beginLap(l,left);
        // Requested order: EscapeFinalV2 is the first escape lap.
        if(l==1)playTrack(R.raw.escape_final_v2,true);
        else if(l==2)playTrack(R.raw.escape_lap2,true);
        else playTrack(R.raw.die,true);
    }

    @Override void update(float dt){
        int before=state;
        scenePulse+=dt;
        if(transitionAlpha>0f) transitionAlpha=Math.max(0f,transitionAlpha-dt*2.4f);
        if(titleCard>0f) titleCard=Math.max(0f,titleCard-dt);
        super.update(dt);
        // When the timer expires and the hunt begins, play the warning sting
        // once, then resume the lap-3 chase music when it finishes.
        // Lap 3 is a permanent hunt: if any rendering/state edge case ever
        // disables Yoshi, bring the chase back instead of leaving an empty lap.
        if(lap>=3 && state==HUNT && !yActive) spawnYoshi();

        if(before!=state){ transitionAlpha=1f; if(state==PLAY||state==HUNT) titleCard=1.65f; lastPolishState=state; }
        if(before!=HUNT && state==HUNT && lap<3){
            playTrack(R.raw.time_is_up,false);
            if(bgm!=null){
                bgm.setOnCompletionListener(mp->{
                    if(state==HUNT&&sound)playTrack(R.raw.die,true);
                });
            }
        }
    }

    @Override void spawnYoshi(){
        yActive=true;
        yGrace4=1.8f;
        yspeed=285f;
        yx=toStart?Math.min(WORLD-120,x+470f):Math.max(30f,x-470f);
        yy=GROUND-94f;
        yoshiVy=0f; yoshiGrounded=true;
    }

    @Override void chase(float dt){
        float dir=Math.signum(x-yx);
        if(dir==0)dir=faceRight?1:-1;

        // Fair pursuit: Mario's run cap is 405. Yoshi pressures but does not
        // simply outrun the player forever.
        float gap=Math.abs(x-yx);
        float targetSpeed=gap>360f?365f:(gap<120f?305f:335f);
        if(yGrace4>0){yGrace4-=dt;targetSpeed=Math.min(targetSpeed,285f);}
        yspeed+=(targetSpeed-yspeed)*Math.min(1f,dt*2.4f);
        yx+=dir*yspeed*dt;
        yx=clamp(yx,20f,WORLD-100f);

        // Yoshi actually follows vertical routes now. If Mario takes a higher
        // platform, Yoshi jumps instead of blindly running underneath it.
        if(yoshiGrounded && y < yy-34f){
            yoshiVy=-545f;
            yoshiGrounded=false;
        }
        float oldY=yy;
        yoshiVy=Math.min(850f,yoshiVy+1450f*dt);
        yy+=yoshiVy*dt;
        yoshiGrounded=false;

        float feetOld=oldY+78f, feetNew=yy+78f;
        float best=GROUND;
        for(RectF s:solids){
            float cx=yx+42f;
            if(cx>s.left+6 && cx<s.right-6 && yoshiVy>=0 && feetOld<=s.top+10 && feetNew>=s.top){
                best=Math.min(best,s.top);
            }
        }
        if(feetNew>=best){
            yy=best-78f;
            yoshiVy=0;
            yoshiGrounded=true;
        }
        if(yy>GROUND-78f){yy=GROUND-78f;yoshiVy=0;yoshiGrounded=true;}

        RectF marioHit=new RectF(x+8f,y+8f,x+30f,y+45f);
        RectF yoshiHit=new RectF(yx+13f,yy+10f,yx+70f,yy+73f);
        if(RectF.intersects(marioHit,yoshiHit)){
            deathCause="YOSHI"; state=OVER; vx=vy=0; stopTrack();
            beep(ToneGenerator.TONE_CDMA_ABBR_ALERT,220);
        }
    }

    @Override void drawYoshi(Canvas c){
        // yoshi_run1 and yoshi_chase are currently the exact same repository
        // asset. yoshi_fly is the independently verified visible sprite, so use
        // it as the visual source until the final sheet is sliced into frames.
        Bitmap b=(yoshiFly!=null)?yoshiFly:(yoshiRun!=null?yoshiRun:yoshiChase);
        if(b==null){ return; }

        float pulse=.5f+.5f*(float)Math.sin(clock*8f);
        p.setColor(Color.argb((int)(45+35*pulse),255,25,20));
        c.drawCircle(yx+42,yy+36,42,p);

        // Face Mario. The source artwork faces right.
        boolean flip=(x>yx);
        drawBitmapAspect(c,b,new RectF(yx-10,yy-10,yx+94,yy+82),flip,false);

        // World-space warning marker. It is transformed by the same camera as Yoshi.
        float vw=getWidth()>0&&getHeight()>0?getWidth()/(getHeight()/H):960f;
        float viewLeft=cam, viewRight=cam+vw;
        if(yx<viewLeft+45 || yx>viewRight-45){
            boolean right=yx>viewRight-45;
            float ax=right?viewRight-70:viewLeft+70, ay=190;
            p.setColor(Color.rgb(220,48,42));
            Path arrow=new Path();
            if(right){arrow.moveTo(ax+24,ay);arrow.lineTo(ax-16,ay-20);arrow.lineTo(ax-16,ay+20);}
            else{arrow.moveTo(ax-24,ay);arrow.lineTo(ax+16,ay-20);arrow.lineTo(ax+16,ay+20);}
            arrow.close(); c.drawPath(arrow,p);
            p.setColor(Color.WHITE);p.setTextSize(16);p.setFakeBoldText(true);
            c.drawText("YOSHI",right?ax-72:ax+28,ay+5,p);p.setFakeBoldText(false);
        }
    }

    private void ringRow(float a,float b,float step,float yy){
        for(float q=a;q<=b;q+=step)addRing(q,yy);
    }
    private void ringArc(float a,int n,float step,float base,float amp){
        for(int i=0;i<n;i++)addRing(a+i*step,base-(float)Math.sin(i*Math.PI/(n-1))*amp);
    }

    @Override void buildLevel(){
        solids.clear(); springs.clear(); spikes.clear(); rings.clear();
        if(mapId==0){
            // 1 - Yoshi's Meadow: introductory route, every platform reachable.
            ground(0,1050); ground(1160,2140); ground(2260,3340); ground(3460,4540); ground(4660,WORLD);
            plat(430,360,180); plat(690,315,170); plat(1280,355,190); plat(1540,305,180);
            plat(2390,350,200); plat(2700,300,180); plat(3600,350,210); plat(3910,300,190); plat(4800,345,220);
            springs.add(new RectF(990,GROUND-20,1038,GROUND)); springs.add(new RectF(3280,GROUND-20,3328,GROUND));
            spikes.add(new RectF(2050,GROUND-18,2088,GROUND));
            ringRow(170,900,78,392); ringArc(1180,8,70,390,95); ringRow(2320,3200,80,392);
            ringArc(3500,8,72,390,92); ringRow(4700,5550,80,392);
        }else if(mapId==1){
            // 2 - Pipe Hills: staircase rhythm, no blind spike landings.
            ground(0,820); ground(940,1800); ground(1920,2780); ground(2900,3900); ground(4020,5000); ground(5120,WORLD);
            plat(300,365,180); plat(560,320,170); plat(1040,360,180); plat(1290,315,175); plat(1515,275,165);
            plat(2030,350,190); plat(2300,305,180); plat(3020,355,200); plat(3300,310,180); plat(3540,270,170);
            plat(4150,350,210); plat(4450,305,190); plat(5260,345,220);
            springs.add(new RectF(770,GROUND-20,818,GROUND)); springs.add(new RectF(3850,GROUND-20,3898,GROUND));
            spikes.add(new RectF(2650,GROUND-18,2688,GROUND));
            ringArc(130,9,72,390,105); ringRow(1020,1690,72,245); ringArc(1970,8,74,390,95);
            ringRow(3010,3650,74,238); ringArc(4080,8,72,390,90); ringRow(5180,5740,75,392);
        }else if(mapId==2){
            // 3 - Forgotten Ruins: more vertical but still readable.
            ground(0,720); ground(850,1510); ground(1640,2360); ground(2490,3260); ground(3390,4200); ground(4330,5100); ground(5230,WORLD);
            plat(250,350,190); plat(510,300,180); plat(960,350,190); plat(1210,300,175);
            plat(1740,355,200); plat(2020,305,185); plat(2590,350,200); plat(2870,300,180);
            plat(3500,350,210); plat(3800,300,190); plat(4440,350,210); plat(4740,300,190); plat(5350,345,220);
            springs.add(new RectF(670,GROUND-20,718,GROUND)); springs.add(new RectF(3210,GROUND-20,3258,GROUND));
            spikes.add(new RectF(2250,GROUND-18,2288,GROUND)); spikes.add(new RectF(4080,GROUND-18,4118,GROUND));
            ringArc(110,8,72,390,100); ringRow(900,1400,74,270); ringArc(1680,8,72,390,98);
            ringRow(2550,3140,74,270); ringArc(3420,8,72,390,98); ringRow(4400,5000,74,270);
        }else if(mapId==3){
            // 4 - Star Road: fast gaps and spring chains.
            ground(0,900); ground(1040,1700); ground(1840,2520); ground(2660,3380); ground(3520,4260); ground(4400,5200); ground(5340,WORLD);
            plat(360,340,210); plat(1160,330,210); plat(1940,320,220); plat(2760,335,210); plat(3630,320,220); plat(4510,330,220); plat(5420,320,220);
            springs.add(new RectF(850,GROUND-20,898,GROUND)); springs.add(new RectF(1650,GROUND-20,1698,GROUND));
            springs.add(new RectF(2470,GROUND-20,2518,GROUND)); springs.add(new RectF(4210,GROUND-20,4258,GROUND));
            ringArc(120,10,72,390,120); ringArc(1060,8,72,390,115); ringArc(1860,8,72,390,115);
            ringArc(2700,8,72,390,115); ringArc(3550,8,72,390,115); ringArc(4430,8,72,390,115);
        }else if(mapId==4){
            // 5 - Inverted World: vertical routes, purple atmosphere.
            ground(0,1180); ground(1300,2280); ground(2400,3420); ground(3540,4580); ground(4700,WORLD);
            plat(500,350,210); plat(790,305,180); plat(1450,350,210); plat(1740,305,185);
            plat(2550,350,220); plat(2860,305,190); plat(3680,350,220); plat(4000,305,190); plat(4860,345,230); plat(5200,300,190);
            springs.add(new RectF(1120,GROUND-20,1168,GROUND)); springs.add(new RectF(3360,GROUND-20,3408,GROUND));
            spikes.add(new RectF(2180,GROUND-18,2218,GROUND)); spikes.add(new RectF(4480,GROUND-18,4518,GROUND));
            ringRow(160,1050,78,392); ringArc(1340,9,72,390,100); ringRow(2460,3300,78,392);
            ringArc(3580,9,72,390,100); ringRow(4760,5600,78,392);
        }else{
            // 6 - Final Confrontation: fast finale with long readable chase lanes.
            ground(0,900); ground(1010,1840); ground(1960,2860); ground(2980,3920); ground(4040,4980); ground(5100,WORLD);
            plat(300,350,190); plat(580,305,170); plat(1120,350,200); plat(1430,300,180);
            plat(2070,345,210); plat(2390,295,180); plat(3100,345,220); plat(3440,295,190);
            plat(4160,340,220); plat(4510,290,190); plat(5230,335,240);
            springs.add(new RectF(840,GROUND-20,888,GROUND)); springs.add(new RectF(2800,GROUND-20,2848,GROUND)); springs.add(new RectF(4920,GROUND-20,4968,GROUND));
            spikes.add(new RectF(1760,GROUND-18,1798,GROUND)); spikes.add(new RectF(3860,GROUND-18,3898,GROUND));
            ringArc(120,9,72,390,110); ringArc(1040,9,72,390,105); ringRow(2020,2760,76,390);
            ringArc(3020,9,72,390,110); ringRow(4100,4860,76,390); ringArc(5140,8,72,390,100);
        }
    }

    private boolean hasGroundAt(float q){
        for(RectF s:solids) if(q>=s.left+10 && q<=s.right-10 && Math.abs(s.top-GROUND)<2) return true;
        return false;
    }

    @Override void drawDecor(Canvas c,float d){
        // Decorations are ground props: never draw one when its X falls over a pit.
        if(mapId==0){
            for(float q=240;q<WORLD;q+=520){ if(!hasGroundAt(q))continue;
                p.setColor(Color.rgb(42,125,48));c.drawRect(q,GROUND-30,q+5,GROUND,p);
                p.setColor(Color.rgb(250,208,62));c.drawCircle(q+2,GROUND-34,7,p);
            }
        }else if(mapId==1){
            for(float q=260;q<WORLD;q+=660){ if(!hasGroundAt(q))continue;
                p.setColor(blend(Color.rgb(70,91,72),Color.rgb(52,38,54),d));
                c.drawRect(q,GROUND-92,q+22,GROUND,p);
                p.setColor(Color.rgb(218,145,54));c.drawRect(q+5,GROUND-108,q+17,GROUND-92,p);
            }
        }else if(mapId==2){
            for(float q=300;q<WORLD;q+=720){ if(!hasGroundAt(q))continue;
                p.setColor(Color.rgb(170,218,244));
                Path z=new Path();z.moveTo(q-18,GROUND);z.lineTo(q,GROUND-72);z.lineTo(q+18,GROUND);z.close();c.drawPath(z,p);
            }
        }else if(mapId==3){
            for(float q=330;q<WORLD;q+=760){ if(!hasGroundAt(q))continue;
                p.setColor(Color.rgb(72,72,82));c.drawRect(q,GROUND-76,q+30,GROUND,p);
                p.setColor(Color.rgb(230,112,43));c.drawCircle(q+15,GROUND-84,10,p);
            }
        }else if(mapId==4){
            for(float q=300;q<WORLD;q+=700){ if(!hasGroundAt(q))continue;
                p.setColor(Color.argb(180,128,62,180));c.drawRect(q,GROUND-48,q+7,GROUND,p);
                c.drawCircle(q+3,GROUND-55,13,p);
            }
        }else{
            for(float q=360;q<WORLD;q+=820){ if(!hasGroundAt(q))continue;
                p.setColor(Color.rgb(86,50,48));c.drawRect(q,GROUND-64,q+32,GROUND,p);
                p.setColor(Color.rgb(242,77,42));c.drawCircle(q+16,GROUND-72,9,p);
            }
        }
    }

    @Override void hud(Canvas c,int w,int h){
        float s=Math.max(1f,h/480f);
        // Compact Super Mario World-style status strip: gameplay remains visible.
        p.setTypeface(Typeface.MONOSPACE);p.setFakeBoldText(true);p.setTextSize(13*s);
        p.setColor(Color.argb(205,12,34,72));c.drawRect(0,0,w,52*s,p);
        p.setColor(Color.rgb(255,225,55));c.drawText("MARIO",14*s,17*s,p);
        p.setColor(Color.WHITE);c.drawText("×"+lives,18*s,38*s,p);
        p.setColor(Color.rgb(255,225,55));c.drawText("★",82*s,18*s,p);
        p.setColor(Color.WHITE);c.drawText(String.format("%02d",ringCount),103*s,18*s,p);
        p.setColor(Color.rgb(255,225,55));c.drawText("TIME",w*.47f,17*s,p);
        p.setColor(Color.WHITE);c.drawText(state==PLAY?"---":String.format("%03d",Math.max(0,(int)Math.ceil(time))),w*.47f+48*s,17*s,p);
        p.setColor(Color.rgb(255,225,55));c.drawText("COURSE",w*.66f,17*s,p);
        p.setColor(Color.WHITE);c.drawText(String.valueOf(mapId+1),w*.66f+66*s,17*s,p);
        p.setColor(Color.rgb(255,225,55));c.drawText("LAP",w*.66f,38*s,p);
        p.setColor(Color.WHITE);c.drawText(String.valueOf(Math.max(1,lap)),w*.66f+42*s,38*s,p);
        p.setColor(Color.WHITE);p.setTextAlign(Paint.Align.RIGHT);c.drawText(String.format("%06d",score),w-14*s,38*s,p);
        p.setTextAlign(Paint.Align.LEFT);p.setTypeface(Typeface.DEFAULT);p.setFakeBoldText(false);
    }

    @Override void drawPlayer(Canvas c){
        if(marioSmw!=null){
            RectF d=new RectF(x-3,y-4,x+39,y+50);
            drawBitmapAspect(c,marioSmw,d,!faceRight,true);
        }else super.drawPlayer(c);
    }

    private void drawPolishOverlay(Canvas c){
        int w=c.getWidth(),h=c.getHeight();
        if(titleCard>0f && (state==PLAY||state==HUNT)){
            float a=Math.min(1f,titleCard*2f);
            p.setColor(Color.argb((int)(175*a),5,12,24));
            c.drawRoundRect(w*.28f,h*.37f,w*.72f,h*.58f,14,14,p);
            p.setTextAlign(Paint.Align.CENTER);p.setTypeface(Typeface.MONOSPACE);p.setFakeBoldText(true);
            p.setColor(Color.argb((int)(255*a),255,225,70));p.setTextSize(Math.max(18,h*.048f));
            c.drawText("COURSE "+(mapId+1),w*.5f,h*.45f,p);
            p.setColor(Color.argb((int)(255*a),255,255,255));p.setTextSize(Math.max(13,h*.029f));
            String[] n={"MEADOW OF MEMORIES","PIPE HILLS","FORGOTTEN RUINS","STAR ROAD","INVERTED WORLD","FINAL CONFRONTATION"};
            c.drawText(n[Math.max(0,Math.min(5,mapId))],w*.5f,h*.52f,p);
            p.setTextAlign(Paint.Align.LEFT);p.setTypeface(Typeface.DEFAULT);p.setFakeBoldText(false);
        }
        if(transitionAlpha>0f){
            int a=(int)(255*Math.min(1f,transitionAlpha));
            p.setColor(Color.argb(a,0,0,0));c.drawRect(0,0,w,h,p);
        }
        // subtle cinematic scanline texture; hand-tuned, not a flat generated panel.
        p.setColor(Color.argb(12,255,255,255));
        for(int yy=1;yy<h;yy+=4)c.drawRect(0,yy,w,yy+1,p);
    }

    @Override protected void onDraw(Canvas c){
        super.onDraw(c);
        drawPolishOverlay(c);
        invalidate();
    }

    @Override void drawStart(Canvas c){
        super.drawStart(c);
        p.setColor(Color.rgb(255,245,190));p.setTextSize(14);
        c.drawText("ACT 1  •  MEADOW OF MEMORIES",165,352,p);
    }

    @Override void drawGoal(Canvas c){
        super.drawGoal(c);
        p.setColor(Color.rgb(230,190,255));p.setTextSize(14);
        c.drawText("YOSHI'S RIFT",GOAL-25,305,p);
    }

    @Override void start(){
        int chosen=mapId;
        super.start();
        mapId=chosen; buildLevel();
        storyIntro=(mapId==0)&&!prefs.getBoolean("prologue_seen_v11",false); storyPage=0;
        playTrack(R.raw.run_you_fool,true);
    }

    private void unlockAfterCourse(){
        courseComplete=true;
        int next=Math.min(5,mapId+1);
        if(next>unlockedMap){
            unlockedMap=next;
            prefs.edit().putInt("unlocked_map_v11",unlockedMap).apply();
        }
    }

    @Override void finish(){
        unlockAfterCourse();
        super.finish();
    }

    @Override void lapDone(){
        super.lapDone();
        if(lap>=3) unlockAfterCourse();
    }

    private void drawStory(Canvas c){
        int w=c.getWidth(),h=c.getHeight();
        p.setColor(Color.rgb(5,9,18));c.drawRect(0,0,w,h,p);
        float l=w*.08f,r=w*.92f,t=h*.10f,b=h*.80f;
        p.setColor(Color.rgb(238,235,215));c.drawRoundRect(l,t,r,b,12,12,p);
        Bitmap panel=storyPage==0?hqPanel1:storyPage==1?hqPanel2:null;
        if(panel!=null){
            drawBitmapAspect(c,panel,new RectF(l+10,t+10,r-10,b-48),false,true);
        }else{
            p.setColor(storyPage<4?Color.rgb(35,67,76):Color.rgb(75,25,38));c.drawRect(l+10,t+10,r-10,b-48,p);
            Bitmap yb=(yoshiFly!=null?yoshiFly:(yoshiRun!=null?yoshiRun:yoshiChase));
            if(storyPage==2){
                drawBitmapAspect(c,yb,new RectF(w*.18f,h*.30f,w*.43f,h*.68f),true,true);
                drawBitmapAspect(c,marioSmw!=null?marioSmw:marioStand,new RectF(w*.65f,h*.35f,w*.76f,h*.68f),false,true);
            }else if(storyPage==3){
                drawBitmapAspect(c,yb,new RectF(w*.37f,h*.22f,w*.63f,h*.69f),false,true);
            }else if(storyPage==4){
                drawBitmapAspect(c,yb,new RectF(w*.15f,h*.28f,w*.43f,h*.69f),false,true);
                drawBitmapAspect(c,marioSmw!=null?marioSmw:marioRun[2],new RectF(w*.67f,h*.36f,w*.78f,h*.69f),false,true);
            }else{
                drawBitmapAspect(c,yb,new RectF(w*.27f,h*.18f,w*.58f,h*.70f),false,true);
                drawBitmapAspect(c,marioSmw!=null?marioSmw:marioRun[3],new RectF(w*.70f,h*.39f,w*.80f,h*.70f),false,true);
            }
        }
        String[] cap={"Antes da fuga...","O último salto.","Yoshi ficou para trás.","Ele não esqueceu.","CORRA.","A CAÇADA COMEÇA."};
        p.setTextAlign(Paint.Align.CENTER);p.setFakeBoldText(true);p.setColor(Color.rgb(20,24,30));p.setTextSize(Math.max(15,h*.031f));
        c.drawText(cap[storyPage],w/2,b-18,p);
        p.setColor(Color.WHITE);p.setTextSize(Math.max(16,h*.034f));c.drawText("PRÓLOGO  "+(storyPage+1)+"/6",w/2,h*.065f,p);
        p.setColor(Color.rgb(255,211,55));p.setTextSize(Math.max(13,h*.026f));c.drawText(storyPage<5?"TOQUE PARA AVANÇAR":"TOQUE PARA CORRER",w/2,h*.89f,p);
        p.setTextAlign(Paint.Align.LEFT);p.setFakeBoldText(false);
    }

    private void drawMapSelect(Canvas c){
        unlockedMap=Math.max(unlockedMap,Math.max(0,Math.min(5,prefs.getInt("unlocked_map_v11",0))));
        // tiny living-map motion: selected node breathes instead of looking like a static mockup.
        scenePulse+=0.016f;
        int w=c.getWidth(),h=c.getHeight();
        p.setColor(Color.rgb(18,35,57));c.drawRect(0,0,w,h,p);
        p.setColor(Color.rgb(31,70,73));
        Path land=new Path();land.moveTo(0,h*.75f);land.lineTo(w*.12f,h*.53f);land.lineTo(w*.28f,h*.62f);land.lineTo(w*.43f,h*.39f);land.lineTo(w*.58f,h*.58f);land.lineTo(w*.72f,h*.34f);land.lineTo(w*.88f,h*.55f);land.lineTo(w,h*.40f);land.lineTo(w,h);land.lineTo(0,h);land.close();c.drawPath(land,p);
        p.setTypeface(Typeface.MONOSPACE);p.setFakeBoldText(true);p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(Math.max(22,h*.045f));p.setColor(Color.WHITE);c.drawText("YOSHI ESCAPE • WORLD",w/2f,h*.105f,p);
        String[] names={"PRADO","FLORESTA","GELO","FORTALEZA","INVERTIDO","FINAL"};
        float[][] pos={{.10f,.63f},{.27f,.46f},{.43f,.65f},{.59f,.43f},{.75f,.63f},{.90f,.45f}};
        for(int i=0;i<6;i++){
            float px=w*pos[i][0],py=h*pos[i][1]; boolean locked=i>unlockedMap;
            if(i<5){float nx=w*pos[i+1][0],ny=h*pos[i+1][1];p.setColor(locked?Color.rgb(70,80,92):Color.rgb(190,220,235));p.setStrokeWidth(5);c.drawLine(px+24,py,nx-24,ny,p);}
            p.setColor(locked?Color.rgb(72,78,90):(i==5?Color.rgb(210,63,46):Color.rgb(241,199,55)));c.drawCircle(px,py,25,p);
            p.setColor(Color.rgb(17,24,35));c.drawCircle(px,py,16,p);
            if(!locked)drawBitmapAspect(c,marioStand,new RectF(px-15,py-33,px+15,py+7),false,true);
            else {p.setColor(Color.WHITE);p.setTextSize(17);c.drawText("X",px,py+6,p);}
            p.setTextSize(Math.max(10,h*.020f));p.setColor(Color.WHITE);c.drawText((i+1)+". "+names[i],px,py+50,p);
        }
        p.setTextSize(Math.max(12,h*.024f));p.setColor(Color.rgb(190,220,235));c.drawText("COMPLETE UMA FASE PARA ABRIR A PRÓXIMA",w/2f,h*.90f,p);
        float bl=w*.035f,bt=h*.06f,bw=Math.max(110,w*.10f),bh=Math.max(42,h*.07f);
        p.setColor(Color.argb(220,8,18,30));c.drawRoundRect(bl,bt,bl+bw,bt+bh,10,10,p);
        p.setColor(Color.WHITE);p.setTextSize(Math.max(13,h*.025f));c.drawText("← MENU",bl+bw/2,bt+bh*.67f,p);
        p.setTextAlign(Paint.Align.LEFT);p.setTypeface(Typeface.DEFAULT);p.setFakeBoldText(false);
    }

    private void startSelectedMap(int id){
        mapSelect=false; mapId=id; start();
        prefs.edit().putInt("last_map_v11",id).apply();
        storyIntro=(id==0)&&!prefs.getBoolean("prologue_seen_v11",false);
    }

    @Override void render(Canvas c){super.render(c);if(mapSelect)drawMapSelect(c);if(storyIntro)drawStory(c);}

    @Override public boolean onTouchEvent(android.view.MotionEvent e){
        if(e.getActionMasked()==android.view.MotionEvent.ACTION_DOWN && state==WIN && courseComplete){
            state=MENU;mapSelect=true;stopTrack();playTrack(R.raw.escape_menu,true);return true;
        }
        if(e.getActionMasked()==android.view.MotionEvent.ACTION_DOWN && playable()){
            float s=Math.max(1f,getHeight()/480f),bw=86*s,bh=30*s,l=getWidth()-bw-12*s,t=74*s;
            if(e.getX()>=l&&e.getX()<=l+bw&&e.getY()>=t&&e.getY()<=t+bh){
                touches.clear();leftDown=rightDown=jumpDown=runDown=false;
                state=MENU;mapSelect=false;storyIntro=false;stopTrack();playTrack(R.raw.escape_menu,true);return true;
            }
        }
        if(mapSelect && e.getActionMasked()==android.view.MotionEvent.ACTION_DOWN){
            float tx=e.getX(),ty=e.getY(),w=getWidth(),h=getHeight();
            float bl=w*.035f,bt=h*.06f,bw=Math.max(110,w*.10f),bh=Math.max(42,h*.07f);
            if(tx>=bl&&tx<=bl+bw&&ty>=bt&&ty<=bt+bh){
                mapSelect=false;state=MENU;stopTrack();playTrack(R.raw.escape_menu,true);return true;
            }
            float[][] pos={{.10f,.63f},{.27f,.46f},{.43f,.65f},{.59f,.43f},{.75f,.63f},{.90f,.45f}};
            for(int i=0;i<6;i++){float dx=tx-w*pos[i][0],dy=ty-h*pos[i][1];if(dx*dx+dy*dy<72*72){if(i<=unlockedMap)startSelectedMap(i);else beep(ToneGenerator.TONE_PROP_NACK,90);return true;}}
            return true;
        }
        if(storyIntro && e.getActionMasked()==android.view.MotionEvent.ACTION_DOWN){
            if(storyPage<5)storyPage++;else{storyIntro=false;prefs.edit().putBoolean("prologue_seen_v11",true).apply();}
            return true;
        }
        return super.onTouchEvent(e);
    }

    @Override void nextLap(){
        if(lap<3){beginLap(lap+1,lap+1>=3);return;}
        unlockAfterCourse();
        state=MENU;stopTrack();playTrack(R.raw.escape_menu,true);mapSelect=true;
    }

    @Override void menuTap(float x,float y){
        float h=getHeight(),w=getWidth(),ch=Math.min(270,h*.52f),t=h*.42f,first=t+58;
        if(Math.abs(y-first)<34&&x>w*.24f&&x<w*.76f){mapSelect=true;return;}
        boolean before=sound;super.menuTap(x,y);
        if(before!=sound){if(sound&&state==MENU)playTrack(R.raw.escape_menu,true);else if(!sound)stopTrack();}
    }

    @Override protected void onDetachedFromWindow(){stopTrack();super.onDetachedFromWindow();}
}
