package com.way.weather.wxapi;

import android.os.Bundle;

import com.baidu.sharesdk.weixin.WXEventHandlerActivity;

/**
 * 处理微信回调
 * 
 */
public class WXEntryActivity extends WXEventHandlerActivity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		finish();
	}

}
