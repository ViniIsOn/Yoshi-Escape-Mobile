package com.vini.yoshiescape;

import android.content.Context;
import android.graphics.*;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;
import android.media.ToneGenerator;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.View;
import java.util.*;

public class GameView extends View {
    static final float H=540f, WORLD=10400f, GROUND=455f, START=105f, GOAL=10120f;
    static final int MENU=0, HOW=1, CREDITS=2, PLAY=3, ESCAPE=4, HUNT=5, LAP=6, WIN=7, OVER=8;

    final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG), px=new Paint();
    final List<RectF> solids=new ArrayList<>(), springs=new ArrayList<>(), spikes=new ArrayList<>();
    final List<Ring> rings=new ArrayList<>();
    final SparseArray<PointF> touches=new SparseArray<>();
    final ToneGenerator tone=new ToneGenerator(AudioManager.STREAM_MUSIC,38);

    Bitmap marioStand, marioJump, yoshiRun, yoshiChase, yoshiFly;
    final Bitmap[] marioRun=new Bitmap[4];
    SoundPool soundPool; int jumpSound=0;

    int state=MENU, lap=0, lives=3, ringCount=0, score=0, lastSec=99;
    float x,y,vx,vy,cam,clock,time,safeX,safeY,safeT,inv,coyote,jumpBuffer;
    float yx,yy,yspeed;
    boolean yActive,toStart=false,sound=true,faceRight=true,grounded=true;
    boolean leftDown,rightDown,jumpDown,runDown;
    long last;
    String rank="C", deathCause="";

    public GameView(Context c){
        super(c); setKeepScreenOn(true);
        px.setAntiAlias(false); px.setFilterBitmap(false);
        AudioAttributes aa=new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
        soundPool=new SoundPool.Builder().setMaxStreams(4).setAudioAttributes(aa).build();
        jumpSound=soundPool.load(c, R.raw.jump, 1);
        loadSprites(); buildLevel(); menu();
    }

    Bitmap bm(int id){ return BitmapFactory.decodeResource(getResources(),id); }
    void loadSprites(){
        marioStand=bm(R.drawable.mario_stand);
        marioRun[0]=bm(R.drawable.mario_run1); marioRun[1]=bm(R.drawable.mario_run2);
        marioRun[2]=bm(R.drawable.mario_run3); marioRun[3]=bm(R.drawable.mario_run4);
        marioJump=bm(R.drawable.mario_jump);
        yoshiRun=bm(R.drawable.yoshi_run1);
        yoshiChase=bm(R.drawable.yoshi_chase);
        yoshiFly=bm(R.drawable.yoshi_fly);
    }

    void ground(float a,float b){ solids.add(new RectF(a,GROUND,b,580)); }
    void plat(float x,float y,float w){ solids.add(new RectF(x,y,x+w,y+22)); }
    void addRing(float a,float b){ rings.add(new Ring(a,b)); }

    void buildLevel(){
        solids.clear(); springs.clear(); spikes.clear(); rings.clear();
        ground(0,900); ground(1010,1900); ground(2010,3030);
        ground(3140,4070); ground(4180,5050); ground(5160,WORLD);

        plat(520,360,180); plat(1180,335,205); plat(1580,285,185);
        plat(2240,350,210); plat(2700,300,210);
        plat(3400,350,210); plat(3820,295,210);
        plat(4480,345,200); plat(4820,292,180);
        plat(5450,345,200);

        springs.add(new RectF(820,GROUND-20,868,GROUND));
        springs.add(new RectF(1880,GROUND-20,1928,GROUND));
        springs.add(new RectF(4050,GROUND-20,4098,GROUND));
        springs.add(new RectF(5030,GROUND-20,5078,GROUND));

        spikes.add(new RectF(1450,GROUND-18,1518,GROUND));
        spikes.add(new RectF(2890,GROUND-18,2958,GROUND));
        spikes.add(new RectF(4620,GROUND-18,4688,GROUND));

        lineRings(250,790,72,392);
        arcRings(1080,7,72,385,95);
        lineRings(2080,2920,80,392);
        arcRings(3230,7,78,382,100);
        lineRings(4230,4950,80,392);
        arcRings(5260,7,72,382,92);
    }
    void lineRings(float a,float b,float step,float yy){ for(float q=a;q<b;q+=step)addRing(q,yy); }
    void arcRings(float start,int n,float step,float base,float amp){
        for(int i=0;i<n;i++) addRing(start+i*step, base-(float)Math.sin(i*Math.PI/(n-1))*amp);
    }

    void menu(){ state=MENU; touches.clear(); last=System.nanoTime(); invalidate(); }
    void start(){
        state=PLAY; lap=0; lives=3; ringCount=0; score=0;
        x=START; y=GROUND-48; vx=vy=0; cam=0; safeX=x; safeY=y; safeT=0; inv=0;
        coyote=.1f; jumpBuffer=0; yActive=false; toStart=false; time=50; rank="C"; deathCause="";
        for(Ring r:rings)r.got=false;
        last=System.nanoTime();
    }
    public void resumeGameClock(){ last=System.nanoTime(); }
    boolean playable(){ return state==PLAY||state==ESCAPE||state==HUNT; }

    @Override protected void onDraw(Canvas c){
        long n=System.nanoTime(); float dt=(n-last)/1e9f; last=n;
        if(dt<=0||dt>.05f)dt=.016f;
        clock+=dt; if(playable())update(dt); render(c); postInvalidateOnAnimation();
    }

    void update(float dt){
        if(inv>0)inv-=dt;
        if(jumpBuffer>0)jumpBuffer-=dt;
        if(grounded)coyote=.10f; else coyote=Math.max(0,coyote-dt);

        float acc=grounded?1900:980, max=runDown?405:305, fr=grounded?1550:260;
        if(leftDown){vx-=acc*dt;faceRight=false;}
        if(rightDown){vx+=acc*dt;faceRight=true;}
        if(!leftDown&&!rightDown){
            float d=fr*dt; vx=Math.abs(vx)<=d?0:vx-Math.signum(vx)*d;
        }
        vx=clamp(vx,-max,max);

        if(jumpBuffer>0 && coyote>0){
            jumpBuffer=0; coyote=0; vy=-615; grounded=false; playJump();
        }
        vy=Math.min(920,vy+1540*dt);

        moveX(dt); moveY(dt); spring(); collect(); hazard();

        if(grounded){
            safeT+=dt;
            if(safeT>.75f){safeX=x;safeY=y;safeT=0;}
        } else safeT=0;
        if(y>630){deathCause="FALL";loseLife();}

        if(state==PLAY && x>GOAL-50) beginLap(1,true);
        if(state==ESCAPE||state==HUNT){
            time-=dt; int s=Math.max(0,(int)Math.ceil(time));
            if(s<=10 && s!=lastSec){lastSec=s; beep(ToneGenerator.TONE_PROP_ACK,32);}
            if(time<=0 && state!=HUNT){state=HUNT;spawnYoshi();}
            boolean hit=toStart?x<START+22:x>GOAL-48;
            if(hit)lapDone();
        }
        if(state==HUNT&&yActive)chase(dt);

        float vw=getWidth()>0&&getHeight()>0?getWidth()/(getHeight()/H):960;
        float target=clamp(x-vw*.42f,0,Math.max(0,WORLD-vw));
        cam+=(target-cam)*Math.min(1,dt*6);
    }

    void beginLap(int l,boolean left){
        lap=l; toStart=left; state=l>=3?HUNT:ESCAPE;
        time=l==1?48:l==2?45:50; lastSec=99; yActive=l>=3;
        if(yActive)spawnYoshi();
        beep(l>=3?ToneGenerator.TONE_CDMA_HIGH_L:ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD,150);
    }
    void lapDone(){vx=vy=0;if(lap>=3){rank="P";score+=5000+ringCount*25;state=WIN;yActive=false;}else state=LAP;}
    void nextLap(){if(lap==1)beginLap(2,false);else beginLap(3,true);}
    void finish(){
        rank=lap>=2?(ringCount>=35?"S":"A"):(time>=25&&ringCount>=25?"S":time>=15||ringCount>=18?"A":time>=6?"B":"C");
        score+=lap*1200+ringCount*20+lives*300; state=WIN; yActive=false;
    }

    void spawnYoshi(){
        yActive=true; yspeed=lap>=3?320:265;
        yx=toStart?Math.min(WORLD-120,x+500):Math.max(30,x-500);
        yy=GROUND-90;
    }
    void chase(float dt){
        float d=Math.signum(x-yx); if(d==0)d=toStart?-1:1;
        yx+=d*yspeed*dt; yy=GROUND-86+(float)Math.sin(clock*9)*10;
        yspeed=Math.min(lap>=3?420:365,yspeed+8*dt);
        if(Math.abs((yx+28)-(x+18))<52 && Math.abs((yy+28)-(y+24))<78){
            state=OVER; vx=vy=0; beep(ToneGenerator.TONE_CDMA_ABBR_ALERT,250);
        }
    }

    void moveX(float dt){
        x+=vx*dt; RectF r=pr();
        for(RectF s:solids)if(RectF.intersects(r,s)){
            if(vx>0)x=s.left-36; else if(vx<0)x=s.right; vx=0; r=pr();
        }
        x=clamp(x,0,WORLD-36);
    }
    void moveY(float dt){
        float oy=y,ob=y+48; y+=vy*dt; grounded=false; RectF r=pr();
        for(RectF s:solids)if(RectF.intersects(r,s)){
            if(vy>=0&&ob<=s.top+14){y=s.top-48;vy=0;grounded=true;}
            else if(vy<0&&oy>=s.bottom-10){y=s.bottom;vy=0;}
            r=pr();
        }
    }
    RectF pr(){return new RectF(x,y,x+36,y+48);}

    void spring(){
        for(RectF s:springs)if(RectF.intersects(pr(),s)&&vy>=0){
            y=s.top-48;vy=-790;grounded=false;playJump();return;
        }
    }
    void collect(){
        float cx=x+18,cy=y+24;
        for(Ring r:rings)if(!r.got){
            float dx=r.x-cx,dy=r.y-cy;
            if(dx*dx+dy*dy<1156){r.got=true;ringCount++;score+=100;}
        }
    }
    void hazard(){
        if(inv>0)return;
        for(RectF s:spikes)if(RectF.intersects(pr(),s)){
            if(ringCount>0){ringCount=Math.max(0,ringCount-Math.min(20,ringCount));inv=1.5f;vy=-360;vx=faceRight?-260:260;}
            else {deathCause="SPIKES";loseLife();}
            beep(ToneGenerator.TONE_PROP_NACK,80);return;
        }
    }
    void loseLife(){
        lives--;
        if(lives<=0){state=OVER;yActive=false;vx=vy=0;return;}
        x=safeX;y=safeY;vx=vy=0;inv=1.5f;
        if(state==ESCAPE||state==HUNT)time=Math.max(0,time-3);
    }

    void render(Canvas c){
        int w=c.getWidth(),h=c.getHeight(); if(w<=0||h<=0)return;
        if(state<=CREDITS){menuScene(c,w,h);if(state==MENU)menuUI(c,w,h);else info(c,w,h,state==HOW);return;}
        world(c,w,h); hud(c,w,h); if(playable())controls(c,w,h);
        if(state==LAP)lapUI(c,w,h); else if(state==WIN||state==OVER)result(c,w,h,state==OVER);
    }

    void world(Canvas c,int w,int h){
        float sc=h/H;
        boolean crit=(state==ESCAPE||state==HUNT)&&time<=15;
        float danger=state==HUNT?1:crit?clamp((15-time)/15,0,1):0;
        p.setColor(blend(Color.rgb(76,173,242),Color.rgb(38,16,60),danger));c.drawRect(0,0,w,h*.60f,p);
        p.setColor(blend(Color.rgb(195,236,255),Color.rgb(144,34,48),danger));c.drawRect(0,h*.60f,w,h,p);
        background(c,w,h,danger);

        float shake=crit?(float)Math.sin(clock*35)*(1.2f+danger*2.3f):0;
        c.save();c.scale(sc,sc);c.translate(-cam+shake,0);
        drawDecor(c,danger); drawPlatforms(c,danger); drawRings(c); drawSprings(c); drawSpikes(c);
        drawStart(c); drawGoal(c); drawPlayer(c); if(yActive)drawYoshi(c);
        c.restore();
        if(crit)dangerOverlay(c,w,h,danger);
    }

    void background(Canvas c,int w,int h,float d){
        float sc=h/H;
        p.setColor(blend(Color.rgb(115,145,187),Color.rgb(57,36,76),d));
        for(int i=-2;i<10;i++){
            float q=(i*330-cam*.12f)*sc,b=h*.62f,pk=h*(.30f+(i%2==0?.03f:.08f));
            Path z=new Path();z.moveTo(q-220*sc,b);z.lineTo(q,pk);z.lineTo(q+220*sc,b);z.close();c.drawPath(z,p);
        }
        p.setColor(blend(Color.rgb(42,116,84),Color.rgb(31,25,52),d));
        for(int i=-2;i<20;i++)c.drawCircle((i*170-cam*.23f)*sc,h*.75f,115*sc,p);
    }

    void drawDecor(Canvas c,float d){
        for(float q=240;q<WORLD;q+=520){
            p.setColor(blend(Color.rgb(35,124,47),Color.rgb(70,34,44),d));c.drawRect(q,407,q+6,GROUND,p);
            p.setColor(blend(Color.rgb(255,214,64),Color.rgb(242,70,60),d));c.drawRect(q-7,395,q+13,410,p);
        }
    }

    void drawPlatforms(Canvas c,float d){
        for(RectF s:solids){
            p.setColor(blend(Color.rgb(125,72,38),Color.rgb(74,34,38),d));c.drawRect(s,p);
            float tile=28;
            for(float yy=s.top+10;yy<s.bottom;yy+=tile)for(float xx=s.left;xx<s.right;xx+=tile){
                int a=(int)((xx-s.left)/tile),b=(int)((yy-s.top)/tile);
                p.setColor(((a+b)&1)==0?blend(Color.rgb(152,90,45),Color.rgb(95,45,46),d):blend(Color.rgb(105,62,34),Color.rgb(55,31,38),d));
                c.drawRect(xx,yy,Math.min(xx+tile,s.right),Math.min(yy+tile,s.bottom),p);
            }
            p.setColor(blend(Color.rgb(53,176,61),Color.rgb(123,48,49),d));c.drawRect(s.left,s.top,s.right,Math.min(s.bottom,s.top+11),p);
            p.setColor(blend(Color.rgb(146,231,82),Color.rgb(223,70,51),d));c.drawRect(s.left,s.top,s.right,Math.min(s.bottom,s.top+4),p);
        }
    }

    void drawRings(Canvas c){
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(4);
        for(Ring r:rings)if(!r.got){
            float k=1+.2f*(float)Math.sin(clock*7+r.x*.02);
            p.setColor(Color.rgb(255,210,44));c.drawOval(new RectF(r.x-8*k,r.y-13,r.x+8*k,r.y+13),p);
        }
        p.setStyle(Paint.Style.FILL);
    }
    void drawSprings(Canvas c){
        for(RectF s:springs){
            p.setColor(Color.rgb(210,31,38));c.drawRect(s.left,s.top,s.right,s.top+8,p);
            p.setColor(Color.WHITE);for(int i=0;i<3;i++){float yy=s.top+9+i*4;c.drawRect(s.left+7,yy,s.right-7,yy+2,p);}
        }
    }
    void drawSpikes(Canvas c){
        p.setColor(Color.LTGRAY);
        for(RectF s:spikes)for(float xx=s.left;xx<s.right;xx+=14){
            Path z=new Path();z.moveTo(xx,s.bottom);z.lineTo(xx+7,s.top);z.lineTo(Math.min(xx+14,s.right),s.bottom);z.close();c.drawPath(z,p);
        }
    }
    void drawStart(Canvas c){
        p.setColor(Color.rgb(132,77,38));c.drawRect(44,330,145,362,p);c.drawRect(62,362,72,GROUND,p);c.drawRect(120,362,130,GROUND,p);
        p.setColor(Color.rgb(255,235,160));p.setTextSize(18);p.setFakeBoldText(true);c.drawText("START",64,352,p);p.setFakeBoldText(false);
    }
    void drawGoal(Canvas c){
        p.setColor(Color.LTGRAY);c.drawRect(GOAL,340,GOAL+10,GROUND,p);c.drawRect(GOAL+76,340,GOAL+86,GROUND,p);
        p.setColor(Color.rgb(119,70,40));c.drawRect(GOAL-8,322,GOAL+94,354,p);
        p.setColor(Color.rgb(255,226,113));p.setTextSize(17);p.setFakeBoldText(true);c.drawText("GOAL",GOAL+18,345,p);p.setFakeBoldText(false);
    }

    void drawPlayer(Canvas c){
        Bitmap b;
        if(!grounded && marioJump!=null)b=marioJump;
        else if(Math.abs(vx)>28)b=marioRun[((int)(clock*(runDown?13:9)))%4];
        else b=marioStand;
        if(inv>0&&(((int)(clock*16))&1)==0)return;
        drawBitmapAspect(c,b,new RectF(x-8,y-8,x+44,y+53),!faceRight,true);
    }

    void drawYoshi(Canvas c){
        boolean near=Math.abs(yx-x)<950;
        Bitmap b=near?(yoshiChase!=null?yoshiChase:yoshiRun):(yoshiFly!=null?yoshiFly:yoshiRun);
        if(b==null)return;
        boolean flip=x<yx;
        drawBitmapAspect(c,b,new RectF(yx-18,yy-15,yx+92,yy+78),flip,false);
    }

    void drawBitmapAspect(Canvas c,Bitmap b,RectF box,boolean flip,boolean bottom){
        if(b==null)return;
        float k=Math.min(box.width()/b.getWidth(),box.height()/b.getHeight());
        float dw=b.getWidth()*k,dh=b.getHeight()*k,l=box.centerX()-dw/2,t=bottom?box.bottom-dh:box.centerY()-dh/2;
        RectF d=new RectF(l,t,l+dw,t+dh);
        c.save();if(flip)c.scale(-1,1,d.centerX(),d.centerY());c.drawBitmap(b,null,d,px);c.restore();
    }

    void dangerOverlay(Canvas c,int w,int h,float d){
        float pulse=.45f+.35f*(float)Math.sin(clock*9);
        p.setColor(Color.argb((int)(42+55*d*pulse),255,18,18));c.drawRect(0,0,w,h,p);
        p.setColor(Color.argb((int)(140+65*pulse),255,38,38));float q=Math.max(7,h*.014f);c.drawRect(0,0,w,q,p);c.drawRect(0,h-q,w,h,p);
    }

    String previewRank(){
        if(lap>=3)return"P";if(lap>=2)return ringCount>=35?"S":"A";
        if(ringCount>=30)return"S";if(ringCount>=18)return"A";if(ringCount>=8)return"B";return"C";
    }

    void hud(Canvas c,int w,int h){
        float d=Math.max(12,h*.022f);
        p.setColor(Color.argb(190,7,18,36));c.drawRoundRect(d,d,w-d,d+h*.115f,14,14,p);
        p.setFakeBoldText(true);p.setTextSize(Math.max(17,h*.037f));
        p.setColor(Color.rgb(255,226,70));c.drawText("TIME",d*1.6f,d+h*.047f,p);
        p.setColor(Color.WHITE);c.drawText(state==PLAY?"--":String.format("%02d",Math.max(0,(int)Math.ceil(time))),d*5.4f,d+h*.047f,p);
        p.setColor(Color.rgb(255,226,70));c.drawText("RINGS",d*1.6f,d+h*.094f,p);
        p.setColor(Color.WHITE);c.drawText(String.format("%03d",ringCount),d*6.2f,d+h*.094f,p);
        float m=w*.49f;c.drawText("LIVES "+lives,m-95,d+h*.047f,p);c.drawText("LAP "+Math.max(1,lap==0?1:lap),m-95,d+h*.094f,p);
        p.setColor(Color.rgb(255,226,70));p.setTextAlign(Paint.Align.RIGHT);c.drawText("RANK "+previewRank(),w-d*1.7f,d+h*.063f,p);
        p.setTextAlign(Paint.Align.LEFT);p.setColor(Color.WHITE);p.setTextSize(Math.max(14,h*.027f));
        String s=state==PLAY?"CHEGUE AO GOAL →":toStart?"ESCAPE ← VOLTE AO START":"LAP → CORRA AO GOAL";
        c.drawText(s,w-d*17,d+h*.102f,p);p.setFakeBoldText(false);
    }

    float[] controlGeom(){
        float h=getHeight(),w=getWidth(),r=Math.max(42,h*.082f),yy=h-r*1.22f;
        float lx=r*1.25f,rx=lx+r*2.65f,jx=w-r*1.25f,runx=jx-r*3.05f;
        return new float[]{r,yy,lx,rx,jx,runx};
    }

    void controls(Canvas c,int w,int h){
        float[] g=controlGeom();float r=g[0],yy=g[1];
        btn(c,g[2],yy,r,leftDown,"◀"); btn(c,g[3],yy,r,rightDown,"▶");
        btn(c,g[5],yy,r*.92f,runDown,"CORRER"); btn(c,g[4],yy,r*1.04f,jumpDown,"A");
    }
    void btn(Canvas c,float x,float y,float r,boolean on,String t){
        p.setColor(on?Color.argb(175,255,255,255):Color.argb(78,7,18,36));c.drawCircle(x,y,r,p);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2.5f);p.setColor(Color.argb(205,255,255,255));c.drawCircle(x,y,r,p);p.setStyle(Paint.Style.FILL);
        p.setColor(Color.WHITE);p.setTextAlign(Paint.Align.CENTER);p.setFakeBoldText(true);p.setTextSize(t.equals("CORRER")?r*.24f:r*.58f);
        Paint.FontMetrics f=p.getFontMetrics();c.drawText(t,x,y-(f.ascent+f.descent)/2,p);p.setTextAlign(Paint.Align.LEFT);p.setFakeBoldText(false);
    }

    void menuScene(Canvas c,int w,int h){
        p.setColor(Color.rgb(74,166,239));c.drawRect(0,0,w/2f,h,p);p.setColor(Color.rgb(32,18,51));c.drawRect(w/2f,0,w,h,p);
        p.setColor(Color.rgb(82,181,72));Path a=new Path();a.moveTo(0,h*.72f);a.lineTo(w*.17f,h*.35f);a.lineTo(w*.5f,h*.72f);a.close();c.drawPath(a,p);
        p.setColor(Color.rgb(95,31,42));Path b=new Path();b.moveTo(w*.5f,h*.72f);b.lineTo(w*.78f,h*.3f);b.lineTo(w,h*.72f);b.close();c.drawPath(b,p);
        drawBitmapAspect(c,marioRun[((int)(clock*8))%4],new RectF(w*.28f,h*.42f,w*.39f,h*.72f),false,true);
        drawBitmapAspect(c,yoshiChase,new RectF(w*.70f,h*.28f,w*.90f,h*.67f),true,false);
        p.setTextAlign(Paint.Align.CENTER);p.setFakeBoldText(true);p.setColor(Color.rgb(116,238,66));p.setTextSize(Math.max(46,h*.12f));c.drawText("YOSHI",w/2f,h*.22f,p);
        p.setColor(Color.rgb(255,201,48));c.drawText("ESCAPE",w/2f,h*.34f,p);p.setTextAlign(Paint.Align.LEFT);p.setFakeBoldText(false);
    }

    void menuUI(Canvas c,int w,int h){
        float cw=Math.min(520,w*.47f),ch=Math.min(270,h*.52f),l=(w-cw)/2,t=h*.42f;
        p.setColor(Color.argb(235,246,246,235));c.drawRoundRect(l,t,l+cw,t+ch,18,18,p);
        String[] it={"START","OPTIONS  •  SOM "+(sound?"ON":"OFF"),"HOW TO PLAY","CREDITS"};
        p.setTextAlign(Paint.Align.CENTER);p.setFakeBoldText(true);p.setTextSize(Math.max(23,h*.049f));
        for(int i=0;i<4;i++){float yy=t+58+i*(ch-85)/3;p.setColor(Color.rgb(30,34,42));c.drawText(it[i],w/2f,yy,p);}
        p.setTextAlign(Paint.Align.LEFT);p.setFakeBoldText(false);
    }

    void info(Canvas c,int w,int h,boolean how){
        p.setColor(Color.argb(225,4,12,25));c.drawRect(0,0,w,h,p);
        float l=w*.18f,r=w*.82f,t=h*.18f,b=h*.82f;p.setColor(Color.rgb(245,245,235));c.drawRoundRect(l,t,r,b,22,22,p);
        p.setTextAlign(Paint.Align.CENTER);p.setFakeBoldText(true);p.setColor(Color.rgb(25,35,48));p.setTextSize(Math.max(28,h*.06f));
        c.drawText(how?"HOW TO PLAY":"CREDITS",w/2f,t+60,p);p.setTextSize(Math.max(16,h*.032f));p.setFakeBoldText(false);
        String[] z=how?new String[]{"Chegue ao Goal e escape de volta ao Start.","Pegue rings, use molas e evite espinhos.","CORRER aumenta a velocidade; A pula.","Lap 3 libera o Yoshi."}:new String[]{"Fangame pessoal / beta mobile.","Game design: Vini + ChatGPT.","Assets visuais fornecidos pelo usuário.","Beta v0.3"};
        float yy=t+120;for(String q:z){c.drawText(q,w/2f,yy,p);yy+=44;}
        p.setFakeBoldText(true);p.setColor(Color.rgb(35,132,196));c.drawText("TOQUE PARA VOLTAR",w/2f,b-36,p);p.setTextAlign(Paint.Align.LEFT);p.setFakeBoldText(false);
    }

    void lapUI(Canvas c,int w,int h){
        p.setColor(Color.argb(210,3,11,24));c.drawRect(0,0,w,h,p);
        float cw=Math.min(w*.74f,760),ch=Math.min(h*.58f,360),l=(w-cw)/2,t=(h-ch)/2;
        p.setColor(Color.rgb(17,37,62));c.drawRoundRect(l,t,l+cw,t+ch,26,26,p);
        p.setTextAlign(Paint.Align.CENTER);p.setFakeBoldText(true);p.setColor(Color.rgb(255,211,55));p.setTextSize(Math.max(34,h*.075f));c.drawText("LAP "+lap+" COMPLETE",w/2f,t+ch*.28f,p);
        p.setColor(Color.WHITE);p.setTextSize(Math.max(16,h*.033f));c.drawText(lap==1?"Finalizar ou arriscar Lap 2?":"Quer Rank P? Lap 3 solta o Yoshi.",w/2f,t+ch*.45f,p);
        float by=t+ch*.67f,bw=cw*.34f;p.setColor(Color.rgb(63,153,91));c.drawRoundRect(w/2f-bw-14,by,w/2f-14,by+64,14,14,p);
        p.setColor(lap==2?Color.rgb(178,35,48):Color.rgb(54,111,195));c.drawRoundRect(w/2f+14,by,w/2f+bw+14,by+64,14,14,p);
        p.setColor(Color.WHITE);p.setTextSize(Math.max(17,h*.036f));c.drawText("FINALIZAR",w/2f-bw/2-14,by+42,p);c.drawText(lap==1?"LAP 2":"LAP 3",w/2f+bw/2+14,by+42,p);
        p.setTextAlign(Paint.Align.LEFT);p.setFakeBoldText(false);
    }

    void result(Canvas c,int w,int h,boolean over){
        p.setColor(Color.argb(215,3,11,24));c.drawRect(0,0,w,h,p);
        float cw=Math.min(w*.68f,700),ch=Math.min(h*.62f,390),l=(w-cw)/2,t=(h-ch)/2;p.setColor(Color.rgb(12,35,61));c.drawRoundRect(l,t,l+cw,t+ch,30,30,p);
        p.setTextAlign(Paint.Align.CENTER);p.setFakeBoldText(true);p.setColor(over?Color.rgb(255,95,80):rank.equals("P")?Color.rgb(255,100,220):Color.rgb(246,200,60));
        p.setTextSize(Math.max(36,h*.105f));c.drawText(over?"RUN OVER":"RANK "+rank,w/2f,t+ch*.34f,p);
        p.setColor(Color.WHITE);p.setTextSize(Math.max(16,h*.034f));c.drawText(over?(deathCause.equals("YOSHI")?"Yoshi alcançou o Mario.":deathCause.equals("SPIKES")?"Os espinhos acabaram com a corrida.":deathCause.equals("FALL")?"Mario caiu no vazio.":"Você ficou sem vidas."):"LAP "+lap+"  •  RINGS "+ringCount+"  •  SCORE "+score,w/2f,t+ch*.55f,p);
        p.setColor(Color.rgb(95,213,255));c.drawText("TOQUE PARA VOLTAR AO MENU",w/2f,t+ch*.78f,p);p.setTextAlign(Paint.Align.LEFT);p.setFakeBoldText(false);
    }

    @Override public boolean onTouchEvent(MotionEvent e){
        int a=e.getActionMasked(),i=e.getActionIndex();
        if(a==MotionEvent.ACTION_DOWN){
            float tx=e.getX(i),ty=e.getY(i);
            if(state==MENU){menuTap(tx,ty);return true;}
            if(state==HOW||state==CREDITS){state=MENU;return true;}
            if(state==LAP){lapTap(tx,ty);return true;}
            if(state==WIN||state==OVER){menu();return true;}
        }
        if(!playable())return true;

        if(a==MotionEvent.ACTION_DOWN||a==MotionEvent.ACTION_POINTER_DOWN){
            int id=e.getPointerId(i);float tx=e.getX(i),ty=e.getY(i);
            touches.put(id,new PointF(tx,ty));
            if(inJumpHitbox(tx,ty)){ jumpBuffer=.14f; jumpDown=true; }
        } else if(a==MotionEvent.ACTION_MOVE){
            for(int k=0;k<e.getPointerCount();k++){
                int id=e.getPointerId(k);PointF q=touches.get(id);
                if(q==null){q=new PointF();touches.put(id,q);} q.set(e.getX(k),e.getY(k));
            }
        } else if(a==MotionEvent.ACTION_UP||a==MotionEvent.ACTION_POINTER_UP){
            touches.remove(e.getPointerId(i));
        } else if(a==MotionEvent.ACTION_CANCEL) touches.clear();

        recalcControls();
        return true;
    }

    boolean inJumpHitbox(float tx,float ty){
        float[] g=controlGeom();float r=g[0];
        float dx=tx-g[4],dy=ty-g[1];return dx*dx+dy*dy<=(r*1.38f)*(r*1.38f);
    }

    void recalcControls(){
        float[] g=controlGeom();float r=g[0],yy=g[1];
        boolean nl=false,nr=false,na=false,ns=false;
        for(int i=0;i<touches.size();i++){
            PointF q=touches.valueAt(i);
            if(in(q,g[2],yy,r*1.25f))nl=true;
            if(in(q,g[3],yy,r*1.25f))nr=true;
            if(in(q,g[4],yy,r*1.38f))na=true;
            if(in(q,g[5],yy,r*1.22f))ns=true;
        }
        if(na&&!jumpDown)jumpBuffer=.14f;
        leftDown=nl;rightDown=nr;jumpDown=na;runDown=ns;
    }

    void menuTap(float x,float y){
        float h=getHeight(),w=getWidth(),ch=Math.min(270,h*.52f),t=h*.42f,row=(ch-85)/3;
        float[] q={t+58,t+58+row,t+58+2*row,t+58+3*row};int s=-1;
        for(int i=0;i<4;i++)if(Math.abs(y-q[i])<30&&x>w*.27f&&x<w*.73f)s=i;
        if(s==0)start();else if(s==1){sound=!sound;beep(ToneGenerator.TONE_PROP_BEEP,35);}else if(s==2)state=HOW;else if(s==3)state=CREDITS;
    }
    void lapTap(float x,float y){
        float w=getWidth(),h=getHeight(),ch=Math.min(h*.58f,360),t=(h-ch)/2,by=t+ch*.67f;
        if(y<by-12||y>by+80)return;if(x<w/2f)finish();else nextLap();
    }

    boolean in(PointF q,float x,float y,float r){float a=q.x-x,b=q.y-y;return a*a+b*b<=r*r;}
    int blend(int a,int b,float t){t=clamp(t,0,1);return Color.rgb((int)(Color.red(a)+(Color.red(b)-Color.red(a))*t),(int)(Color.green(a)+(Color.green(b)-Color.green(a))*t),(int)(Color.blue(a)+(Color.blue(b)-Color.blue(a))*t));}
    float clamp(float v,float a,float b){return Math.max(a,Math.min(b,v));}
    void beep(int t,int ms){if(sound)try{tone.startTone(t,ms);}catch(Exception ignored){}}
    void playJump(){if(sound&&jumpSound!=0)try{soundPool.play(jumpSound,.55f,.55f,1,0,1f);}catch(Exception ignored){}}

    @Override protected void onDetachedFromWindow(){
        super.onDetachedFromWindow();tone.release();if(soundPool!=null)soundPool.release();
    }
    static class Ring{final float x,y;boolean got;Ring(float a,float b){x=a;y=b;}}
}
