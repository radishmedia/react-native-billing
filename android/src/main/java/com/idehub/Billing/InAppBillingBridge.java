package com.idehub.Billing;

import android.app.Activity;
import android.content.Intent;
import android.util.Log;

import com.anjlab.android.iab.v3.BillingProcessor;
import com.anjlab.android.iab.v3.PurchaseData;
import com.anjlab.android.iab.v3.SkuDetails;
import com.anjlab.android.iab.v3.TransactionDetails;
import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InAppBillingBridge extends ReactContextBaseJavaModule implements ActivityEventListener, BillingProcessor.IBillingHandler {
    static final String LOG_TAG = "rnbilling";

    ReactApplicationContext _reactContext;
    String LICENSE_KEY = null;
    BillingProcessor bp;
    Boolean mShortCircuit = false;
    int PURCHASE_FLOW_REQUEST_CODE = 32459;
    int BILLING_RESPONSE_RESULT_OK = 0;
    String RESPONSE_CODE = "RESPONSE_CODE";
    HashMap<String, Promise> mPromiseCache = new HashMap<>();

    public InAppBillingBridge(ReactApplicationContext reactContext, String licenseKey) {
        super(reactContext);
        _reactContext = reactContext;
        LICENSE_KEY = licenseKey;

        reactContext.addActivityEventListener(this);
    }

    public InAppBillingBridge(ReactApplicationContext reactContext) {
        super(reactContext);
        _reactContext = reactContext;
        int keyResourceId = _reactContext
                .getResources()
                .getIdentifier("RNB_GOOGLE_PLAY_LICENSE_KEY", "string", _reactContext.getPackageName());
        LICENSE_KEY = _reactContext.getString(keyResourceId);

        reactContext.addActivityEventListener(this);
    }

    @Override
    public String getName() {
        return "InAppBillingBridge";
    }

    @Override
    public Map<String, Object> getConstants() {
        final Map<String, Object> constants = new HashMap<>();
        return constants;
    }

    @Override
    public void onBillingInitialized() {
        resolvePromise(PromiseConstants.OPEN, true);
    }

    @ReactMethod
    public void open(final Promise promise) {
        if (!isIabServiceAvailable()) {
            promise.reject("E_NO_EMULATOR", "InAppBilling is not available. InAppBilling will not work/test on an emulator, only a physical Android device.");
            return;
        }

        if (bp != null) {
            promise.reject("E_CONNECTION", "Channel is already open. Call close() on InAppBilling to be able to open().");
            return;
        }

        clearPromises();

        if (putPromise(PromiseConstants.OPEN, promise)) {
            try {
                bp = new BillingProcessor(_reactContext, LICENSE_KEY, this);
            } catch (Exception ex) {
                rejectPromise(PromiseConstants.OPEN, "E_CONNECTION", ex.getMessage(), ex);
            }
        } else {
            promise.reject("E_UNKNOWN", "Previous open operation is not resolved.");
        }
    }

    @ReactMethod
    public void close(final Promise promise) {
        if (bp != null) {
            bp.release();
            bp = null;
        }

        clearPromises();
        promise.resolve(true);
    }

    @ReactMethod
    public void loadOwnedPurchasesFromGoogle(final Promise promise) {
        if (bp == null) {
            promise.reject("E_CONNECTION", "Channel is not opened. Call open() on InAppBilling.");
            return;
        }

        bp.loadOwnedPurchasesFromGoogle();
        promise.resolve(true);
    }

    @Override
    public void onProductPurchased(String productId, TransactionDetails details) {
        if (details == null) {
            rejectPromise(PromiseConstants.PURCHASE_OR_SUBSCRIBE, "E_UNKNOWN", "There is no transaction information.", null);
            return;
        }

        if (!productId.equals(details.purchaseInfo.purchaseData.productId)) {
            rejectPromise(PromiseConstants.PURCHASE_OR_SUBSCRIBE, "E_UNKNOWN", "Product id does not match.", null);
            return;
        }

        WritableMap map = mapTransactionDetails(details);
        resolvePromise(PromiseConstants.PURCHASE_OR_SUBSCRIBE, map);
    }

    @Override
    public void onBillingError(int errorCode, Throwable error) {
        if (!hasPromise(PromiseConstants.PURCHASE_OR_SUBSCRIBE)) {
            return;
        }

        String errorMessage = error == null ? "Could not purchase product." : error.getMessage();

        rejectPromise(PromiseConstants.PURCHASE_OR_SUBSCRIBE, getResponseCode(errorCode), errorMessage, error);
    }

    @ReactMethod
    public void purchase(final String productId, final String developerPayload, final Promise promise) {
        if (bp == null) {
            promise.reject("E_CONNECTION", "Channel is not opened. Call open() on InAppBilling.");
            return;
        }

        if (putPromise(PromiseConstants.PURCHASE_OR_SUBSCRIBE, promise)) {
            boolean purchaseProcessStarted = bp.purchase(getCurrentActivity(), productId, developerPayload);
            if (!purchaseProcessStarted)
                rejectPromise(PromiseConstants.PURCHASE_OR_SUBSCRIBE, "E_UNKNOWN", "Could not start purchase process.", null);
        } else {
            promise.reject("E_UNKNOWN", "Previous purchase or subscribe operation is not resolved.");
        }
    }

    @ReactMethod
    public void consumePurchase(final String productId, final Promise promise) {
        if (bp == null) {
            promise.reject("E_CONNECTION", "Channel is not opened. Call open() on InAppBilling.");
            return;
        }

        try {
            boolean consumed = bp.consumePurchase(productId);
            if (consumed)
                promise.resolve(true);
            else
                promise.reject("E_UNKNOWN", "Could not consume purchase");
        } catch (Exception ex) {
            promise.reject("E_UNKNOWN", ex.getMessage(), ex);
        }
    }

    @ReactMethod
    public void subscribe(final String productId, final String developerPayload, final Promise promise) {
        if (bp == null) {
            promise.reject("E_CONNECTION", "Channel is not opened. Call open() on InAppBilling.");
            return;
        }

        if (putPromise(PromiseConstants.PURCHASE_OR_SUBSCRIBE, promise)) {
            boolean subscribeProcessStarted = bp.subscribe(getCurrentActivity(), productId, developerPayload);
            if (!subscribeProcessStarted)
                rejectPromise(PromiseConstants.PURCHASE_OR_SUBSCRIBE, "E_UNKNOWN", "Could not start subscribe process.", null);
        } else {
            promise.reject("E_UNKNOWN", "Previous subscribe or purchase operation is not resolved.");
        }
    }

    @ReactMethod
    public void updateSubscription(final ReadableArray oldProductIds, final String productId, final String developerPayload, final Promise promise) {
        if (bp == null) {
            promise.reject("E_CONNECTION", "Channel is not opened. Call open() on InAppBilling.");
            return;
        }

        if (putPromise(PromiseConstants.PURCHASE_OR_SUBSCRIBE, promise)) {
            ArrayList<String> oldProductIdList = new ArrayList<>();
            for (int i = 0; i < oldProductIds.size(); i++) {
                oldProductIdList.add(oldProductIds.getString(i));
            }

            boolean updateProcessStarted = bp.updateSubscription(getCurrentActivity(), oldProductIdList, productId, developerPayload);

            if (!updateProcessStarted)
                rejectPromise(PromiseConstants.PURCHASE_OR_SUBSCRIBE, "E_UNKNOWN", "Could not start subscribe process.", null);
        } else {
            promise.reject("E_UNKNOWN", "Previous subscribe or purchase operation is not resolved.");
        }
    }

    @ReactMethod
    public void isSubscribed(final String productId, final Promise promise) {
        if (bp == null) {
            promise.reject("E_CONNECTION", "Channel is not opened. Call open() on InAppBilling.");
            return;
        }

        boolean subscribed = bp.isSubscribed(productId);
        promise.resolve(subscribed);
    }

    @ReactMethod
    public void isPurchased(final String productId, final Promise promise) {
        if (bp == null) {
            promise.reject("E_CONNECTION", "Channel is not opened. Call open() on InAppBilling.");
            return;
        }

        boolean purchased = bp.isPurchased(productId);
        promise.resolve(purchased);
    }

    @ReactMethod
    public void isOneTimePurchaseSupported(final Promise promise) {
        if (bp == null) {
            promise.reject("E_CONNECTION", "Channel is not opened. Call open() on InAppBilling.");
            return;
        }

        boolean oneTimePurchaseSupported = bp.isOneTimePurchaseSupported();
        promise.resolve(oneTimePurchaseSupported);
    }

    @ReactMethod
    public void isValidTransactionDetails(final String productId, final Promise promise) {
        if (bp == null) {
            promise.reject("E_CONNECTION", "Channel is not opened. Call open() on InAppBilling.");
            return;
        }

        try {
            TransactionDetails details = bp.getPurchaseTransactionDetails(productId);
            promise.resolve(bp.isValidTransactionDetails(details));
        } catch (Exception ex) {
            promise.reject("E_UNKNOWN", "Could not validate transaction details", ex);
        }
    }

    @ReactMethod
    public void listOwnedProducts(final Promise promise) {
        if (bp == null) {
            promise.reject("E_CONNECTION", "Channel is not opened. Call open() on InAppBilling.");
            return;
        }

        List<String> purchasedProductIds = bp.listOwnedProducts();
        WritableArray arr = Arguments.createArray();

        for (int i = 0; i < purchasedProductIds.size(); i++) {
            arr.pushString(purchasedProductIds.get(i));
        }

        promise.resolve(arr);
    }

    @ReactMethod
    public void listOwnedSubscriptions(final Promise promise) {
        if (bp == null) {
            promise.reject("E_CONNECTION", "Channel is not opened. Call open() on InAppBilling.");
            return;
        }

        List<String> ownedSubscriptionsIds = bp.listOwnedSubscriptions();
        WritableArray arr = Arguments.createArray();

        for (int i = 0; i < ownedSubscriptionsIds.size(); i++) {
            arr.pushString(ownedSubscriptionsIds.get(i));
        }

        promise.resolve(arr);
    }

    @ReactMethod
    public void getProductDetails(final ReadableArray productIds, final Promise promise) {
        if (bp == null) {
            promise.reject("E_CONNECTION", "Channel is not opened. Call open() on InAppBilling.");
            return;
        }

        try {
            ArrayList<String> productIdList = new ArrayList<>();
            for (int i = 0; i < productIds.size(); i++) {
                productIdList.add(productIds.getString(i));
            }

            List<SkuDetails> details = bp.getPurchaseListingDetails(productIdList);

            if (details != null) {
                WritableArray arr = Arguments.createArray();
                for (SkuDetails detail : details) {
                    if (detail != null) {
                        WritableMap map = Arguments.createMap();

                        map.putString("productId", detail.productId);
                        map.putString("title", detail.title);
                        map.putString("description", detail.description);
                        map.putBoolean("isSubscription", detail.isSubscription);
                        map.putString("currency", detail.currency);
                        map.putDouble("priceValue", detail.priceValue);
                        map.putString("priceText", detail.priceText);
                        arr.pushMap(map);
                    }
                }

                promise.resolve(arr);
            } else {
                promise.reject("E_UNKNOWN", "Could not find product details.");
            }
        } catch (Exception ex) {
            promise.reject("E_UNKNOWN", "Could not get product details.", ex);
        }
    }

    @ReactMethod
    public void getSubscriptionDetails(final ReadableArray productIds, final Promise promise) {
        if (bp == null) {
            promise.reject("E_UNKNOWN", "Channel is not opened. Call open() on InAppBilling.");
            return;
        }

        try {
            ArrayList<String> productIdList = new ArrayList<>();
            for (int i = 0; i < productIds.size(); i++) {
                productIdList.add(productIds.getString(i));
            }

            List<SkuDetails> details = bp.getSubscriptionListingDetails(productIdList);

            if (details != null) {
                WritableArray arr = Arguments.createArray();
                for (SkuDetails detail : details) {
                    if (detail != null) {
                        WritableMap map = Arguments.createMap();

                        map.putString("productId", detail.productId);
                        map.putString("title", detail.title);
                        map.putString("description", detail.description);
                        map.putBoolean("isSubscription", detail.isSubscription);
                        map.putString("currency", detail.currency);
                        map.putDouble("priceValue", detail.priceValue);
                        map.putString("priceText", detail.priceText);
                        map.putString("subscriptionPeriod", detail.subscriptionPeriod);
                        if (detail.subscriptionFreeTrialPeriod != null)
                            map.putString("subscriptionFreeTrialPeriod", detail.subscriptionFreeTrialPeriod);
                        map.putBoolean("haveTrialPeriod", detail.haveTrialPeriod);
                        map.putDouble("introductoryPriceValue", detail.introductoryPriceValue);
                        if (detail.introductoryPriceText != null)
                            map.putString("introductoryPriceText", detail.introductoryPriceText);
                        if (detail.introductoryPricePeriod != null)
                            map.putString("introductoryPricePeriod", detail.introductoryPricePeriod);
                        map.putBoolean("haveIntroductoryPeriod", detail.haveIntroductoryPeriod);
                        map.putInt("introductoryPriceCycles", detail.introductoryPriceCycles);
                        arr.pushMap(map);
                    }
                }

                promise.resolve(arr);
            } else {
                promise.reject("E_UNKNOWN", "Could not find subscription details.");
            }
        } catch (Exception ex) {
            promise.reject("E_UNKNOWN", "Could not get subscription details.", ex);
        }
    }

    @ReactMethod
    public void getPurchaseTransactionDetails(final String productId, final Promise promise) {
        if (bp == null) {
            promise.reject("E_UNKNOWN", "Channel is not opened. Call open() on InAppBilling.");
            return;
        }

        TransactionDetails details = bp.getPurchaseTransactionDetails(productId);
        if (details != null && productId.equals(details.purchaseInfo.purchaseData.productId)) {
            WritableMap map = mapTransactionDetails(details);
            promise.resolve(map);
        } else {
            promise.reject("E_UNKNOWN", "Could not find transaction details for product id.");
        }
    }

    @ReactMethod
    public void getSubscriptionTransactionDetails(final String productId, final Promise promise) {
        if (bp == null) {
            promise.reject("E_UNKNOWN", "Channel is not opened. Call open() on InAppBilling.");
            return;
        }

        TransactionDetails details = bp.getSubscriptionTransactionDetails(productId);
        if (details != null && productId.equals(details.purchaseInfo.purchaseData.productId)) {
            WritableMap map = mapTransactionDetails(details);
            promise.resolve(map);
        } else {
            promise.reject("E_UNKNOWN", "Could not find transaction details for product id.");
        }
    }

    private WritableMap mapTransactionDetails(TransactionDetails details) {
        WritableMap map = Arguments.createMap();

        map.putString("receiptData", details.purchaseInfo.responseData);

        if (details.purchaseInfo.signature != null)
            map.putString("receiptSignature", details.purchaseInfo.signature);

        PurchaseData purchaseData = details.purchaseInfo.purchaseData;

        map.putString("productId", purchaseData.productId);
        map.putString("orderId", purchaseData.orderId);
        map.putString("purchaseToken", purchaseData.purchaseToken);
        map.putString("purchaseTime", purchaseData.purchaseTime == null
                ? "" : purchaseData.purchaseTime.toString());
        map.putString("purchaseState", purchaseData.purchaseState == null
                ? "" : purchaseData.purchaseState.toString());
        map.putBoolean("autoRenewing", purchaseData.autoRenewing);

        if (purchaseData.developerPayload != null) {
            map.putString("developerPayload", purchaseData.developerPayload);
        }

        return map;
    }

    @Override
    public void onPurchaseHistoryRestored() {
        /*
         * Called when purchase history was restored and the list of all owned PRODUCT ID's
         * was loaded from Google Play
         */
    }

    private Boolean isIabServiceAvailable() {
        return BillingProcessor.isIabServiceAvailable(_reactContext);
    }

    public void onActivityResult(final Activity activity, final int requestCode, final int resultCode, final Intent intent) {
        if (mShortCircuit) {
            shortCircuitActivityResult(activity, requestCode, resultCode, intent);
            return;
        }

        if (bp != null) {
            bp.handleActivityResult(requestCode, resultCode, intent);
        }
    }

    @ReactMethod
    public void shortCircuitPurchaseFlow(final Boolean enable) {
        mShortCircuit = enable;
    }

    private void shortCircuitActivityResult(final Activity activity, final int requestCode, final int resultCode, final Intent intent) {
        if (requestCode != PURCHASE_FLOW_REQUEST_CODE) {
            return;
        }

        int responseCode = intent.getIntExtra(RESPONSE_CODE, BILLING_RESPONSE_RESULT_OK);
        if (resultCode == Activity.RESULT_OK && responseCode == BILLING_RESPONSE_RESULT_OK) {
            resolvePromise(PromiseConstants.PURCHASE_OR_SUBSCRIBE, true);
        } else {
            rejectPromise(PromiseConstants.PURCHASE_OR_SUBSCRIBE, getResponseCode(responseCode), "An error has occurred. Code: " + requestCode, null);
        }
    }

    @Override
    public void onNewIntent(Intent intent) {

    }

    synchronized void resolvePromise(String key, Object value) {
        if (mPromiseCache.containsKey(key)) {
            Promise promise = mPromiseCache.get(key);
            promise.resolve(value);
            mPromiseCache.remove(key);
        } else {
            Log.w(LOG_TAG, String.format("Tried to resolve promise: %s - but does not exist in cache", key));
        }
    }

    synchronized void rejectPromise(String key, String code, String reason, Throwable throwable) {
        if (mPromiseCache.containsKey(key)) {
            Promise promise = mPromiseCache.get(key);
            promise.reject(code, reason, throwable);
            mPromiseCache.remove(key);
        } else {
            Log.w(LOG_TAG, String.format("Tried to reject promise: %s - but does not exist in cache", key));
        }
    }

    synchronized Boolean putPromise(String key, Promise promise) {
        if (!mPromiseCache.containsKey(key)) {
            mPromiseCache.put(key, promise);
            return true;
        } else {
            Log.w(LOG_TAG, String.format("Tried to put promise: %s - already exists in cache", key));
        }
        return false;
    }

    synchronized Boolean hasPromise(String key) {
        return mPromiseCache.containsKey(key);
    }

    synchronized void clearPromises() {
        mPromiseCache.clear();
    }

    // https://developer.android.com/google/play/billing/billing_reference
    synchronized String getResponseCode(Integer code) {
        final Map<Integer, String> constants = new HashMap<>();

        constants.put(1, "E_USER_CANCELED");
        constants.put(2, "E_SERVICE_UNAVAILABLE");
        constants.put(3, "E_BILLING_UNAVAILABLE");
        constants.put(4, "E_ITEM_UNAVAILABLE");
        constants.put(5, "E_DEVELOPER_ERROR");
        constants.put(6, "E_API_ERROR");
        constants.put(7, "E_ITEM_ALREADY_OWNED");
        constants.put(8, "E_ITEM_NOT_OWNED");

        if (!constants.containsKey(code)) {
            return "E_UNKNOWN";
        }

        String resCode = constants.get(code);

        return resCode;
    }
}
