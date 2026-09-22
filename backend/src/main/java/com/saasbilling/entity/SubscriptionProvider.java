package com.saasbilling.entity;

/**
 * MANUAL is the only provider implemented right now (see V8 migration
 * comment). RAZORPAY/STRIPE are reserved for a future real gateway
 * integration.
 */
public enum SubscriptionProvider {
    MANUAL,
    RAZORPAY,
    STRIPE
}
