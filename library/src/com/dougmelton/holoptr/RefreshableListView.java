package com.dougmelton.holoptr;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.Adapter;
import android.widget.FrameLayout;
import android.widget.ListView;

import com.dougmelton.holoptr.HoloPullToRefreshLayout.GlowListener;
import com.dougmelton.holoptr.HoloPullToRefreshLayout.Refreshable;

public class RefreshableListView extends ListView implements GlowListener, Refreshable, OnScrollListener {
	private static final String TAG = RefreshableListView.class.getSimpleName();

	private int mGlowAmount;
	private Drawable mGlowDrawable;
	private Drawable mEdgeDrawable;
	private int mHeaderHeight;

	private View mHeaderView;
	private View mFooterView;

	private OnScrollListener mOnScrollListener;

	public RefreshableListView(Context context) {
		super(context);
		init(context);
	}

	public RefreshableListView(Context context, AttributeSet attrs) {
		super(context, attrs);
		init(context);
	}

	public RefreshableListView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		init(context);
	}

	private void init(Context context) {
		Resources res = getResources();

		mGlowDrawable = res.getDrawable(R.drawable.overscroll_glow);
		mEdgeDrawable = res.getDrawable(R.drawable.overscroll_edge);
		mHeaderHeight = res.getDimensionPixelSize(R.dimen.header_height);

		FrameLayout headerLayout = new FrameLayout(context);
		mHeaderView = new View(context);
		headerLayout.addView(mHeaderView, FrameLayout.LayoutParams.MATCH_PARENT, mHeaderHeight);

		FrameLayout footerLayout = new FrameLayout(context);
		mFooterView = new View(context);
		footerLayout.addView(mFooterView, FrameLayout.LayoutParams.MATCH_PARENT, mHeaderHeight);

		addHeaderView(headerLayout, null, false);
		addFooterView(footerLayout, null, false);

		super.setOnScrollListener(this);

		setRefreshingTop(false);
		setRefreshingBottom(false);
	}

	@Override
	protected void dispatchDraw(Canvas canvas) {
		super.dispatchDraw(canvas);

		if (mGlowAmount > 0) {
			mGlowDrawable.setBounds(0, 0, getWidth(), mGlowAmount / 2);
			mGlowDrawable.draw(canvas);

			mEdgeDrawable.setBounds(0, 0, getWidth(), Math.min(mGlowAmount, mEdgeDrawable.getIntrinsicHeight()));
			mEdgeDrawable.setAlpha(Math.min(mGlowAmount, 255));
			mEdgeDrawable.draw(canvas);
		}
	}

	/////////////////////////////////////////////////////////////////////////////
	// OnScrollListener

	@Override
	public void setOnScrollListener(OnScrollListener l) {
		mOnScrollListener = l;
	}

	@Override
	public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
		// Dynamically set the overscroll mode
		if (mHeaderView.getVisibility() == View.VISIBLE && firstVisibleItem == 0) {
			setOverScrollMode(OVER_SCROLL_NEVER);
		}
		else if (mFooterView.getVisibility() == View.VISIBLE && firstVisibleItem + visibleItemCount == totalItemCount) {
			setOverScrollMode(OVER_SCROLL_NEVER);
		}
		else {
			setOverScrollMode(OVER_SCROLL_ALWAYS);
		}

		if (mOnScrollListener != null) {
			mOnScrollListener.onScroll(view, firstVisibleItem, visibleItemCount, totalItemCount);
		}
	}

	@Override
	public void onScrollStateChanged(AbsListView view, int scrollState) {
		if (mOnScrollListener != null) {
			mOnScrollListener.onScrollStateChanged(view, scrollState);
		}
	}

	/////////////////////////////////////////////////////////////////////////////
	// Refreshable

	@Override
	public void setRefreshingTop(boolean is) {
		mHeaderView.setVisibility(is ? View.VISIBLE : View.GONE);
		if (getFirstVisiblePosition() == 0) {
			setSelectionFromTop(0, 0);
		}
	}

	@Override
	public void setRefreshingBottom(boolean is) {
		mFooterView.setVisibility(is ? View.VISIBLE : View.GONE);
	}

	@Override
	public boolean isReadyForPull() {
		final Adapter adapter = getAdapter();

		if (null == adapter || adapter.isEmpty()) {
			Log.d(TAG, "isFirstItemVisible. Empty View.");
			return true;
		}
		else if (getFirstVisiblePosition() == 0) {
			final View firstVisibleChild = getChildAt(0);
			if (firstVisibleChild != null) {
				return firstVisibleChild.getTop() >= getTop();
			}
		}

		return false;
	}

	@Override
	public int getViewTopOffset() {
		if (getFirstVisiblePosition() == 0) {
			View v = getChildAt(1);
			return v == null ? 0 : v.getTop() - getDividerHeight();
		}
		else {
			return -1;
		}
	}

	/////////////////////////////////////////////////////////////////////////////
	// GlowListener

	public void onGlow(int amount) {
		this.mGlowAmount = Math.min(getMeasuredHeight(), amount);
		setOverScrollMode(amount > 0 ? AbsListView.OVER_SCROLL_NEVER : AbsListView.OVER_SCROLL_IF_CONTENT_SCROLLS);
		invalidate();
	}
}
