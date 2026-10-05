package com.vini.yoshiescape;

import android.content.Context;
import android.graphics.*;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.ToneGenerator;
import android.util.Base64;

public class GameViewV3 extends GameView {
    private Bitmap marioJumpReal;
    private Bitmap yoshiRunReal;
    private Bitmap yoshiChaseReal;
    private float jumpBuffer = 0f;

    public GameViewV3(Context context) {
        super(context);
        marioJumpReal = decode(MarioJumpAsset.DATA);
        yoshiRunReal = decode(YoshiRunAsset.DATA);
        yoshiChaseReal = decode(YoshiChaseAsset.DATA);
    }

    private Bitmap decode(String data) {
        byte[] bytes = Base64.decode(data, Base64.DEFAULT);
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
    }

    @Override
    void level() {
        solids.clear();
        springs.clear();
        spikes.clear();
        rings.clear();

        ground(0, 900);
        plat(430, 360, 170);
        springs.add(new RectF(810, GROUND - 20, 860, GROUND));
        spikes.add(new RectF(650, GROUND - 18, 710, GROUND));
        for (float q = 220; q < 760; q += 68) addRing(q, 390);

        for (int i=0;i<7;i++) {
            float t=i/6f;
            addRing(900+i*55, 375-(float)Math.sin(t*Math.PI)*105);
        }

        ground(1020, 2050);
        plat(1180, 340, 190);
        plat(1500, 300, 205);
        spikes.add(new RectF(1730, GROUND - 18, 1790, GROUND));
        springs.add(new RectF(1965, GROUND - 20, 2015, GROUND));
        for (float q = 1100; q < 1900; q += 74) addRing(q, 390);

        for (int i=0;i<7;i++) {
            float t=i/6f;
            addRing(2040+i*58, 372-(float)Math.sin(t*Math.PI)*110);
        }

        ground(2180, 3300);
        plat(2380, 350, 185);
        plat(2710, 305, 205);
        spikes.add(new RectF(2870, GROUND - 18, 2930, GROUND));
        springs.add(new RectF(3215, GROUND - 20, 3265, GROUND));
        for (float q = 2260; q < 3140; q += 76) addRing(q, 390);

        for (int i=0;i<7;i++) {
            float t=i/6f;
            addRing(3290+i*58, 370-(float)Math.sin(t*Math.PI)*110);
        }

        ground(3440, 4550);
        plat(3640, 345, 190);
        plat(3980, 300, 210);
        spikes.add(new RectF(4140, GROUND - 18, 4200, GROUND));
        springs.add(new RectF(4465, GROUND - 20, 4515, GROUND));
        for (float q = 3520; q < 4400; q += 78) addRing(q, 390);

        for (int i=0;i<7;i++) {
            float t=i/6f;
            addRing(4540+i*58, 370-(float)Math.sin(t*Math.PI)*110);
        }

        ground(4680, WORLD);
        plat(4860, 350, 190);
        plat(5150, 305, 195);
        spikes.add(new RectF(5210, GROUND - 18, 5270, GROUND));
        for (float q = 4760; q < 5320; q += 70) addRing(q, 390);
    }

    @Override
    void update(float dt) {
        if (jumpBuffer > 0f) {
            jumpBuffer -= dt;
            if (grounded) {
                jumpReq = true;
                jumpBuffer = 0f;
            }
        }
        super.update(dt);
    }

    @Override
    void mountains(Canvas c, int w, int h, float danger) {
        float sc = h / H;
        p.setColor(blend(Color.rgb(118, 150, 196), Color.rgb(50, 31, 72), danger));
        for (int i=-2;i<9;i++) {
            float cx=(i*360-camera*.13f)*sc;
            float base=h*.64f;
            float peak=h*(i%2==0?.32f:.39f);
            Path z=new Path();
            z.moveTo(cx-230*sc,base);
            z.lineTo(cx,peak);
            z.lineTo(cx+230*sc,base);
            z.close();
            c.drawPath(z,p);
        }

        p.setColor(blend(Color.rgb(45, 119, 88), Color.rgb(29, 24, 47), danger));
        for (int i=-2;i<16;i++) {
            float left=(i*210-camera*.23f)*sc;
            c.drawRect(left,h*.66f,left+150*sc,h*.82f,p);
        }
    }

    @Override
    void decor(Canvas c, float danger) {
        for (float q=330;q<WORLD;q+=930) {
            p.setColor(blend(Color.rgb(111, 73, 39), Color.rgb(70, 42, 45), danger));
            c.drawRect(q, 365, q+10, GROUND, p);
            p.setColor(blend(Color.rgb(48, 151, 62), Color.rgb(98, 44, 50), danger));
            c.drawRect(q-28, 354, q+38, 365, p);
            c.drawRect(q-14, 341, q+24, 354, p);
        }

        for (float q=190;q<WORLD;q+=330) {
            p.setColor(blend(Color.rgb(40, 130, 47), Color.rgb(72, 35, 45), danger));
            c.drawRect(q, 426, q+4, GROUND, p);
            p.setColor(blend(Color.rgb(255, 220, 65), Color.rgb(245, 70, 65), danger));
            c.drawRect(q-5, 418, q+9, 426, p);
        }
    }

    @Override
    void player(Canvas c) {
        Bitmap b = stand;

        if (!grounded && marioJumpReal != null) {
            b = marioJumpReal;
        } else if (Math.abs(vx) > 28f) {
            int frame=((int)(clock*(S?13f:9f)))%run.length;
            b=run[frame];
        }

        if (inv > 0 && (((int)(clock*16))&1)==0) return;

        RectF box=new RectF(x-7,y-7,x+43,y+52);
        bitmap(c,b,box,!faceRight,true);
    }

