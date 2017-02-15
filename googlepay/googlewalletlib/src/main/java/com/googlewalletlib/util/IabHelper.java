/* Copyright (c) 2012 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.googlewalletlib.util;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender.SendIntentException;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.text.TextUtils;
import android.util.Log;

import com.android.vending.billing.IInAppBillingService;

import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

/**
 * Provides convenience methods for in-app billing. You can create one instance
 * of this class for your application and use it to process in-app billing
 * operations. It provides synchronous (blocking) and asynchronous
 * (non-blocking) methods for many common in-app billing operations, as well as
 * automatic signature verification.
 * 
 * After instantiating, you must perform setup in order to start using the
 * object. To perform setup, call the {@link #startSetup} method and provide a
 * listener; that listener will be notified when setup is complete, after which
 * (and not before) you may call other methods.
 * 
 * After setup is complete, you will typically want to request an inventory of
 * owned items and subscriptions. See {@link #queryInventory},
 * {@link #queryInventoryAsync} and related methods.
 * 
 * When you are done with this object, don't forget to call {@link #dispose} to
 * ensure proper cleanup. This object holds a binding to the in-app billing
 * service, which will leak unless you dispose of it correctly. If you created
 * the object on an Activity's onCreate method, then the recommended place to
 * dispose of it is the Activity's onDestroy method.
 * 
 * A note about threading: When using this object from a background thread, you
 * may call the blocking versions of methods; when using from a UI thread, call
 * only the asynchronous versions and handle the results via callbacks. Also,
 * notice that you can only call one asynchronous operation at a time;
 * attempting to start a second asynchronous operation while the first one has
 * not yet completed will result in an exception being thrown.
 * 
 * @author Bruno Oliveira (Google)
 * 
 */
public class IabHelper {
	// 需要显示log吗
	boolean mDebugLog = false;
	String mDebugTag = "IabHelper";

	// 初始化了吗
	boolean mSetupDone = false;

	// 时候支持订阅
	boolean mSubscriptionsSupported = false;

	// 是否需要异步操作
	// (只允许一人一次)
	boolean mAsyncInProgress = false;

	// 当前进程的名字
	String mAsyncOperation = "";

	// 上下文
	Context mContext;

	// 连接服务器的操作对象
	IInAppBillingService mService;
	ServiceConnection mServiceConn;

	// 请求码
	int mRequestCode;

	// 购买类型
	String mPurchasingItemType;

	// 开发人员的密钥
	String mSignatureBase64 = null;

	// Billing 结果码
	public static final int BILLING_RESPONSE_RESULT_OK = 0;
	public static final int BILLING_RESPONSE_RESULT_USER_CANCELED = 1;
	public static final int BILLING_RESPONSE_RESULT_BILLING_UNAVAILABLE = 3;
	public static final int BILLING_RESPONSE_RESULT_ITEM_UNAVAILABLE = 4;
	public static final int BILLING_RESPONSE_RESULT_DEVELOPER_ERROR = 5;
	public static final int BILLING_RESPONSE_RESULT_ERROR = 6;
	public static final int BILLING_RESPONSE_RESULT_ITEM_ALREADY_OWNED = 7;
	public static final int BILLING_RESPONSE_RESULT_ITEM_NOT_OWNED = 8;

	// IAB Helper error codes
	public static final int IABHELPER_ERROR_BASE = -1000;
	public static final int IABHELPER_REMOTE_EXCEPTION = -1001;// 说明设备不支持billing支付
	public static final int IABHELPER_BAD_RESPONSE = -1002;// 不是错误，也不是正确的响应码
	public static final int IABHELPER_VERIFICATION_FAILED = -1003;// 验证失败
	public static final int IABHELPER_SEND_INTENT_FAILED = -1004;// 发送intent失败
	public static final int IABHELPER_USER_CANCELLED = -1005;// 用户取消
	public static final int IABHELPER_UNKNOWN_PURCHASE_RESPONSE = -1006;// 未知的购买
	public static final int IABHELPER_MISSING_TOKEN = -1007;//没有 token
	public static final int IABHELPER_UNKNOWN_ERROR = -1008;// 购买后没有信息返回
	public static final int IABHELPER_SUBSCRIPTIONS_NOT_AVAILABLE = -1009;// 订阅无法使用
	public static final int IABHELPER_INVALID_CONSUMPTION = -1010;//无效的消费

