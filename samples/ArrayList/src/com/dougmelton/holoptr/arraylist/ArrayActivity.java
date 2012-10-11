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
import android.widget.ArrayAdapter;

import com.dougmelton.holoptr.HoloPullToRefreshLayout;
import com.dougmelton.holoptr.HoloPullToRefreshLayout.OnRefreshListener;
import com.dougmelton.holoptr.R;
import com.dougmelton.holoptr.RefreshableListView;

public class ArrayActivity extends ListActivity {
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
				new PretendRefreshTask().execute();
			}
		});

		RefreshableListView lv = (RefreshableListView) findViewById(android.R.id.list);
		lv.setRefreshingBottom(true);
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

	private class PretendRefreshTask extends AsyncTask<Void, Void, Void> {

		protected PretendRefreshTask() {
		}

		@Override
		protected Void doInBackground(Void... params) {
			try {
				Thread.sleep(100);
			}
			catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return null;
		}

		@Override
		protected void onPostExecute(Void result) {
			mPtrLayout.onRefreshComplete(true);
		}
	}

}
