package com.technnext.hrms.plan.dto;

/** billingCycle: MONTHLY or YEARLY */
public record SubscribeRequest(Integer planId, String billingCycle) {}