	// Keys for the responses from InAppBillingService
	public static final String RESPONSE_CODE = "RESPONSE_CODE";
	public static final String RESPONSE_GET_SKU_DETAILS_LIST = "DETAILS_LIST";
	public static final String RESPONSE_BUY_INTENT = "BUY_INTENT";
	public static final String RESPONSE_INAPP_PURCHASE_DATA = "INAPP_PURCHASE_DATA";
	public static final String RESPONSE_INAPP_SIGNATURE = "INAPP_DATA_SIGNATURE";
	public static final String RESPONSE_INAPP_ITEM_LIST = "INAPP_PURCHASE_ITEM_LIST";
	public static final String RESPONSE_INAPP_PURCHASE_DATA_LIST = "INAPP_PURCHASE_DATA_LIST";
	public static final String RESPONSE_INAPP_SIGNATURE_LIST = "INAPP_DATA_SIGNATURE_LIST";
	public static final String INAPP_CONTINUATION_TOKEN = "INAPP_CONTINUATION_TOKEN";

	// Item types
	public static final String ITEM_TYPE_INAPP = "inapp";
	public static final String ITEM_TYPE_SUBS = "subs";

	// some fields on the getSkuDetails response bundle
	public static final String GET_SKU_DETAILS_ITEM_LIST = "ITEM_ID_LIST";
	public static final String GET_SKU_DETAILS_ITEM_TYPE_LIST = "ITEM_TYPE_LIST";

	/**
	 * 初始化参数，base64PublicKey 是开发公共密钥
	 */
	public IabHelper(Context ctx, String base64PublicKey) {
		mContext = ctx.getApplicationContext();
		mSignatureBase64 = base64PublicKey;
		logDebug("IAB helper 启动");
	}

	/**
	 * 启用或者禁用log输出 tag = IabHelper
	 */
	public void enableDebugLogging(boolean enable, String tag) {
		mDebugLog = enable;
		mDebugTag = tag;
	}

	public void enableDebugLogging(boolean enable) {
		mDebugLog = enable;
	}

	/**
	 * 安装过程中调用
	 */
	public interface OnIabSetupFinishedListener {
		/**
		 * 在安装过程中是否成功
		 */
		public void onIabSetupFinished(IabResult result);
	}

	/**
	 * 连接准备工作
	 * 
	 * @param listener 监听安装过程
	 */
	public void startSetup(final OnIabSetupFinishedListener listener) {
		// 根据要求，是否可以继续购买
		// if (mSetupDone) throw new
		// IllegalStateException("IAB helper is already set up.");

		logDebug("开始初始化service");
		// 操作代码
		mServiceConn = new ServiceConnection() {
			public void onServiceDisconnected(ComponentName name) {
				mService = null;
			}

			// 连接服务器
			public void onServiceConnected(ComponentName name, IBinder service) {
				mService = IInAppBillingService.Stub.asInterface(service);
				String packageName = mContext.getPackageName();
				try {
					logDebug("检测是否支持in-app billing 3");

					// 是否支持3版本
					int response = mService.isBillingSupported(3, packageName,
							ITEM_TYPE_INAPP);
					if (response != BILLING_RESPONSE_RESULT_OK) {
						if (listener != null)
							listener.onIabSetupFinished(new IabResult(response,
									"检查billing v3错误"));
						System.out.println("订阅无法使用  Response: " + response);

						mSubscriptionsSupported = false;
						return;
					}
					logDebug("In-app billing version 3 支持" + packageName);

					// 检测设备是否支持订阅3版本
					response = mService.isBillingSupported(3, packageName,
							ITEM_TYPE_SUBS);
					if (response == BILLING_RESPONSE_RESULT_OK) {
						logDebug("有效的支持");
						mSubscriptionsSupported = true;
					} else {
						logDebug("订阅无法使用  Response: " + response);
					}

					mSetupDone = true;
				} catch (RemoteException e) {
					if (listener != null) {
						listener.onIabSetupFinished(new IabResult(
								IABHELPER_REMOTE_EXCEPTION,
								"RemoteException while setting up in-app billing."));
					}
					e.printStackTrace();
					return;
				}

				if (listener != null) {
					listener.onIabSetupFinished(new IabResult(
							BILLING_RESPONSE_RESULT_OK, "设备可用"));
				}
			}
		};
		// 开启服务
		Intent serviceIntent = new Intent(
				"com.android.vending.billing.InAppBillingService.BIND");
		serviceIntent.setPackage("com.android.vending");
		if (!mContext.getPackageManager().queryIntentServices(serviceIntent, 0)
				.isEmpty()) {
			// 绑定服务
			mContext.bindService(serviceIntent, mServiceConn,
					Context.BIND_AUTO_CREATE);
		} else {
			// 计费API版本不支持请求的类型
			if (listener != null) {
				listener.onIabSetupFinished(new IabResult(
						BILLING_RESPONSE_RESULT_BILLING_UNAVAILABLE,
						"支付平台设备不支持"));
			}
		}
	}

