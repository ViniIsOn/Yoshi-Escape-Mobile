package com.vini.yoshiescape;

import android.content.Context;
import android.graphics.*;
import android.media.MediaPlayer;
import android.media.ToneGenerator;

public class GameViewV4 extends GameView {
    private Context ctx4;
    private MediaPlayer bgm;
    private float yGrace4=0f;

    public GameViewV4(Context c){
        super(c);
        ctx4=c;
        playTrack(R.raw.escape_menu,true);
    }

    private void stopTrack(){
        if(bgm!=null){
            try{bgm.stop();}catch(Exception ignored){}
            try{bgm.release();}catch(Exception ignored){}
            bgm=null;
        }
    }

    private void playTrack(int res,boolean loop){
        stopTrack();
        if(!sound||ctx4==null)return;
        try{
            bgm=MediaPlayer.create(ctx4,res);
            if(bgm!=null){
                bgm.setLooping(loop);
                bgm.setVolume(.72f,.72f);
                bgm.start();
            }
        }catch(Exception ignored){}
    }

    @Override void menu(){
        super.menu();
        if(ctx4!=null)playTrack(R.raw.escape_menu,true);
    }

    @Override void start(){
        stopTrack();
        super.start();
    }

    @Override void beginLap(int l,boolean left){
        super.beginLap(l,left);
        if(l==1)playTrack(R.raw.run_you_fool,true);
        else if(l==2)playTrack(R.raw.escape_lap2,true);
        else playTrack(R.raw.die,true);
    }

    @Override void spawnYoshi(){
        yActive=true;
        yGrace4=3.2f;
        yspeed=150f;
        yx=toStart?x+470f:x-470f;
        yy=GROUND-102f;
    }

    @Override void chase(float dt){
        float dir=Math.signum(x-yx);
        if(dir==0)dir=toStart?-1:1;
        if(yGrace4>0){
            yGrace4-=dt;
            yx+=dir*52f*dt;
            yy=GROUND-104f+(float)Math.sin(clock*7f)*8f;
            return;
        }
        yspeed=Math.min(280f,yspeed+3f*dt);
        yx+=dir*yspeed*dt;
        yy=GROUND-94f+(float)Math.sin(clock*9f)*7f;
        if(Math.abs((yx+40)-(x+18))<40 && Math.abs((yy+38)-(y+24))<62){
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
        c.drawCircle(yx+45,yy+42,68,p);
        boolean flip=x<yx;
        drawBitmapAspect(c,b,new RectF(yx-24,yy-24,yx+126,yy+104),flip,false);
        if(yGrace4>0){
            p.setColor(Color.WHITE);
            p.setTextSize(17);
            p.setFakeBoldText(true);
            c.drawText("YOSHI!",yx+15,yy-10,p);
            p.setFakeBoldText(false);
        }
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
