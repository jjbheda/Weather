package com.way.weather;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import android.app.Dialog;
import android.app.NotificationManager;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.view.ViewPager;
import android.text.TextUtils;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.baidu.location.BDLocation;
import com.baidu.location.BDLocationListener;
import com.baidu.location.LocationClient;
import com.way.apapter.WeatherPagerAdapter;
import com.way.app.Application;
import com.way.app.NetBroadcastReceiver;
import com.way.app.NetBroadcastReceiver.EventHandler;
import com.way.bean.City;
import com.way.bean.WeatherInfo;
import com.way.db.CityDB;
import com.way.fragment.FirstWeatherFragment;
import com.way.fragment.SecondWeatherFragment;
import com.way.indicator.CirclePageIndicator;
import com.way.plistview.RotateImageView;
import com.way.util.GetWeatherTask;
import com.way.util.IphoneDialog;
import com.way.util.NetUtil;
import com.way.util.SharePreferenceUtil;
import com.way.util.ShareUtil;
import com.way.util.T;
import com.way.util.TimeUtil;

public class MainActivity extends FragmentActivity implements EventHandler,
		OnClickListener {
	public static final String UPDATE_WIDGET_WEATHER_ACTION = "com.way.action.update_weather";
	private static final int LOACTION_OK = 0;
	private static final int UPDATE_EXISTS_CITY = 2;
	public static final int GET_WEATHER_SCUESS = 3;
	public static final int GET_WEATHER_FAIL = 4;
	private LocationClient mLocationClient;
	private CityDB mCityDB;
	private SharePreferenceUtil mSpUtil;
	private Application mApplication;
	private View mSplashView;
	private static final int SHOW_TIME_MIN = 3000;// 最小显示时间
	private long mStartTime;// 开始时间
	private ImageView mCityManagerBtn, mLocationBtn, mShareBtn;
	private RotateImageView mUpdateProgressBar;
	private TextView mTitleTextView;
	private WeatherPagerAdapter mWeatherPagerAdapter;

	private TextView cityTv, timeTv, weekTv, aqiDataTv, aqiQualityTv,
			temperatureTv, climateTv, windTv;
	private View mAqiRootView;
	private View mShareView;
	private ImageView weatherImg, aqiImg;;
	private ViewPager mViewPager;
	private List<Fragment> fragments;
	private ShareUtil mShareUtil;

	private Handler mHandler = new Handler() {
		public void handleMessage(android.os.Message msg) {
			switch (msg.what) {
			case LOACTION_OK:
				City curCity = (City) msg.obj;
				if (curCity != null) {
//					T.showShort(MainActivity.this, "定位到：" + curCity.getName());
					mSpUtil.setCity(curCity.getName());
					updateWeather(curCity);
				}
				break;
			case UPDATE_EXISTS_CITY:
				String sPCityName = mSpUtil.getCity();
				updateWeather(mCityDB.getCity(sPCityName));
				break;
			case GET_WEATHER_SCUESS:
				WeatherInfo weatherInfo = (mApplication.getAllWeather());
				updateWeatherInfo(weatherInfo);
				updateAqiInfo(weatherInfo);
				updateWidgetWeather();
				mUpdateProgressBar.stopAnim();
				
				long loadingTime = System.currentTimeMillis() - mStartTime;// 计算一下总共花费的时间
				if (loadingTime < SHOW_TIME_MIN) {// 如果比最小显示时间还短，就延时进入MainActivity，否则直接进入
					mHandler.postDelayed(splashGone, SHOW_TIME_MIN
							- loadingTime);
				} else {
					mHandler.post(splashGone);
				}
				break;
			case GET_WEATHER_FAIL:
				updateWeatherInfo(null);
				updateAqiInfo(null);
				T.show(MainActivity.this, "获取天气失败，请重试", Toast.LENGTH_SHORT);
				mUpdateProgressBar.stopAnim();
				break;
			default:
				break;
			}
		}

	};
	// 进入下一个Activity
	Runnable splashGone = new Runnable() {

		@Override
		public void run() {
			Animation anim = AnimationUtils.loadAnimation(MainActivity.this,
					R.anim.push_right_out);
			anim.setAnimationListener(new AnimationListener() {

				@Override
				public void onAnimationStart(Animation animation) {
				}

				@Override
				public void onAnimationRepeat(Animation animation) {
				}

				@Override
				public void onAnimationEnd(Animation animation) {
					// TODO Auto-generated method stub
					mSplashView.setVisibility(View.GONE);

				}
			});
			mSplashView.startAnimation(anim);
		}
	};

	private void updateWidgetWeather() {
		sendBroadcast(new Intent(UPDATE_WIDGET_WEATHER_ACTION));
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		initData();
		initView();
	}

	@Override
	protected void onResume() {
		super.onResume();
		NotificationManager notificationManager = mApplication
				.getNotificationManager();
		if (notificationManager != null)
			notificationManager.cancelAll();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		NetBroadcastReceiver.mListeners.remove(this);
	}

	private void startActivityForResult() {
		Intent i = new Intent(this, SelectCtiyActivity.class);
		startActivityForResult(i, 0);
	}

	private void initView() {
		mSplashView = findViewById(R.id.splash_view);
		mCityManagerBtn = (ImageView) findViewById(R.id.title_city_manager);
		mShareBtn = (ImageView) findViewById(R.id.title_share);
		mLocationBtn = (ImageView) findViewById(R.id.title_location);
		mCityManagerBtn.setOnClickListener(this);
		mShareBtn.setOnClickListener(this);
		mLocationBtn.setOnClickListener(this);
		mUpdateProgressBar = (RotateImageView) findViewById(R.id.title_update_progress);
		mUpdateProgressBar.setOnClickListener(this);
		mTitleTextView = (TextView) findViewById(R.id.title_city_name);

		cityTv = (TextView) findViewById(R.id.city);
		timeTv = (TextView) findViewById(R.id.time);
		timeTv.setText("未发布");
		weekTv = (TextView) findViewById(R.id.week_today);

		mAqiRootView = findViewById(R.id.aqi_root_view);
		mAqiRootView.setVisibility(View.INVISIBLE);
		mShareView = findViewById(R.id.weather_share_view);
		aqiDataTv = (TextView) findViewById(R.id.pm_data);
		aqiQualityTv = (TextView) findViewById(R.id.pm2_5_quality);
		aqiImg = (ImageView) findViewById(R.id.pm2_5_img);
		temperatureTv = (TextView) findViewById(R.id.temperature);
		climateTv = (TextView) findViewById(R.id.climate);
		windTv = (TextView) findViewById(R.id.wind);
		weatherImg = (ImageView) findViewById(R.id.weather_img);
		fragments = new ArrayList<Fragment>();
		fragments.add(new FirstWeatherFragment());
		fragments.add(new SecondWeatherFragment());
		mViewPager = (ViewPager) findViewById(R.id.viewpager);
		mWeatherPagerAdapter = new WeatherPagerAdapter(
				getSupportFragmentManager(), fragments);
		mViewPager.setAdapter(mWeatherPagerAdapter);
		((CirclePageIndicator) findViewById(R.id.indicator))
				.setViewPager(mViewPager);
		if (TextUtils.isEmpty(mSpUtil.getCity())) {
			if (NetUtil.getNetworkState(this) != NetUtil.NETWORN_NONE) {
				mLocationClient.start();
				mLocationClient.requestLocation();
				mUpdateProgressBar.startAnim();
			} else {
				T.showShort(this, R.string.net_err);
			}
		} else {
			mHandler.sendEmptyMessage(UPDATE_EXISTS_CITY);
		}
	}

	private void initData() {
		mStartTime = System.currentTimeMillis();// 记录开始时间，
		NetBroadcastReceiver.mListeners.add(this);
		mApplication = Application.getInstance();
		mSpUtil = mApplication.getSharePreferenceUtil();
		mLocationClient = mApplication.getLocationClient();

		mLocationClient.registerLocationListener(mLocationListener);
		mCityDB = mApplication.getCityDB();
		mShareUtil = new ShareUtil(this, mHandler);
	}

	private void updateWeather(City city) {
		// 因为在异步任务中有判断网络连接问题，在网络请求前先获取文件中的缓存，所以，此处未处理网络连接问题
		if (city == null) {
			T.showLong(mApplication, "未找到此城市,请重新定位或选择...");
			return;
		}
		timeTv.setText("同步中...");
		mTitleTextView.setText(city.getName());
		mUpdateProgressBar.startAnim();
		new GetWeatherTask(mHandler, city).execute();
	}

	/**
	 * 更新天气界面
	 */
	private void updateWeatherInfo(WeatherInfo allWeather) {
		weekTv.setText("今天 " + TimeUtil.getWeek(0, TimeUtil.XING_QI));
		if (fragments.size() > 0) {
			((FirstWeatherFragment) mWeatherPagerAdapter.getItem(0))
					.updateWeather(allWeather);
			((SecondWeatherFragment) mWeatherPagerAdapter.getItem(1))
					.updateWeather(allWeather);
		}
		if (allWeather != null) {
			cityTv.setText(allWeather.getCity());
			if (!TextUtils.isEmpty(allWeather.getFeelTemp())) {
				temperatureTv.setText(allWeather.getFeelTemp());
				mSpUtil.setSimpleTemp(allWeather.getFeelTemp()
						.replace("~", "/").replace("℃", "°"));// 保存一下温度信息，用户小插件
			} else {
				temperatureTv.setText(allWeather.getTemp0());
				mSpUtil.setSimpleTemp(allWeather.getTemp0().replace("~", "/")
						.replace("℃", "°"));
			}

			String climate = allWeather.getWeather0();
			climateTv.setText(climate);
			mSpUtil.setSimpleClimate(climate);// 保存一下天气信息，用户小插件

			weatherImg.setImageResource(getWeatherIcon(climate));
			windTv.setText(allWeather.getWind0());

			String time = allWeather.getIntime();
			mSpUtil.setTimeSamp(TimeUtil.getLongTime(time));// 保存一下更新的时间戳，记录更新时间
			timeTv.setText(TimeUtil.getDay(mSpUtil.getTimeSamp()) + "发布");
		} else {
			cityTv.setText(mSpUtil.getCity());
			timeTv.setText("未同步");
			temperatureTv.setText("N/A");
			climateTv.setText("N/A");
			windTv.setText("N/A");
			weatherImg.setImageResource(R.drawable.na);
			T.showLong(mApplication, "获取天气信息失败");
		}
	}

	/**
	 * 更新aqi界面
	 */
	private void updateAqiInfo(WeatherInfo allWeather) {
		if (allWeather != null && allWeather.getAQIData() != null) {
			mAqiRootView.setVisibility(View.VISIBLE);
			aqiDataTv.setText(allWeather.getAQIData());
			int aqi = Integer.parseInt(allWeather.getAQIData());
			int aqi_img = R.drawable.biz_plugin_weather_0_50;
			String aqiText = "无数据";
			if (aqi > 300) {
				aqi_img = R.drawable.biz_plugin_weather_greater_300;
				aqiText = "严重污染";
			} else if (aqi > 200) {
				aqi_img = R.drawable.biz_plugin_weather_201_300;
				aqiText = "重度污染";
			} else if (aqi > 150) {
				aqi_img = R.drawable.biz_plugin_weather_151_200;
				aqiText = "中度污染";
			} else if (aqi > 100) {
				aqi_img = R.drawable.biz_plugin_weather_101_150;
				aqiText = "轻度污染";
			} else if (aqi > 50) {
				aqi_img = R.drawable.biz_plugin_weather_51_100;
				aqiText = "良";
			} else {
				aqi_img = R.drawable.biz_plugin_weather_0_50;
				aqiText = "优";
			}
			aqiImg.setImageResource(aqi_img);
			aqiQualityTv.setText(aqiText);
		} else {
			mAqiRootView.setVisibility(View.INVISIBLE);
			aqiQualityTv.setText("");
			aqiDataTv.setText("");
			aqiImg.setImageResource(R.drawable.biz_plugin_weather_0_50);
			T.showShort(mApplication, "该城市暂无空气质量数据");
		}
	}

	private int getWeatherIcon(String climate) {
		int weatherIcon = R.drawable.biz_plugin_weather_qing;
		if (climate.contains("转")) {// 天气带转字，取前面那部分
			String[] strs = climate.split("转");
			climate = strs[0];
			if (climate.contains("到")) {// 如果转字前面那部分带到字，则取它的后部分
				strs = climate.split("到");
				climate = strs[1];
			}
		}
		if (mApplication.getWeatherIconMap().containsKey(climate)) {
			weatherIcon = mApplication.getWeatherIconMap().get(climate);
		}
		return weatherIcon;
	}

	BDLocationListener mLocationListener = new BDLocationListener() {

		@Override
		public void onReceivePoi(BDLocation arg0) {
			// do nothing
		}

		@Override
		public void onReceiveLocation(BDLocation location) {
			mUpdateProgressBar.stopAnim();
			if (location == null || TextUtils.isEmpty(location.getCity())) {
				showLocationFailDialog();
				return;
			}
			// 获取当前城市，
			String cityName = location.getCity();
			mLocationClient.stop();

			City curCity = mCityDB.getCity(cityName);// 从数据库中找到该城市
			if (curCity != null) {
				Message msg = mHandler.obtainMessage();
				msg.what = LOACTION_OK;
				msg.obj = curCity;
				mHandler.sendMessage(msg);// 更新天气
			} else {// 如果定位到的城市数据库中没有，也弹出定位失败
				showLocationFailDialog();
			}
		}
	};

	private void showLocationFailDialog() {
		final Dialog dialog = IphoneDialog.getTwoBtnDialog(MainActivity.this,
				"定位失败", "是否手动选择城市?");
		((Button) dialog.findViewById(R.id.ok))
				.setOnClickListener(new OnClickListener() {

					@Override
					public void onClick(View v) {
						startActivityForResult();
						dialog.dismiss();
					}
				});
		dialog.show();
	}

	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == 0 && resultCode == RESULT_OK) {
			City city = (City) data.getSerializableExtra("city");
			mSpUtil.setCity(city.getName());
			updateWeather(city);
		}
	}

	@Override
	public void onNetChange() {
		if (NetUtil.getNetworkState(this) == NetUtil.NETWORN_NONE) {
			T.showLong(this, R.string.net_err);
		} else {
			if (!TextUtils.isEmpty(mSpUtil.getCity()))
				mHandler.sendEmptyMessage(UPDATE_EXISTS_CITY);
		}
	}

	@Override
	public void onClick(View v) {
		switch (v.getId()) {
		case R.id.title_city_manager:
			startActivityForResult();
			break;
		case R.id.title_location:
			if (NetUtil.getNetworkState(this) != NetUtil.NETWORN_NONE) {
				if (!mLocationClient.isStarted())
					mLocationClient.start();
				mLocationClient.requestLocation();
				T.showShort(this, "正在定位...");
			} else {
				T.showShort(this, R.string.net_err);
			}
			break;
		case R.id.title_share:
			if (NetUtil.getNetworkState(this) != NetUtil.NETWORN_NONE) {
				byte[] bm = getSharePicture();
				if (bm != null && mApplication.getAllWeather() != null)
					mShareUtil.share(bm,
							getShareContent(mApplication.getAllWeather()));
				else
					T.showShort(this, "分享失败");
			} else {
				T.showShort(this, R.string.net_err);
			}
			break;
		case R.id.title_update_progress:
			if (NetUtil.getNetworkState(this) != NetUtil.NETWORN_NONE) {
				if (TextUtils.isEmpty(mSpUtil.getCity())) {
					T.showShort(this, "请先选择城市或定位！");
				} else {
					String sPCityName = mSpUtil.getCity();
					City curCity = mCityDB.getCity(mSpUtil.getCity());
					updateWeather(curCity);
				}
			} else {
				T.showShort(this, R.string.net_err);
			}
			break;

		default:
			break;
		}
	}

	private byte[] getSharePicture() {
		try {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			mShareView.setDrawingCacheEnabled(true);
			Bitmap.createBitmap(mShareView.getDrawingCache()).compress(
					Bitmap.CompressFormat.PNG, 100, baos);
			return baos.toByteArray();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	private String mShare = "今日%s天气：%s，温度：%s；空气质量指数(AQI)：%s，PM2.5 浓度值：%s μg/m3。";
	private String mShareSimple = "今日%s天气：%s，温度：%s，湿度：%s，风向：%s。";

	private String getShareContent(WeatherInfo weatherInfo) {
		String aqi = weatherInfo.getAQIData();
		String pm2d5 = weatherInfo.getPM2Dot5Data();
		if (!TextUtils.isEmpty(aqi) && !TextUtils.isEmpty(pm2d5))
			return String.format(mShare, new Object[] { weatherInfo.getCity(),
					weatherInfo.getWeather0(), weatherInfo.getTemp0(),
					weatherInfo.getAQIData(), weatherInfo.getPM2Dot5Data() });
		return String.format(mShareSimple,
				new Object[] { weatherInfo.getCity(),
						weatherInfo.getWeather0(), weatherInfo.getTemp0(),
						weatherInfo.getShidu(), weatherInfo.getWinNow() });
	}

	/**
	 * 连续按两次返回键就退出
	 */
	private long firstTime;

	@Override
	public void onBackPressed() {
		if (System.currentTimeMillis() - firstTime < 3000) {
			finish();
		} else {
			firstTime = System.currentTimeMillis();
			T.showShort(this, R.string.press_again_exit);
		}
	}

}