	/**
	 * Dispose of object, releasing resources. It's very important to call this
	 * method when you are done with this object. It will release any resources
	 * used by it such as service connections. Naturally, once the object is
	 * disposed of, it can't be used again.
	 */
	public void dispose() {
		logDebug("处理");
		mSetupDone = false;
		if (mServiceConn != null) {
			logDebug("销毁service");
			if (mContext != null)
				mContext.unbindService(mServiceConn);
			mServiceConn = null;
			mService = null;
			mPurchaseListener = null;
		}
	}

	/**
	 * 是否支持订阅
	 * 
	 * @return
	 */
	public boolean subscriptionsSupported() {
		return mSubscriptionsSupported;
	}

	/**
	 * 回调函数，购买完成后 通知
	 */
	public interface OnIabPurchaseFinishedListener {
		/**
		 * 通知购买完成
		 * 
		 * @param result 购买结果
		 * @param info 购买的信息
		 */
		public void onIabPurchaseFinished(IabResult result, Purchase info);
	}

	// The listener registered on launchPurchaseFlow, which we have to call back
	// when
	// the purchase finishes
	OnIabPurchaseFinishedListener mPurchaseListener;

	/**
	 * 购买时监听
	 * 
	 * @param act
	 * @param sku
	 * @param requestCode
	 * @param listener
	 */
	public void launchPurchaseFlow(Activity act, String sku, int requestCode,
			OnIabPurchaseFinishedListener listener) {
		launchPurchaseFlow(act, sku, requestCode, listener, "");
	}

	/**
	 * 购买时监听
	 * 
	 * @param act
	 * @param sku
	 *            购买商品的名字
	 * @param requestCode
	 *            请求码
	 * @param listener
	 * @param extraData
	 *            开发有效数字，基于安全性使用
	 */
	public void launchPurchaseFlow(Activity act, String sku, int requestCode,
			OnIabPurchaseFinishedListener listener, String extraData) {
		launchPurchaseFlow(act, sku, ITEM_TYPE_INAPP, requestCode, listener,
				extraData);
	}

	// 购买时监听
	public void launchSubscriptionPurchaseFlow(Activity act, String sku,
			int requestCode, OnIabPurchaseFinishedListener listener) {
		launchSubscriptionPurchaseFlow(act, sku, requestCode, listener, "");
	}

	// 购买时监听
	public void launchSubscriptionPurchaseFlow(Activity act, String sku,
			int requestCode, OnIabPurchaseFinishedListener listener,
			String extraData) {
		launchPurchaseFlow(act, sku, ITEM_TYPE_SUBS, requestCode, listener,
				extraData);
	}

	/**
	 * 启动的UI流的应用程序内购买，调用此方法来启动一个应用程序内购买
	 * 
	 * @param act activity
	 *            .
	 * @param  sku 商品的名字
	 * @param itemType 一个产品或者是认购
	 *            (ITEM_TYPE_INAPP or ITEM_TYPE_SUBS)
	 * @param requestCode 请求码
	 * @param listener 购买过程中的监听器
	 * @param extraData 开发有效数字
	 *            ，基于安全性使用
	 */
	public void launchPurchaseFlow(Activity act, String sku, String itemType,
			int requestCode, OnIabPurchaseFinishedListener listener,
			String extraData) {
		checkSetupDone("launchPurchaseFlow");
		flagStartAsync("launchPurchaseFlow");
		IabResult result;

		if (itemType.equals(ITEM_TYPE_SUBS) && !mSubscriptionsSupported) {
			IabResult r = new IabResult(IABHELPER_SUBSCRIPTIONS_NOT_AVAILABLE,
					"订阅无法使用");
			if (listener != null)
				listener.onIabPurchaseFinished(r, null);
			return;
		}

		try {
			logDebug("Constructing buy intent for " + sku + ", item type: "
					+ itemType);
			// 核心操作
			Bundle buyIntentBundle = mService.getBuyIntent(3,
					mContext.getPackageName(), sku, itemType, extraData);
			int response = getResponseCodeFromBundle(buyIntentBundle);
			System.out.println("xxxxxxxx ： "+response);
			if (response != BILLING_RESPONSE_RESULT_OK) {
				logError("购买请求失败: " + getResponseDesc(response));

				result = new IabResult(response, "不能购买");
				if (listener != null)
					listener.onIabPurchaseFinished(result, null);
				return;
			}

			PendingIntent pendingIntent = buyIntentBundle
					.getParcelable(RESPONSE_BUY_INTENT);
			logDebug("Launching buy intent for " + sku + ". Request code: "
					+ requestCode);
			mRequestCode = requestCode;
			mPurchaseListener = listener;
			mPurchasingItemType = itemType;
			act.startIntentSenderForResult(pendingIntent.getIntentSender(),
					requestCode, new Intent(), Integer.valueOf(0),
					Integer.valueOf(0), Integer.valueOf(0));
		} catch (SendIntentException e) {
			logError("SendIntentException while launching purchase flow for sku "
					+ sku);
			e.printStackTrace();

			result = new IabResult(IABHELPER_SEND_INTENT_FAILED, "发送intent失败");
			if (listener != null)
				listener.onIabPurchaseFinished(result, null);
		} catch (RemoteException e) {
			logError("RemoteException while launching purchase flow for sku "
					+ sku);
			e.printStackTrace();

			result = new IabResult(IABHELPER_REMOTE_EXCEPTION,
					"Remote exception while starting purchase flow");
			if (listener != null)
				listener.onIabPurchaseFinished(result, null);
		}
	}

