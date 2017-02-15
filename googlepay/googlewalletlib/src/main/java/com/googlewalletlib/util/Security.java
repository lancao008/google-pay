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

import android.text.TextUtils;
import android.util.Log;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;

/**
 * 与安全相关的方法。一个安全的实现，所有这些代码应该实现的服务器上的设备上的应用程序进行通信。
 * 如果在本实施例的简单和清楚起见，此代码被包括在这里，在设备上执行。
 * 如果您必须在手机上确认购买，你应该混淆代码，使其更难攻击者的代码替换的存根对待所有购买验证。
 */
public class Security {
    private static final String TAG = "IABUtil/Security";

    private static final String KEY_FACTORY_ALGORITHM = "RSA";
    private static final String SIGNATURE_ALGORITHM = "SHA1withRSA";

    /**
     * 验证的数据与给定的签名，签署并返回验证的购买。该数据是JSON格式，并用私有密钥签署。数据还包含PurchaseState和产品ID的购买。
     * @param base64PublicKey the base64-encoded public key to use for verifying.
     * @param signedData the signed JSON string (signed, not encrypted)
     * @param signature the signature for the data, signed with the private key
     */
    public static boolean verifyPurchase(String base64PublicKey, String signedData, String signature) {
        if (signedData == null) {
            Log.e(TAG, "google返回的json数据为空");
            return false;
        }

        boolean verified = false;
        if (!TextUtils.isEmpty(signature)) {
            PublicKey key = Security.generatePublicKey(base64PublicKey);
            verified = Security.verify(key, signedData, signature);
            if (!verified) {
                Log.w(TAG, "不相匹配的key");
                return false;
            }
        }
        return true;
    }

    /**
     *把一个字符串，生成公钥实例
     *
     * @param encodedPublicKey Base64-encoded public key
     * @throws IllegalArgumentException if encodedPublicKey is invalid
     */
    public static PublicKey generatePublicKey(String encodedPublicKey) {
        try {
            byte[] decodedKey = Base64.decode(encodedPublicKey);
            //************************
            System.out.println("byte[] decodedKey : " +new String(decodedKey));
            KeyFactory keyFactory = KeyFactory.getInstance(KEY_FACTORY_ALGORITHM);
            return keyFactory.generatePublic(new X509EncodedKeySpec(decodedKey));
        } catch (Exception e) {
            Log.e(TAG, "Base64 解码错误");
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * 验证从服务器签名相匹配的计算
     *
     * @param publicKey public key associated with the developer account
     * @param signedData signed data from server
     * @param signature server signature
     * @return true if the data and signature match
     */
    public static boolean verify(PublicKey publicKey, String signedData, String signature) {
        Signature sig;
        try {
            sig = Signature.getInstance(SIGNATURE_ALGORITHM);
            sig.initVerify(publicKey);
            sig.update(signedData.getBytes());
            if (!sig.verify(Base64.decode(signature))) {
            	//********************
            	System.out.println("Base64.decode(signature) :"+Base64.decode(signature)+"signedData.getBytes() :"+signedData.getBytes()+"publicKey :"+publicKey.toString());
                Log.e(TAG, "签名验证失败");
                return false;
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Base64 解码错误.");
        }
        return false;
    }
}
