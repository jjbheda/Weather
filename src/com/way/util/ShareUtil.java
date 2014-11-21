package com.way.util;

import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;

import com.baidu.sharesdk.BaiduShareException;
import com.baidu.sharesdk.BaiduSocialShare;
import com.baidu.sharesdk.ShareContent;
import com.baidu.sharesdk.ShareListener;
import com.baidu.sharesdk.Utility;
import com.baidu.sharesdk.ui.BaiduSocialShareUserInterface;
import com.baidu.sharesdk.weixin.Constants;

public class ShareUtil {
	private Handler mHandler;
	private Activity mActivity;
	private BaiduSocialShare mSocialShare;
	private BaiduSocialShareUserInterface mSocialShareUi;
	private ShareContent mPicContent;

	public ShareUtil(Activity activity, Handler handler) {
		this.mActivity = activity;
		this.mHandler = handler;
		mSocialShare = BaiduSocialShare.getInstance(activity,
				"MqfN4zEGMphtse7jAeuzTGI5");// 这是我的百度apikey，可以直接利用
		// 设置支持微信平台 传入wxAppId
		// 微信分享比较麻烦，如果第三方要使用我的微信apikey。
		// 则必须使用应用包名、apk签名，所以还是建议大家去官网申请一个，大概一天内能申请到。
		mSocialShare.supportWeixin("wx980ca31ed6d667fe");
		mSocialShareUi = mSocialShare.getSocialShareUserInterfaceInstance();

		mPicContent = new ShareContent();// UI接口 分享图片
		mPicContent.setTitle("简洁天气分享");
		mPicContent.setUrl("http://blog.csdn.net/way_ping_li");
		mPicContent.setWxShareFlag(Constants.WeiXinShareType.IMAGE_PAGE);
	}

	public void share(byte[] picDatas, String content) {
		mPicContent.setContent(content);
		mPicContent.addImageByContent(picDatas);

		mSocialShareUi.showShareMenu(mActivity, mPicContent,
				Utility.SHARE_THEME_STYLE, new ShareListener() {
					@Override
					public void onAuthComplete(Bundle values) {
						L.i("lwp", "share onAuthComplete:" + values.toString());
					}

					@Override
					public void onApiComplete(String responses) {
						L.i("lwp", "share onApiComplete:" + responses);
						final String msg = responses;
						mHandler.post(new Runnable() {
							@Override
							public void run() {
								try {
									JSONObject dataJson = new JSONObject(msg);
									if (!TextUtils.isEmpty(dataJson
											.getString("success"))
											&& TextUtils.isEmpty(dataJson
													.getString("fail"))) {
										T.showShort(mActivity, "分享成功");
									} else {
										T.showShort(mActivity, "分享失败");
									}
								} catch (JSONException e) {
									e.printStackTrace();
								}

							}
						});
					}

					@Override
					public void onError(BaiduShareException e) {
						L.i("lwp", "share onError:" + e.getMessage());
					}

				});
	}

}
