"use strict";

const InAppBillingBridge = require("react-native").NativeModules.InAppBillingBridge;

class InAppBilling {
    static open() {
      return InAppBillingBridge.open();
    }

    static close() {
      return InAppBillingBridge.close();
    }

    static purchase(productId) {
      return InAppBillingBridge.purchase(productId);
    }

    static consumePurchase(productId) {
      return InAppBillingBridge.consumePurchase(productId);
    }

    static subscribe(productId) {
      return InAppBillingBridge.subscribe(productId);
    }

    static isSubscribed(productId) {
      return InAppBillingBridge.isSubscribed(productId);
    }

    static isPurchased(productId) {
      return InAppBillingBridge.isPurchased(productId);
    }

    static listOwnedProducts() {
      return InAppBillingBridge.listOwnedProducts();
    }

    static listOwnedSubscriptions() {
      return InAppBillingBridge.listOwnedSubscriptions();
    }

    static getProductDetails(productId) {
      return InAppBillingBridge.getProductDetails([productId])
        .then((arr) => {
          if (arr != null && arr.length > 0) {
            return Promise.resolve(arr[0]);
          } else {
            return Promise.reject("Could not find details.");
          }
        })
        .catch((error) => {
          return Promise.reject(error);
        });
    }

    static getProductDetailsArray(productIds) {
      return InAppBillingBridge.getProductDetails(productIds);
    }

    static getSubscriptionDetails(productId) {
      return InAppBillingBridge.getSubscriptionDetails([productId])
        .then((arr) => {
          if (arr != null && arr.length > 0) {
            return Promise.resolve(arr[0]);
          } else {
            return Promise.reject("Could not find details.");
          }
        })
        .catch((error) => {
          return Promise.reject(error);
        });
    }

    static getSubscriptionDetailsArray(productIds) {
      return InAppBillingBridge.getSubscriptionDetails(productIds);
    }
}

module.exports = InAppBilling;
