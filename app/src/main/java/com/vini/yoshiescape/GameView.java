package com.vini.yoshiescape;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.RectF;
import android.graphics.Shader;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class GameView extends View {
    private static final float LOGICAL_H = 540f;
    private static final float WORLD_W = 5200f;
    private static final float GROUND_Y = 455f;
    private static final float GOAL_X = 4930f;

    private static final int EXPLORE = 0;
    private static final int ESCAPE = 1;
    private static final int HUNTED = 2;
    private static final int FINISHED = 3;
    private static final int GAME_OVER = 4;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pixelPaint = new Paint();
    private final List<RectF> solids = new ArrayList<>();
    private final SparseArray<PointF> touches = new SparseArray<>();
    private final ToneGenerator tones = new ToneGenerator(AudioManager.STREAM_MUSIC, 55);

    private Bitmap marioStand;
    private Bitmap[] marioRun = new Bitmap[4];
    private Bitmap yoshiSprite;

    private float playerX;
    private float playerY;
    private float playerVx;
    private float playerVy;
    private final float playerW = 34f;
    private final float playerH = 48f;
    private boolean grounded;
    private boolean faceRight = true;

    private float safeX;
    private float safeY;
    private float safeTimer;

    private float yoshiX;
    private float yoshiSpeed;
    private boolean yoshiActive;

    private int state;
    private float timeLeft;
    private float cameraX;
    private float animationClock;
    private float introClock;
    private int lastCountdownSecond;
    private String resultRank = "C";

    private boolean leftDown;
    private boolean rightDown;
    private boolean jumpDown;
    private boolean spinDown;
    private boolean jumpRequested;

    private long lastFrameNanos;

    public GameView(Context context) {
        super(context);
        setFocusable(true);
        setKeepScreenOn(true);
        pixelPaint.setAntiAlias(false);
        pixelPaint.setFilterBitmap(false);
        loadSprites();
        buildLevel();
        resetGame();
    }

    private void loadSprites() {
        marioStand = BitmapFactory.decodeResource(getResources(), R.drawable.mario_stand);
        marioRun[0] = BitmapFactory.decodeResource(getResources(), R.drawable.mario_run1);
        marioRun[1] = BitmapFactory.decodeResource(getResources(), R.drawable.mario_run2);
        marioRun[2] = BitmapFactory.decodeResource(getResources(), R.drawable.mario_run3);
        marioRun[3] = BitmapFactory.decodeResource(getResources(), R.drawable.mario_run4);
        yoshiSprite = BitmapFactory.decodeResource(getResources(), R.drawable.yoshi);
    }

    private void buildLevel() {
        solids.clear();
        // Chão com buracos para obrigar o jogador a pular.
        solids.add(new RectF(0, GROUND_Y, 850, 560));
        solids.add(new RectF(930, GROUND_Y, 1650, 560));
        solids.add(new RectF(1730, GROUND_Y, 2600, 560));
        solids.add(new RectF(2680, GROUND_Y, 3400, 560));
        solids.add(new RectF(3500, GROUND_Y, 4550, 560));
        solids.add(new RectF(4620, GROUND_Y, WORLD_W, 560));

        solids.add(new RectF(760, 385, 910, 405));
        solids.add(new RectF(1120, 330, 1300, 350));
        solids.add(new RectF(1540, 380, 1720, 400));
        solids.add(new RectF(2040, 315, 2220, 335));
        solids.add(new RectF(2490, 370, 2690, 390));
        solids.add(new RectF(3180, 335, 3380, 355));
        solids.add(new RectF(3820, 315, 4020, 335));
        solids.add(new RectF(4380, 375, 4630, 395));
    }

    private void resetGame() {
        state = EXPLORE;
        playerX = 90f;
        playerY = GROUND_Y - playerH;
        playerVx = 0;
        playerVy = 0;
        grounded = true;
        faceRight = true;
        safeX = playerX;
        safeY = playerY;
        safeTimer = 0;

        yoshiX = GOAL_X + 30f;
        yoshiSpeed = 235f;
        yoshiActive = false;

        timeLeft = 35f;
        cameraX = 0f;
        animationClock = 0f;
        introClock = 4.5f;
        lastCountdownSecond = 99;
        resultRank = "C";
        leftDown = rightDown = jumpDown = spinDown = jumpRequested = false;
        touches.clear();
        lastFrameNanos = System.nanoTime();
        invalidate();
    }

    public void resumeGameClock() {
        lastFrameNanos = System.nanoTime();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        long now = System.nanoTime();
        float dt = (now - lastFrameNanos) / 1_000_000_000f;
        lastFrameNanos = now;
        if (dt < 0f || dt > 0.05f) dt = 0.016f;

        if (state != FINISHED && state != GAME_OVER) {
            update(dt);
        }
        drawGame(canvas);
        postInvalidateOnAnimation();
    }

    private void update(float dt) {
        animationClock += dt;
        if (introClock > 0) introClock -= dt;

        float accel = grounded ? 1750f : 900f;
        float maxSpeed = spinDown ? 335f : 265f;
        float friction = grounded ? 1450f : 250f;

        if (leftDown) {
            playerVx -= accel * dt;
            faceRight = false;
        }
        if (rightDown) {
            playerVx += accel * dt;
            faceRight = true;
        }
        if (!leftDown && !rightDown) {
            float change = friction * dt;
            if (Math.abs(playerVx) <= change) playerVx = 0;
            else playerVx -= Math.signum(playerVx) * change;
        }
        playerVx = clamp(playerVx, -maxSpeed, maxSpeed);

        if (jumpRequested && grounded) {
            playerVy = -590f;
            grounded = false;
            playTone(ToneGenerator.TONE_PROP_BEEP, 45);
        }
        jumpRequested = false;

        playerVy += 1500f * dt;
        playerVy = Math.min(playerVy, 900f);

        moveHorizontal(dt);
        moveVertical(dt);

        if (grounded) {
            safeTimer += dt;
            if (safeTimer >= 0.55f) {
                safeX = playerX;
                safeY = playerY;
                safeTimer = 0f;
            }
        } else {
            safeTimer = 0f;
        }

        if (playerY > 620f) {
            respawn();
        }

        if (state == EXPLORE && playerX > GOAL_X - 45f) {
            state = ESCAPE;
            timeLeft = 35f;
            lastCountdownSecond = 99;
            playTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 220);
        }

        if (state == ESCAPE) {
            timeLeft -= dt;
            int sec = Math.max(0, (int) Math.ceil(timeLeft));
            if (sec <= 10 && sec != lastCountdownSecond) {
                lastCountdownSecond = sec;
                playTone(ToneGenerator.TONE_PROP_ACK, 45);
            }
            if (timeLeft <= 0f) {
                timeLeft = 0f;
                state = HUNTED;
                yoshiActive = true;
                yoshiX = GOAL_X + 20f;
                yoshiSpeed = 235f;
                playTone(ToneGenerator.TONE_CDMA_HIGH_L, 420);
            }
        }

        if ((state == ESCAPE || state == HUNTED) && playerX < 105f) {
            finishRun();
        }

        if (yoshiActive && state == HUNTED) {
            float direction = Math.signum(playerX - yoshiX);
            yoshiX += direction * yoshiSpeed * dt;
            yoshiSpeed = Math.min(345f, yoshiSpeed + 5.5f * dt);

            float dx = Math.abs((yoshiX + 18f) - (playerX + playerW / 2f));
            float dy = Math.abs((GROUND_Y - 42f) - playerY);
            if (dx < 35f && dy < 78f) {
                state = GAME_OVER;
                playerVx = 0;
                playerVy = 0;
                playTone(ToneGenerator.TONE_CDMA_ABBR_ALERT, 350);
            }
        }

        float viewportW = getWidth() > 0 && getHeight() > 0
                ? getWidth() / (getHeight() / LOGICAL_H)
                : 960f;
        float targetCamera = playerX - viewportW * 0.42f;
        float maxCamera = Math.max(0f, WORLD_W - viewportW);
        targetCamera = clamp(targetCamera, 0f, maxCamera);
        cameraX += (targetCamera - cameraX) * Math.min(1f, dt * 5.5f);
    }

    private void moveHorizontal(float dt) {
        playerX += playerVx * dt;
        RectF p = playerRect();
        for (RectF s : solids) {
            if (RectF.intersects(p, s)) {
                if (playerVx > 0) playerX = s.left - playerW;
                else if (playerVx < 0) playerX = s.right;
                playerVx = 0;
                p = playerRect();
            }
        }
        playerX = clamp(playerX, 0f, WORLD_W - playerW);
    }

    private void moveVertical(float dt) {
        float oldY = playerY;
        float oldBottom = oldY + playerH;
        playerY += playerVy * dt;
        grounded = false;
        RectF p = playerRect();

        for (RectF s : solids) {
            if (!RectF.intersects(p, s)) continue;

            if (playerVy >= 0 && oldBottom <= s.top + 12f) {
                playerY = s.top - playerH;
                playerVy = 0;
                grounded = true;
            } else if (playerVy < 0 && oldY >= s.bottom - 10f) {
                playerY = s.bottom;
                playerVy = 0;
            }
            p = playerRect();
        }
    }

    private void respawn() {
        playerX = safeX;
        playerY = safeY;
        playerVx = 0;
        playerVy = 0;
        if (state == ESCAPE) timeLeft = Math.max(0f, timeLeft - 2.5f);
        playTone(ToneGenerator.TONE_PROP_NACK, 110);
    }

    private void finishRun() {
        if (state == HUNTED) resultRank = "C";
        else if (timeLeft >= 20f) resultRank = "S";
        else if (timeLeft >= 12f) resultRank = "A";
        else if (timeLeft >= 5f) resultRank = "B";
        else resultRank = "C";

        state = FINISHED;
        playerVx = 0;
        playerVy = 0;
        playTone(ToneGenerator.TONE_CDMA_ONE_MIN_BEEP, 240);
    }

    private RectF playerRect() {
        return new RectF(playerX, playerY, playerX + playerW, playerY + playerH);
    }

    private void drawGame(Canvas canvas) {
        int w = canvas.getWidth();
        int h = canvas.getHeight();
        if (w <= 0 || h <= 0) return;

        paint.setShader(new LinearGradient(0, 0, 0, h,
                Color.rgb(55, 145, 235), Color.rgb(185, 226, 255), Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, paint);
        paint.setShader(null);

        float scale = h / LOGICAL_H;
        float viewportW = w / scale;

        // Montanhas simples em parallax.
        paint.setColor(Color.rgb(92, 177, 100));
        for (int i = 0; i < 13; i++) {
            float hx = i * 480f - cameraX * 0.22f;
            canvas.drawCircle(hx * scale, 455f * scale, 220f * scale, paint);
        }

        canvas.save();
        canvas.scale(scale, scale);
        canvas.translate(-cameraX, 0);

        drawPlatforms(canvas);
        drawStartGate(canvas);
        drawGoal(canvas);
        drawPlayer(canvas);
        if (yoshiActive) drawYoshi(canvas);

        canvas.restore();

        drawHud(canvas, w, h);
        drawControls(canvas, w, h);

        if (state == FINISHED) drawResultOverlay(canvas, w, h, false);
        if (state == GAME_OVER) drawResultOverlay(canvas, w, h, true);
    }

    private void drawPlatforms(Canvas canvas) {
        for (RectF s : solids) {
            paint.setColor(Color.rgb(112, 70, 42));
            canvas.drawRect(s, paint);
            paint.setColor(Color.rgb(57, 144, 55));
            canvas.drawRect(s.left, s.top, s.right, Math.min(s.bottom, s.top + 10f), paint);
            paint.setColor(Color.rgb(129, 208, 91));
            canvas.drawRect(s.left, s.top, s.right, Math.min(s.bottom, s.top + 4f), paint);
        }
    }

    private void drawStartGate(Canvas canvas) {
        paint.setColor(Color.WHITE);
        canvas.drawRect(55, 345, 65, GROUND_Y, paint);
        canvas.drawRect(112, 345, 122, GROUND_Y, paint);
        canvas.drawRect(55, 345, 122, 355, paint);
        paint.setColor(Color.rgb(12, 25, 45));
        paint.setTextSize(17f);
        paint.setFakeBoldText(true);
        canvas.drawText("INÍCIO", 57, 336, paint);
        paint.setFakeBoldText(false);
    }

    private void drawGoal(Canvas canvas) {
        paint.setColor(Color.rgb(246, 200, 60));
        canvas.drawRect(GOAL_X, 330, GOAL_X + 9, GROUND_Y, paint);
        canvas.drawCircle(GOAL_X + 4.5f, 329, 31, paint);
        paint.setColor(Color.rgb(125, 28, 20));
        paint.setTextSize(14f);
        paint.setFakeBoldText(true);
        canvas.drawText("FIM", GOAL_X - 10, 334, paint);
        paint.setFakeBoldText(false);
    }

    private void drawPlayer(Canvas canvas) {
        Bitmap sprite = marioStand;
        if (grounded && Math.abs(playerVx) > 28f) {
            int frame = ((int) (animationClock * (spinDown ? 15f : 10f))) % marioRun.length;
            sprite = marioRun[frame];
        }

        RectF dest = new RectF(playerX, playerY, playerX + playerW, playerY + playerH);
        if (sprite == null) {
            paint.setColor(Color.RED);
            canvas.drawRect(dest, paint);
            return;
        }

        canvas.save();
        if (!faceRight) canvas.scale(-1f, 1f, dest.centerX(), dest.centerY());
        canvas.drawBitmap(sprite, null, dest, pixelPaint);
        canvas.restore();
    }

    private void drawYoshi(Canvas canvas) {
        RectF dest = new RectF(yoshiX, GROUND_Y - 47f, yoshiX + 36f, GROUND_Y);
        if (yoshiSprite != null) {
            canvas.drawBitmap(yoshiSprite, null, dest, pixelPaint);
        } else {
            paint.setColor(Color.GREEN);
            canvas.drawRect(dest, paint);
        }
        paint.setColor(Color.WHITE);
        paint.setTextSize(13f);
        paint.setFakeBoldText(true);
        canvas.drawText("YOSHI", yoshiX - 3f, GROUND_Y - 54f, paint);
        paint.setFakeBoldText(false);
    }

    private void drawHud(Canvas canvas, int w, int h) {
        float pad = Math.max(14f, h * 0.025f);
        paint.setColor(Color.argb(155, 4, 15, 31));
        canvas.drawRoundRect(pad, pad, w - pad, pad + h * 0.11f, 18f, 18f, paint);

        paint.setColor(Color.WHITE);
        paint.setFakeBoldText(true);
        paint.setTextSize(Math.max(20f, h * 0.045f));

        String status;
        if (state == EXPLORE) status = "CHEGUE AO FIM  →";
        else if (state == ESCAPE) status = "ESCAPE! VOLTE AO INÍCIO  ←";
        else if (state == HUNTED) status = "YOSHI ESTÁ VINDO. CORRE!  ←";
        else status = "YOSHI ESCAPE";
        canvas.drawText(status, pad * 1.7f, pad + h * 0.072f, paint);

        if (state == ESCAPE || state == HUNTED) {
            String timer = state == HUNTED ? "YOSHI!" : String.format("%02d", Math.max(0, (int) Math.ceil(timeLeft)));
            paint.setTextAlign(Paint.Align.RIGHT);
            paint.setTextSize(Math.max(28f, h * 0.064f));
            if (state == ESCAPE && timeLeft <= 10f) paint.setColor(Color.rgb(255, 214, 64));
            canvas.drawText(timer, w - pad * 1.7f, pad + h * 0.075f, paint);
            paint.setTextAlign(Paint.Align.LEFT);
        }
        paint.setFakeBoldText(false);

        if (introClock > 0f && state == EXPLORE) {
            paint.setColor(Color.argb(175, 4, 15, 31));
            float boxW = Math.min(w * 0.72f, 660f);
            float left = (w - boxW) / 2f;
            float top = h * 0.15f;
            canvas.drawRoundRect(left, top, left + boxW, top + h * 0.105f, 18, 18, paint);
            paint.setColor(Color.WHITE);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(Math.max(16f, h * 0.033f));
            paint.setFakeBoldText(true);
            canvas.drawText("Segure ◀ ▶ para andar • A pula • GIRO acelera", w / 2f, top + h * 0.066f, paint);
            paint.setTextAlign(Paint.Align.LEFT);
            paint.setFakeBoldText(false);
        }

        if (state == HUNTED) {
            float distance = Math.abs(yoshiX - playerX);
            paint.setColor(Color.argb(175, 4, 15, 31));
            float bw = Math.min(w * 0.34f, 330f);
            canvas.drawRoundRect(w / 2f - bw / 2f, h * 0.14f, w / 2f + bw / 2f, h * 0.205f, 14, 14, paint);
            paint.setColor(Color.WHITE);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setFakeBoldText(true);
            paint.setTextSize(Math.max(15f, h * 0.029f));
            canvas.drawText("Yoshi: " + Math.round(distance) + " px", w / 2f, h * 0.185f, paint);
            paint.setTextAlign(Paint.Align.LEFT);
            paint.setFakeBoldText(false);
        }
    }

    private void drawControls(Canvas canvas, int w, int h) {
        float r = Math.max(42f, h * 0.095f);
        float y = h - r * 1.25f;
        float leftX = r * 1.35f;
        float rightX = r * 3.05f;
        float jumpX = w - r * 1.35f;
        float spinX = w - r * 3.0f;

        drawControlCircle(canvas, leftX, y, r, leftDown, "◀");
        drawControlCircle(canvas, rightX, y, r, rightDown, "▶");
        drawControlCircle(canvas, spinX, y, r * 0.88f, spinDown, "GIRO");
        drawControlCircle(canvas, jumpX, y, r * 1.05f, jumpDown, "A");
    }

    private void drawControlCircle(Canvas canvas, float cx, float cy, float r, boolean down, String label) {
        paint.setColor(down ? Color.argb(155, 255, 255, 255) : Color.argb(92, 5, 18, 38));
        canvas.drawCircle(cx, cy, r, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(2f, r * 0.035f));
        paint.setColor(Color.argb(190, 255, 255, 255));
        canvas.drawCircle(cx, cy, r, paint);
        paint.setStyle(Paint.Style.FILL);

        paint.setColor(Color.WHITE);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setFakeBoldText(true);
        paint.setTextSize(label.equals("GIRO") ? r * 0.34f : r * 0.62f);
        Paint.FontMetrics fm = paint.getFontMetrics();
        float baseline = cy - (fm.ascent + fm.descent) / 2f;
        canvas.drawText(label, cx, baseline, paint);
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setFakeBoldText(false);
    }

    private void drawResultOverlay(Canvas canvas, int w, int h, boolean gameOver) {
        paint.setColor(Color.argb(210, 3, 11, 24));
        canvas.drawRect(0, 0, w, h, paint);

        float cardW = Math.min(w * 0.68f, 700f);
        float cardH = Math.min(h * 0.62f, 390f);
        float l = (w - cardW) / 2f;
        float t = (h - cardH) / 2f;
        paint.setColor(Color.rgb(12, 35, 61));
        canvas.drawRoundRect(l, t, l + cardW, t + cardH, 30, 30, paint);

        paint.setTextAlign(Paint.Align.CENTER);
        paint.setFakeBoldText(true);
        paint.setColor(gameOver ? Color.rgb(255, 110, 90) : Color.rgb(246, 200, 60));
        paint.setTextSize(Math.max(36f, h * 0.11f));
        canvas.drawText(gameOver ? "YOSHI TE PEGOU" : "RANK " + resultRank, w / 2f, t + cardH * 0.38f, paint);

        paint.setColor(Color.WHITE);
        paint.setTextSize(Math.max(17f, h * 0.038f));
        String line = gameOver
                ? "O tempo acabou e ele alcançou o Mario."
                : (state == FINISHED && resultRank.equals("C") && yoshiActive
                ? "Você escapou mesmo com o Yoshi na perseguição!"
                : "Você voltou ao início a tempo!");
        canvas.drawText(line, w / 2f, t + cardH * 0.58f, paint);

        paint.setColor(Color.rgb(95, 213, 255));
        paint.setTextSize(Math.max(16f, h * 0.034f));
        canvas.drawText("TOQUE NA TELA PARA JOGAR DE NOVO", w / 2f, t + cardH * 0.78f, paint);
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setFakeBoldText(false);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        int index = event.getActionIndex();

        if ((state == FINISHED || state == GAME_OVER) && action == MotionEvent.ACTION_DOWN) {
            resetGame();
            return true;
        }

        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            int id = event.getPointerId(index);
            touches.put(id, new PointF(event.getX(index), event.getY(index)));
        } else if (action == MotionEvent.ACTION_MOVE) {
            for (int i = 0; i < event.getPointerCount(); i++) {
                int id = event.getPointerId(i);
                PointF p = touches.get(id);
                if (p == null) {
                    p = new PointF();
                    touches.put(id, p);
                }
                p.set(event.getX(i), event.getY(i));
            }
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) {
            int id = event.getPointerId(index);
            touches.remove(id);
        } else if (action == MotionEvent.ACTION_CANCEL) {
            touches.clear();
        }

        recomputeControls();
        return true;
    }

    private void recomputeControls() {
        float w = getWidth();
        float h = getHeight();
        float r = Math.max(42f, h * 0.095f);
        float y = h - r * 1.25f;
        float leftX = r * 1.35f;
        float rightX = r * 3.05f;
        float jumpX = w - r * 1.35f;
        float spinX = w - r * 3.0f;

        boolean newLeft = false;
        boolean newRight = false;
        boolean newJump = false;
        boolean newSpin = false;

        for (int i = 0; i < touches.size(); i++) {
            PointF p = touches.valueAt(i);
            if (inCircle(p.x, p.y, leftX, y, r * 1.12f)) newLeft = true;
            if (inCircle(p.x, p.y, rightX, y, r * 1.12f)) newRight = true;
            if (inCircle(p.x, p.y, jumpX, y, r * 1.18f)) newJump = true;
            if (inCircle(p.x, p.y, spinX, y, r * 1.05f)) newSpin = true;
        }

        if (newJump && !jumpDown) jumpRequested = true;
        leftDown = newLeft;
        rightDown = newRight;
        jumpDown = newJump;
        spinDown = newSpin;
    }

    private boolean inCircle(float x, float y, float cx, float cy, float r) {
        float dx = x - cx;
        float dy = y - cy;
        return dx * dx + dy * dy <= r * r;
    }

    private void playTone(int tone, int ms) {
        try {
            tones.startTone(tone, ms);
        } catch (Exception ignored) {
        }
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        tones.release();
    }
}
