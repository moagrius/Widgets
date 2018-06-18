package com.qozix.widgets;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AnimationUtils;

public class ProgressEllipsis extends View {

  private static final int ALPHA_RANGE = 255;

  private static final long DEFAULT_DURATION = 600;
  private static final float DEFAULT_THRESHOLD = 0.35f; // 1f;
  private static final int DEFAULT_QUANTITY = 3;
  private static final int DEFAULT_COLOR = 0xFF000000;

  private long mDuration;
  private float mThreshold;  // how far the animation needs to progress to trigger an event

  private int mSize;
  private int mSpacing;
  private float mRadius;

  private Dot[] mDots;

  private Paint mPaint = new Paint();
  {
    mPaint.setStyle(Paint.Style.FILL);
    mPaint.setAntiAlias(true);
  }

  public ProgressEllipsis(Context context) {
    this(context, null);
  }

  public ProgressEllipsis(Context context, @Nullable AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public ProgressEllipsis(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    TypedArray a = context.getTheme().obtainStyledAttributes(attrs, R.styleable.ProgressEllipsis, 0, 0);
    int quantity;
    try {
      quantity = a.getInteger(R.styleable.ProgressEllipsis_quantity, DEFAULT_QUANTITY);
      mDuration = a.getInteger(R.styleable.ProgressEllipsis_duration, (int) DEFAULT_DURATION);
      mThreshold = a.getFloat(R.styleable.ProgressEllipsis_threshold, DEFAULT_THRESHOLD);
      mPaint.setColor(a.getColor(R.styleable.ProgressEllipsis_color, DEFAULT_COLOR));
    } finally {
      a.recycle();
    }

    mDots = new Dot[quantity];
    for (int i = 0; i < quantity; i++) {
      Dot dot = new Dot();
      if (i > 0) {
        Dot previous = mDots[i - 1];
        previous.setNextDot(dot);
      }
      // last dot circles back around
      if (i == (quantity - 1)) {
        dot.setLastDot(true);
        dot.setNextDot(mDots[0]);
      }
      mDots[i] = dot;
    }
  }

  public long getDuration() {
    return mDuration;
  }

  public void setDuration(long duration) {
    mDuration = duration;
  }

  public float getThreshold() {
    return mThreshold;
  }

  public void setThreshold(float threshold) {
    mThreshold = threshold;
  }

  public int getColor() {
    return mPaint.getColor();
  }

  public void setColor(int color) {
    mPaint.setColor(color);
    invalidate();
  }

  @Override
  protected void onAttachedToWindow() {
    super.onAttachedToWindow();
    if (mDots.length == 0) {
      return;
    }
    for (Dot dot : mDots) {
      dot.setCurrentValue(0);
    }
    mDots[0].startFadeIn(AnimationUtils.currentAnimationTimeMillis());
    invalidate();
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    mSize = getMeasuredHeight();
    mRadius = mSize * 0.5f;
    int quantity = mDots.length;
    int consumed = quantity * mSize;
    int remaining = getMeasuredWidth() - consumed;
    mSpacing = remaining / (quantity - 1);
  }

  public void onDraw(Canvas canvas) {
    if (!isAttachedToWindow()) {
      return;
    }
    // figure out where we are in the animation
    long now = AnimationUtils.currentAnimationTimeMillis();
    int left = 0;
    for (Dot dot : mDots) {
      dot.tick(now);
      mPaint.setAlpha(dot.getCurrentValue());
      canvas.drawCircle(left + mRadius, mRadius, mRadius, mPaint);
      left += mSize + mSpacing;
    }
    invalidate();
  }

  private class Dot {

    private static final int FADING_IN = 1;
    private static final int FADE_TYPE_INVALID = 0;
    private static final int FADING_OUT = -1;

    private Dot mNextDot;
    private boolean mIsLastDot;

    private long mStartTime;

    private int mDirection;  // FADING_IN or FADING_OUT

    private int mCurrentValue;
    private int mOriginalValue;
    private int mDestinationValue;

    private boolean mHasBroadcast;

    public void setNextDot(Dot nextDot) {
      mNextDot = nextDot;
    }

    public void setLastDot(boolean lastDot) {
      mIsLastDot = lastDot;
    }

    public void start(long startTime, int from, int to) {
      mHasBroadcast = false;
      mStartTime = startTime;
      mOriginalValue = from;
      mDestinationValue = to;
      mDirection = (int) Math.signum(to - from);
    }

    public void startFadeIn(long startTime) {
      start(startTime, 0, ALPHA_RANGE);
    }

    public void startFadeOut(long startTime) {
      start(startTime, ALPHA_RANGE, 0);
    }

    private void tick(long timestamp) {
      if (mCurrentValue == mDestinationValue) {
        return;
      }
      long elapsed = timestamp - mStartTime;
      float factor = elapsed == 0 ? 0 : (elapsed / (float) mDuration);
      mCurrentValue = (int) (mOriginalValue + (mDestinationValue - mOriginalValue) * factor);
      boolean done = false;
      switch (mDirection) {
        case FADING_IN:
          if (mCurrentValue >= mDestinationValue) {
            mCurrentValue = mDestinationValue;
            done = true;
          }
          if (done || factor >= mThreshold) {
            if (!mHasBroadcast) {
              if (mIsLastDot) {
                mNextDot.startFadeOut(timestamp);
              } else {
                mNextDot.startFadeIn(timestamp);
              }
              mHasBroadcast = true;
            }
          }
          break;
        case FADING_OUT:
          if (mCurrentValue <= mDestinationValue) {
            mCurrentValue = mDestinationValue;
            done = true;
          }
          if (done || factor >= mThreshold) {
            if (!mHasBroadcast) {
              if (mIsLastDot) {
                mNextDot.startFadeIn(timestamp);
              } else {
                mNextDot.startFadeOut(timestamp);
              }
              mHasBroadcast = true;
            }
          }
          break;
        case FADE_TYPE_INVALID:
          mCurrentValue = mDestinationValue;  // whatever
          break;
      }
    }

    public int getCurrentValue() {
      return mCurrentValue;
    }

    public void setCurrentValue(int value) {
      mCurrentValue = value;
    }
    
  }

}
