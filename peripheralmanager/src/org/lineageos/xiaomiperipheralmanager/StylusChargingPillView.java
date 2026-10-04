/*
 * Copyright (C) 2026 Mufasa
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.xiaomiperipheralmanager;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.PathParser;
import android.util.TypedValue;
import android.view.View;
import android.view.WindowManager;

import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.FloatValueHolder;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

import com.android.internal.graphics.drawable.BackgroundBlurDrawable;

import java.io.File;
import java.util.function.Consumer;

/**
 * Material 3 Expressive stylus charging pill.
 *
 * Everything is drawn on Canvas: a cross-window blurred, dynamic-color tinted capsule that
 * springs out of a circle, a morphing "cookie" shape holding the stylus glyph, an animated
 * wavy circular progress indicator and spring driven content transitions.
 */
public class StylusChargingPillView extends View {

    public enum StylusPopupState {
        CHARGING,
        FULLY_CHARGED,
        CONNECTED,
        DISCONNECTED,
        LOW_BATTERY,
        CRITICAL_BATTERY,
        UNKNOWN
    }

    public static class StylusPopupModel {
        public final String title;
        public final String subtitle;
        public final Integer batteryPercent;
        public final StylusPopupState state;

        public StylusPopupModel(String title, String subtitle, Integer batteryPercent, StylusPopupState state) {
            this.title = title;
            this.subtitle = subtitle;
            this.batteryPercent = batteryPercent;
            this.state = state;
        }
    }

    // M3 Expressive motion scheme springs
    private static final float SPATIAL_FAST_DAMPING = 0.6f;
    private static final float SPATIAL_FAST_STIFFNESS = 800f;
    private static final float SPATIAL_DEFAULT_DAMPING = 0.8f;
    private static final float SPATIAL_DEFAULT_STIFFNESS = 380f;
    private static final float EFFECTS_DAMPING = 1f;
    private static final float EFFECTS_DEFAULT_STIFFNESS = 1600f;
    private static final float EFFECTS_SLOW_STIFFNESS = 800f;
    private static final float COLLAPSE_STIFFNESS = 900f;

    private static final String FONT_PATH = "/product/fonts/GoogleSansFlex-Regular.ttf";

    // Stylus with cap band, nib pointing bottom-left (even-odd)
    private static final String STYLUS_PATH_DATA =
            "M4.82,19.74L10.06,18.19L19.95,8.29A3,3 0 0,0 15.71,4.05L5.81,13.94L4.26,19.18"
            + "Q4.05,19.95 4.82,19.74ZM17.41,10.83L18.4,9.84L14.16,5.6L13.17,6.59Z";

    // Material Symbols "bolt"
    private static final String BOLT_PATH_DATA =
            "M11,21h-1l1,-7H7.5c-0.58,0 -0.57,-0.32 -0.38,-0.66 0.19,-0.34 0.05,-0.08 0.07,-0.12"
            + "C8.48,10.94 10.42,7.54 13,3h1l-1,7h3.5c0.49,0 0.56,0.33 0.47,0.51l-0.07,0.15"
            + "C12.96,17.55 11,21 11,21z";

    private static final float PILL_MAX_WIDTH_DP = 360f;
    private static final float PILL_HEIGHT_DP = 76f;
    private static final float WINDOW_PADDING_DP = 20f;
    private static final float COOKIE_SIZE_DP = 56f;
    private static final float RING_SIZE_DP = 52f;
    private static final float RING_STROKE_DP = 4f;
    private static final float RING_GAP_DP = 4f;
    private static final float RING_WAVE_AMPLITUDE_DP = 1.6f;
    private static final int RING_WAVE_COUNT = 10;
    private static final int COOKIE_SAMPLES = 144;

    /** Polar "cookie" shape: r(θ) = 1 - amplitude * (1 - cos(lobes * θ)) / 2. */
    private static final class ShapeSpec {
        final int lobes;
        final float amplitude;

        ShapeSpec(int lobes, float amplitude) {
            this.lobes = lobes;
            this.amplitude = amplitude;
        }

        float radius(double theta) {
            if (amplitude == 0f) return 1f;
            return 1f - amplitude * (1f - (float) Math.cos(lobes * theta)) / 2f;
        }
    }

    private static final ShapeSpec SHAPE_CIRCLE = new ShapeSpec(0, 0f);
    private static final ShapeSpec SHAPE_COOKIE_9 = new ShapeSpec(9, 0.16f);
    private static final ShapeSpec SHAPE_SUNNY = new ShapeSpec(12, 0.11f);
    private static final ShapeSpec SHAPE_COOKIE_4 = new ShapeSpec(4, 0.14f);
    private static final ShapeSpec SHAPE_COOKIE_6 = new ShapeSpec(6, 0.16f);
    private static final ShapeSpec SHAPE_FLOWER_5 = new ShapeSpec(5, 0.22f);

    /** Dynamic color roles for a state. */
    private static final class Palette {
        int container;
        int onContainer;
        int indicator;
        int track;

        Palette set(int container, int onContainer, int indicator, int track) {
            this.container = container;
            this.onContainer = onContainer;
            this.indicator = indicator;
            this.track = track;
            return this;
        }

