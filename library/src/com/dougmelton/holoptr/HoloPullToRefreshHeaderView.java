package com.dougmelton.holoptr;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import android.content.Context;
import android.content.res.Resources.NotFoundException;
import android.graphics.Color;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.dougmelton.holoptr.AnimateRunnable.OnTickHandler;

public class HoloPullToRefreshHeaderView extends FrameLayout {
	private static final String TAG = HoloPullToRefreshHeaderView.class.getSimpleName();

	private int mNaturalHeight;

	private ImageView mSpinner;
	private TextView mPullToRefresh;
	private TextView mReleaseToRefresh;

	private int mInstructionTranslationX = 0;
	private int mSpinnerTranslationX = 0;

	public HoloPullToRefreshHeaderView(Context context) {
		this(context, null);
	}

	public HoloPullToRefreshHeaderView(Context context, AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public HoloPullToRefreshHeaderView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);

		mNaturalHeight = getResources().getDimensionPixelSize(R.dimen.hptr_header_height);

		// Refresh spinner image
		mSpinner = new ImageView(context);
		mSpinner.setImageResource(R.drawable.hptr_ic_refresh);
		mSpinner.setVisibility(View.INVISIBLE);
		this.addView(mSpinner);

		// "Pull to refresh."
		final Typeface type = getFontFromRes(R.raw.roboto_light);
		mPullToRefresh = new TextView(context);
		mPullToRefresh.setText(R.string.hptr_Pull_to_refresh);
		mPullToRefresh.setTypeface(type);
		mPullToRefresh.setTextSize(24);
		mPullToRefresh.setTextColor(Color.BLACK);
		mPullToRefresh.setGravity(Gravity.CENTER_VERTICAL);
		mPullToRefresh.setVisibility(View.INVISIBLE);
		this.addView(mPullToRefresh);

		// "Release to refresh."
		mReleaseToRefresh = new TextView(context);
		mReleaseToRefresh.setText(R.string.hptr_Release_to_refresh);
		mReleaseToRefresh.setTextSize(24);
		mReleaseToRefresh.setTypeface(type);
		mReleaseToRefresh.setTextColor(Color.BLACK);
		mReleaseToRefresh.setGravity(Gravity.CENTER_VERTICAL);
		mReleaseToRefresh.setVisibility(View.INVISIBLE);
		this.addView(mReleaseToRefresh);

