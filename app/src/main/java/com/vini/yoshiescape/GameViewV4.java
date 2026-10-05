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
    
    private android.content.SharedPreferences prefs;

    public GameViewV4(Context c){
        super(c);
        ctx4=c.getApplicationContext();
        prefs=c.getSharedPreferences("yoshi_escape_save",Context.MODE_PRIVATE);
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
        super.update(dt);
        // When the timer expires and the hunt begins, play the warning sting
        // once, then resume the lap-3 chase music when it finishes.
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
        yGrace4=1.4f;
        yspeed=390f;
        yx=toStart?x+390f:x-390f;
        yy=GROUND-102f;
    }

    @Override void chase(float dt){
        float dir=Math.signum(x-yx);
        if(dir==0)dir=toStart?-1:1;
        if(yGrace4>0){
            yGrace4-=dt;
            yx+=dir*240f*dt;
            yy=GROUND-104f+(float)Math.sin(clock*7f)*8f;
            return;
        }
        yspeed=Math.min(690f,yspeed+82f*dt);
        yx+=dir*yspeed*dt;

        // Chase failsafe: Yoshi must never be logically active but lost far
        // outside the playable camera. Re-enter from the pursuit side.
        if(Float.isNaN(yx) || Float.isInfinite(yx) || Math.abs(yx-x)>760f){
            yx=toStart?x+610f:x-610f;
            yy=GROUND-94f;
            yGrace4=.65f;
        }
        yy=GROUND-94f+(float)Math.sin(clock*9f)*7f;
        // Capture is tied to the visible sprite centers, not a broad invisible box.
        float marioCx=x+18f, marioCy=y+27f;
        float yoshiCx=yx+43f, yoshiCy=yy+40f;
        float catchDx=Math.abs(marioCx-yoshiCx);
        float catchDy=Math.abs(marioCy-yoshiCy);
        boolean yoshiVisible=(yoshiChase!=null||yoshiRun!=null);
        if(yoshiVisible && catchDx<25f && catchDy<34f){
            deathCause="YOSHI";
            state=OVER;
            vx=vy=0;
            stopTrack();
            beep(ToneGenerator.TONE_CDMA_ABBR_ALERT,220);
        }
    }

    @Override void drawYoshi(Canvas c){
        Bitmap b=(yGrace4>0&&yoshiFly!=null)?yoshiFly:(yoshiChase!=null?yoshiChase:yoshiRun);
        if(b==null){ yActive=false; return; }
        float pulse=.5f+.5f*(float)Math.sin(clock*8f);
        p.setColor(Color.argb((int)(65+45*pulse),255,25,20));
        c.drawCircle(yx+42,yy+36,48,p);
        boolean flip=x>yx;
        // Draw Yoshi larger and keep a directional warning visible whenever
        // he is outside the camera. This prevents invisible/unfair catches.
        drawBitmapAspect(c,b,new RectF(yx-18,yy-16,yx+104,yy+86),flip,false);

        float viewLeft=cam, viewRight=cam+(getWidth()/(getHeight()/H));
        if(yx<viewLeft+35 || yx>viewRight-35){
            boolean right=yx>viewRight-35;
            float ax=right?viewRight-62:viewLeft+62;
            float ay=185;
            p.setColor(Color.argb(220,210,35,35));
            Path arrow=new Path();
            if(right){arrow.moveTo(ax+28,ay);arrow.lineTo(ax-18,ay-24);arrow.lineTo(ax-18,ay+24);}
            else{arrow.moveTo(ax-28,ay);arrow.lineTo(ax+18,ay-24);arrow.lineTo(ax+18,ay+24);}
            arrow.close();c.drawPath(arrow,p);
            p.setColor(Color.WHITE);p.setTextSize(18);p.setFakeBoldText(true);
            c.drawText("YOSHI",right?ax-80:ax+36,ay+6,p);p.setFakeBoldText(false);
        }
        if(yGrace4>0){
            p.setColor(Color.WHITE);
            p.setTextSize(17);
            p.setFakeBoldText(true);
            c.drawText("YOSHI!",yx+15,yy-10,p);
            p.setFakeBoldText(false);
        }
    }

    @Override void buildLevel(){
        solids.clear(); springs.clear(); spikes.clear(); rings.clear();
        if(mapId==0){
            // MAP 1 - Meadow of Memories: open, readable and fast.
            ground(0,920); ground(1035,1860); ground(1990,2920); ground(3050,4040); ground(4160,5050); ground(5170,WORLD);
            plat(520,355,190); plat(1210,330,210); plat(1600,285,170); plat(2310,350,220); plat(3500,340,220); plat(4550,325,210);
            springs.add(new RectF(860,GROUND-20,908,GROUND)); springs.add(new RectF(4000,GROUND-20,4048,GROUND));
            spikes.add(new RectF(2670,GROUND-18,2712,GROUND));
            lineRings(220,850,74,392); arcRings(1080,7,74,385,92); lineRings(2050,2850,80,392); arcRings(4200,8,74,386,96);
        }else if(mapId==1){
            // MAP 2 - Forgotten Ruins: vertical routes, ruins and safe telegraphing.
            ground(0,690); ground(830,1460); ground(1600,2260); ground(2400,3180); ground(3330,3980); ground(4130,4800); ground(4950,WORLD);
            plat(300,345,170); plat(930,370,150); plat(1130,310,155); plat(1690,350,180); plat(1940,285,165);
            plat(2520,365,170); plat(2760,305,180); plat(3440,350,190); plat(3720,290,180); plat(4260,335,190); plat(4540,275,165); plat(5300,335,210);
            springs.add(new RectF(650,GROUND-20,698,GROUND)); springs.add(new RectF(3140,GROUND-20,3188,GROUND)); springs.add(new RectF(4760,GROUND-20,4808,GROUND));
            spikes.add(new RectF(1810,GROUND-18,1848,GROUND)); spikes.add(new RectF(3590,GROUND-18,3628,GROUND));
            arcRings(120,8,70,388,100); lineRings(900,1400,70,390); arcRings(1640,8,70,386,110); lineRings(2440,3120,76,390); arcRings(4160,8,72,385,105);
        }else{
            // MAP 3 - Yoshi's Rift: chase-focused finale, long sight lines and escape ramps.
            ground(0,1120); ground(1240,2110); ground(2240,3150); ground(3270,4210); ground(4340,5220); ground(5350,WORLD);
            plat(650,350,210); plat(1420,320,220); plat(1800,270,180); plat(2490,340,230); plat(2880,285,180);
            plat(3510,330,230); plat(3910,275,190); plat(4580,325,220); plat(4930,270,180); plat(5520,330,220);
            springs.add(new RectF(1070,GROUND-20,1118,GROUND)); springs.add(new RectF(3100,GROUND-20,3148,GROUND)); springs.add(new RectF(5170,GROUND-20,5218,GROUND));
            spikes.add(new RectF(2000,GROUND-18,2038,GROUND)); spikes.add(new RectF(4040,GROUND-18,4078,GROUND));
            lineRings(180,1040,76,392); arcRings(1280,9,70,386,115); lineRings(2300,3080,78,390); arcRings(3320,9,70,385,112); lineRings(4400,5150,76,390);
        }
    }

    @Override void drawDecor(Canvas c,float d){
        super.drawDecor(c,d);
        if(mapId==0){
            for(float q=700;q<WORLD;q+=900){p.setColor(Color.rgb(60,145,65));c.drawCircle(q,380,32,p);}
        }else if(mapId==1){
        for(float q=250;q<WORLD;q+=330){
            p.setColor(blend(Color.rgb(95,101,115),Color.rgb(75,39,55),d));
            c.drawRect(q,330,q+34,GROUND,p);
            c.drawRect(q-18,326,q+52,342,p);
        }
        }else{
        for(float q=220;q<WORLD;q+=280){
            p.setColor(Color.argb(145,120,42,170));
            c.drawCircle(q,365+(float)Math.sin(q)*18,22,p);
        }
        }
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
        super.start();
        mapId=0; buildLevel();
        storyIntro=!prefs.getBoolean("prologue_seen_v08",false); storyPage=0;
        playTrack(R.raw.run_you_fool,true);
    }

    private void drawStory(Canvas c){
        int w=c.getWidth(),h=c.getHeight();
        p.setColor(Color.rgb(8,12,22));c.drawRect(0,0,w,h,p);
        float l=w*.12f,r=w*.88f,t=h*.12f,b=h*.76f;
        p.setColor(Color.rgb(238,235,215));c.drawRoundRect(l,t,r,b,18,18,p);
        p.setColor(storyPage<3?Color.rgb(82,170,92):Color.rgb(62,42,86));c.drawRect(l+12,t+12,r-12,b-70,p);

        Bitmap yb;
        if(storyPage<2) yb=yoshiRun;
        else if(storyPage<4) yb=(yoshiChase!=null?yoshiChase:yoshiRun);
        else yb=(yoshiFly!=null?yoshiFly:(yoshiChase!=null?yoshiChase:yoshiRun));
        if(storyPage==0){
            drawBitmapAspect(c,yb,new RectF(w*.23f,h*.38f,w*.43f,h*.68f),false,true);
            drawBitmapAspect(c,marioJump,new RectF(w*.58f,h*.22f,w*.72f,h*.58f),false,true);
        }else if(storyPage==1){
            drawBitmapAspect(c,yb,new RectF(w*.20f,h*.34f,w*.42f,h*.69f),false,true);
            drawBitmapAspect(c,marioJump,new RectF(w*.67f,h*.18f,w*.80f,h*.48f),false,true);
        }else if(storyPage==2){
            drawBitmapAspect(c,yb,new RectF(w*.39f,h*.25f,w*.61f,h*.67f),false,true);
        }else if(storyPage==3){
            drawBitmapAspect(c,yb,new RectF(w*.18f,h*.28f,w*.42f,h*.68f),false,true);
            drawBitmapAspect(c,marioStand,new RectF(w*.66f,h*.37f,w*.76f,h*.67f),true,true);
        }else if(storyPage==4){
            drawBitmapAspect(c,yb,new RectF(w*.16f,h*.28f,w*.42f,h*.68f),false,true);
            drawBitmapAspect(c,marioRun[2],new RectF(w*.65f,h*.37f,w*.76f,h*.68f),false,true);
            p.setColor(Color.WHITE);for(int i=0;i<4;i++)c.drawRect(w*.48f+i*28,h*.47f,w*.48f+i*28+18,h*.48f,p);
        }else{
            drawBitmapAspect(c,yb,new RectF(w*.28f,h*.20f,w*.57f,h*.69f),false,true);
            drawBitmapAspect(c,marioRun[3],new RectF(w*.68f,h*.39f,w*.79f,h*.69f),false,true);
        }

        String[] cap={"Mais um salto impossível.","Mario salta. Yoshi fica para trás.","...ele se lembra de todos os outros.","Um rugido ecoa nas ruínas.","Mario corre. Yoshi não para.","A ÚLTIMA MONTARIA começa agora."};
        p.setTextAlign(Paint.Align.CENTER);p.setFakeBoldText(true);p.setColor(Color.rgb(25,29,35));p.setTextSize(Math.max(16,h*.034f));
        c.drawText(cap[storyPage],w/2,b-27,p);
        p.setColor(Color.WHITE);p.setTextSize(Math.max(18,h*.038f));c.drawText("PRÓLOGO  •  "+(storyPage+1)+"/6",w/2,h*.08f,p);
        p.setColor(Color.rgb(255,211,55));p.setTextSize(Math.max(14,h*.028f));
        c.drawText(storyPage<5?"TOQUE PARA AVANÇAR":"TOQUE PARA CORRER",w/2,h*.86f,p);
        p.setTextAlign(Paint.Align.LEFT);p.setFakeBoldText(false);
    }

    @Override void render(Canvas c){
        super.render(c);
        if(storyIntro)drawStory(c);
    }

    @Override public boolean onTouchEvent(android.view.MotionEvent e){
        if(storyIntro && e.getActionMasked()==android.view.MotionEvent.ACTION_DOWN){
            if(storyPage<5)storyPage++; else {storyIntro=false; prefs.edit().putBoolean("prologue_seen_v08",true).apply();}
            return true;
        }
        return super.onTouchEvent(e);
    }

    @Override void nextLap(){
        if(lap==1){mapId=1;buildLevel();beginLap(2,false);}
        else {mapId=2;buildLevel();beginLap(3,true);}
    }

    @Override void lapDone(){
        super.lapDone();
        if(state==WIN)stopTrack();
    }

    @Override void finish(){
        super.finish();
        stopTrack();
    }

    @Override void menuTap(float x,float y){
        boolean before=sound;
        super.menuTap(x,y);
        if(before!=sound){
            if(sound&&state==MENU)playTrack(R.raw.escape_menu,true);
            else if(!sound)stopTrack();
        }
    }

    @Override protected void onDetachedFromWindow(){
        stopTrack();
        super.onDetachedFromWindow();
    }
}