    @Override
    void yoshi(Canvas c) {
        Bitmap b = Math.abs(yx-x) < 560f && yoshiChaseReal != null
                ? yoshiChaseReal
                : yoshiRunReal;

        if (b == null) {
            super.yoshi(c);
            return;
        }

        boolean flip = x < yx;
        RectF box = new RectF(yx-34, yy-18, yx+78, yy+58);
        bitmap(c,b,box,flip,true);
    }

    private void metrics(int w,int h,float[] m) {
        float r=Math.max(46f,h*.09f);
        float yy=h-r*1.20f;
        m[0]=r;
        m[1]=yy;
        m[2]=r*1.35f;
        m[3]=r*4.00f;
        m[4]=w-r*1.30f;
        m[5]=w-r*4.30f;
    }

    @Override
    void controls(Canvas c,int w,int h) {
        float[] m=new float[6];
        metrics(w,h,m);
        float r=m[0],yy=m[1];

        buttonCircle(c,m[2],yy,r,L,"◀");
        buttonCircle(c,m[3],yy,r,R,"▶");
        buttonRun(c,m[5],yy,r,S);
        buttonCircle(c,m[4],yy,r*1.04f,A,"A");
    }

    private void buttonCircle(Canvas c,float cx,float cy,float r,boolean down,String label) {
        p.setColor(down?Color.argb(175,255,255,255):Color.argb(78,7,18,36));
        c.drawCircle(cx,cy,r,p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2.5f);
        p.setColor(Color.argb(205,255,255,255));
        c.drawCircle(cx,cy,r,p);
        p.setStyle(Paint.Style.FILL);

        p.setColor(Color.WHITE);
        p.setTextAlign(Paint.Align.CENTER);
        p.setFakeBoldText(true);
        p.setTextSize(r*.58f);
        Paint.FontMetrics fm=p.getFontMetrics();
        c.drawText(label,cx,cy-(fm.ascent+fm.descent)/2,p);
        p.setTextAlign(Paint.Align.LEFT);
        p.setFakeBoldText(false);
    }

    private void buttonRun(Canvas c,float cx,float cy,float r,boolean down) {
        RectF box=new RectF(cx-r*1.55f,cy-r*.78f,cx+r*1.55f,cy+r*.78f);

        p.setColor(down?Color.argb(175,255,255,255):Color.argb(78,7,18,36));
        c.drawRoundRect(box,r*.55f,r*.55f,p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2.5f);
        p.setColor(Color.argb(205,255,255,255));
        c.drawRoundRect(box,r*.55f,r*.55f,p);
        p.setStyle(Paint.Style.FILL);

        p.setColor(Color.WHITE);
        p.setTextAlign(Paint.Align.CENTER);
        p.setFakeBoldText(true);
        p.setTextSize(r*.34f);
        Paint.FontMetrics fm=p.getFontMetrics();
        c.drawText("CORRER",cx,cy-(fm.ascent+fm.descent)/2,p);
        p.setTextAlign(Paint.Align.LEFT);
        p.setFakeBoldText(false);
    }

    @Override
    void recalc() {
        float[] m=new float[6];
        metrics(getWidth(),getHeight(),m);
        float r=m[0],yy=m[1];

        boolean nl=false,nr=false,nj=false,nrun=false;

        for (int i=0;i<touches.size();i++) {
            PointF q=touches.valueAt(i);

            if (circle(q,m[2],yy,r*1.25f)) nl=true;
            if (circle(q,m[3],yy,r*1.25f)) nr=true;
            if (circle(q,m[4],yy,r*1.42f)) nj=true;
            if (q.x>=m[5]-r*1.75f && q.x<=m[5]+r*1.75f &&
                q.y>=yy-r*1.05f && q.y<=yy+r*1.05f) nrun=true;
        }

        if (nj && !A) {
            jumpReq=true;
            jumpBuffer=.16f;
        }

        L=nl;
        R=nr;
        A=nj;
        S=nrun;
    }

    private boolean circle(PointF q,float cx,float cy,float r) {
        float dx=q.x-cx,dy=q.y-cy;
        return dx*dx+dy*dy<=r*r;
    }

    @Override
    void beep(int toneId,int ms) {
        if (toneId==ToneGenerator.TONE_PROP_BEEP && ms<=50) {
            jumpSound();
            return;
        }
        super.beep(toneId,ms);
    }

    private void jumpSound() {
        if (!sound) return;

        new Thread(() -> {
            try {
                final int rate=22050;
                final int count=(int)(rate*.145f);
                short[] pcm=new short[count];
                double phase=0;

                for (int i=0;i<count;i++) {
                    double t=i/(double)count;
                    double freq=390+900*Math.pow(t,.72);
                    phase+=2*Math.PI*freq/rate;

                    double env=Math.pow(1-t,1.25);
                    double wave=Math.sin(phase)*.70+Math.sin(phase*2)*.18;
                    pcm[i]=(short)(wave*env*10500);
                }

                AudioTrack track=new AudioTrack(
                        AudioManager.STREAM_MUSIC,
                        rate,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        pcm.length*2,
                        AudioTrack.MODE_STATIC
                );

                track.write(pcm,0,pcm.length);
                track.setVolume(.50f);
                track.play();

                try { Thread.sleep(170); } catch (InterruptedException ignored) {}
                track.stop();
                track.release();
            } catch (Exception ignored) {}
        }).start();
    }
}
