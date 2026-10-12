package com.technnext.hrms.plan.dto;

import java.math.BigDecimal;

public record PlanRequest(String name, String code, BigDecimal priceMonthly, BigDecimal priceYearly,
                          Integer maxEmployees, String features, Boolean isActive) {}