        void lerp(Palette from, Palette to, float t, ArgbEvaluator evaluator) {
            container = (int) evaluator.evaluate(t, from.container, to.container);
            onContainer = (int) evaluator.evaluate(t, from.onContainer, to.onContainer);
            indicator = (int) evaluator.evaluate(t, from.indicator, to.indicator);
            track = (int) evaluator.evaluate(t, from.track, to.track);
        }

        void copyFrom(Palette other) {
            set(other.container, other.onContainer, other.indicator, other.track);
        }
    }

    /** Thin wrapper over SpringAnimation driving a plain float. */
    private final class Spring {
        private final FloatValueHolder mHolder = new FloatValueHolder();
        private final SpringAnimation mAnimation;
        private float mTarget;

        Spring(float initial, float damping, float stiffness, float minVisibleChange) {
            mHolder.setValue(initial);
            mTarget = initial;
            mAnimation = new SpringAnimation(mHolder);
            mAnimation.setSpring(new SpringForce(initial)
                    .setDampingRatio(damping)
                    .setStiffness(stiffness));
            mAnimation.setMinimumVisibleChange(minVisibleChange);
            mAnimation.addUpdateListener((anim, value, velocity) -> invalidate());
        }

        float get() {
            return mHolder.getValue();
        }

        float target() {
            return mTarget;
        }

        SpringForce force() {
            return mAnimation.getSpring();
        }

        void animateTo(float target) {
            animateTo(target, 0f);
        }

        void animateTo(float target, float velocity) {
            mTarget = target;
            if (!ValueAnimator.areAnimatorsEnabled()) {
                snapTo(target);
                return;
            }
            if (velocity != 0f) {
                mAnimation.setStartVelocity(velocity);
            }
            mAnimation.animateToFinalPosition(target);
        }

        void snapTo(float value) {
            mTarget = value;
            mAnimation.cancel();
            mAnimation.getSpring().setFinalPosition(value);
            mHolder.setValue(value);
            invalidate();
        }

        void cancel() {
            mAnimation.cancel();
        }

        void addEndListener(DynamicAnimation.OnAnimationEndListener listener) {
            mAnimation.addEndListener(listener);
        }
    }

    private final float mDensity;
    private final ArgbEvaluator mArgb = new ArgbEvaluator();
    private final WindowManager mWindowManager;

    // Container motion
    private final Spring mExpand;   // 0 = circle, 1 = full capsule
    private final Spring mScale;    // overall capsule scale, bouncy
    private final Spring mReveal;   // surface + blur opacity
    // Content motion
    private final Spring mMorph;    // cookie shape morph from -> to
    private final Spring mSpin;     // extra cookie rotation (degrees)
    private final Spring mTextSwap; // old text -> new text
    private final Spring mColor;    // palette crossfade
    private final Spring mProgress; // indicator sweep 0..1
    private final Spring mBadge;    // charging badge scale
    private final Spring mWave;     // wave amplitude multiplier
    private final Spring mIndeterminate; // 1 when battery level is unknown

    private StylusPopupModel mModel = new StylusPopupModel("", "", null, StylusPopupState.UNKNOWN);
    private StylusPopupModel mPreviousModel = mModel;
    private ShapeSpec mShapeFrom = SHAPE_CIRCLE;
    private ShapeSpec mShapeTo = SHAPE_CIRCLE;
    private final Palette mPaletteFrom = new Palette();
    private final Palette mPaletteTo = new Palette();
    private final Palette mPalette = new Palette();

    private int mSurfaceColor;
    private int mSurfaceBlurColor;
    private int mOnSurfaceColor;
    private int mOnSurfaceVariantColor;
    private int mOutlineColor;

    private BackgroundBlurDrawable mBlurDrawable;
    private boolean mBlurEnabled;
    private final Consumer<Boolean> mBlurListener = enabled -> {
        mBlurEnabled = enabled;
        invalidate();
    };

    private float mRotation;
    private long mLastFrameTime;

    private Runnable mExitAction;
    private final Runnable mFadeOutRunnable = this::fadeOut;

    private RadialGradient mGlowShader;
    private int mGlowColor;
    private float mGlowX = Float.NaN;
    private float mGlowY = Float.NaN;

    private final RectF mCapsuleRect = new RectF();
    private final RectF mContentRect = new RectF();
    private final RectF mRingRect = new RectF();
    private final Path mCapsulePath = new Path();
    private final Path mCookiePath = new Path();
    private final Path mWavePath = new Path();
    private final Path mStylusPath;
    private final Path mBoltPath;
    private final Path mTmpPath = new Path();
    private final Matrix mTmpMatrix = new Matrix();

    private final Paint mSurfacePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mOutlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mClearPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mIndicatorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mTrackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mTitlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mSubtitlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mPercentPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mPercentSignPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public StylusChargingPillView(Context context) {
        this(context, null);
    }

    public StylusChargingPillView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public StylusChargingPillView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mDensity = getResources().getDisplayMetrics().density;
        mWindowManager = context.getSystemService(WindowManager.class);