		rest();
	}

	/**
	 * Returns a set of layout parameters with a width of
	 * {@link android.view.ViewGroup.LayoutParams#WRAP_CONTENT},
	 * and a height of {@link android.view.ViewGroup.LayoutParams#WRAP_CONTENT}.
	 */
	@Override
	protected LayoutParams generateDefaultLayoutParams() {
		LayoutParams params = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT);
		return params;
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		int parentWidth = MeasureSpec.getSize(widthMeasureSpec);

		setMeasuredDimension(parentWidth, mNaturalHeight);

		int count = this.getChildCount();
		for (int i = 0; i < count; i++) {
			final View child = getChildAt(i);
			final MarginLayoutParams lp = (MarginLayoutParams) child.getLayoutParams();
			int childWidthMeasureSpec = getChildMeasureSpec(widthMeasureSpec, 0, lp.width);
			int childHeightMeasureSpec = getChildMeasureSpec(heightMeasureSpec, 0, lp.height);
			child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
		}
	}

	/**
	 * Perform custom layout. The two texts, "pull to refresh" and "release to refresh" should
	 * be overlayed, with the same left, top, and bottom. The spinner should be to the left
	 * of those and the whole thing should be centered.
	 */
	@Override
	protected void onLayout(boolean changed, int left, int top, int right, int bottom) {

		// Find width of (pull to refresh) union (release to refresh)
		int textWidth = Math.max(mPullToRefresh.getMeasuredWidth(), mReleaseToRefresh.getMeasuredWidth());
		int spinnerWidth = mSpinner.getMeasuredWidth();
		int padding = (int) (getResources().getDisplayMetrics().density * 10);

		int spinnerLeft = (getMeasuredWidth() - textWidth - padding - spinnerWidth) / 2;

		int textLeft = spinnerLeft + spinnerWidth + padding;

		spinnerLeft += mSpinnerTranslationX;
		mSpinner.layout(spinnerLeft, top, spinnerLeft + spinnerWidth, bottom);

		textLeft += mInstructionTranslationX;
		mPullToRefresh.layout(textLeft, top, textLeft + textWidth, bottom);
		mReleaseToRefresh.layout(textLeft, top, textLeft + textWidth, bottom);
	}

	public void stopSpinning() {
		mSpinner.clearAnimation();
	}

	public void rest() {
		mInstructionTranslationX = 0;
		mSpinnerTranslationX = 0;
		mSpinner.clearAnimation();
		mReleaseToRefresh.clearAnimation();
		mSpinner.setVisibility(View.INVISIBLE);
		mPullToRefresh.setVisibility(View.INVISIBLE);
		mReleaseToRefresh.setVisibility(View.INVISIBLE);
		requestLayout();
	}

	public void pullToRefresh(boolean animated) {
		mSpinner.setVisibility(View.VISIBLE);
		stopSpinning();
		requestLayout();
		if (animated) {
			crossfade(mReleaseToRefresh, mPullToRefresh);
		}
		else {
			mPullToRefresh.setVisibility(View.VISIBLE);
			mReleaseToRefresh.setVisibility(View.INVISIBLE);
		}
	}

	public void releaseToRefresh(boolean animated) {
		mSpinner.setVisibility(View.VISIBLE);
		requestLayout();
		if (animated) {
			crossfade(mPullToRefresh, mReleaseToRefresh);
		}
		else {
			mReleaseToRefresh.setVisibility(View.VISIBLE);
			mPullToRefresh.setVisibility(View.INVISIBLE);
		}
	}

	public void refresh(boolean animated) {
		mSpinner.setVisibility(View.VISIBLE);

		final int instructionEndX = getWidth() - mReleaseToRefresh.getLeft();
		final int spinnerEndX = (getWidth() + mSpinner.getWidth()) / 2 - mSpinner.getRight();

		// Slide "release to refresh" off the screen
		// Slide refresh icon to the middle

		animate(0, 1000, animated, new OnTickHandler() {
			@Override
			public void tick(int y) {
				mInstructionTranslationX = y * instructionEndX / 1000;
				mSpinnerTranslationX = y * spinnerEndX / 1000;
				requestLayout();
			}

			@Override
			public void done() {
				mInstructionTranslationX = instructionEndX;
				mSpinnerTranslationX = spinnerEndX;
				mPullToRefresh.setVisibility(View.INVISIBLE);
				mReleaseToRefresh.setVisibility(View.INVISIBLE);
				spin();
				requestLayout();
			}
		});

	}

	private void animate(int from, int to, boolean animated, OnTickHandler handler) {
		if (animated) {
			post(new AnimateRunnable(this, from, to, handler));
		}
		else {
			handler.done();
		}

	}

	// Make the icon rotate (even if animated == false)
	private void spin() {
		final Animation rotate = new RotateAnimation(0, 360, mSpinner.getWidth() / 2,
				mSpinner.getHeight() / 2);
		rotate.setRepeatCount(Animation.INFINITE);
		rotate.setInterpolator(new LinearInterpolator());
		rotate.setDuration(1000);
		mSpinner.startAnimation(rotate);
	}

	private void crossfade(View outView, View inView) {
		inView.setVisibility(View.VISIBLE);
		outView.setVisibility(View.INVISIBLE);

		final Animation out = new AlphaAnimation(1.0f, 0.0f);
		out.setDuration(200);
		final Animation in = new AlphaAnimation(0.0f, 1.0f);
		in.setDuration(200);
		inView.startAnimation(in);
		outView.startAnimation(out);
	}

	private Typeface getFontFromRes(int resource) {
		Typeface tf = null;
		InputStream is = null;
		try {
			is = getResources().openRawResource(resource);
		}
		catch (NotFoundException e) {
			Log.e(TAG, "Could not find font in resources!");
		}

		String outPath = getContext().getCacheDir() + "/tmp.raw";

		try {
			byte[] buffer = new byte[is.available()];
			BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(outPath));

			int l = 0;
			while ((l = is.read(buffer)) > 0)
				bos.write(buffer, 0, l);

			bos.close();

			tf = Typeface.createFromFile(outPath);

			// clean up
			new File(outPath).delete();
		}
		catch (IOException e) {
			Log.e(TAG, "Error reading in font!");
			return null;
		}

		Log.d(TAG, "Successfully loaded font.");

		return tf;
	}
}
