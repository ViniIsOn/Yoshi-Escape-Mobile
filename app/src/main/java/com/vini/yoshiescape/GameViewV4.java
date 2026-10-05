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
    private float yGrace4=0f;

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

    @Override void start(){
        super.start();
        // Normal run before the escape laps.
        playTrack(R.raw.run_you_fool,true);
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
