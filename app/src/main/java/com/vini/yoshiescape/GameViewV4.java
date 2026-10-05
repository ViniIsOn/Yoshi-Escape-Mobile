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
    private int storyPage=0;\n    private final android.content.SharedPreferences prefs;

    public GameViewV4(Context c){
        super(c);
        ctx4=c.getApplicationContext();
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
        yGrace4=3.2f;
        yspeed=255f;
        yx=toStart?x+390f:x-390f;
        yy=GROUND-102f;
    }

    @Override void chase(float dt){
        float dir=Math.signum(x-yx);
        if(dir==0)dir=toStart?-1:1;
        if(yGrace4>0){
            yGrace4-=dt;
            yx+=dir*180f*dt;
            yy=GROUND-104f+(float)Math.sin(clock*7f)*8f;
            return;
        }
        yspeed=Math.min(610f,yspeed+62f*dt);
        yx+=dir*yspeed*dt;
        yy=GROUND-94f+(float)Math.sin(clock*9f)*7f;
        RectF marioHit=new RectF(x+7,y+8,x+31,y+46);
        RectF yoshiHit=new RectF(yx+16,yy+13,yx+72,yy+69);
        if(RectF.intersects(marioHit,yoshiHit)){
            state=OVER;
            vx=vy=0;
            stopTrack();
            beep(ToneGenerator.TONE_CDMA_ABBR_ALERT,220);
        }
    }

    @Override void drawYoshi(Canvas c){
        Bitmap b=(yGrace4>0&&yoshiFly!=null)?yoshiFly:(yoshiChase!=null?yoshiChase:yoshiRun);
        if(b==null)return;
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

        // ACT 1 - Meadow of Memories: readable jumps and two optional high routes.
        ground(0,760); ground(875,1540); ground(1660,2360);
        plat(430,365,190); plat(1010,350,220); plat(1280,300,165);
        plat(1810,355,200); plat(2070,305,165);
        springs.add(new RectF(715,GROUND-20,763,GROUND));
        springs.add(new RectF(1515,GROUND-20,1563,GROUND));
        spikes.add(new RectF(1370,GROUND-18,1412,GROUND));
        lineRings(220,700,72,392); arcRings(900,7,72,385,92);
        lineRings(1700,2280,76,392);

        // ACT 2 - Forgotten Ruins: pillars and alternating upper/lower routes.
        ground(2470,3200); ground(3330,4010); ground(4135,4680);
        plat(2570,370,150); plat(2790,320,175); plat(3050,270,150);
        plat(3430,350,190); plat(3710,295,185);
        plat(4220,350,180); plat(4470,300,160);
        springs.add(new RectF(3160,GROUND-20,3208,GROUND));
        springs.add(new RectF(3980,GROUND-20,4028,GROUND));
        spikes.add(new RectF(2860,GROUND-18,2902,GROUND));
        spikes.add(new RectF(3830,GROUND-18,3872,GROUND));
        arcRings(2500,8,70,390,105); lineRings(3360,3940,76,392);
        arcRings(4160,7,70,388,88);

        // ACT 3 - Yoshi's Rift: faster finale, generous landing zones.
        ground(4800,5350); ground(5465,WORLD);
        plat(4880,350,190); plat(5160,292,170);
        plat(5580,345,190);
        springs.add(new RectF(5310,GROUND-20,5358,GROUND));
        spikes.add(new RectF(5050,GROUND-18,5092,GROUND));
        lineRings(4740,5260,72,392); arcRings(5450,7,70,386,96);
    }

    @Override void drawDecor(Canvas c,float d){
        // Landmarks make each third of the stage feel like a different place.
        super.drawDecor(c,d);
        for(float q=2500;q<4680;q+=330){
            p.setColor(blend(Color.rgb(95,101,115),Color.rgb(75,39,55),d));
            c.drawRect(q,330,q+34,GROUND,p);
            c.drawRect(q-18,326,q+52,342,p);
        }
        for(float q=4860;q<WORLD;q+=280){
            p.setColor(Color.argb(145,120,42,170));
            c.drawCircle(q,365+(float)Math.sin(q)*18,22,p);
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
        storyIntro=!prefs.getBoolean("prologue_seen",false); storyPage=0;
        playTrack(R.raw.run_you_fool,true);
    }

    private void drawStory(Canvas c){
        int w=c.getWidth(),h=c.getHeight();
        p.setColor(Color.rgb(10,13,24));c.drawRect(0,0,w,h,p);
        float gap=12, top=h*.12f, ph=h*.58f, pw=(w-gap*4)/3f;

        for(int i=0;i<3;i++){
            float l=gap+(pw+gap)*i;
            p.setColor(i==storyPage%3?Color.rgb(245,245,225):Color.rgb(205,210,205));
            c.drawRoundRect(l,top,l+pw,top+ph,12,12,p);
            p.setColor(Color.rgb(65,145,90));
            c.drawRect(l+8,top+8,l+pw-8,top+ph*.68f,p);
        }

        // Comic action panels: Mario jumps, Yoshi is left at an edge,
        // then the remembered chase begins. Short captions keep it visual.
        int scene=storyPage;
        float l=gap+(pw+gap)*(scene%3);
        if(scene==0){
            drawBitmapAspect(c,marioJump,new RectF(l+pw*.52f,top+45,l+pw*.82f,top+ph*.55f),false,true);
            drawBitmapAspect(c,yoshiRun,new RectF(l+pw*.15f,top+ph*.38f,l+pw*.48f,top+ph*.66f),false,false);
        }else if(scene==1){
            drawBitmapAspect(c,marioJump,new RectF(l+pw*.65f,top+35,l+pw*.93f,top+ph*.5f),false,true);
            drawBitmapAspect(c,yoshiRun,new RectF(l+pw*.12f,top+ph*.42f,l+pw*.45f,top+ph*.7f),true,false);
        }else{
            drawBitmapAspect(c,yoshiChase,new RectF(l+pw*.28f,top+30,l+pw*.78f,top+ph*.66f),false,false);
        }

        String[] cap={"OUTRO SALTO...","DE NOVO NÃO.","AGORA É MINHA VEZ."};
        p.setTextAlign(Paint.Align.CENTER);p.setFakeBoldText(true);
        p.setColor(Color.rgb(24,28,35));p.setTextSize(Math.max(15,h*.029f));
        c.drawText(cap[scene],l+pw/2,top+ph*.84f,p);
        p.setColor(Color.WHITE);p.setTextSize(Math.max(18,h*.034f));
        c.drawText("CAPÍTULO 1  •  A ÚLTIMA MONTARIA",w/2,h*.08f,p);
        p.setColor(Color.rgb(255,211,55));p.setTextSize(Math.max(14,h*.027f));
        c.drawText(scene<2?"TOQUE PARA O PRÓXIMO QUADRO":"TOQUE PARA CORRER",w/2,h*.83f,p);
        p.setTextAlign(Paint.Align.LEFT);p.setFakeBoldText(false);
    }

    @Override void render(Canvas c){
        super.render(c);
        if(storyIntro)drawStory(c);
    }

    @Override public boolean onTouchEvent(android.view.MotionEvent e){
        if(storyIntro && e.getActionMasked()==android.view.MotionEvent.ACTION_DOWN){
            if(storyPage<2)storyPage++; else {storyIntro=false; prefs.edit().putBoolean("prologue_seen",true).apply();}
            return true;
        }
        return super.onTouchEvent(e);
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
