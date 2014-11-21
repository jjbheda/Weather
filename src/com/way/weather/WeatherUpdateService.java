package com.way.weather;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.app.PendingIntent;
import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.IBinder;
import android.text.TextUtils;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.Toast;

import com.way.app.Application;
import com.way.bean.City;
import com.way.bean.WeatherInfo;
import com.way.db.CityDB;
import com.way.util.GetWeatherTask;
import com.way.util.L;
import com.way.util.Lunar;
import com.way.util.SharePreferenceUtil;
import com.way.util.T;
import com.way.util.TimeUtil;

public class WeatherUpdateService extends Service {
	private static final int TIME_FORMAT_24 = 0;
	private static final int TIME_FORMAT_AM = 1;
	private static final int TIME_FORMAT_PM = 2;

	private RemoteViews remoteViews;
	private int[] timesImg = { R.drawable.nw0, R.drawable.nw1, R.drawable.nw2,
			R.drawable.nw3, R.drawable.nw4, R.drawable.nw5, R.drawable.nw6,
			R.drawable.nw7, R.drawable.nw8, R.drawable.nw9, };
	private int[] dateViews = { R.id.h_left, R.id.h_right, R.id.m_left,
			R.id.m_right };
	private Application mApplication;
	private ActivityManager mActivityManager;
	private String mPackageName;
	private SharePreferenceUtil mSpUtil;
	private CityDB mCityDB;

	Handler mHandler = new Handler() {
		public void handleMessage(android.os.Message msg) {
			switch (msg.what) {
			case MainActivity.GET_WEATHER_SCUESS:
				WeatherInfo allWeather = mApplication.getAllWeather();
				if (allWeather != null) {
					if (!TextUtils.isEmpty(allWeather.getFeelTemp())) {
						mSpUtil.setSimpleTemp(allWeather.getFeelTemp()
								.replace("~", "/").replace("℃", "°"));// 保存一下温度信息，用于小插件
					} else {
						mSpUtil.setSimpleTemp(allWeather.getTemp0()
								.replace("~", "/").replace("℃", "°"));
					}
					mSpUtil.setSimpleClimate(allWeather.getWeather0());
					String time = allWeather.getIntime();
					mSpUtil.setTimeSamp(TimeUtil.getLongTime(time));
					updateWeather();
					T.show(mApplication, "天气更新成功", Toast.LENGTH_SHORT);
				}
				break;
			case MainActivity.GET_WEATHER_FAIL:

				break;
			default:
				break;
			}
		}
	};

	@Override
	public IBinder onBind(Intent intent) {
		// do nothing
		return null;
	}