	/**
	 * 获取google返回账单结果
	 * 
	 * @param requestCode 请求码
	 * @param resultCode 结果码
	 * @param data google返回的对象
	 * @return 返回true，如果结果是一个采购流程和相关的处理; false，如果结果是不相关的购买，在这种情况下，你应该 正常处理。
	 */
	public boolean handleActivityResult(int requestCode, int resultCode,
			Intent data) {
		IabResult result;
		if (requestCode != mRequestCode)
			return false;

		checkSetupDone("handleActivityResult");

		// 购买结束后
		flagEndAsync();

		if (data == null) {
			logError("google返回来的是空值");
			System.out.println("google界面切换回来后，数据为空");
			result = new IabResult(IABHELPER_BAD_RESPONSE,
					"Null data in IAB result");
			if (mPurchaseListener != null)
				mPurchaseListener.onIabPurchaseFinished(result, null);
			return true;
		}

		int responseCode = getResponseCodeFromIntent(data);
		String purchaseData = data.getStringExtra(RESPONSE_INAPP_PURCHASE_DATA);// 返回订单的json信息
		String dataSignature = data.getStringExtra(RESPONSE_INAPP_SIGNATURE);// 包含购买数据签署的私钥开发的的签名。
//		Toast.makeText(mContext,purchaseData,Toast.LENGTH_SHORT).show();
		if (resultCode == Activity.RESULT_OK&& responseCode == BILLING_RESPONSE_RESULT_OK) {
			logDebug("成功从google界面返回");
			logDebug("购买商品的信息: " + purchaseData);
			logDebug("Data signature: " + dataSignature);
			logDebug("Extras: " + data.getExtras());
			logDebug("Expected item type: " + mPurchasingItemType);

			if (purchaseData == null || dataSignature == null) {
				logError("BUG: 返回的购买信息错误");
				logDebug("Extras: " + data.getExtras().toString());
				result = new IabResult(IABHELPER_UNKNOWN_ERROR, "购买后没有信息返回");
				if (mPurchaseListener != null)
					mPurchaseListener.onIabPurchaseFinished(result, null);
				return true;
			}

			Purchase purchase = null;
			try {
				// 初始化Purchase
				purchase = new Purchase(mPurchasingItemType, purchaseData,
						dataSignature);
				String sku = purchase.getSku();

				// 检查key
				if (!Security.verifyPurchase(mSignatureBase64, purchaseData,
						dataSignature)) {
					logError("购买签名验证失败 ： " + sku);
					result = new IabResult(IABHELPER_VERIFICATION_FAILED,
							"验证失败 ：  " + sku);
					if (mPurchaseListener != null)
						mPurchaseListener.onIabPurchaseFinished(result,
								purchase);
					return true;
				}
				logDebug("消费验证成功");
			} catch (JSONException e) {
				logError("Failed to parse purchase data.");
				e.printStackTrace();
				result = new IabResult(IABHELPER_BAD_RESPONSE, "购买信息解析失败");
				if (mPurchaseListener != null)
					mPurchaseListener.onIabPurchaseFinished(result, null);
				return true;
			}

			if (mPurchaseListener != null) {
				mPurchaseListener.onIabPurchaseFinished(new IabResult(
						BILLING_RESPONSE_RESULT_OK, "Success"), purchase);
			}
		} else if (resultCode == Activity.RESULT_OK) {
			// 结果码ok, 但响应码不通过
			logDebug("结果码ok, 但响应码不通过: " + getResponseDesc(responseCode));
			if (mPurchaseListener != null) {
				result = new IabResult(responseCode,
						"Problem purchashing item.");
				mPurchaseListener.onIabPurchaseFinished(result, null);
			}
		} else if (resultCode == Activity.RESULT_CANCELED) {
			logDebug("购买取消 : " + getResponseDesc(responseCode));
			result = new IabResult(IABHELPER_USER_CANCELLED, "用户取消");
			if (mPurchaseListener != null)
				mPurchaseListener.onIabPurchaseFinished(result, null);
		} else {
			logError("Purchase failed. Result code: "
					+ Integer.toString(resultCode) + ". Response: "
					+ getResponseDesc(responseCode));
			result = new IabResult(IABHELPER_UNKNOWN_PURCHASE_RESPONSE, "未知的购买");
			if (mPurchaseListener != null)
				mPurchaseListener.onIabPurchaseFinished(result, null);
		}
		return true;
	}

