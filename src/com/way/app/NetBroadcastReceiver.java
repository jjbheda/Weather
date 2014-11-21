package com.way.app;

import java.util.ArrayList;

import com.way.util.NetUtil;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class NetBroadcastReceiver extends BroadcastReceiver {
	public static ArrayList<EventHandler> mListeners = new ArrayList<EventHandler>();
	private static String NET_CHANGE_ACTION = "android.net.conn.CONNECTIVITY_CHANGE";
	@Override
	public void onReceive(Context context, Intent intent) {
		if (intent.getAction().equals(NET_CHANGE_ACTION)) {
			Application.mNetWorkState = NetUtil.getNetworkState(context);
			if (mListeners.size() > 0)// 通知接口完成加载
				for (EventHandler handler : mListeners) {
					handler.onNetChange();
				}
		}
	}

	public static abstract interface EventHandler {

		public abstract void onNetChange();
	}
}
