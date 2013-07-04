package com.dougmelton.holoptr;

import java.util.ArrayList;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.Adapter;
import android.widget.FrameLayout;
import android.widget.HeaderViewListAdapter;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.ListView;

import com.dougmelton.holoptr.HoloPullToRefreshLayout.GlowListener;
import com.dougmelton.holoptr.HoloPullToRefreshLayout.Refreshable;

public class RefreshableListView extends ListView implements GlowListener, Refreshable, OnScrollListener {
	private static final String TAG = RefreshableListView.class.getSimpleName();

	private int mGlowAmount;
	private Drawable mGlowDrawable;
	private Drawable mEdgeDrawable;
	private int mHeaderHeight;

	private FrameLayout mHeaderLayout;
	private FrameLayout mFooterLayout;
	private View mHeaderView;
	private View mFooterView;
	private HeaderViewListAdapter mWrapper;

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

		mGlowDrawable = res.getDrawable(R.drawable.hptr_overscroll_glow);
		mEdgeDrawable = res.getDrawable(R.drawable.hptr_overscroll_edge);
		mHeaderHeight = res.getDimensionPixelSize(R.dimen.hptr_header_height);

		mHeaderLayout = new FrameLayout(context);
		mHeaderView = new View(context);
		mHeaderView.setBackgroundResource(R.drawable.hptr_shadow_top);
		mHeaderLayout.addView(mHeaderView, FrameLayout.LayoutParams.MATCH_PARENT, mHeaderHeight);

		mFooterLayout = new FrameLayout(context);
		mFooterView = new View(context);
		mFooterView.setBackgroundResource(R.drawable.hptr_shadow_bottom);
		mFooterLayout.addView(mFooterView, FrameLayout.LayoutParams.MATCH_PARENT, mHeaderHeight);

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

	@Override
	public void setAdapter(ListAdapter adapter) {
		ListView.FixedViewInfo headerInfo = this.new FixedViewInfo();
		ListView.FixedViewInfo footerInfo = this.new FixedViewInfo();

		headerInfo.view = mHeaderLayout;
		headerInfo.isSelectable = false;

		footerInfo.view = mFooterLayout;
		footerInfo.isSelectable = false;

		ArrayList<ListView.FixedViewInfo> headers = new ArrayList<ListView.FixedViewInfo>(1);
		ArrayList<ListView.FixedViewInfo> footers = new ArrayList<ListView.FixedViewInfo>(1);

		headers.add(headerInfo);
		footers.add(footerInfo);

		mWrapper = new HeaderViewListAdapter(headers, footers, adapter) {
			@Override
			public boolean areAllItemsEnabled() {
				return true;
			}
		};

		super.setAdapter(mWrapper);
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
