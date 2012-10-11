package com.dougmelton.holoptr;

import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Interpolator;

final class AnimateRunnable implements Runnable {

	static final int ANIMATION_DURATION_MS = 200;
	static final int ANIMATION_DELAY = 10;

	// So that we can mView.post and mView.removeCallbacks
	private final View mView;

	private final Interpolator mInterpolator;
	private final int mTo;
	private final int mFrom;

	private boolean mContinueRunning = true;
	private boolean mIsStopped = false;

	private long mStartTime = -1;
	private int mCurrent = -1;
	private OnTickHandler mTickHandler = null;

	public AnimateRunnable(View view, int fromY, int toY, OnTickHandler tickHandler) {
		this(view, fromY, toY, new AccelerateDecelerateInterpolator(), tickHandler);
	}

	public AnimateRunnable(View view, int fromY, int toY, Interpolator interpolator, OnTickHandler tickHandler) {
		mView = view;
		mFrom = fromY;
		mTo = toY;
		mInterpolator = interpolator;
		mTickHandler = tickHandler;
	}

	@Override
	public void run() {

		/**
		 * Only set mStartTime if this is the first time we're starting,
		 * else actually calculate the Y delta
		 */
		if (mStartTime == -1) {
			mStartTime = System.currentTimeMillis();
		}
		else {

			/**
			 * We do do all calculations in long to reduce software float
			 * calculations. We use 1000 as it gives us good accuracy and
			 * small rounding errors
			 */
			long normalizedTime = (1000 * (System.currentTimeMillis() - mStartTime)) / ANIMATION_DURATION_MS;
			normalizedTime = Math.max(Math.min(normalizedTime, 1000), 0);

			final int deltaY = Math.round((mFrom - mTo)
					* mInterpolator.getInterpolation(normalizedTime / 1000f));
			mCurrent = mFrom - deltaY;
			mTickHandler.tick(mCurrent);
		}

		// If we're not at the target Y, keep going...
		if (mContinueRunning && mTo != mCurrent) {
			// if (VERSION.SDK_INT >= VERSION_CODES.JELLY_BEAN) {
			// SDK16.postOnAnimation(PullToRefreshBase.this, this);
			// } else {
			mView.postDelayed(this, ANIMATION_DELAY);
			// }
		}
		else {
			mTickHandler.done(false);
			mIsStopped = true;
		}
	}

	public void cancel() {
		mContinueRunning = false;
		mView.removeCallbacks(this);
		mTickHandler.done(true);
		mIsStopped = true;
	}

	public boolean isStopped() {
		return mIsStopped;
	}

	public interface OnTickHandler {
		public void tick(int y);

		public void done(boolean cancelled);
	}

}
