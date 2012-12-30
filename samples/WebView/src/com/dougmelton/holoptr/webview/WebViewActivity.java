package com.dougmelton.holoptr.webview;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebView;

import com.dougmelton.holoptr.HoloPullToRefreshLayout;
import com.dougmelton.holoptr.HoloPullToRefreshLayout.OnRefreshListener;
import com.dougmelton.holoptr.R;

public class WebViewActivity extends Activity implements OnRefreshListener {
	private static final String TAG = WebViewActivity.class.getSimpleName();

	private HoloPullToRefreshLayout mPtrLayout;
	private WebView mBrowser;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_webview);

		mPtrLayout = (HoloPullToRefreshLayout) findViewById(R.id.ptr_layout);
		mPtrLayout.setOnRefreshListener(this);

		mBrowser = (WebView) findViewById(R.id.browser);
		mBrowser.loadUrl("http://www.google.com/finance?cid=694653");
	}

	@Override
	public void onRefresh(HoloPullToRefreshLayout refreshView) {
		mBrowser.reload();
	}

}
