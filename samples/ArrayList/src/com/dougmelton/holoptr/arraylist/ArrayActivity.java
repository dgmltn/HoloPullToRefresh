package com.dougmelton.holoptr.arraylist;

import java.util.Arrays;
import java.util.LinkedList;

import android.app.ListActivity;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.ArrayAdapter;

import com.dougmelton.holoptr.HoloPullToRefreshLayout;
import com.dougmelton.holoptr.HoloPullToRefreshLayout.OnRefreshListener;
import com.dougmelton.holoptr.R;
import com.dougmelton.holoptr.RefreshableListView;

public class ArrayActivity extends ListActivity implements OnScrollListener {
	private static final String TAG = ArrayActivity.class.getSimpleName();

	private LinkedList<String> mListItems;
	private ArrayAdapter<String> mAdapter;
	private HoloPullToRefreshLayout mPtrLayout;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_array);

		mPtrLayout = (HoloPullToRefreshLayout) findViewById(R.id.ptr_layout);
		mPtrLayout.setOnRefreshListener(new OnRefreshListener() {
			@Override
			public void onRefresh(HoloPullToRefreshLayout refreshView) {
				new PretendPullToRefreshTask().execute();
			}
		});

		RefreshableListView lv = (RefreshableListView) findViewById(android.R.id.list);
		lv.setRefreshingBottom(true);
		lv.setOnScrollListener(this);
		spin();

		mListItems = new LinkedList<String>();
		mListItems.addAll(Arrays.asList(mStrings));

		mAdapter = new ArrayAdapter<String>(this, R.layout.list_item, mListItems);
		setListAdapter(mAdapter);
	}

	// Make the icon rotate (even if animated == false)
	private void spin() {
		final View spinner = findViewById(R.id.refresh_icon);
		spinner.post(new Runnable() {
			@Override
			public void run() {
				final Animation rotate = new RotateAnimation(0, 360, spinner.getWidth() / 2,
						spinner.getHeight() / 2);
				rotate.setRepeatCount(Animation.INFINITE);
				rotate.setInterpolator(new LinearInterpolator());
				rotate.setDuration(1000);
				spinner.startAnimation(rotate);
			}
		});
	}

	private String[] mStrings = {
			"Basking",
			"Bigeye Thresher",
			"Blue",
			"Bluntnose Sixgill",
			"Common Thresher",
			"Dusky",
			"Great Hammerhead",
			"Great White",
			"Greenland",
			"Goblin",
			"Humpback Cat",
			"Indian Sand Tiger",
			"Longfin Mako",
			"Megamouth",
			"Nurse",
			"Oceanic Whitetip",
			"Pacific Sleeper",
			"Pelagic Thresher",
			"Prickly",
			"Scalloped Hammerhead",
			"Shortfin Mako",
			"Smalltooth Sand Tiger",
			"Smooth Hammerhead",
			"Tiger",
			"Whale"
	};

	//////////////////////////////////////////////////////////////////////////
	// Pull-to-refresh on top of list

	private int mTopIndex = 0;
	
	private class PretendPullToRefreshTask extends AsyncTask<Void, Void, Void> {

		protected PretendPullToRefreshTask() {
		}

		@Override
		protected Void doInBackground(Void... params) {
			try {
				Thread.sleep(4000);
			}
			catch (InterruptedException e) {
				e.printStackTrace();
			}
			return null;
		}

		@Override
		protected void onPostExecute(Void result) {
			mTopIndex = (mTopIndex + mStrings.length - 1) % mStrings.length;
			mAdapter.insert(mStrings[mTopIndex], 0);
			mPtrLayout.onRefreshComplete(true);
		}
	}

	//////////////////////////////////////////////////////////////////////////
	// Slide-to-refresh on bottom of list
	
	private int mBotIndex = -1;
	private boolean mIsBotRefreshing = false;

	@Override
	public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
		boolean loadMore = firstVisibleItem + visibleItemCount + 1 >= totalItemCount;
		if (loadMore && !mIsBotRefreshing) {
			new PretendSlideToRefreshTask().execute();
		}
	}

	@Override
	public void onScrollStateChanged(AbsListView view, int scrollState) {
		// unused
	}
	
	private class PretendSlideToRefreshTask extends AsyncTask<Void, Void, Void> {

		protected PretendSlideToRefreshTask() {
		}

		@Override
		protected Void doInBackground(Void... params) {
			try {
				mIsBotRefreshing = true;
				Thread.sleep(1000);
			}
			catch (InterruptedException e) {
				e.printStackTrace();
			}
			return null;
		}

		@Override
		protected void onPostExecute(Void result) {
			mBotIndex = (mBotIndex + 1) % mStrings.length;
			mAdapter.add(mStrings[mBotIndex]);
			mBotIndex = (mBotIndex + 1) % mStrings.length;
			mAdapter.add(mStrings[mBotIndex]);
			mIsBotRefreshing = false;
		}
	}

}
