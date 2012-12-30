package com.dougmelton.holoptr;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;

import com.dougmelton.holoptr.AnimateRunnable.OnTickHandler;

public class HoloPullToRefreshLayout extends FrameLayout {
	private static final String TAG = HoloPullToRefreshLayout.class.getSimpleName();

	//	private static final float FRICTION = 2.5f;

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
	private View mShadow;

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
		mHeaderHeight = context.getResources().getDimensionPixelSize(R.dimen.hptr_header_height);

		TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.HoloPullToRefresh);
		mRefreshableViewResId = a.getResourceId(R.styleable.HoloPullToRefresh_refreshableView, View.NO_ID);

		mHeader = new HoloPullToRefreshHeaderView(context, attrs);
		FrameLayout.LayoutParams lpHeader = generateDefaultLayoutParams();
		lpHeader.height = mHeaderHeight;
		mHeader.setLayoutParams(lpHeader);
		addView(mHeader, 0);

		mShadow = new View(context, attrs);
		mShadow.setBackgroundColor(Color.TRANSPARENT);
		FrameLayout.LayoutParams lpShadow = generateDefaultLayoutParams();
		lpShadow.height = mHeaderHeight;
		mShadow.setLayoutParams(lpShadow);
		mShadow.setBackgroundResource(R.drawable.hptr_shadow_top);
		addView(mShadow, 1);

		ViewTreeObserver.OnGlobalLayoutListener listener = new ViewTreeObserver.OnGlobalLayoutListener() {
			public void onGlobalLayout() {
				getViewTreeObserver().removeGlobalOnLayoutListener(this);
				setRefreshableView(mRefreshableViewResId);
				setState(State.REST, false);
			}
		};
		getViewTreeObserver().addOnGlobalLayoutListener(listener);
	}

	/////////////////////////////////////////////////////////////////////////////
	// Properties

	private int mRefreshableViewResId;
	private View mRefreshableView;

	public void setRefreshableView(int resId) {
		if (resId == View.NO_ID) {
			setRefreshableView(getChildAt(getChildCount() - 1));
		}
		else {
			setRefreshableView(findViewById(resId));
		}
	}

	public void setRefreshableView(View refreshableView) {
		mRefreshableView = refreshableView;
		if (AnimatorProxy.NEEDS_PROXY) {
			mAnimProxy = AnimatorProxy.wrap(mRefreshableView);
		}
	}

	public View getRefreshableView() {
		return mRefreshableView;
	}

	/////////////////////////////////////////////////////////////////////////////
	// Touch Events

	@Override
	public final boolean onInterceptTouchEvent(MotionEvent event) {
		final int action = event.getAction();

		switch (action) {

		case MotionEvent.ACTION_DOWN: {
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
						mPullDistance = Math.max(0, (int) augmentedPullDistance(mLastMotionY - mInitialMotionY));
						setState(State.PULL_TO_REFRESH, true);
						return true;
					}
				}
			}

			return false;
		}

		case MotionEvent.ACTION_CANCEL:
		case MotionEvent.ACTION_UP: {
			return mState != State.REST;
		}
		}

		return false;
	}

	// This calculates a springiness to the pull
	private float augmentedPullDistance(float pixels) {
		//		float augmented1 = pixels / FRICTION;
		//		float augmented2 = (pixels * 650f) / (pixels + 1500f);
		float augmented2 = (pixels * 400f) / (pixels + 1000f);
		//		Log.e(TAG, pixels + " => " + augmented1 + " vs " + augmented2);
		return augmented2;
	}

	@Override
	public final boolean onTouchEvent(MotionEvent event) {

		// If we're refreshing, eat the event
		if (mState == State.REFRESH) {
			return !mTouchDuringRefresh;
		}

		switch (event.getAction()) {
		case MotionEvent.ACTION_DOWN: {
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
			if (mState != State.REST) {
				mLastMotionY = event.getY();
				mPullDistance = Math.max(0, (int) augmentedPullDistance(mLastMotionY - mInitialMotionY));
				pullEvent();
				return true;
			}
			break;
		}

		case MotionEvent.ACTION_CANCEL:
		case MotionEvent.ACTION_UP: {
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
		offset(mPullDistance, mPullDistance, mPullDistance);

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
		offsetGlow(glow);
		offsetTop(top);
		offsetSkew(rotation);
		offsetShadow(top, rotation);
	}

	protected final void offsetShadow(int top, int rotation) {
		top = Math.min(mHeaderHeight, top);
		mShadow.setTranslationY(top - mShadow.getHeight());
	}

	protected final void offsetGlow(int y) {
		mOffsetGlow = y;

		if (mRefreshableView instanceof GlowListener) {
			((GlowListener) mRefreshableView).onGlow(mOffsetGlow);
		}
	}

	protected final void offsetTop(int y) {
		mOffsetTop = Math.min(mHeaderHeight, y);

		if (AnimatorProxy.NEEDS_PROXY) {
			if (mAnimProxy == null) {
				Log.e(TAG, "mAnimProxy is null");
				return;
			}
			mAnimProxy.setTranslationY(mOffsetTop);
		}
		else {
			mRefreshableView.setTranslationY(mOffsetTop);
		}
	}

	protected void offsetSkew(int y) {
		mOffsetRotation = Math.min(mHeaderHeight, y);
		float degrees = Math.min(32f, mOffsetRotation / 24f);
		float scale = 1f + degrees / 128f;
		//float scale = 1f - degrees / 1024f;

		if (AnimatorProxy.NEEDS_PROXY) {
			if (mAnimProxy == null) {
				Log.e(TAG, "mAnimProxy is null");
				return;
			}
			mAnimProxy.setPivotY(0);
			mAnimProxy.setPivotX(getWidth() / 2);
			mAnimProxy.setRotationX(-degrees);
			mAnimProxy.setScaleX(scale);
			mAnimProxy.setScaleY(scale);
		}
		else {
			mRefreshableView.setPivotY(0);
			mRefreshableView.setPivotX(getWidth() / 2);
			mRefreshableView.setRotationX(-degrees);
			mRefreshableView.setScaleX(scale);
			mRefreshableView.setScaleY(scale);
		}
	}

	/////////////////////////////////////////////////////////////////////////////
	// State machine

	private Queue<StateTransition> mStateQueue = null;

	private static class StateTransition {
		public State state;
		public boolean animated;

		public StateTransition(State state, boolean animated) {
			this.state = state;
			this.animated = animated;
		}
	}

	private void dequeueState() {
		if (mStateQueue == null || mStateQueue.isEmpty()) {
			return;
		}
		post(new Runnable() {
			@Override
			public void run() {
				StateTransition transition = mStateQueue.poll();
				setState(transition.state, transition.animated);
			}
		});
	}

	private void setState(State state, boolean animated) {
		// If an animation is already going, queue up the next one
		if (mAnimation != null && !mAnimation.isStopped()) {
			if (mStateQueue == null) {
				mStateQueue = new ConcurrentLinkedQueue<StateTransition>();
			}
			mStateQueue.add(new StateTransition(state, animated));
			return;
		}

		State fromState = mState;
		mState = state;

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
			//TODO: we didn't used to set offsetTop(0) here... for some reason
			offset(0, 0, 0);
			setRefreshingTop(false);
			mHeader.rest();
			return;
		}

		if (fromState == State.REFRESH) {
			mHeader.stopSpinning();

			int starty = Math.max(0, getViewTopOffset());
			offset(starty, 0, 0);
			setRefreshingTop(false);

			animate(starty, 0, isAnimated, new OnTickHandler() {
				@Override
				public void tick(int y) {
					offset(y, 0, 0);
				}

				@Override
				public void done() {
					mHeader.rest();
					dequeueState();
				}
			});
		}
		else {
			animate(mPullDistance, 0, isAnimated, new OnTickHandler() {
				@Override
				public void tick(int y) {
					offset(y, y, y);
				}

				@Override
				public void done() {
					mHeader.rest();
					dequeueState();
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

		// Remove the glow and the tilt
		animate(mPullDistance, 0, animated, new OnTickHandler() {
			@Override
			public void tick(int y) {
				offset(mPullDistance, y, y);
			}

			@Override
			public void done() {
				if (mRefreshableView instanceof Refreshable) {
					offset(0, 0, 0);
				}
				setRefreshingTop(true);
				dequeueState();
			}
		});
	}

	AnimateRunnable mAnimation;

	private void animate(int from, int to, boolean animated, OnTickHandler handler) {
		if (animated) {
			mAnimation = new AnimateRunnable(this, from, to, handler);
			post(mAnimation);
		}
		else {
			handler.done();
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
		if (mRefreshableView == null) {
			return false;
		}
		if (mRefreshableView instanceof Refreshable) {
			return ((Refreshable) mRefreshableView).isReadyForPull();
		}

		return mRefreshableView.getScrollY() == 0;
	}

	private int getViewTopOffset() {
		if (mRefreshableView instanceof Refreshable) {
			return ((Refreshable) mRefreshableView).getViewTopOffset();
		}

		return mOffsetTop;
	}
}
