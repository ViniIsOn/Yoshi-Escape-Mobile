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
    private float transitionAlpha=0f;
    private float scenePulse=0f;
    private float titleCard=0f;
    private float ringFlash=0f, springFlash=0f, goalFlash=0f;
    private int lastRingVisual=0;
    private boolean wasGroundedVisual=false;
    private int lastPolishState=-1;
    private int coursesCleared=0;
    private float mapPulse=0f;
    private float gimmickClock=0f;
    private float dangerPulse=0f;

    // Replay/progression layer. These values stay local for now and can later
    // become the source of truth for Play Games cloud save/achievements.
    private int totalRings=0, totalMedals=0, achievementMask=0;
    private final int[] medalMask=new int[6];
    private final float[][] medalX={
            {690,2700,5050},{1290,3300,5350},{1210,3260,4740},
            {1160,2760,5200},{790,2860,5200},{1430,3440,5230}
    };
    private final float[][] medalY={
            {280,260,250},{265,260,235},{250,205,250},
            {280,285,235},{255,260,250},{250,245,245}
    };
    private int runStartLives=3, runStartRings=0, runRingsCollected=0;
    private float runElapsed=0f;
    private boolean tookDamageThisRun=false;
    private float achievementToast=0f;
    private String achievementToastText="";
    private boolean resultSaved=false;

    private static final String[] ACH_NAMES={
            "A FUGA COMEÇA","ALGO ESTÁ ATRÁS...","PERDIDO NA FLORESTA","SANGUE FRIO",
            "INVADINDO A FORTALEZA","DE CABEÇA PARA BAIXO","NÃO OLHE PARA TRÁS!",
            "100 RINGS!","CAÇADOR DE RINGS","SEM UM ARRANHÃO","VELOCISTA",
            "O MUNDO É SEU","A VINGANÇA TERMINOU?","CAÇADOR DE SEGREDOS"
    };

    private android.content.SharedPreferences prefs;

    public GameViewV4(Context c){
        super(c);
        ctx4=c.getApplicationContext();
        prefs=c.getSharedPreferences("yoshi_escape_save",Context.MODE_PRIVATE);
        unlockedMap=Math.max(0,Math.min(5,prefs.getInt("unlocked_map_v11",0)));
        coursesCleared=Math.max(0,prefs.getInt("courses_cleared_v12",0));
        totalRings=Math.max(0,prefs.getInt("total_rings_v13",0));
        totalMedals=Math.max(0,prefs.getInt("total_medals_v13",0));
        achievementMask=prefs.getInt("achievements_v13",0);
        for(int i=0;i<6;i++)medalMask[i]=prefs.getInt("medals_"+i+"_v13",0);
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
        gimmickClock+=dt;
        dangerPulse=Math.max(0f,dangerPulse-dt);

        if(titleCard>0f) titleCard=Math.max(0f,titleCard-dt);
        int ringsBefore=ringCount, livesBefore=lives; boolean groundBefore=grounded; float vyBefore=vy;
        if(state==PLAY||state==ESCAPE||state==HUNT)runElapsed+=dt;
        super.update(dt);
        if(ringCount>ringsBefore){
            int gained=ringCount-ringsBefore;
            runRingsCollected+=gained; totalRings+=gained;
            prefs.edit().putInt("total_rings_v13",totalRings).apply();
            if(totalRings>=100)unlockAchievement(7);
            if(totalRings>=500)unlockAchievement(8);
        }
        if(lives<livesBefore || ringCount<ringsBefore)tookDamageThisRun=true;
        collectSecretMedal();
        achievementToast=Math.max(0f,achievementToast-dt);
        // World gimmicks: each course changes how the run feels, not only its palette.
        if(state==PLAY || state==HUNT){
            if(mapId==2 && grounded){
                // Frozen Heights: low-friction momentum. Releasing direction no longer stops instantly.
                if(!leftDown && !rightDown) vx*=0.992f;
            }else if(mapId==3){
                // Abandoned Keep: timed danger windows. The warning is visual and predictable.
                float cycle=gimmickClock%5.0f;
                if(cycle>3.55f && cycle<4.35f) dangerPulse=.16f;
            }else if(mapId==4){
                // Inverted Dream: periodic light gravity; still controllable and never reverses controls.
                if(!grounded && ((int)(gimmickClock/3.5f)%2==1)) vy-=310f*dt;
            }else if(mapId==5 && state==HUNT && yActive){
                // Finale: Yoshi gets a mild late-course pressure boost, capped below Mario's run speed.
                if(x>WORLD*.62f) yspeed=Math.min(382f,yspeed+22f*dt);
            }
        }
        if(ringCount>ringsBefore){ ringFlash=.28f; lastRingVisual=ringCount; beep(ToneGenerator.TONE_PROP_BEEP,24); }
        if(!groundBefore && grounded && vyBefore>260f) springFlash=Math.max(springFlash,.12f);
        ringFlash=Math.max(0f,ringFlash-dt); springFlash=Math.max(0f,springFlash-dt); goalFlash=Math.max(0f,goalFlash-dt);
        // When the timer expires and the hunt begins, play the warning sting
        // once, then resume the lap-3 chase music when it finishes.
        // Lap 3 is a permanent hunt: if any rendering/state edge case ever
        // disables Yoshi, bring the chase back instead of leaving an empty lap.
        if(lap>=3 && state==HUNT && !yActive) spawnYoshi();

        if(before!=state){ if(state==PLAY||state==HUNT) titleCard=1.65f; lastPolishState=state; }
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
        unlockAchievement(1);
        unlockAchievement(6);
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
            plat(2070,345,210); plat(2390,295,180); plat(2700,250,150); plat(3100,345,220); plat(3440,295,190); plat(3720,250,150);
            plat(4160,340,220); plat(4510,290,190); plat(5230,335,240);
            springs.add(new RectF(840,GROUND-20,888,GROUND)); springs.add(new RectF(2800,GROUND-20,2848,GROUND)); springs.add(new RectF(3920,GROUND-20,3968,GROUND)); springs.add(new RectF(4920,GROUND-20,4968,GROUND));
            spikes.add(new RectF(1760,GROUND-18,1798,GROUND)); spikes.add(new RectF(3860,GROUND-18,3898,GROUND));
            ringArc(120,9,72,390,110); ringArc(1040,9,72,390,105); ringRow(2020,2760,76,390);
            ringArc(3020,9,72,390,110); ringRow(4100,4860,76,390); ringArc(5140,8,72,390,100);
        }
        // Extra hand-placed rhythm section so courses do not feel like short test rooms.
        if(mapId==0){plat(5050,300,180);ringArc(5120,7,66,382,72);}
        else if(mapId==1){plat(5350,285,190);ringArc(5350,7,65,370,80);}
        else if(mapId==2){plat(5450,270,180);ringArc(5400,7,64,360,86);}
        else if(mapId==3){plat(5200,270,190);ringArc(5230,8,62,355,92);}
        else if(mapId==4){plat(5400,255,190);ringArc(5350,8,62,350,96);}
        else {plat(5350,250,200);ringArc(5300,8,60,345,105);}

        // Signature set-pieces per world: hand placed, readable and reachable.
        if(mapId==1){
            plat(1840,255,145); plat(3990,255,145);
            ringArc(1835,5,38,250,42); ringArc(3985,5,38,250,42);
        }else if(mapId==2){
            plat(3260,245,150); springs.add(new RectF(3198,GROUND-20,3246,GROUND));
            ringArc(3240,6,46,245,62);
        }else if(mapId==3){
            springs.add(new RectF(980,GROUND-20,1028,GROUND));
            springs.add(new RectF(2580,GROUND-20,2628,GROUND));
            ringArc(2550,7,52,315,88);
        }else if(mapId==4){
            plat(2210,235,155); plat(4550,235,155);
            ringArc(2180,6,48,230,58); ringArc(4520,6,48,230,58);
        }else if(mapId==5){
            plat(4860,225,155); springs.add(new RectF(5025,GROUND-20,5073,GROUND));
            ringArc(4800,8,48,280,105);
        }

        // ACT 2 extension: every course now continues far beyond the old 6100px
        // finish. It adds a second half with longer runs, vertical detours,
        // springs, hazards and alternate upper routes before the real goal.
        buildExtendedAct();
    }

    private void buildExtendedAct(){
        // Shared backbone keeps the extension readable while each world gets
        // its own rhythm/set pieces below.
        ground(6000,6520); ground(6660,7280); ground(7410,8060);
        ground(8190,8820); ground(8950,9580); ground(9710,WORLD);

        if(mapId==0){
            plat(6200,345,190); plat(6810,305,175); plat(7080,255,165);
            plat(7560,350,210); plat(7860,295,180); plat(8340,340,210);
            plat(8650,285,185); plat(9120,350,220); plat(9460,295,180); plat(9880,335,200);
            springs.add(new RectF(6460,GROUND-20,6508,GROUND)); springs.add(new RectF(8760,GROUND-20,8808,GROUND));
            spikes.add(new RectF(7200,GROUND-18,7240,GROUND)); spikes.add(new RectF(9490,GROUND-18,9530,GROUND));
            ringArc(6070,8,68,390,90); ringRow(6720,7200,70,392); ringArc(7460,9,66,390,110);
            ringRow(8240,8720,68,392); ringArc(9000,9,66,390,100); ringRow(9740,10040,64,392);
        }else if(mapId==1){
            plat(6150,330,180); plat(6420,275,160); plat(6760,350,190); plat(7050,300,170);
            plat(7500,330,200); plat(7790,265,170); plat(8280,345,205); plat(8580,290,175);
            plat(9050,335,210); plat(9370,270,175); plat(9820,330,210);
            springs.add(new RectF(7240,GROUND-20,7288,GROUND)); springs.add(new RectF(9520,GROUND-20,9568,GROUND));
            spikes.add(new RectF(7990,GROUND-18,8030,GROUND));
            ringArc(6060,9,66,390,115); ringRow(6720,7150,68,245); ringArc(7440,8,70,390,100);
            ringRow(8250,8680,68,250); ringArc(8990,9,66,390,110); ringRow(9740,10030,62,392);
        }else if(mapId==2){
            plat(6180,335,190); plat(6480,275,165); plat(6810,220,150); plat(7480,345,210);
            plat(7790,285,180); plat(8330,330,205); plat(8640,255,170); plat(9090,345,220);
            plat(9420,280,175); plat(9860,325,205);
            springs.add(new RectF(6460,GROUND-20,6508,GROUND)); springs.add(new RectF(8020,GROUND-20,8068,GROUND));
            springs.add(new RectF(9540,GROUND-20,9588,GROUND));
            spikes.add(new RectF(7160,GROUND-18,7200,GROUND)); spikes.add(new RectF(8740,GROUND-18,8780,GROUND));
            ringArc(6060,9,65,390,125); ringArc(6680,7,52,300,105); ringRow(7480,7950,68,265);
            ringArc(8230,9,64,390,120); ringRow(9020,9480,66,265); ringArc(9720,7,58,390,90);
        }else if(mapId==3){
            plat(6180,325,210); plat(6800,315,220); plat(7480,300,220); plat(8240,320,220);
            plat(9000,300,220); plat(9760,315,220);
            springs.add(new RectF(6470,GROUND-20,6518,GROUND)); springs.add(new RectF(7220,GROUND-20,7268,GROUND));
            springs.add(new RectF(8000,GROUND-20,8048,GROUND)); springs.add(new RectF(8760,GROUND-20,8808,GROUND));
            springs.add(new RectF(9520,GROUND-20,9568,GROUND));
            spikes.add(new RectF(8470,GROUND-18,8510,GROUND));
            ringArc(6040,10,64,390,130); ringArc(6700,9,65,390,130); ringArc(7440,9,65,390,135);
            ringArc(8200,9,65,390,135); ringArc(8960,9,65,390,130); ringArc(9700,7,62,390,100);
        }else if(mapId==4){
            plat(6200,345,205); plat(6500,270,175); plat(6850,205,155); plat(7500,330,210);
            plat(7820,250,180); plat(8320,340,215); plat(8640,235,175); plat(9100,325,220);
            plat(9440,245,180); plat(9860,320,210);
            springs.add(new RectF(6460,GROUND-20,6508,GROUND)); springs.add(new RectF(8040,GROUND-20,8088,GROUND));
            springs.add(new RectF(9560,GROUND-20,9608,GROUND));
            spikes.add(new RectF(7160,GROUND-18,7200,GROUND)); spikes.add(new RectF(8780,GROUND-18,8820,GROUND));
            ringArc(6070,8,68,390,110); ringArc(6460,8,55,300,130); ringRow(7480,7960,68,260);
            ringArc(8240,9,64,390,125); ringArc(9020,8,58,300,125); ringRow(9720,10030,62,392);
        }else{
            // Finale extension: a longer escalating gauntlet with safe readable
            // landing zones; the pursuit becomes intense without impossible gaps.
            plat(6160,340,200); plat(6450,285,175); plat(6780,235,160); plat(7480,335,215);
            plat(7800,275,180); plat(8320,325,220); plat(8650,260,175); plat(9100,330,220);
            plat(9440,265,180); plat(9820,315,220);
            springs.add(new RectF(6460,GROUND-20,6508,GROUND)); springs.add(new RectF(7240,GROUND-20,7288,GROUND));
            springs.add(new RectF(8040,GROUND-20,8088,GROUND)); springs.add(new RectF(8800,GROUND-20,8848,GROUND));
            springs.add(new RectF(9560,GROUND-20,9608,GROUND));
            spikes.add(new RectF(7160,GROUND-18,7200,GROUND)); spikes.add(new RectF(8720,GROUND-18,8760,GROUND));
            spikes.add(new RectF(9480,GROUND-18,9520,GROUND));
            ringArc(6040,9,66,390,120); ringArc(6680,8,58,320,115); ringArc(7440,9,65,390,125);
            ringArc(8240,9,64,390,130); ringArc(9000,9,62,390,130); ringArc(9720,7,60,390,100);
        }
    }

    private boolean hasGroundAt(float q){
        for(RectF s:solids) if(q>=s.left+10 && q<=s.right-10 && Math.abs(s.top-GROUND)<2) return true;
        return false;
    }

    @Override void world(Canvas c,int w,int h){
        float sc=h/H;
        boolean crit=(state==ESCAPE||state==HUNT)&&time<=15;
        float danger=state==HUNT?1:crit?clamp((15-time)/15,0,1):0;
        int[][] sky={{96,190,242},{35,91,82},{124,190,232},{47,40,74},{92,45,128},{104,28,34}};
        int[][] low={{194,239,217},{27,67,55},{215,239,249},{63,55,77},{56,25,79},{45,20,25}};
        int si=Math.max(0,Math.min(5,mapId));
        int skyC=Color.rgb(sky[si][0],sky[si][1],sky[si][2]);
        int lowC=Color.rgb(low[si][0],low[si][1],low[si][2]);
        p.setColor(blend(skyC,Color.rgb(28,10,42),danger));c.drawRect(0,0,w,h*.60f,p);
        p.setColor(blend(lowC,Color.rgb(105,25,35),danger));c.drawRect(0,h*.60f,w,h,p);
        drawStageBackground(c,w,h,danger);

        float shake=crit?(float)Math.sin(clock*35)*(1.2f+danger*2.3f):0;
        c.save();c.scale(sc,sc);c.translate(-cam+shake,0);
        drawDecor(c,danger); drawPlatforms(c,danger); drawRings(c); drawSecretMedals(c); drawSprings(c); drawSpikes(c);
        drawStart(c); drawGoal(c); drawPlayer(c); if(yActive)drawYoshi(c);
        c.restore();
        if(crit)dangerOverlay(c,w,h,danger);
    }

    private void drawStageBackground(Canvas c,int w,int h,float d){
        float sc=h/H,t=(float)clock;
        int id=Math.max(0,Math.min(5,mapId));
        if(id==0){
            p.setColor(blend(Color.rgb(83,151,105),Color.rgb(45,31,59),d));
            for(int i=-2;i<12;i++){float q=(i*300-cam*.10f)*sc;Path z=new Path();z.moveTo(q-190*sc,h*.66f);z.lineTo(q,h*.30f);z.lineTo(q+190*sc,h*.66f);z.close();c.drawPath(z,p);}
            p.setColor(blend(Color.rgb(39,112,67),Color.rgb(34,25,47),d));for(int i=-2;i<22;i++)c.drawCircle((i*150-cam*.22f)*sc,h*.74f,95*sc,p);
        }else if(id==1){
            p.setColor(blend(Color.rgb(16,52,43),Color.rgb(37,19,48),d));for(int i=-1;i<18;i++){float q=(i*210-cam*.17f)*sc;c.drawRect(q,h*.18f,q+32*sc,h*.72f,p);c.drawCircle(q+16*sc,h*.18f,72*sc,p);}
            p.setColor(Color.argb(90,255,194,73));for(int i=0;i<10;i++){float q=(i*260-cam*.28f)*sc;c.drawCircle(q,h*(.25f+.12f*(i%3)),5*sc,p);}
        }else if(id==2){
            p.setColor(blend(Color.rgb(207,232,248),Color.rgb(68,52,86),d));for(int i=-2;i<13;i++){float q=(i*280-cam*.11f)*sc;Path z=new Path();z.moveTo(q-150*sc,h*.68f);z.lineTo(q,h*.20f);z.lineTo(q+150*sc,h*.68f);z.close();c.drawPath(z,p);}
            p.setColor(Color.argb(155,240,250,255));for(int i=0;i<34;i++){float q=((i*97+(t*18)%97)-cam*.05f)*sc;c.drawCircle(q,h*((i*37)%100)/100f,2.5f*sc,p);}
        }else if(id==3){
            p.setColor(blend(Color.rgb(31,28,48),Color.rgb(55,17,33),d));for(int i=-1;i<14;i++){float q=(i*260-cam*.12f)*sc;c.drawRect(q,h*.32f,q+145*sc,h*.73f,p);c.drawRect(q+28*sc,h*.22f,q+54*sc,h*.73f,p);}
            p.setColor(Color.argb(120,244,94,48));for(int i=0;i<12;i++)c.drawCircle((i*240-cam*.2f)*sc,h*.63f,8*sc,p);
        }else if(id==4){
            p.setColor(blend(Color.rgb(70,31,104),Color.rgb(26,15,43),d));for(int i=-2;i<14;i++){float q=(i*300-cam*.13f)*sc;Path z=new Path();z.moveTo(q-130*sc,h*.66f);z.lineTo(q,h*.29f);z.lineTo(q+130*sc,h*.66f);z.close();c.drawPath(z,p);}
            p.setColor(Color.argb(105,224,116,255));for(int i=0;i<18;i++)c.drawCircle((i*170-cam*.25f)*sc,h*(.25f+.3f*((i%4)/4f)),5*sc,p);
        }else{
            p.setColor(blend(Color.rgb(54,20,26),Color.rgb(20,8,20),d));for(int i=-1;i<13;i++){float q=(i*310-cam*.10f)*sc;c.drawRect(q,h*.38f,q+180*sc,h*.73f,p);c.drawRect(q+55*sc,h*.22f,q+90*sc,h*.73f,p);}
            p.setColor(Color.argb(130,255,80,34));for(int i=0;i<14;i++){float q=(i*220-cam*.2f)*sc;c.drawCircle(q,h*.66f,10*sc,p);}
        }
    }

    @Override void drawPlatforms(Canvas c,float d){
        int id=Math.max(0,Math.min(5,mapId));
        int[] body={Color.rgb(125,72,38),Color.rgb(48,70,55),Color.rgb(115,151,171),Color.rgb(58,55,67),Color.rgb(78,42,101),Color.rgb(72,43,42)};
        int[] body2={Color.rgb(93,57,35),Color.rgb(31,48,39),Color.rgb(76,112,137),Color.rgb(37,36,46),Color.rgb(51,28,73),Color.rgb(43,28,29)};
        int[] top={Color.rgb(57,172,66),Color.rgb(57,112,69),Color.rgb(211,239,250),Color.rgb(125,117,126),Color.rgb(167,83,197),Color.rgb(222,76,42)};
        int[] edge={Color.rgb(145,229,84),Color.rgb(109,160,93),Color.rgb(245,253,255),Color.rgb(188,173,170),Color.rgb(224,130,244),Color.rgb(255,145,55)};
        for(RectF s:solids){
            p.setColor(blend(body[id],Color.rgb(62,29,38),d));c.drawRect(s,p);
            float tile=id==2?22:28;
            for(float yy=s.top+10;yy<s.bottom;yy+=tile)for(float xx=s.left;xx<s.right;xx+=tile){
                int a=(int)((xx-s.left)/tile),b=(int)((yy-s.top)/tile);
                p.setColor(((a+b)&1)==0?blend(body[id],Color.rgb(90,38,43),d):blend(body2[id],Color.rgb(48,26,34),d));
                c.drawRect(xx,yy,Math.min(xx+tile,s.right),Math.min(yy+tile,s.bottom),p);
            }
            p.setColor(blend(top[id],Color.rgb(128,45,47),d));c.drawRect(s.left,s.top,s.right,Math.min(s.bottom,s.top+11),p);
            p.setColor(blend(edge[id],Color.rgb(228,72,50),d));c.drawRect(s.left,s.top,s.right,Math.min(s.bottom,s.top+4),p);
            if(id==2){p.setColor(Color.argb(110,255,255,255));for(float xx=s.left+12;xx<s.right;xx+=42)c.drawCircle(xx,s.top+7,3,p);}
            if(id==3){p.setColor(Color.argb(100,0,0,0));for(float xx=s.left+24;xx<s.right;xx+=56)c.drawRect(xx,s.top+13,xx+5,s.bottom,p);}
        }
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
        // course progress bar
        float prog=clamp(x/GOAL,0,1),barL=w*.29f,barR=w*.43f,barY=34*s;
        p.setColor(Color.rgb(34,54,79));c.drawRoundRect(barL,barY,barR,barY+7*s,4*s,4*s,p);
        p.setColor(state==HUNT?Color.rgb(235,67,61):Color.rgb(255,220,61));c.drawRoundRect(barL,barY,barL+(barR-barL)*prog,barY+7*s,4*s,4*s,p);
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
        if(titleCard>0f) titleCard=Math.max(0f,titleCard-0.025f);
        if(titleCard>0f && (state==PLAY||state==HUNT)){
            float a=Math.min(1f,titleCard*2f);
            p.setColor(Color.argb((int)(175*a),5,12,24));
            c.drawRoundRect(w*.28f,h*.37f,w*.72f,h*.58f,14,14,p);
            p.setTextAlign(Paint.Align.CENTER);p.setTypeface(Typeface.MONOSPACE);p.setFakeBoldText(true);
            p.setColor(Color.argb((int)(255*a),255,225,70));p.setTextSize(Math.max(18,h*.048f));
            c.drawText("COURSE "+(mapId+1),w*.5f,h*.45f,p);
            p.setColor(Color.argb((int)(255*a),255,255,255));p.setTextSize(Math.max(13,h*.029f));
            String[] n={"MEADOW OF MEMORIES","WHISPERING WOODS","FROZEN HEIGHTS","ABANDONED KEEP","INVERTED DREAM","FINAL CONFRONTATION"};
            c.drawText(n[Math.max(0,Math.min(5,mapId))],w*.5f,h*.52f,p);
            p.setTextAlign(Paint.Align.LEFT);p.setTypeface(Typeface.DEFAULT);p.setFakeBoldText(false);
        }
        // Gameplay feedback: short, transparent effects only; never a fullscreen black transition.
        float sp=Math.min(1f,Math.abs(vx)/405f);
        if(sp>.72f){
            p.setStrokeWidth(2);p.setColor(Color.argb((int)(42*sp),255,255,255));
            for(int i=0;i<9;i++){float yy=(i*61+(float)clock*170)%h;float len=28+sp*52;c.drawLine(w*.72f,yy,w*.72f+len,yy,p);}
        }
        if(ringFlash>0f){
            float a=ringFlash/.28f;p.setTextAlign(Paint.Align.CENTER);p.setTypeface(Typeface.MONOSPACE);p.setFakeBoldText(true);
            p.setColor(Color.argb((int)(255*a),255,224,65));p.setTextSize(Math.max(18,h*.040f));
            c.drawText("+ RING",w*.5f,h*(.30f-.035f*(1-a)),p);p.setTextAlign(Paint.Align.LEFT);p.setTypeface(Typeface.DEFAULT);p.setFakeBoldText(false);
        }
        if(state==HUNT && yActive){
            float pulse=.5f+.5f*(float)Math.sin(clock*8);
            p.setColor(Color.argb((int)(55+45*pulse),205,38,45));c.drawRect(0,52*Math.max(1f,h/480f),w,56*Math.max(1f,h/480f),p);
            p.setTextAlign(Paint.Align.CENTER);p.setTypeface(Typeface.MONOSPACE);p.setFakeBoldText(true);
            p.setTextSize(Math.max(11,h*.022f));p.setColor(Color.argb((int)(170+80*pulse),255,225,120));c.drawText("YOSHI IS HUNTING YOU",w/2f,72*Math.max(1f,h/480f),p);
            p.setTextAlign(Paint.Align.LEFT);p.setTypeface(Typeface.DEFAULT);p.setFakeBoldText(false);
        }
        // Course-specific readable gimmick feedback.
        p.setTextAlign(Paint.Align.CENTER);p.setTypeface(Typeface.MONOSPACE);p.setFakeBoldText(true);
        if(mapId==2 && (state==PLAY||state==HUNT)){
            p.setColor(Color.argb(150,205,240,255));p.setTextSize(Math.max(10,h*.019f));
            c.drawText("FROZEN • KEEP MOMENTUM",w*.5f,h*.94f,p);
        }else if(mapId==3 && (state==PLAY||state==HUNT)){
            float cyc=gimmickClock%5f;
            if(cyc>3.0f){
                float pulse=.5f+.5f*(float)Math.sin(clock*11);
                p.setColor(Color.argb((int)(130+100*pulse),255,125,60));p.setTextSize(Math.max(11,h*.022f));
                c.drawText(cyc<4.35f?"⚠ KEEP MOVING":"SAFE",w*.5f,h*.90f,p);
                if(dangerPulse>0)p.setColor(Color.argb((int)(45*(dangerPulse/.16f)),255,70,30));
            }
        }else if(mapId==4 && (state==PLAY||state==HUNT)){
            boolean light=((int)(gimmickClock/3.5f)%2==1);
            p.setColor(Color.argb(160,224,168,255));p.setTextSize(Math.max(10,h*.019f));
            c.drawText(light?"GRAVITY SHIFT • LIGHT":"GRAVITY SHIFT • NORMAL",w*.5f,h*.94f,p);
        }else if(mapId==5 && state==HUNT){
            float pulse=.5f+.5f*(float)Math.sin(clock*9);
            p.setColor(Color.argb((int)(170+75*pulse),255,90,65));p.setTextSize(Math.max(12,h*.024f));
            c.drawText(x>WORLD*.62f?"FINAL RUSH!":"DON'T LOOK BACK",w*.5f,h*.90f,p);
        }
        p.setTextAlign(Paint.Align.LEFT);p.setTypeface(Typeface.DEFAULT);p.setFakeBoldText(false);

        // subtle cinematic scanline texture.
        p.setColor(Color.argb(9,255,255,255));
        for(int yy=1;yy<h;yy+=5)c.drawRect(0,yy,w,yy+1,p);
    }



    private void unlockAchievement(int id){
        if(id<0||id>=ACH_NAMES.length)return;
        int bit=1<<id;
        if((achievementMask&bit)!=0)return;
        achievementMask|=bit;
        prefs.edit().putInt("achievements_v13",achievementMask).apply();
        achievementToast=3.2f; achievementToastText=ACH_NAMES[id];
        beep(ToneGenerator.TONE_PROP_ACK,90);
    }

    private void collectSecretMedal(){
        if(!(state==PLAY||state==ESCAPE||state==HUNT) || mapId<0||mapId>5)return;
        for(int i=0;i<3;i++){
            if((medalMask[mapId]&(1<<i))!=0)continue;
            float dx=(x+18)-medalX[mapId][i],dy=(y+24)-medalY[mapId][i];
            if(dx*dx+dy*dy<42*42){
                medalMask[mapId]|=1<<i; totalMedals++;
                prefs.edit().putInt("medals_"+mapId+"_v13",medalMask[mapId]).putInt("total_medals_v13",totalMedals).apply();
                achievementToast=2.8f; achievementToastText="SECRET MEDAL  "+totalMedals+"/18";
                score+=750; beep(ToneGenerator.TONE_PROP_ACK,80);
                if(totalMedals>=18)unlockAchievement(13);
            }
        }
    }

    private void drawSecretMedals(Canvas c){
        if(mapId<0||mapId>5)return;
        for(int i=0;i<3;i++){
            if((medalMask[mapId]&(1<<i))!=0)continue;
            float xx=medalX[mapId][i],yy=medalY[mapId][i];
            float pulse=1f+.10f*(float)Math.sin(clock*5+i);
            p.setColor(Color.argb(65,255,220,70));c.drawCircle(xx,yy,23*pulse,p);
            p.setColor(Color.rgb(255,211,55));c.drawCircle(xx,yy,13*pulse,p);
            p.setColor(Color.rgb(120,72,25));c.drawCircle(xx,yy,7*pulse,p);
            p.setColor(Color.WHITE);p.setTextAlign(Paint.Align.CENTER);p.setFakeBoldText(true);p.setTextSize(11);
            c.drawText("Y",xx,yy+4,p);p.setTextAlign(Paint.Align.LEFT);p.setFakeBoldText(false);
        }
    }

    private void saveBestResult(){
        if(resultSaved)return;
        resultSaved=true;
        String k="best_"+mapId+"_v13";
        int sec=Math.max(1,Math.round(runElapsed));
        int old=prefs.getInt(k,999999);
        android.content.SharedPreferences.Editor e=prefs.edit();
        if(sec<old)e.putInt(k,sec);
        e.putInt("best_rank_"+mapId+"_v13",Math.max(prefs.getInt("best_rank_"+mapId+"_v13",0),rankValue(rank)));
        e.apply();
    }

    private int rankValue(String r){
        if("P".equals(r))return 5;if("S".equals(r))return 4;if("A".equals(r))return 3;if("B".equals(r))return 2;return 1;
    }

    private String performanceRank(){
        if(lap>=3 && !tookDamageThisRun && totalMedals>0)return "P";
        int v=0;
        if(!tookDamageThisRun)v+=2;
        if(runRingsCollected>=35)v+=2; else if(runRingsCollected>=18)v++;
        if(runElapsed>0&&runElapsed<155)v+=2; else if(runElapsed<210)v++;
        return v>=6?"S":v>=4?"A":v>=2?"B":"C";
    }

    private void drawAchievementToast(Canvas c){
        int w=c.getWidth(),h=c.getHeight();
        float a=Math.min(1f,achievementToast*2f),bw=Math.min(w*.54f,560),bh=Math.max(58,h*.105f);
        float l=(w-bw)/2f,t=h*.12f;
        p.setColor(Color.argb((int)(225*a),8,20,38));c.drawRoundRect(l,t,l+bw,t+bh,14,14,p);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);p.setColor(Color.argb((int)(255*a),255,211,55));c.drawRoundRect(l,t,l+bw,t+bh,14,14,p);p.setStyle(Paint.Style.FILL);
        p.setTextAlign(Paint.Align.CENTER);p.setTypeface(Typeface.MONOSPACE);p.setFakeBoldText(true);
        p.setColor(Color.argb((int)(255*a),255,211,55));p.setTextSize(Math.max(12,h*.024f));c.drawText("★ CONQUISTA DESBLOQUEADA",w/2f,t+bh*.40f,p);
        p.setColor(Color.argb((int)(255*a),255,255,255));p.setTextSize(Math.max(13,h*.027f));c.drawText(achievementToastText,w/2f,t+bh*.72f,p);
        p.setTextAlign(Paint.Align.LEFT);p.setTypeface(Typeface.DEFAULT);p.setFakeBoldText(false);
    }

    private void drawEnhancedResult(Canvas c){
        int w=c.getWidth(),h=c.getHeight();
        String pr=performanceRank();
        float cw=Math.min(w*.70f,720),ch=Math.min(h*.66f,410),l=(w-cw)/2f,t=(h-ch)/2f;
        p.setColor(Color.argb(238,5,15,30));c.drawRoundRect(l,t,l+cw,t+ch,22,22,p);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(3);p.setColor(Color.rgb(255,211,55));c.drawRoundRect(l,t,l+cw,t+ch,22,22,p);p.setStyle(Paint.Style.FILL);
        p.setTextAlign(Paint.Align.CENTER);p.setTypeface(Typeface.MONOSPACE);p.setFakeBoldText(true);
        p.setColor(Color.rgb(255,211,55));p.setTextSize(Math.max(22,h*.050f));c.drawText("COURSE CLEAR!",w/2f,t+ch*.17f,p);
        p.setColor(Color.WHITE);p.setTextSize(Math.max(14,h*.029f));c.drawText("TIME  "+Math.max(1,Math.round(runElapsed))+"s    RINGS +"+runRingsCollected+"    MEDALS "+Integer.bitCount(medalMask[mapId])+"/3",w/2f,t+ch*.34f,p);
        c.drawText("NO DAMAGE  "+(!tookDamageThisRun?"YES":"NO")+"    TOTAL MEDALS "+totalMedals+"/18",w/2f,t+ch*.46f,p);
        p.setColor(pr.equals("S")||pr.equals("P")?Color.rgb(255,226,70):Color.rgb(105,213,255));p.setTextSize(Math.max(42,h*.11f));c.drawText("RANK "+pr,w/2f,t+ch*.69f,p);
        int best=prefs.getInt("best_"+mapId+"_v13",999999);
        p.setColor(Color.rgb(170,210,235));p.setTextSize(Math.max(12,h*.024f));c.drawText(best<999999?"BEST  "+best+"s":"NEW RECORD!",w/2f,t+ch*.80f,p);
        p.setColor(Color.WHITE);p.setTextSize(Math.max(13,h*.026f));c.drawText("TOQUE PARA VOLTAR AO MAPA",w/2f,t+ch*.91f,p);
        p.setTextAlign(Paint.Align.LEFT);p.setTypeface(Typeface.DEFAULT);p.setFakeBoldText(false);
    }

    @Override void drawStart(Canvas c){
        super.drawStart(c);
        p.setColor(Color.rgb(255,245,190));p.setTextSize(14);
        String[] n={"MEADOW OF MEMORIES","WHISPERING WOODS","FROZEN HEIGHTS","ABANDONED KEEP","INVERTED DREAM","FINAL CONFRONTATION"};
        c.drawText("ACT "+(mapId+1)+"  •  "+n[Math.max(0,Math.min(5,mapId))],165,352,p);
    }

    @Override void drawGoal(Canvas c){
        super.drawGoal(c);
        p.setColor(Color.rgb(230,190,255));p.setTextSize(14);
        c.drawText("YOSHI'S RIFT",GOAL-25,305,p);
    }

    @Override void start(){
        int chosen=mapId;
        super.start();
        runStartLives=lives; runStartRings=ringCount; runRingsCollected=0; runElapsed=0f;
        tookDamageThisRun=false; resultSaved=false;
        mapId=chosen; buildLevel();
        storyIntro=(mapId==0)&&!prefs.getBoolean("prologue_seen_v11",false); storyPage=0;
        playTrack(R.raw.run_you_fool,true);
    }

    private void unlockAfterCourse(){
        courseComplete=true;
        unlockAchievement(mapId==0?0:mapId==1?2:mapId==2?3:mapId==3?4:mapId==4?5:12);
        if(mapId==5)unlockAchievement(11);
        if(!tookDamageThisRun)unlockAchievement(9);
        if(runElapsed>0 && runElapsed<155f)unlockAchievement(10);
        saveBestResult();

        coursesCleared=Math.max(coursesCleared,mapId+1);
        prefs.edit().putInt("courses_cleared_v12",coursesCleared).apply();
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
        Bitmap panel=storyPage<3?hqPanel1:hqPanel2;
        if(panel!=null){
            int part=storyPage%3;
            int sw=panel.getWidth(),sh=panel.getHeight();
            // The supplied HQ art is presented as six cinematic crops instead
            // of replacing four pages with generated-looking placeholder art.
            int cropW=Math.max(1,sw/3);
            int left=Math.min(sw-1,part*cropW);
            int right=(part==2)?sw:Math.min(sw,left+cropW);
            Rect src=new Rect(left,0,right,sh);
            RectF dst=new RectF(l+10,t+10,r-10,b-48);
            c.save();
            c.clipRect(dst);
            c.drawBitmap(panel,src,dst,px);
            c.restore();
        }else{
            p.setColor(Color.rgb(28,38,52));c.drawRect(l+10,t+10,r-10,b-48,p);
            Bitmap yb=(yoshiFly!=null?yoshiFly:(yoshiRun!=null?yoshiRun:yoshiChase));
            drawBitmapAspect(c,yb,new RectF(w*.24f,h*.22f,w*.55f,h*.69f),false,true);
            drawBitmapAspect(c,marioSmw!=null?marioSmw:marioStand,new RectF(w*.68f,h*.38f,w*.80f,h*.69f),false,true);
        }
        String[] cap={"Antes da fuga...","O último salto.","Yoshi ficou para trás.","Ele não esqueceu.","CORRA.","A CAÇADA COMEÇA."};
        p.setTextAlign(Paint.Align.CENTER);p.setFakeBoldText(true);p.setColor(Color.rgb(20,24,30));p.setTextSize(Math.max(15,h*.031f));
        c.drawText(cap[storyPage],w/2,b-18,p);
        p.setColor(Color.WHITE);p.setTextSize(Math.max(16,h*.034f));c.drawText("PRÓLOGO  "+(storyPage+1)+"/6",w/2,h*.065f,p);
        p.setColor(Color.rgb(255,211,55));p.setTextSize(Math.max(13,h*.026f));c.drawText(storyPage<5?"TOQUE PARA AVANÇAR":"TOQUE PARA CORRER",w/2,h*.89f,p);
        p.setTextAlign(Paint.Align.LEFT);p.setFakeBoldText(false);
    }

    @Override void menuUI(Canvas c,int w,int h){
        float pulse=.5f+.5f*(float)Math.sin(scenePulse*2.2f);
        // Dark SNES-like panel over the animated base menu scene.
        float pw=Math.min(470,w*.44f),ph=Math.min(286,h*.55f),l=w*.53f,t=h*.27f;
        p.setColor(Color.argb(218,5,13,28));c.drawRoundRect(l,t,l+pw,t+ph,10,10,p);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Math.max(2,h*.004f));
        p.setColor(Color.rgb(84,185,239));c.drawRoundRect(l,t,l+pw,t+ph,10,10,p);p.setStyle(Paint.Style.FILL);

        p.setTypeface(Typeface.MONOSPACE);p.setFakeBoldText(true);p.setTextAlign(Paint.Align.LEFT);
        p.setColor(Color.rgb(255,222,72));p.setTextSize(Math.max(18,h*.038f));c.drawText("YOSHI ESCAPE",l+28,t+42,p);
        p.setColor(Color.rgb(150,190,215));p.setTextSize(Math.max(10,h*.020f));c.drawText("THE REVENGE RUN",l+29,t+64,p);

        String[] it={"▶  JOGAR","◆  SOM  "+(sound?"ON":"OFF"),"?  COMO JOGAR","★  CRÉDITOS"};
        float first=t+112,row=(ph-128)/3f;
        for(int i=0;i<4;i++){
            float yy=first+i*row;
            if(i==0){
                p.setColor(Color.argb((int)(145+45*pulse),255,210,55));
                c.drawRoundRect(l+18,yy-29,l+pw-18,yy+13,7,7,p);
                p.setColor(Color.rgb(15,24,38));
            }else p.setColor(Color.WHITE);
            p.setTextSize(Math.max(15,h*.030f));c.drawText(it[i],l+34,yy,p);
        }
        p.setTextAlign(Paint.Align.CENTER);p.setTextSize(Math.max(10,h*.019f));p.setColor(Color.rgb(128,176,202));
        c.drawText("ESCAPE • SURVIVE • UNLOCK THE WORLD",l+pw/2,t+ph-17,p);
        p.setTextAlign(Paint.Align.LEFT);p.setTypeface(Typeface.DEFAULT);p.setFakeBoldText(false);
    }

    private void drawMapSelect(Canvas c){
        unlockedMap=Math.max(unlockedMap,Math.max(0,Math.min(5,prefs.getInt("unlocked_map_v11",0))));
        // tiny living-map motion: selected node breathes instead of looking like a static mockup.
        scenePulse+=0.016f; mapPulse+=0.016f;
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
            float breathe=(!locked && i==Math.min(unlockedMap,5))?(float)(3+2*Math.sin(mapPulse*3)):0;
            p.setColor(locked?Color.rgb(72,78,90):(i==5?Color.rgb(210,63,46):Color.rgb(241,199,55)));c.drawCircle(px,py,25+breathe,p);
            p.setColor(Color.rgb(17,24,35));c.drawCircle(px,py,16,p);
            if(!locked)drawBitmapAspect(c,marioStand,new RectF(px-15,py-33,px+15,py+7),false,true);
            else {p.setColor(Color.WHITE);p.setTextSize(17);c.drawText("X",px,py+6,p);}
            p.setTextSize(Math.max(10,h*.020f));p.setColor(Color.WHITE);c.drawText((i+1)+". "+names[i],px,py+50,p);
        }
        p.setTextSize(Math.max(12,h*.024f));p.setColor(Color.rgb(190,220,235));c.drawText("PROGRESSO  "+coursesCleared+"/6  •  MEDALHAS "+totalMedals+"/18  •  CONQUISTAS "+Integer.bitCount(achievementMask)+"/14",w/2f,h*.90f,p);
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

    @Override void render(Canvas c){
        super.render(c);
        if(mapSelect) drawMapSelect(c);
        if(storyIntro) drawStory(c);
        if(!mapSelect && !storyIntro && state==WIN){
            int w=c.getWidth(),h=c.getHeight(); float pulse=.5f+.5f*(float)Math.sin(clock*5);
            p.setTextAlign(Paint.Align.CENTER);p.setTypeface(Typeface.MONOSPACE);p.setFakeBoldText(true);
            p.setColor(Color.argb((int)(180+70*pulse),255,222,65));p.setTextSize(Math.max(15,h*.030f));
            c.drawText(mapId<5?"NEW COURSE UNLOCKED!":"THE CHASE IS OVER... FOR NOW.",w/2f,h*.18f,p);
            p.setTextAlign(Paint.Align.LEFT);p.setTypeface(Typeface.DEFAULT);p.setFakeBoldText(false);
        }
        // Presentation overlays only during active gameplay; menus/cutscenes must never be covered.
        if(!mapSelect && !storyIntro && (state==PLAY || state==ESCAPE || state==HUNT)) drawPolishOverlay(c);
        if(achievementToast>0f)drawAchievementToast(c);
        if(!mapSelect && !storyIntro && state==WIN)drawEnhancedResult(c);
    }

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
        float h=getHeight(),w=getWidth(),pw=Math.min(470,w*.44f),ph=Math.min(286,h*.55f),l=w*.53f,t=h*.27f;
        float first=t+112,row=(ph-128)/3f; int hit=-1;
        if(x>=l&&x<=l+pw) for(int i=0;i<4;i++) if(Math.abs(y-(first+i*row))<30) hit=i;
        if(hit==0){mapSelect=true;beep(ToneGenerator.TONE_PROP_ACK,45);return;}
        if(hit==1){sound=!sound;if(sound)playTrack(R.raw.escape_menu,true);else stopTrack();return;}
        if(hit==2){state=HOW;beep(ToneGenerator.TONE_PROP_ACK,35);return;}
        if(hit==3){state=CREDITS;beep(ToneGenerator.TONE_PROP_ACK,35);return;}
    }

    @Override protected void onDetachedFromWindow(){stopTrack();super.onDetachedFromWindow();}
}
