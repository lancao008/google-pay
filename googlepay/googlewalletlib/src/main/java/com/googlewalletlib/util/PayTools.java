package com.googlewalletlib.util;


import android.app.Activity;
import android.content.Intent;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

public class PayTools {

    private Activity activity;
    public String SKU_GAS = "";//商品id
    static final int RC_REQUEST = 10001;
    SkuDetails skuDetails;
    IabHelper mHelper;
    private Handler handlerResult;
    private String payload;//关联的订单id
    private String TAG = "PayTools";


    public PayTools(Activity _activity, Handler _reslutHandler, String _SKU_GAS, String _payload) {
        this.activity = _activity;
        this.handlerResult = _reslutHandler;
        this.SKU_GAS = _SKU_GAS;
        this.payload = _payload;
    }

    /**
     * 查询用户购买的商品
     * @param orderid 商品订单id
     */
    public void getQuery(final String orderid) {
        //googlekey app上传google市场后可在后台找到这个googlekey
        String base64EncodedPublicKey = "googlekey";
        // 创建IabHelper来验证key
        // Log.d(TAG, "创建IabHelper");
        mHelper = new IabHelper(activity, base64EncodedPublicKey);
        // 是否需要输出调试内容
        mHelper.enableDebugLogging(false);
        // 启动安装程序
        mHelper.startSetup(new IabHelper.OnIabSetupFinishedListener() {
            public void onIabSetupFinished(IabResult result) {
                // Log.d(TAG, "设备检验完成");
                if (!result.isSuccess()) {
                    handlerResult.sendEmptyMessage(0);
                    // 失败
                    // complain("設備不支持billing支付: " + result);
                    return;
                }
                // 连接成功
                try {
                    Inventory inventory = new Inventory();
                    int success = mHelper.queryPurchases(inventory, "inapp");
                    if (success == 0) {
                        if (inventory.getAllPurchases() != null) {
                            for (int i = 0; i < inventory.getAllPurchases().size(); i++) {
                                Purchase purchaseStr = inventory.getAllPurchases().get(i);//获取到用户购买过的商品
                            }
                        }
                        handlerResult.sendEmptyMessage(3);
                        mHelper.dispose();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });


    }

    /**
     * 购买重要的方法
     * @param requestCode
     * @param resultCode
     * @param data
     */
    public void getHandleActivityResultData(int requestCode, int resultCode, Intent data) {
        if (mHelper != null) {
            mHelper.handleActivityResult(requestCode, resultCode, data);
        }
    }

    // 购买
    public void buy() {
        // 建议从服务器发过来
        String base64EncodedPublicKey = "googlekey";
        // 检测key
        mHelper = new IabHelper(activity, base64EncodedPublicKey);
        // 是否需要输出调试内容
        mHelper.enableDebugLogging(false);
        mHelper.startSetup(new IabHelper.OnIabSetupFinishedListener() {
            public void onIabSetupFinished(IabResult result) {
                if (!result.isSuccess()) {
                    handlerResult.sendEmptyMessage(0);
                    // 失败
                    // complain("設備不支持billing支付: " + result);
                    return;
                }
                try {
                    if (mHelper != null) {
                        if (!mHelper.subscriptionsSupported()) {
                            handlerResult.sendEmptyMessage(0);
                            return;
                        }
                        handlerResult.sendEmptyMessage(4);
                        mHelper.launchPurchaseFlow(activity, SKU_GAS, "inapp", RC_REQUEST,
                                mPurchaseFinishedListener, payload);
                    }
                } catch (Exception e) {

                }
            }
        });

    }

    // 购买商品后
    IabHelper.OnIabPurchaseFinishedListener mPurchaseFinishedListener = new IabHelper.OnIabPurchaseFinishedListener() {
        public void onIabPurchaseFinished(IabResult result, Purchase purchase) {
            if (result.isFailure()) {

                if (result.getResponse() == -1005) {//用户取消
                    handlerResult.sendEmptyMessage(3);
                    return;
                }
                if (purchase != null) {
                    commitBuy(purchase);
                }
                return;
            }
            if (!verifyDeveloperPayload(purchase)) {
                Log.e(TAG, "payload验证出错");
                handlerResult.sendEmptyMessage(0);
                return;
            }

            if (purchase.getSku().equals(SKU_GAS)) {
                mHelper.consumeAsync(purchase, mConsumeFinishedListener);
            }
            commitBuy(purchase);

        }
    };

    public void commitBuy(Purchase purchase) {
        //处理购买成功后的方法
    }

    /**
     * 往服务器提交数据
     *
     * @param purchase
     */
    public void setMessage(Purchase purchase) {
        Message msg = new Message();
        msg.obj = purchase;
        msg.what = 1;
        handlerResult.sendMessage(msg);

    }

    /**
     * 验证开发有效载荷的购买。
     *
     * @param p
     * @return 如果true，说明返回的Payload正确
     */
    boolean verifyDeveloperPayload(Purchase p) {
        String payload = p.getDeveloperPayload();
        System.out.println("payload :" + payload);
        // 获取随机字符串，做处理
        if (payload.equals(p.getDeveloperPayload())) {
            System.out.println("payload if:" + payload);
            return true;
        }
        return false;
    }

    // 监听我们完成查询的项目和我们自己的订阅
    IabHelper.QueryInventoryFinishedListener mGotInventoryListener = new IabHelper.QueryInventoryFinishedListener() {
        public void onQueryInventoryFinished(IabResult result,
                                             Inventory inventory) {
            handlerResult.sendEmptyMessage(4);
            if (result.isFailure()) {
                handlerResult.sendEmptyMessage(0);
                // complain("查詢庫存失敗: " + result.toString());
                return;
            }
            Purchase gasPurchase = inventory.getPurchase(SKU_GAS);
            skuDetails = inventory.getSkuDetails(SKU_GAS);
            System.out.println("skuDetails my:" + skuDetails);
            if (gasPurchase != null) {
                // 根据SKU_GAS 来消耗
                mHelper.consumeAsync(inventory.getPurchase(SKU_GAS),
                        mConsumeFinishedListener);
                return;
            }
        }
    };

    // 消耗商品
    IabHelper.OnConsumeFinishedListener mConsumeFinishedListener = new IabHelper.OnConsumeFinishedListener() {
        public void onConsumeFinished(Purchase purchase, IabResult result) {
            Log.d(TAG, "消耗商品: " + purchase + ", result: " + result);
            if (result.isSuccess()) {
                Log.d(TAG, "消耗成功");
            } else {
            }
            Log.d(TAG, "消耗结束");
        }
    };

    public void dis() {
        if (mHelper != null) {
            mHelper.dispose();
            mHelper = null;
        }

    }
}
