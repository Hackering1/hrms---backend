package com.technnext.hrms.plan.dto;

import com.technnext.hrms.plan.entity.CompanySubscription;
import com.technnext.hrms.plan.entity.SubscriptionPlan;

/** The active subscription with its plan and current employee usage. subscription/plan are null if none. */
public record CurrentPlanResponse(
        CompanySubscription subscription,
        SubscriptionPlan plan,
        long employeeCount,
        Integer maxEmployees,
        boolean overLimit
) {}