	// 广播接收者去接收系统每分钟的提示广播，来更新时间
	private BroadcastReceiver mTimePickerBroadcast = new BroadcastReceiver() {

		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			if (action.equals(MainActivity.UPDATE_WIDGET_WEATHER_ACTION)) {
				// L.i("updateWeather.........");
				updateWeather();
			} else {
				updateTime();
			}
		}
	};

	@Override
	public void onCreate() {
		super.onCreate();
		mApplication = Application.getInstance();
		mActivityManager = ((ActivityManager) getSystemService(Context.ACTIVITY_SERVICE));
		mPackageName = getPackageName();
		mSpUtil = mApplication.getSharePreferenceUtil();
		mCityDB = mApplication.getCityDB();
		remoteViews = new RemoteViews(getApplication().getPackageName(),
				R.layout.widget_4x2);// 实例化RemoteViews
		PendingIntent WeatherIconHotAreaPI = PendingIntent.getActivity(this, 0,
				new Intent(this, MainActivity.class), 0);
		remoteViews.setOnClickPendingIntent(R.id.WeatherIconHotArea,
				WeatherIconHotAreaPI);
		// 左边热区点击广播
		Intent active = new Intent(getApplicationContext(), WeatherWidget.class);
		active.setAction(WeatherWidget.TEXTINFO_LEFT_HOTAREA_ACTION);
		PendingIntent TextInfoLeftHotArea = PendingIntent.getBroadcast(this, 1,
				active, 1);
		remoteViews.setOnClickPendingIntent(R.id.TextInfoLeftHotArea,
				TextInfoLeftHotArea);
		registerReceiver();// 注册广播

	}

	private void updateTime() {
		int timeFormat = getTimeFormat();
		// 定义SimpleDateFormat对象
		SimpleDateFormat df = new SimpleDateFormat("hhmm");
		if (timeFormat == TIME_FORMAT_24) {
			df = new SimpleDateFormat("HHmm");
		}
		// 将当前时间格式化成HHmm的形式
		String timeStr = df.format(new Date(System.currentTimeMillis()));
		for (int i = 0; i < timeStr.length(); i++) {
			// 将第i个数字字符转换为对应的数字
			int num2 = Integer.parseInt(timeStr.substring(i, i + 1));
			// 将第i个图片的设为对应的数字图片
			// Log.i("WeatherWidget", "时间：" + num2);
			remoteViews.setImageViewResource(dateViews[i], timesImg[num2]);
		}
		if (timeFormat == 1) {
			remoteViews.setImageViewResource(R.id.am_pm, R.drawable.w_amw);
		} else if (timeFormat == 2) {
			remoteViews.setImageViewResource(R.id.am_pm, R.drawable.w_pmw);
		} else {
			remoteViews.setImageViewResource(R.id.am_pm,
					R.drawable.switch_camera_hide);
		}
		// remoteViews.setTextViewText(R.id.weather_icon_left, "深圳" + "\r\n"
		// + "阵雨 30");
		// remoteViews.setTextViewText(R.id.weather_icon_right, "07/02 周二"
		// + "\r\n" + "五月二五");
		remoteViews.setTextViewText(R.id.weather_icon_right,
				TimeUtil.getZhouWeek() + "\r\n" + Lunar.getDay());
		remoteViews.setViewVisibility(R.id.TextViewMessage, View.GONE);
		ComponentName componentName = new ComponentName(getApplication(),
				WeatherWidget.class);
		AppWidgetManager.getInstance(getApplication()).updateAppWidget(
				componentName, remoteViews);
	}

	private void updateWeather() {
		String city = mSpUtil.getCity();
		String climate = parseWeather(mSpUtil.getSimpleClimate());
		String temp = mSpUtil.getSimpleTemp();
		// L.i(city + " " + climate + " " + temp);
		remoteViews.setTextViewText(R.id.weather_icon_left, city + "\r\n"
				+ climate + " " + temp);
		remoteViews
				.setImageViewResource(
						R.id.weather_icon,
						Application.getInstance().getWidgetWeatherIcon(
								mSpUtil.getSimpleClimate()));
		remoteViews.setViewVisibility(R.id.TextViewMessage, View.GONE);
		ComponentName componentName = new ComponentName(getApplication(),
				WeatherWidget.class);
		AppWidgetManager.getInstance(getApplication()).updateAppWidget(
				componentName, remoteViews);
	}

	private String parseWeather(String climate) {
		String[] strs;
		if (climate.contains("转")) {// 天气带转字，取前面那部分
			strs = climate.split("转");
			climate = strs[0];
		}
		return climate;
	}

	/**
	 * 获取时间是24小时制还是12小时制
	 */
	private int getTimeFormat() {
		ContentResolver cv = this.getContentResolver();
		String strTimeFormat = android.provider.Settings.System.getString(cv,
				android.provider.Settings.System.TIME_12_24);
		if (strTimeFormat != null) {
			if (strTimeFormat.equals("24")) {
				// L.i("24小时制");
				return TIME_FORMAT_24;
			} else {
				// String amPmValues;
				Calendar c = Calendar.getInstance();
				if (c.get(Calendar.AM_PM) == 0) {
					// amPmValues = "AM";
					return TIME_FORMAT_AM;
				} else {
					// amPmValues = "PM";
					return TIME_FORMAT_PM;
				}
			}
		}
		return TIME_FORMAT_24;
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if (intent != null) {
			String action = intent.getAction();
			if (!TextUtils.isEmpty(action)
					&& action
							.endsWith(WeatherWidget.TEXTINFO_LEFT_HOTAREA_ACTION)) {
				City city = mCityDB.getCity(mSpUtil.getCity());
				if (city != null) {
					new GetWeatherTask(mHandler, city).execute();
					T.showLong(mApplication, "开始更新天气");
				}
			}
		}
		updateTime();
		updateWeather();
		// mHandler.post(monitorStatus);//后来觉得作用不大，就去掉了
		return super.onStartCommand(intent, flags, startId);
	}

	private void registerReceiver() {
		IntentFilter updateIntent = new IntentFilter();
		updateIntent.addAction(MainActivity.UPDATE_WIDGET_WEATHER_ACTION);
		updateIntent.addAction("android.intent.action.TIME_TICK");
		updateIntent.addAction("android.intent.action.TIME_SET");
		updateIntent.addAction("android.intent.action.DATE_CHANGED");
		updateIntent.addAction("android.intent.action.TIMEZONE_CHANGED");
		registerReceiver(mTimePickerBroadcast, updateIntent);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		if (mTimePickerBroadcast != null)
			unregisterReceiver(mTimePickerBroadcast);
	}

	// 判断程序是否在后台运行的任务
	Runnable monitorStatus = new Runnable() {
		public void run() {
			try {
				if (!isAppOnForeground()) {
					L.i("app run in background...");
					mApplication.free();
				}
				mHandler.postDelayed(monitorStatus, 1000L);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	};

	public boolean isAppOnForeground() {
		List<RunningAppProcessInfo> appProcesses = mActivityManager
				.getRunningAppProcesses();
		if (appProcesses == null)
			return false;
		for (RunningAppProcessInfo appProcess : appProcesses) {
			// The name of the process that this object is associated with.
			if (appProcess.processName.equals(mPackageName)
					&& appProcess.importance == RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
				return true;
			}
		}
		return false;
	}
}