        final float visibleScale = DynamicAnimation.MIN_VISIBLE_CHANGE_SCALE;
        mExpand = new Spring(0f, SPATIAL_DEFAULT_DAMPING, SPATIAL_DEFAULT_STIFFNESS, visibleScale);
        mScale = new Spring(0.6f, SPATIAL_FAST_DAMPING, SPATIAL_FAST_STIFFNESS, visibleScale);
        mReveal = new Spring(0f, EFFECTS_DAMPING, EFFECTS_DEFAULT_STIFFNESS, visibleScale);
        mMorph = new Spring(1f, SPATIAL_FAST_DAMPING, SPATIAL_FAST_STIFFNESS, visibleScale);
        mSpin = new Spring(0f, SPATIAL_DEFAULT_DAMPING, SPATIAL_DEFAULT_STIFFNESS,
                DynamicAnimation.MIN_VISIBLE_CHANGE_ROTATION_DEGREES);
        mTextSwap = new Spring(1f, EFFECTS_DAMPING, EFFECTS_DEFAULT_STIFFNESS, visibleScale);
        mColor = new Spring(1f, EFFECTS_DAMPING, EFFECTS_DEFAULT_STIFFNESS, visibleScale);
        mProgress = new Spring(0f, EFFECTS_DAMPING, EFFECTS_SLOW_STIFFNESS / 2f, 0.001f);
        mBadge = new Spring(0f, SPATIAL_FAST_DAMPING, SPATIAL_FAST_STIFFNESS, visibleScale);
        mWave = new Spring(0f, EFFECTS_DAMPING, EFFECTS_SLOW_STIFFNESS, visibleScale);
        mIndeterminate = new Spring(0f, EFFECTS_DAMPING, EFFECTS_DEFAULT_STIFFNESS, visibleScale);

        mReveal.addEndListener((animation, canceled, value, velocity) -> {
            if (!canceled && mExitAction != null && mReveal.target() <= 0f) {
                Runnable action = mExitAction;
                mExitAction = null;
                action.run();
            }
        });

        mBoltPath = PathParser.createPathFromPathData(BOLT_PATH_DATA);
        mStylusPath = PathParser.createPathFromPathData(STYLUS_PATH_DATA);
        mStylusPath.setFillType(Path.FillType.EVEN_ODD);

