package com.dougmelton.holoptr;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Adapter;
import android.widget.FrameLayout;
import android.widget.ListView;

import com.dougmelton.holoptr.AnimateRunnable.OnTickHandler;

public class HoloPullToRefreshLayout extends FrameLayout {
	private static final String TAG = HoloPullToRefreshLayout.class.getSimpleName();

	private static final float FRICTION = 2.5f;

	private static enum State {
		REST,
		PULL_TO_REFRESH,
		RELEASE_TO_REFRESH,
		REFRESH;
	}

	private State mState;

	private float mLastMotionY;
	private float mInitialMotionY;
	private int mPullDistance;

	private int mOffsetTop;
	private int mOffsetGlow;
	private int mOffsetRotation;

	private boolean mTouchDuringRefresh = true;

	private int mHeaderHeight;
	private HoloPullToRefreshHeaderView mHeader;

	private View mRefreshableView;
	private AnimatorProxy mAnimProxy;

	private OnRefreshListener mOnRefreshListener;

	public HoloPullToRefreshLayout(Context context) {
		super(context);
		init(context, null);
	}

	public HoloPullToRefreshLayout(Context context, AttributeSet attrs) {
		super(context, attrs);
		init(context, attrs);
	}

	private void init(Context context, AttributeSet attrs) {
		mHeaderHeight = context.getResources().getDimensionPixelSize(R.dimen.header_height);

		mHeader = new HoloPullToRefreshHeaderView(context, attrs);
		FrameLayout.LayoutParams lpHeader = new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT,
				mHeaderHeight);
		mHeader.setLayoutParams(lpHeader);
		addView(mHeader, 0);