	public Inventory queryInventory(boolean querySkuDetails,
			List<String> moreSkus) throws IabException {
		return queryInventory(querySkuDetails, moreSkus, null);
	}

	/**
	 * 查询用户所购买的产品
	 * 
	 * @param querySkuDetails
	 *            if true, SKU details (price, description, etc) will be queried
	 *            as well as purchase information.
	 * @param moreItemSkus
	 *            additional PRODUCT skus to query information on, regardless of
	 *            ownership. Ignored if null or if querySkuDetails is false.
	 * @param moreSubsSkus
	 *            additional SUBSCRIPTIONS skus to query information on,
	 *            regardless of ownership. Ignored if null or if querySkuDetails
	 *            is false.
	 * @throws IabException
	 *             if a problem occurs while refreshing the inventory.
	 */
	public Inventory queryInventory(boolean querySkuDetails,
			List<String> moreItemSkus, List<String> moreSubsSkus)
			throws IabException {
		checkSetupDone("queryInventory");
		try {
			Inventory inv = new Inventory();
			int r = queryPurchases(inv, ITEM_TYPE_INAPP);
			if (r != BILLING_RESPONSE_RESULT_OK) {
				throw new IabException(r, "更新货存失败 (querying owned items).");
			}

			if (querySkuDetails) {
				r = querySkuDetails(ITEM_TYPE_INAPP, inv, moreItemSkus);
				if (r != BILLING_RESPONSE_RESULT_OK) {
					throw new IabException(r,
							"刷新库存失败 (querying prices of items).");
				}
			}

			// 如果订阅的支持，那么还可以查询订阅
			if (mSubscriptionsSupported) {
				r = queryPurchases(inv, ITEM_TYPE_SUBS);
				if (r != BILLING_RESPONSE_RESULT_OK) {
					throw new IabException(r,
							"刷新库存失败 (querying owned subscriptions).");
				}

				if (querySkuDetails) {
					r = querySkuDetails(ITEM_TYPE_SUBS, inv, moreItemSkus);// 这次查询为null
					if (r != BILLING_RESPONSE_RESULT_OK) {
						throw new IabException(r,
								"刷新库存失败 (querying prices of subscriptions).");
					}
				}
			}

			return inv;
		} catch (RemoteException e) {
			throw new IabException(IABHELPER_REMOTE_EXCEPTION,
					"Remote exception while refreshing inventory.", e);
		} catch (JSONException e) {
			throw new IabException(IABHELPER_BAD_RESPONSE,
					"Error parsing JSON response while refreshing inventory.",
					e);
		}
	}

	/**
	 * 监听商品查询操作完成后 通知。
	 */
	public interface QueryInventoryFinishedListener {
		/**
		 * 调用通知，库存查询操作完成。
		 * 
		 * @param result
		 *            The result of the operation.
		 * @param inv
		 *            The inventory.
		 */
		public void onQueryInventoryFinished(IabResult result, Inventory inv);
	}

	/**
	 * 异步的购买前的查询所有商品
	 * 
	 * @param querySkuDetails
	 *            as in {@link #queryInventory}
	 * @param moreSkus
	 *            as in {@link #queryInventory}
	 * @param listener
	 *            The listener to notify when the refresh operation completes.
	 */
	public void queryInventoryAsync(final boolean querySkuDetails,
			final List<String> moreSkus,
			final QueryInventoryFinishedListener listener) {
		final Handler handler = new Handler();
		checkSetupDone("queryInventory");
		flagStartAsync("refresh inventory");
		(new Thread(new Runnable() {
			public void run() {
				IabResult result = new IabResult(BILLING_RESPONSE_RESULT_OK,
						"货存更新成功");
				Inventory inv = null;
				try {
					inv = queryInventory(querySkuDetails, moreSkus);
				} catch (IabException ex) {
					result = ex.getResult();
				}

				flagEndAsync();

				final IabResult result_f = result;
				final Inventory inv_f = inv;
				handler.post(new Runnable() {
					public void run() {
						listener.onQueryInventoryFinished(result_f, inv_f);
					}
				});
			}
		})).start();
	}

