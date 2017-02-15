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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 货存
 */
public class Inventory {
    Map<String,SkuDetails> mSkuMap = new HashMap<String,SkuDetails>();
    Map<String,Purchase> mPurchaseMap = new HashMap<String,Purchase>();

    Inventory() { }

    //拿得购买商品的对象
    public SkuDetails getSkuDetails(String sku) {
        return mSkuMap.get(sku);
    }

    //同sku拿得商品操作对象
    public Purchase getPurchase(String sku) {
        return mPurchaseMap.get(sku);
    }

    //是否包含sku
    public boolean hasPurchase(String sku) {
        return mPurchaseMap.containsKey(sku);
    }

    //返回是否含有此商品
    public boolean hasDetails(String sku) {
        return mSkuMap.containsKey(sku);
    }

    /**
     * 清除账单，但不能影响服务器，只能影响本地
     */
    public void erasePurchase(String sku) {
        if (mPurchaseMap.containsKey(sku)) mPurchaseMap.remove(sku);
    }

    //那得所有账单的id
    List<String> getAllOwnedSkus() {
        return new ArrayList<String>(mPurchaseMap.keySet());
    }
    
    //拿得所有账单的类型 如app
    List<String> getAllOwnedSkus(String itemType) {
        List<String> result = new ArrayList<String>();
        for (Purchase p : mPurchaseMap.values()) {
            if (p.getItemType().equals(itemType)) result.add(p.getSku());
        }
        return result;
    }

    //拿得所有账单的对象
    List<Purchase> getAllPurchases() {
        return new ArrayList<Purchase>(mPurchaseMap.values());
    }

    void addSkuDetails(SkuDetails d) {
        mSkuMap.put(d.getSku(), d);
    }

    void addPurchase(Purchase p) {
        mPurchaseMap.put(p.getSku(), p);
    }
   
}