        mSurfacePaint.setStyle(Paint.Style.FILL);
        mOutlinePaint.setStyle(Paint.Style.STROKE);
        mOutlinePaint.setStrokeWidth(dp(1f));
        mGlowPaint.setStyle(Paint.Style.FILL);
        mFillPaint.setStyle(Paint.Style.FILL);
        mClearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));

        mIndicatorPaint.setStyle(Paint.Style.STROKE);
        mIndicatorPaint.setStrokeWidth(dp(RING_STROKE_DP));
        mIndicatorPaint.setStrokeCap(Paint.Cap.ROUND);
        mIndicatorPaint.setStrokeJoin(Paint.Join.ROUND);
        mTrackPaint.set(mIndicatorPaint);

        Typeface typeface = loadTypeface();
        setupText(mTitlePaint, typeface, 16f, "'wght' 600, 'ROND' 100, 'opsz' 16");
        setupText(mSubtitlePaint, typeface, 13f, "'wght' 450, 'ROND' 100, 'opsz' 13");
        setupText(mPercentPaint, typeface, 15f, "'wght' 750, 'ROND' 100, 'wdth' 90, 'opsz' 15");
        setupText(mPercentSignPaint, typeface, 9.5f, "'wght' 700, 'ROND' 100, 'opsz' 9");

        loadColors();
        Palette initial = paletteFor(StylusPopupState.UNKNOWN, new Palette());
        mPaletteFrom.copyFrom(initial);
        mPaletteTo.copyFrom(initial);

        setWillNotDraw(false);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    // ---------------------------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------------------------

    /** Width of the window hosting the pill, including room for spring overshoot. */
    public static int getWindowWidth(Context context) {
        float density = context.getResources().getDisplayMetrics().density;
        return (int) Math.ceil(pillWidthPx(context) + 2f * WINDOW_PADDING_DP * density);
    }

    /** Height of the window hosting the pill, including room for spring overshoot. */
    public static int getWindowHeight(Context context) {
        float density = context.getResources().getDisplayMetrics().density;
        return (int) Math.ceil((PILL_HEIGHT_DP + 2f * WINDOW_PADDING_DP) * density);
    }

    /** Space between the window edge and the pill, so callers can align the visible capsule. */
    public static int getWindowPadding(Context context) {
        return (int) (WINDOW_PADDING_DP * context.getResources().getDisplayMetrics().density);
    }

    private static float pillWidthPx(Context context) {
        float density = context.getResources().getDisplayMetrics().density;
        int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
        return Math.min(PILL_MAX_WIDTH_DP * density, screenWidth - 2f * 16f * density);
    }

    public void setModel(StylusPopupModel newModel) {
        setModel(newModel, isAttachedToWindow() && mExpand.target() > 0f);
    }

    /**
     * Updates the content. When {@code animate} is true the shape morphs, colors crossfade,
     * text swaps and the container gets a small spring "squish" to draw attention.
     */
    public void setModel(StylusPopupModel newModel, boolean animate) {
        StylusPopupModel old = mModel;
        mModel = newModel;
        boolean stateChanged = old.state != newModel.state;
        boolean textChanged = !safeEquals(old.title, newModel.title)
                || !safeEquals(old.subtitle, newModel.subtitle);

        // Palette
        mPaletteFrom.copyFrom(mPalette.container == 0 ? mPaletteTo : mPalette);
        paletteFor(newModel.state, mPaletteTo);

        // Shape
        ShapeSpec target = shapeFor(newModel.state);
        if (target != mShapeTo) {
            mShapeFrom = currentShapeBlendSource();
            mShapeTo = target;
            if (animate) {
                mMorph.snapTo(0f);
                mMorph.animateTo(1f);
            } else {
                mMorph.snapTo(1f);
            }
        }

        boolean charging = isCharging(newModel.state);
        boolean known = newModel.batteryPercent != null && newModel.batteryPercent >= 0;
        float progress = known ? Math.min(100, newModel.batteryPercent) / 100f : 0f;

        if (animate) {
            if (stateChanged) {
                mColor.snapTo(0f);
                mColor.animateTo(1f);
            }
            if (textChanged) {
                mPreviousModel = old;
                mTextSwap.snapTo(0f);
                mTextSwap.animateTo(1f);
            }
            if (newModel.state == StylusPopupState.FULLY_CHARGED && stateChanged) {
                mSpin.animateTo(mSpin.target() + 120f);
            }
            if (known) mProgress.animateTo(progress);
            mBadge.animateTo(charging ? 1f : 0f);
            mWave.animateTo(charging && progress < 1f ? 1f : 0f);
            mIndeterminate.animateTo(known ? 0f : 1f);
            // Attention squish
            mScale.animateTo(1f, -1.2f);
        } else {
            mColor.snapTo(1f);
            mTextSwap.snapTo(1f);
            mPreviousModel = newModel;
            mProgress.snapTo(known ? progress : 0f);
            mBadge.snapTo(charging ? 1f : 0f);
            mWave.snapTo(charging && progress < 1f ? 1f : 0f);
            mIndeterminate.snapTo(known ? 0f : 1f);
        }

        setContentDescription(newModel.title + ", " + newModel.subtitle
                + (known ? ", " + newModel.batteryPercent + "%" : ""));
        invalidate();
    }

    /** Container transform entrance: a small circle springs open into the full pill. */
    public void animateIn() {
        resetContainerSprings();
        mExpand.snapTo(0f);
        mScale.snapTo(0.6f);
        mReveal.snapTo(0f);

        // Content enters from a neutral state so the reveal itself carries motion.
        float targetProgress = mProgress.target();
        boolean charging = isCharging(mModel.state);
        mProgress.snapTo(0f);
        mBadge.snapTo(0f);
        mMorph.snapTo(0f);
        mShapeFrom = SHAPE_CIRCLE;
        mSpin.snapTo(-90f);

        mReveal.animateTo(1f);
        mScale.animateTo(1f);
        mExpand.animateTo(1f);
        mMorph.animateTo(1f);
        mSpin.animateTo(0f);
        mProgress.animateTo(targetProgress);
        mBadge.animateTo(charging ? 1f : 0f);
    }

    /** Reverse container transform, then {@code endAction} once the pill is gone. */
    public void animateOut(Runnable endAction) {
        if (!ValueAnimator.areAnimatorsEnabled()) {
            mReveal.snapTo(0f);
            endAction.run();
            return;
        }
        mExitAction = endAction;
        mExpand.force().setDampingRatio(EFFECTS_DAMPING).setStiffness(COLLAPSE_STIFFNESS);
        mReveal.force().setStiffness(EFFECTS_SLOW_STIFFNESS);
        mExpand.animateTo(0f);
        mScale.animateTo(0.6f);
        // Let the capsule collapse a little before fading away.
        postDelayed(mFadeOutRunnable, 120);
    }

    private void fadeOut() {
        mReveal.animateTo(0f);
    }

    /** Whether an exit animation is in flight. */
    public boolean isExiting() {
        return mExitAction != null;
    }

    /** Cancels a running exit so the pill can be reused in place. */
    public void cancelExit() {
        mExitAction = null;
        removeCallbacks(mFadeOutRunnable);
        resetContainerSprings();
        mReveal.animateTo(1f);
        mExpand.animateTo(1f);
        mScale.animateTo(1f);
    }

    private void resetContainerSprings() {
        mExpand.force().setDampingRatio(SPATIAL_DEFAULT_DAMPING)
                .setStiffness(SPATIAL_DEFAULT_STIFFNESS);
        mReveal.force().setStiffness(EFFECTS_DEFAULT_STIFFNESS);
    }

    // ---------------------------------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------------------------------

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (getViewRootImpl() != null) {
            mBlurDrawable = getViewRootImpl().createBackgroundBlurDrawable();
            mBlurDrawable.setBlurRadius((int) dp(36f));
        }
        if (mWindowManager != null) {
            mBlurEnabled = mWindowManager.isCrossWindowBlurEnabled();
            mWindowManager.addCrossWindowBlurEnabledListener(getContext().getMainExecutor(),
                    mBlurListener);
        }
        mLastFrameTime = 0L;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mWindowManager != null) {
            mWindowManager.removeCrossWindowBlurEnabledListener(mBlurListener);
        }
        if (mBlurDrawable != null) {
            mBlurDrawable.setVisible(false, false);
            mBlurDrawable = null;
        }
        removeCallbacks(mFadeOutRunnable);
        mExitAction = null;
        for (Spring spring : new Spring[] { mExpand, mScale, mReveal, mMorph, mSpin, mTextSwap,
                mColor, mProgress, mBadge, mWave, mIndeterminate }) {
            spring.cancel();
        }
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        loadColors();
        paletteFor(mModel.state, mPaletteTo);
        mPaletteFrom.copyFrom(mPaletteTo);
        mColor.snapTo(1f);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(
                resolveSize(getWindowWidth(getContext()), widthMeasureSpec),
                resolveSize(getWindowHeight(getContext()), heightMeasureSpec));
    }

    // ---------------------------------------------------------------------------------------
    // Drawing
    // ---------------------------------------------------------------------------------------

    @Override
    protected void onDraw(Canvas canvas) {
        final float width = getWidth();
        final float height = getHeight();
        if (width <= 0f || height <= 0f) return;

        final long now = SystemClock.uptimeMillis();
        final float dt = mLastFrameTime == 0L ? 0f : Math.min(64, now - mLastFrameTime) / 1000f;
        mLastFrameTime = now;
        final boolean animatorsEnabled = ValueAnimator.areAnimatorsEnabled();
        final boolean charging = isCharging(mModel.state);
        if (charging && animatorsEnabled) {
            mRotation = (mRotation + dt * 24f) % 360f;
        }

        mPalette.lerp(mPaletteFrom, mPaletteTo, clamp01(mColor.get()), mArgb);

        final float pad = dp(WINDOW_PADDING_DP);
        final float fullWidth = width - 2f * pad;
        final float pillHeight = dp(PILL_HEIGHT_DP);
        final float reveal = clamp01(mReveal.get());
        final float expand = mExpand.get();
        final float scale = mScale.get();

        final float cx = width / 2f;
        // Drop down slightly from under the status bar while revealing
        final float cy = pad + pillHeight / 2f - (1f - reveal) * dp(10f);

        final float curWidth = (pillHeight + (fullWidth - pillHeight) * expand) * scale;
        final float curHeight = pillHeight * scale;
        final float radius = curHeight / 2f;
        mCapsuleRect.set(cx - curWidth / 2f, cy - curHeight / 2f,
                cx + curWidth / 2f, cy + curHeight / 2f);

        if (reveal <= 0.001f) {
            if (mBlurDrawable != null) mBlurDrawable.setAlpha(0);
            return;
        }

        // 1. Surface: real cross-window blur tinted with surfaceContainer, or opaque fallback
        boolean useBlur = mBlurEnabled && mBlurDrawable != null;
        if (mBlurDrawable != null) {
            mBlurDrawable.setAlpha(useBlur ? (int) (255 * reveal) : 0);
        }
        if (useBlur) {
            mBlurDrawable.setColor(mSurfaceBlurColor);
            mBlurDrawable.setCornerRadius(radius);
            mBlurDrawable.setBounds((int) mCapsuleRect.left, (int) mCapsuleRect.top,
                    (int) mCapsuleRect.right, (int) mCapsuleRect.bottom);
            mBlurDrawable.draw(canvas);
        } else {
            mSurfacePaint.setColor(mSurfaceColor);
            mSurfacePaint.setAlpha((int) (Color.alpha(mSurfaceColor) * reveal));
            canvas.drawRoundRect(mCapsuleRect, radius, radius, mSurfacePaint);
        }

        mCapsulePath.reset();
        mCapsulePath.addRoundRect(mCapsuleRect, radius, radius, Path.Direction.CW);

        int save = canvas.save();
        canvas.clipPath(mCapsulePath);

        // Content is laid out against the full size pill and revealed by the expanding clip
        mContentRect.set(cx - fullWidth / 2f, cy - pillHeight / 2f,
                cx + fullWidth / 2f, cy + pillHeight / 2f);
        canvas.scale(scale, scale, cx, cy);

        final float cookieCx = mContentRect.left + dp(10f) + dp(COOKIE_SIZE_DP) / 2f;

        // 2. Tonal glow behind the shape, breathing while charging
        drawGlow(canvas, cookieCx, cy, reveal, charging && animatorsEnabled, now);

        // 3. Content layer
        float contentAlpha = clamp01((expand - 0.55f) / 0.45f) * reveal;
        if (contentAlpha > 0.004f) {
            int layer = canvas.saveLayerAlpha(mContentRect, (int) (255 * contentAlpha));
            drawCookie(canvas, cookieCx, cy, now, animatorsEnabled);
            float ringCx = mContentRect.right - dp(12f) - dp(RING_SIZE_DP) / 2f;
            drawRing(canvas, ringCx, cy, now, animatorsEnabled);
            float textLeft = cookieCx + dp(COOKIE_SIZE_DP) / 2f + dp(14f);
            float textRight = ringCx - dp(RING_SIZE_DP) / 2f - dp(12f);
            drawText(canvas, textLeft, textRight, cy);
            canvas.restoreToCount(layer);
        }
        canvas.restoreToCount(save);

        // 4. Hairline outline for definition over busy wallpapers
        mOutlinePaint.setColor(mOutlineColor);
        mOutlinePaint.setAlpha((int) (Color.alpha(mOutlineColor) * reveal));
        float inset = mOutlinePaint.getStrokeWidth() / 2f;
        canvas.drawRoundRect(mCapsuleRect.left + inset, mCapsuleRect.top + inset,
                mCapsuleRect.right - inset, mCapsuleRect.bottom - inset,
                radius - inset, radius - inset, mOutlinePaint);

        if (needsContinuousFrames(animatorsEnabled)) {
            postInvalidateOnAnimation();
        } else {
            mLastFrameTime = 0L;
        }
    }

    private boolean needsContinuousFrames(boolean animatorsEnabled) {
        if (!animatorsEnabled || mReveal.target() <= 0f) return false;
        return isCharging(mModel.state)
                || mModel.state == StylusPopupState.CRITICAL_BATTERY
                || mIndeterminate.get() > 0.01f
                || mWave.get() > 0.01f;
    }

    private void drawGlow(Canvas canvas, float x, float y, float reveal, boolean breathe, long now) {
        float strength = 0.22f;
        if (breathe) {
            strength *= 0.8f + 0.2f * (float) Math.sin(now / 600.0);
        }
        int accent = mPalette.indicator | 0xFF000000;
        if (mGlowShader == null || accent != mGlowColor || x != mGlowX || y != mGlowY) {
            mGlowShader = new RadialGradient(x, y, dp(150f),
                    new int[] { accent, accent & 0x00FFFFFF }, null, Shader.TileMode.CLAMP);
            mGlowColor = accent;
            mGlowX = x;
            mGlowY = y;
        }
        mGlowPaint.setShader(mGlowShader);
        mGlowPaint.setAlpha((int) (255 * strength * reveal));
        canvas.drawRect(mContentRect, mGlowPaint);
    }

    private void drawCookie(Canvas canvas, float cx, float cy, long now, boolean animate) {
        float maxRadius = dp(COOKIE_SIZE_DP) / 2f;
        if (mModel.state == StylusPopupState.CRITICAL_BATTERY && animate) {
            maxRadius *= 1f + 0.045f * (float) Math.sin(now / 160.0);
        }

        float morph = mMorph.get();
        double rotation = Math.toRadians(mRotation + mSpin.get());
        mCookiePath.reset();
        for (int i = 0; i <= COOKIE_SAMPLES; i++) {
            double theta = 2.0 * Math.PI * i / COOKIE_SAMPLES;
            double local = theta - rotation;
            float r = mShapeFrom.radius(local)
                    + (mShapeTo.radius(local) - mShapeFrom.radius(local)) * morph;
            float x = cx + (float) Math.cos(theta) * r * maxRadius;
            float y = cy + (float) Math.sin(theta) * r * maxRadius;
            if (i == 0) {
                mCookiePath.moveTo(x, y);
            } else {
                mCookiePath.lineTo(x, y);
            }
        }
        mCookiePath.close();

        mFillPaint.setColor(mPalette.container);
        canvas.drawPath(mCookiePath, mFillPaint);

        // Stylus glyph
        float iconSize = dp(28f);
        mTmpMatrix.reset();
        mTmpMatrix.setScale(iconSize / 24f, iconSize / 24f);
        mTmpMatrix.postTranslate(cx - iconSize / 2f, cy - iconSize / 2f);
        mStylusPath.transform(mTmpMatrix, mTmpPath);
        mFillPaint.setColor(mPalette.onContainer);
        canvas.drawPath(mTmpPath, mFillPaint);

        // Charging badge with a true cut-out around it
        float badge = mBadge.get();
        if (badge > 0.01f) {
            float badgeRadius = dp(10f) * badge;
            float bx = cx + maxRadius * 0.72f;
            float by = cy + maxRadius * 0.72f;
            canvas.drawCircle(bx, by, badgeRadius + dp(2.5f) * badge, mClearPaint);
            mFillPaint.setColor(mPalette.indicator);
            canvas.drawCircle(bx, by, badgeRadius, mFillPaint);

            float boltSize = badgeRadius * 1.5f;
            mTmpMatrix.reset();
            mTmpMatrix.setScale(boltSize / 24f, boltSize / 24f);
            mTmpMatrix.postTranslate(bx - boltSize / 2f, by - boltSize / 2f);
            mBoltPath.transform(mTmpMatrix, mTmpPath);
            mFillPaint.setColor(onColorFor(mPalette.indicator));
            canvas.drawPath(mTmpPath, mFillPaint);
        }
    }

    private void drawRing(Canvas canvas, float cx, float cy, long now, boolean animate) {
        float stroke = dp(RING_STROKE_DP);
        float amplitudeMax = dp(RING_WAVE_AMPLITUDE_DP);
        float radius = dp(RING_SIZE_DP) / 2f - stroke / 2f - amplitudeMax;
        mRingRect.set(cx - radius, cy - radius, cx + radius, cy + radius);

        float indeterminate = clamp01(mIndeterminate.get());
        float progress = clamp01(mProgress.get());
        double time = animate ? now / 1000.0 : 0.0;

        // Determinate sweep blended with a spinning indeterminate arc
        float startDeg = -90f + indeterminate * (float) ((time * 300.0) % 360.0);
        float sweepDeg = 360f * (progress + (0.28f - progress) * indeterminate);

        // M3: amplitude flattens near the ends so the wave never looks clipped
        float envelope = Math.min(1f, Math.min(sweepDeg / 36f, (360f - sweepDeg) / 36f + 0.0001f));
        float amplitude = amplitudeMax * clamp01(mWave.get() + indeterminate) * clamp01(envelope);
        double phase = time * 2.0 * Math.PI * 0.8;

        // Track
        float gapDeg = (float) Math.toDegrees((dp(RING_GAP_DP) + stroke) / radius);
        float trackSweep = 360f - sweepDeg - 2f * gapDeg;
        mTrackPaint.setColor(mPalette.track);
        if (sweepDeg < 0.5f) {
            canvas.drawCircle(cx, cy, radius, mTrackPaint);
        } else if (trackSweep > 0.5f) {
            canvas.drawArc(mRingRect, startDeg + sweepDeg + gapDeg, trackSweep, false, mTrackPaint);
        }

        // Active indicator
        if (sweepDeg >= 0.5f) {
            mIndicatorPaint.setColor(mPalette.indicator);
            if (amplitude < 0.05f) {
                canvas.drawArc(mRingRect, startDeg, Math.min(sweepDeg, 359.99f), false,
                        mIndicatorPaint);
            } else {
                mWavePath.reset();
                int steps = Math.max(8, (int) (sweepDeg / 2f));
                for (int i = 0; i <= steps; i++) {
                    double deg = startDeg + sweepDeg * i / steps;
                    double rad = Math.toRadians(deg);
                    float r = radius + amplitude * (float) Math.sin(
                            RING_WAVE_COUNT * Math.toRadians(deg + 90.0) - phase);
                    float x = cx + (float) Math.cos(rad) * r;
                    float y = cy + (float) Math.sin(rad) * r;
                    if (i == 0) {
                        mWavePath.moveTo(x, y);
                    } else {
                        mWavePath.lineTo(x, y);
                    }
                }
                canvas.drawPath(mWavePath, mIndicatorPaint);
            }
        }

        // Percentage
        Integer percent = mModel.batteryPercent;
        if (percent != null && percent >= 0 && indeterminate < 0.99f) {
            String number = String.valueOf(Math.round(progress * 100f));
            mPercentPaint.setColor(mOnSurfaceColor);
            mPercentSignPaint.setColor(mOnSurfaceVariantColor);
            int alpha = (int) (255 * (1f - indeterminate));
            mPercentPaint.setAlpha(alpha);
            mPercentSignPaint.setAlpha(alpha);
            float numberWidth = mPercentPaint.measureText(number);
            float signWidth = mPercentSignPaint.measureText("%");
            float left = cx - (numberWidth + signWidth) / 2f;
            Paint.FontMetrics fm = mPercentPaint.getFontMetrics();
            float baseline = cy - (fm.ascent + fm.descent) / 2f;
            canvas.drawText(number, left, baseline, mPercentPaint);
            canvas.drawText("%", left + numberWidth, baseline, mPercentSignPaint);
        }
    }

    private void drawText(Canvas canvas, float left, float right, float cy) {
        float maxWidth = right - left;
        if (maxWidth < dp(40f)) return;

        float t = clamp01(mTextSwap.get());
        float travel = dp(10f);
        if (t < 1f && mPreviousModel != mModel) {
            drawTextBlock(canvas, mPreviousModel, left, maxWidth, cy - travel * t, 1f - t);
        }
        drawTextBlock(canvas, mModel, left, maxWidth, cy + travel * (1f - t), t);
    }

    private void drawTextBlock(Canvas canvas, StylusPopupModel model, float left, float maxWidth,
            float cy, float alpha) {
        if (alpha <= 0.01f) return;
        String title = ellipsize(model.title != null ? model.title : "", mTitlePaint, maxWidth);
        String subtitle = ellipsize(model.subtitle != null ? model.subtitle : "",
                mSubtitlePaint, maxWidth);

        Paint.FontMetrics tm = mTitlePaint.getFontMetrics();
        Paint.FontMetrics sm = mSubtitlePaint.getFontMetrics();
        float titleHeight = tm.descent - tm.ascent;
        float subtitleHeight = sm.descent - sm.ascent;
        float spacing = dp(2f);
        float top = cy - (titleHeight + spacing + subtitleHeight) / 2f;

        mTitlePaint.setColor(mOnSurfaceColor);
        mTitlePaint.setAlpha((int) (255 * alpha));
        canvas.drawText(title, left, top - tm.ascent, mTitlePaint);

        mSubtitlePaint.setColor(mOnSurfaceVariantColor);
        mSubtitlePaint.setAlpha((int) (255 * alpha));
        canvas.drawText(subtitle, left, top + titleHeight + spacing - sm.ascent, mSubtitlePaint);
    }

    // ---------------------------------------------------------------------------------------
    // Styling
    // ---------------------------------------------------------------------------------------

    private boolean isNight() {
        return (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }

    private int sys(int light, int dark) {
        return getContext().getColor(isNight() ? dark : light);
    }

    private void loadColors() {
        int surface = sys(android.R.color.system_surface_container_light,
                android.R.color.system_surface_container_dark);
        mSurfaceColor = withAlpha(sys(android.R.color.system_surface_container_high_light,
                android.R.color.system_surface_container_high_dark), 0.97f);
        mSurfaceBlurColor = withAlpha(surface, isNight() ? 0.62f : 0.70f);
        mOnSurfaceColor = sys(android.R.color.system_on_surface_light,
                android.R.color.system_on_surface_dark);
        mOnSurfaceVariantColor = sys(android.R.color.system_on_surface_variant_light,
                android.R.color.system_on_surface_variant_dark);
        mOutlineColor = withAlpha(sys(android.R.color.system_outline_variant_light,
                android.R.color.system_outline_variant_dark), 0.45f);
    }

    private Palette paletteFor(StylusPopupState state, Palette out) {
        int track = sys(android.R.color.system_secondary_container_light,
                android.R.color.system_secondary_container_dark);
        if (state == null) state = StylusPopupState.UNKNOWN;
        switch (state) {
            case CHARGING:
                return out.set(
                        sys(android.R.color.system_primary_container_light,
                                android.R.color.system_primary_container_dark),
                        sys(android.R.color.system_on_primary_container_light,
                                android.R.color.system_on_primary_container_dark),
                        sys(android.R.color.system_primary_light,
                                android.R.color.system_primary_dark),
                        track);
            case FULLY_CHARGED:
                return out.set(
                        sys(android.R.color.system_tertiary_container_light,
                                android.R.color.system_tertiary_container_dark),
                        sys(android.R.color.system_on_tertiary_container_light,
                                android.R.color.system_on_tertiary_container_dark),
                        sys(android.R.color.system_tertiary_light,
                                android.R.color.system_tertiary_dark),
                        track);
            case CONNECTED:
                return out.set(
                        sys(android.R.color.system_secondary_container_light,
                                android.R.color.system_secondary_container_dark),
                        sys(android.R.color.system_on_secondary_container_light,
                                android.R.color.system_on_secondary_container_dark),
                        sys(android.R.color.system_secondary_light,
                                android.R.color.system_secondary_dark),
                        sys(android.R.color.system_surface_container_highest_light,
                                android.R.color.system_surface_container_highest_dark));
            case LOW_BATTERY:
                return out.set(
                        sys(android.R.color.system_error_container_light,
                                android.R.color.system_error_container_dark),
                        sys(android.R.color.system_on_error_container_light,
                                android.R.color.system_on_error_container_dark),
                        sys(android.R.color.system_error_light,
                                android.R.color.system_error_dark),
                        track);
            case CRITICAL_BATTERY:
                return out.set(
                        sys(android.R.color.system_error_light,
                                android.R.color.system_error_dark),
                        sys(android.R.color.system_on_error_light,
                                android.R.color.system_on_error_dark),
                        sys(android.R.color.system_error_light,
                                android.R.color.system_error_dark),
                        sys(android.R.color.system_error_container_light,
                                android.R.color.system_error_container_dark));
            case DISCONNECTED:
            case UNKNOWN:
            default:
                return out.set(
                        sys(android.R.color.system_surface_container_highest_light,
                                android.R.color.system_surface_container_highest_dark),
                        sys(android.R.color.system_on_surface_variant_light,
                                android.R.color.system_on_surface_variant_dark),
                        sys(android.R.color.system_outline_light,
                                android.R.color.system_outline_dark),
                        sys(android.R.color.system_surface_container_highest_light,
                                android.R.color.system_surface_container_highest_dark));
        }
    }

    private int onColorFor(int indicator) {
        // Badge sits on the indicator color; pick the matching "on" role by contrast.
        float luminance = Color.luminance(indicator);
        return luminance > 0.4f ? Color.argb(255, 20, 20, 24) : Color.WHITE;
    }

    private static ShapeSpec shapeFor(StylusPopupState state) {
        if (state == null) return SHAPE_CIRCLE;
        switch (state) {
            case CHARGING:
                return SHAPE_COOKIE_9;
            case FULLY_CHARGED:
                return SHAPE_SUNNY;
            case CONNECTED:
                return SHAPE_COOKIE_4;
            case LOW_BATTERY:
                return SHAPE_COOKIE_6;
            case CRITICAL_BATTERY:
                return SHAPE_FLOWER_5;
            case DISCONNECTED:
            case UNKNOWN:
            default:
                return SHAPE_CIRCLE;
        }
    }

    /** While a morph is still running, restart from whichever shape currently dominates. */
    private ShapeSpec currentShapeBlendSource() {
        return mMorph.get() < 0.5f ? mShapeFrom : mShapeTo;
    }

    private static boolean isCharging(StylusPopupState state) {
        return state == StylusPopupState.CHARGING;
    }

    private Typeface loadTypeface() {
        File font = new File(FONT_PATH);
        if (font.canRead()) {
            Typeface typeface = new Typeface.Builder(font).setFallback("sans-serif").build();
            if (typeface != null) return typeface;
        }
        return Typeface.create("sans-serif", Typeface.NORMAL);
    }

    private void setupText(Paint paint, Typeface typeface, float sizeSp, String variation) {
        paint.setTypeface(typeface);
        paint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sizeSp,
                getResources().getDisplayMetrics()));
        paint.setFontVariationSettings(variation);
        paint.setSubpixelText(true);
    }

    // ---------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------

    private float dp(float value) {
        return value * mDensity;
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private static int withAlpha(int color, float alpha) {
        return Color.argb((int) (255 * alpha), Color.red(color), Color.green(color),
                Color.blue(color));
    }

    private static boolean safeEquals(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    private static String ellipsize(String text, Paint paint, float maxWidth) {
        if (paint.measureText(text) <= maxWidth) return text;
        String suffix = "…";
        int end = text.length();
        while (end > 0 && paint.measureText(text, 0, end) + paint.measureText(suffix) > maxWidth) {
            end--;
        }
        return text.substring(0, end) + suffix;
    }
}