	public void queryInventoryAsync(QueryInventoryFinishedListener listener,
			List<String> moreSkus) {
		queryInventoryAsync(true, moreSkus, listener);
	}

	public void queryInventoryAsync(boolean querySkuDetails,
			QueryInventoryFinishedListener listener) {
		queryInventoryAsync(querySkuDetails, null, listener);
	}

	public void queryInventoryAsync(QueryInventoryFinishedListener listener){
		queryInventoryAsync(true, null, listener);
	}

	/**
	 * 消耗一个给定的应用程序产品
	 * 
	 * @param itemInfo 消费项目
	 * @throws IabException
	 *             if there is a problem during consumption.
	 */
	void consume(Purchase itemInfo) throws IabException {
		checkSetupDone("consume");

		if (!itemInfo.mItemType.equals(ITEM_TYPE_INAPP)) {
			throw new IabException(IABHELPER_INVALID_CONSUMPTION,
					"Items of type '" + itemInfo.mItemType
							+ "' can't be consumed.");
		}

		try {
			String token = itemInfo.getToken();
			String sku = itemInfo.getSku();
			if (token == null || token.equals("")) {
				logError("不能消耗 " + sku + ". 没有 token.");
				throw new IabException(IABHELPER_MISSING_TOKEN,
						"PurchaseInfo is missing token for sku: " + sku + " "
								+ itemInfo);
			}

			logDebug("Consuming sku: " + sku + ", token: " + token);
			int response = mService.consumePurchase(3,
					mContext.getPackageName(), token);
			if (response == BILLING_RESPONSE_RESULT_OK) {
				Log.d(mDebugTag, "成功销毁sku: " + String.valueOf(response));
				logDebug("成功销毁sku: " + sku);
			} else {
				logDebug("消耗 sku 出错" + sku + ". " + getResponseDesc(response));
				throw new IabException(response, "消耗 sku 出错 " + sku);
			}
		} catch (RemoteException e) {
			throw new IabException(IABHELPER_REMOTE_EXCEPTION,
					"Remote exception while consuming. PurchaseInfo: "
							+ itemInfo, e);
		}
	}

	/**
	 * 消费结束时监听
	 */
	public interface OnConsumeFinishedListener {
		/**
		 * 通知消费已完成
		 * 
		 * @param purchase
		 *            The purchase that was (or was to be) consumed.
		 * @param result
		 *            The result of the consumption operation.
		 */
		public void onConsumeFinished(Purchase purchase, IabResult result);
	}

	/**
	 * 回调函数，多项目的消费操作完成时通知。
	 */
	public interface OnConsumeMultiFinishedListener {
		/**
		 * Called to notify that a consumption of multiple items has finished.
		 * 
		 * @param purchases
		 *            The purchases that were (or were to be) consumed.
		 * @param results
		 *            The results of each consumption operation, corresponding
		 *            to each sku.
		 */
		public void onConsumeMultiFinished(List<Purchase> purchases,
										   List<IabResult> results);
	}

	/**
	 * 根据商品 异步查询
	 * 
	 * @param purchase 商品
	 * @param listener 监听器监听消耗操作完成
	 *            ，时通知
	 */
	public void consumeAsync(Purchase purchase,
			OnConsumeFinishedListener listener) {
		checkSetupDone("consume");
		List<Purchase> purchases = new ArrayList<Purchase>();
		purchases.add(purchase);
		consumeAsyncInternal(purchases, listener, null);
	}

	/**
	 *
	 * @param purchases
	 *            The list of PurchaseInfo objects representing the purchases to
	 *            consume.
	 * @param listener
	 *            The listener to notify when the consumption operation
	 *            finishes.
	 */
	public void consumeAsync(List<Purchase> purchases,
			OnConsumeMultiFinishedListener listener) {
		checkSetupDone("consume");
		consumeAsyncInternal(purchases, null, listener);
	}

	/**
	 * 响应码处理
	 */
	public static String getResponseDesc(int code) {
		String[] iab_msgs = ("0:OK/1:用戶取消操作/2:未知/"
				+ "3:计费API版本不支持请求的类型/4:获取不到产品/"
				+ "5:没有权限/6:操作错误/7:不购买，因为项目已经拥有/"
				+ "8:丐商品不属于消费").split("/");
		String[] iabhelper_msgs = ("OK/設備不支持billing支付/"
				+ "無效的響應碼/" + "驗證失敗/" + "發送intent失敗/"
				+ "用戶取消操作/" + "未知的購買/" + "没有 token/"
				+ "購買後沒有信息返回/" + "訂閱無法使用/"
				+ "無效的消費").split("/");

		if (code <= IABHELPER_ERROR_BASE) {
			int index = IABHELPER_ERROR_BASE - code;
			if (index >= 0 && index < iabhelper_msgs.length)
				return iabhelper_msgs[index];
			else
				return String.valueOf(code) + ":未知的 IAB Helper 错误";
		} else if (code < 0 || code >= iab_msgs.length)
			return String.valueOf(code) + ":未知的";
		else
			return iab_msgs[code];
	}