		post(new Runnable() {
			@Override
			public void run() {
				mRefreshableView = findViewById(android.R.id.list);
				mAnimProxy = AnimatorProxy.wrap(mRefreshableView);
				setState(State.REST, false);
			}
		});

	}

	/////////////////////////////////////////////////////////////////////////////
	// Touch Events

	@Override
	public final boolean onInterceptTouchEvent(MotionEvent event) {
		final int action = event.getAction();

		switch (action) {

		case MotionEvent.ACTION_DOWN: {
			Log.e(TAG, "Touch Intercept DOWN");
			if (mState == State.REFRESH) {
				return !mTouchDuringRefresh;
			}

			if (isReadyForPull()) {
				mLastMotionY = mInitialMotionY = event.getY();
				mPullDistance = 0;
				return mState != State.REST;
			}

			return false;
		}

		case MotionEvent.ACTION_MOVE: {
			Log.e(TAG, "Touch Intercept MOVE");

			// If we're refreshing, and the flag is set. Eat all MOVE events
			if (mState == State.REFRESH) {
				return !mTouchDuringRefresh;
			}

			if (mState == State.REST) {
				if (isReadyForPull()) {
					final float y = event.getY();
					final float dy = y - mLastMotionY;
					final float yDiff = Math.abs(dy);

					if (yDiff > 8 && dy >= 1f) {
						mLastMotionY = y;
						mPullDistance = (int) ((mLastMotionY - mInitialMotionY) / FRICTION);
						setState(State.PULL_TO_REFRESH, true);
						return true;
					}
				}
			}

			return false;
		}

		case MotionEvent.ACTION_CANCEL:
		case MotionEvent.ACTION_UP: {
			Log.e(TAG, "Touch Intercept CANCEL/UP");
			return mState != State.REST;
		}
		}

		Log.e(TAG, "Touch Intercept: unexpected state, returning false");
		return false;
	}

	@Override
	public final boolean onTouchEvent(MotionEvent event) {

		// If we're refreshing, eat the event
		if (mState == State.REFRESH) {
			Log.e(TAG, "Touch NOM NOM");
			return !mTouchDuringRefresh;
		}

		switch (event.getAction()) {
		case MotionEvent.ACTION_DOWN: {
			Log.e(TAG, "Touch DOWN");
			if (event.getEdgeFlags() != 0) {
				return false;
			}
			if (isReadyForPull()) {
				mLastMotionY = mInitialMotionY = event.getY();
				mPullDistance = 0;
				return true;
			}
			break;
		}

		case MotionEvent.ACTION_MOVE: {
			Log.e(TAG, "Touch MOVE");
			if (mState != State.REST) {
				mLastMotionY = event.getY();
				mPullDistance = (int) ((mLastMotionY - mInitialMotionY) / FRICTION);
				pullEvent();
				return true;
			}
			break;
		}

		case MotionEvent.ACTION_CANCEL:
		case MotionEvent.ACTION_UP: {
			Log.e(TAG, "Touch UP");
			if (mState == State.RELEASE_TO_REFRESH) {
				setState(State.REFRESH, true);
			}
			else {
				setState(State.REST, true);
			}
			return true;
		}
		}

		return false;
	}

	/////////////////////////////////////////////////////////////////////////////
	// Move the views around holo-like

	private void pullEvent() {
		offsetTop(mPullDistance);
		offsetGlow(mPullDistance);
		offsetRotation(mPullDistance);

		if (mPullDistance == 0) {
			setState(State.REST, true);
		}

		if (mState == State.PULL_TO_REFRESH && mHeaderHeight < mPullDistance) {
			setState(State.RELEASE_TO_REFRESH, true);
		}

		else if (mState == State.RELEASE_TO_REFRESH && mHeaderHeight >= mPullDistance) {
			setState(State.PULL_TO_REFRESH, true);
		}
	}

	protected final void offset(int top, int glow, int rotation) {
		offsetTop(top);
		offsetGlow(glow);
		offsetRotation(rotation);
	}

	protected final void offsetTop(int y) {
		mOffsetTop = Math.min(128, y);

		mAnimProxy.setTranslationY(mOffsetTop, true);
	}

	protected final void offsetGlow(int y) {
		mOffsetGlow = y;

		if (mRefreshableView instanceof GlowListener) {
			((GlowListener) mRefreshableView).onGlow(mOffsetGlow);
		}
	}

	protected void offsetRotation(int y) {
		mOffsetRotation = Math.min(128, y);

		mAnimProxy.setPivotY(0);
		mAnimProxy.setPivotX(getWidth() / 2);

		float degrees = Math.min(32f, mOffsetRotation / 24f);
		mAnimProxy.setRotationX(-degrees);

		float scale = 1f + degrees / 128f;
		//float scale = 1f - degrees / 1024f;
		mAnimProxy.setScaleX(scale);
		mAnimProxy.setScaleY(scale);
	}

	/////////////////////////////////////////////////////////////////////////////
	// State machine

	private void setState(State state, boolean animated) {
		State fromState = mState;
		mState = state;

		Log.e(TAG, "setState " + fromState + " => " + mState);

		switch (state) {
		case REST:
			onRest(fromState, animated);
			break;
		case PULL_TO_REFRESH:
			onPullToRefresh(fromState);
			break;
		case RELEASE_TO_REFRESH:
			onReleaseToRefresh();
			break;
		case REFRESH:
			onRefresh(fromState, animated);
			break;
		}
	}

	/**
	 * Called when the UI needs to be updated to the 'Rest' state
	 */
	protected void onRest(State fromState, boolean isAnimated) {

		if (!isAnimated || mPullDistance == 0) {
			Log.e(TAG, "onRest, not animated");
			offsetGlow(0);
			offsetRotation(0);
			setRefreshingTop(false);
			mHeader.rest();
			return;
		}

		if (fromState == State.REFRESH) {
			mHeader.stopSpinning();

			int starty = Math.max(0, getViewTopOffset());

			stopAnimation();
			offset(starty, 0, 0);
			setRefreshingTop(false);

			animate(starty, 0, new OnTickHandler() {
				@Override
				public void tick(int y) {
					offset(y, 0, 0);
				}

				@Override
				public void done(boolean cancelled) {
					mHeader.rest();
				}
			});
		}
		else {
			animate(mPullDistance, 0, new OnTickHandler() {
				@Override
				public void tick(int y) {
					offset(y, y, y);
				}

				@Override
				public void done(boolean cancelled) {
					mHeader.rest();
				}
			});
		}
	}

	/**
	 * Called when the UI needs to be updated to the 'Pull to Refresh' state
	 */
	protected void onPullToRefresh(State fromState) {
		switch (fromState) {
		case PULL_TO_REFRESH:
			return;
		case REST:
			mHeader.pullToRefresh(false);
			break;
		default:
			mHeader.pullToRefresh(true);
			break;
		}
	}

	/**
	 * Called when the UI needs to be updated to the 'Release to Refresh' state
	 */
	protected void onReleaseToRefresh() {
		mHeader.releaseToRefresh(true);
	}

	/**
	 * Called when the UI needs to be updated to the 'Refreshing' state
	 */
	protected void onRefresh(State fromState, boolean animated) {
		if (null == mOnRefreshListener) {
			onRefreshComplete(animated);
			return;
		}
		mOnRefreshListener.onRefresh(this);

		mHeader.refresh(animated);

		if (animated) {
			// Remove the glow and the tilt
			animate(mPullDistance, 0, new OnTickHandler() {
				@Override
				public void tick(int y) {
					offset(mPullDistance, y, y);
				}

				@Override
				public void done(boolean cancelled) {
					if (!cancelled) {
						offset(0, 0, 0);
						setRefreshingTop(true);
					}
				}
			});
		}
		else {
			offset(0, 0, 0);
			setRefreshingTop(true);
		}
	}

	AnimateRunnable mAnimation;

	private void animate(int from, int to, OnTickHandler handler) {
		if (mAnimation != null && !mAnimation.isStopped()) {
			mAnimation.cancel();
		}
		mAnimation = new AnimateRunnable(this, from, to, handler);
		post(mAnimation);
	}

	private void stopAnimation() {
		if (mAnimation != null && !mAnimation.isStopped()) {
			mAnimation.cancel();
		}
	}

	/////////////////////////////////////////////////////////////////////////////
	// OnRefreshListener interface

	/**
	 * Simple Listener to listen for any callbacks to Refresh.
	 */
	public static interface OnRefreshListener {

		/**
		 * onRefresh will be called for Pull Down from top
		 */
		public void onRefresh(final HoloPullToRefreshLayout refreshView);

	}

	/**
	 * Set OnRefreshListener for the Widget
	 * 
	 * @param listener - Listener to be used when the Widget is set to Refresh
	 */
	public final void setOnRefreshListener(OnRefreshListener listener) {
		mOnRefreshListener = listener;
	}

	/**
	 * Mark the current Refresh as complete. Will Reset the UI and hide the
	 * Refreshing View
	 */
	public final void onRefreshComplete(boolean animated) {
		setState(State.REST, animated);
	}

	/////////////////////////////////////////////////////////////////////////////
	// GlowListener interface

	public static interface GlowListener {
		public void onGlow(int amount);
	}

	/////////////////////////////////////////////////////////////////////////////
	// Refreshable interface

	public static interface Refreshable {
		public void setRefreshingTop(boolean is);

		public void setRefreshingBottom(boolean is);

		public boolean isReadyForPull();

		public int getViewTopOffset();
	}

	private void setRefreshingTop(boolean is) {
		if (mRefreshableView instanceof Refreshable) {
			((Refreshable) mRefreshableView).setRefreshingTop(is);
		}
	}

	private boolean isReadyForPull() {
		if (mRefreshableView instanceof Refreshable) {
			return ((Refreshable) mRefreshableView).isReadyForPull();
		}

		return false;
	}

	private int getViewTopOffset() {
		if (mRefreshableView instanceof Refreshable) {
			return ((Refreshable) mRefreshableView).getViewTopOffset();
		}

		return mOffsetTop;
	}
}