	/**
	 * 解决错误
	 * 
	 * @param
	 * @return 返回服务器的结果码
	 */
	int getResponseCodeFromBundle(Bundle b) {
		Object o = b.get(RESPONSE_CODE);
		if (o == null) {
			logDebug("Bundle 没有返回值, 假设成功 ");
			return BILLING_RESPONSE_RESULT_OK;
		} else if (o instanceof Integer)
			return ((Integer) o).intValue();
		else if (o instanceof Long)
			return (int) ((Long) o).longValue();
		else {
			logError("错误的响应码");
			logError(o.getClass().getName());
			throw new RuntimeException("错误的响应码: " + o.getClass().getName());
		}
	}

	/**
	 * 返回响应码
	 * 
	 * @param i
	 * @return
	 */
	int getResponseCodeFromIntent(Intent i) {
		Object o = i.getExtras().get(RESPONSE_CODE);
		if (o == null) {
			logError("Intent 中没有响应码返回, 假设 OK (known issue)");
			return BILLING_RESPONSE_RESULT_OK;
		} else if (o instanceof Integer)
			return ((Integer) o).intValue();
		else if (o instanceof Long)
			return (int) ((Long) o).longValue();
		else {
			logError("无效的响应码");
			logError(o.getClass().getName());
			throw new RuntimeException(
					"无效的响应码: "+ o.getClass().getName());
		}
	}

	/**
	 * 检查设备
	 * 
	 * @param operation
	 *            标志
	 */
	void checkSetupDone(String operation) {
		if (!mSetupDone) {
			logError("非法的 (" + operation
					+ "): IAB helper 不能初始化.");
			throw new IllegalStateException(
					"IAB helper没有创建. 不能执行: "
							+ operation);
		}
	}

	void flagStartAsync(String operation) {
		if (mAsyncInProgress)
			throw new IllegalStateException("不能启动 ("
					+ operation + ") 因为另外一个流程 ("
					+ mAsyncOperation + ")在运行");
		mAsyncOperation = operation;
		mAsyncInProgress = true;
		logDebug("开始异步的操作: " + operation);
	}

	/**
	 * 标志当前的步骤
	 */
	void flagEndAsync() {
		logDebug("Ending async operation: " + mAsyncOperation);
		mAsyncOperation = "";
		mAsyncInProgress = false;
	}

	/**
	 * 查询当前用户所拥有的产品
	 * 
	 * @param inv
	 * @param itemType
	 * @return
	 * @throws JSONException
	 * @throws RemoteException
	 */
	int queryPurchases(Inventory inv, String itemType) throws JSONException,
			RemoteException {
		// Query purchases
		logDebug("查询产品, 产品类型: " + itemType);
		logDebug("包名: " + mContext.getPackageName());
		boolean verificationFailed = false;
		String continueToken = null;

		do {
			logDebug("延续标记调用getPurchases: "
					+ continueToken);
			// 查询
			Bundle ownedItems = mService.getPurchases(3,
					mContext.getPackageName(), itemType, continueToken);

			int response = getResponseCodeFromBundle(ownedItems);// 返回的响应码是0
			System.out.println("getPurchases response my:" + response);
			logDebug("Owned items response: " + String.valueOf(response));
			if (response != BILLING_RESPONSE_RESULT_OK) {
				logDebug("getPurchases() 出错: " + getResponseDesc(response));
				return response;
			}
			if (!ownedItems.containsKey(RESPONSE_INAPP_ITEM_LIST)||
				!ownedItems.containsKey(RESPONSE_INAPP_PURCHASE_DATA_LIST)||
				!ownedItems.containsKey(RESPONSE_INAPP_SIGNATURE_LIST)) {
				logError("getPurchases()返回来的bundle，没有包含需要的字段");
				return IABHELPER_BAD_RESPONSE;
			}

			ArrayList<String> ownedSkus = ownedItems
					.getStringArrayList(RESPONSE_INAPP_ITEM_LIST);
			ArrayList<String> purchaseDataList = ownedItems
					.getStringArrayList(RESPONSE_INAPP_PURCHASE_DATA_LIST);
			ArrayList<String> signatureList = ownedItems
					.getStringArrayList(RESPONSE_INAPP_SIGNATURE_LIST);
			for (int i = 0; i < purchaseDataList.size(); ++i) {
				String purchaseData = purchaseDataList.get(i);
				String signature = signatureList.get(i);
				String sku = ownedSkus.get(i);
				if (Security.verifyPurchase(mSignatureBase64, purchaseData,signature)) {
					logDebug("Sku可用: " + sku);
					Purchase purchase = new Purchase(itemType, purchaseData,signature);

					if (TextUtils.isEmpty(purchase.getToken())) {
						logWarn("BUG:  token为空");
						logDebug("Purchase 数据: " + purchaseData);
					}

					// 记录的所有权和令牌
					inv.addPurchase(purchase);
				} else {
					logWarn("Purchase signature verification **FAILED**. Not adding item.");
					logDebug("   Purchase data: " + purchaseData);
					logDebug("   Signature: " + signature);
					verificationFailed = true;
				}
			}

			continueToken = ownedItems.getString(INAPP_CONTINUATION_TOKEN);
			logDebug("Continuation token: " + continueToken);
		} while (!TextUtils.isEmpty(continueToken));

		return verificationFailed ? IABHELPER_VERIFICATION_FAILED
				: BILLING_RESPONSE_RESULT_OK;
	}

	/**
	 * 查询商品信息
	 * 
	 * @param itemType
	 * @param inv
	 * @param moreSkus
	 * @return
	 * @throws RemoteException
	 * @throws JSONException
	 */
	int querySkuDetails(String itemType, Inventory inv, List<String> moreSkus)
			throws RemoteException, JSONException {
		logDebug("查询 SKU信息.");
		ArrayList<String> skuList = new ArrayList<String>();
		skuList.addAll(inv.getAllOwnedSkus(itemType));
		if (moreSkus != null)
			skuList.addAll(moreSkus);

		if (skuList.size() == 0) {
			logDebug("queryPrices: 没有产品查询");
			return BILLING_RESPONSE_RESULT_OK;
		}

		Bundle querySkus = new Bundle();
		querySkus.putStringArrayList(GET_SKU_DETAILS_ITEM_LIST, skuList);
		Bundle skuDetails = mService.getSkuDetails(3,
				mContext.getPackageName(), itemType, querySkus);

		if (!skuDetails.containsKey(RESPONSE_GET_SKU_DETAILS_LIST)) {
			int response = getResponseCodeFromBundle(skuDetails);
			if (response != BILLING_RESPONSE_RESULT_OK) {
				logDebug("getSkuDetails() 失败: " + getResponseDesc(response));
				return response;
			} else {
				logError("getSkuDetails() 返回的 bundle不是错误，也不是正确的响应码");
				return IABHELPER_BAD_RESPONSE;
			}
		}

		ArrayList<String> responseList = skuDetails
				.getStringArrayList(RESPONSE_GET_SKU_DETAILS_LIST);
		System.out.println("responseList list : " + responseList);
		for (String thisResponse : responseList) {
			SkuDetails d = new SkuDetails(itemType, thisResponse);
			logDebug("Got sku details: " + d);
			Log.d("Got sku details my: ", d.toString());
			inv.addSkuDetails(d);
		}
		return BILLING_RESPONSE_RESULT_OK;
	}

	/**
	 * 异步消费商品
	 * 
	 * @param purchases
	 * @param singleListener
	 * @param multiListener
	 */
	void consumeAsyncInternal(final List<Purchase> purchases,
			final OnConsumeFinishedListener singleListener,
			final OnConsumeMultiFinishedListener multiListener) {
		final Handler handler = new Handler();
		flagStartAsync("consume");
		(new Thread(new Runnable() {
			public void run() {
				final List<IabResult> results = new ArrayList<IabResult>();
				for (Purchase purchase : purchases) {
					try {
						consume(purchase);
						results.add(new IabResult(BILLING_RESPONSE_RESULT_OK,
								"成功消耗 sku " + purchase.getSku()));
					} catch (IabException ex) {
						results.add(ex.getResult());
					}
				}

				flagEndAsync();
				if (singleListener != null) {
					handler.post(new Runnable() {
						public void run() {
							singleListener.onConsumeFinished(purchases.get(0),
									results.get(0));
						}
					});
				}
				if (multiListener != null) {
					handler.post(new Runnable() {
						public void run() {
							multiListener.onConsumeMultiFinished(purchases,
									results);
						}
					});
				}
			}
		})).start();
	}

	void logDebug(String msg) {
		if (mDebugLog)
			Log.d(mDebugTag, msg);
	}

	void logError(String msg) {
		Log.e(mDebugTag, "In-app billing 错误: " + msg);
	}

	void logWarn(String msg) {
		Log.w(mDebugTag, "In-app billing 警告: " + msg);
	}




}
