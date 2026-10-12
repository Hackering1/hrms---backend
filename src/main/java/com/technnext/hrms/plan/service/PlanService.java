package com.technnext.hrms.plan.service;

import com.technnext.hrms.common.exception.BadRequestException;
import com.technnext.hrms.common.exception.ResourceNotFoundException;
import com.technnext.hrms.employee.repository.EmployeeRepository;
import com.technnext.hrms.plan.dto.CurrentPlanResponse;
import com.technnext.hrms.plan.dto.PlanRequest;
import com.technnext.hrms.plan.dto.SubscribeRequest;
import com.technnext.hrms.plan.entity.CompanySubscription;
import com.technnext.hrms.plan.entity.SubscriptionPlan;
import com.technnext.hrms.plan.repository.CompanySubscriptionRepository;
import com.technnext.hrms.plan.repository.SubscriptionPlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * The company's own plan catalogue and which plan it is on. This is bookkeeping for a SINGLE
 * company (no multi-tenancy): it records the plan, billing cycle, renewal date and employee-cap
 * usage. It does not switch modules on or off and does not block adding employees.
 */
@Service
@RequiredArgsConstructor
public class PlanService {

    private static final BigDecimal MAX_PRICE = new BigDecimal("100000000");

    private final SubscriptionPlanRepository planRepository;
    private final CompanySubscriptionRepository subscriptionRepository;
    private final EmployeeRepository employeeRepository;

    // ── Plans ───────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<SubscriptionPlan> listPlans() {
        return planRepository.findAllByOrderByPriceMonthlyAsc();
    }

    @Transactional
    public SubscriptionPlan createPlan(PlanRequest req) {
        Validated v = validate(req, null);
        return planRepository.save(SubscriptionPlan.builder()
                .name(v.name).code(v.code)
                .priceMonthly(req.priceMonthly()).priceYearly(req.priceYearly())
                .maxEmployees(req.maxEmployees()).features(clean(req.features()))
                .isActive(req.isActive() == null ? true : req.isActive())
                .build());
    }

    @Transactional
    public SubscriptionPlan updatePlan(Integer id, PlanRequest req) {
        SubscriptionPlan p = planRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Plan", id));
        Validated v = validate(req, p);
        p.setName(v.name);
        p.setCode(v.code);
        p.setPriceMonthly(req.priceMonthly());
        p.setPriceYearly(req.priceYearly());
        p.setMaxEmployees(req.maxEmployees());
        p.setFeatures(clean(req.features()));
        if (req.isActive() != null) p.setIsActive(req.isActive());
        return planRepository.save(p);
    }

    // ── Subscription ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public CurrentPlanResponse current() {
        long count = employeeCount();
        CompanySubscription sub = subscriptionRepository.findFirstByStatusOrderByIdDesc("ACTIVE").orElse(null);
        if (sub == null) return new CurrentPlanResponse(null, null, count, null, false);
        SubscriptionPlan plan = planRepository.findById(sub.getPlanId()).orElse(null);
        Integer max = plan == null ? null : plan.getMaxEmployees();
        return new CurrentPlanResponse(sub, plan, count, max, max != null && count > max);
    }

    @Transactional(readOnly = true)
    public List<CompanySubscription> history() {
        return subscriptionRepository.findAllByOrderByIdDesc();
    }

    @Transactional
    public CurrentPlanResponse subscribe(SubscribeRequest req) {
        if (req == null || req.planId() == null) throw new BadRequestException("Choose a plan.");
        String cycle = req.billingCycle() == null ? "" : req.billingCycle().trim().toUpperCase();
        if (!"MONTHLY".equals(cycle) && !"YEARLY".equals(cycle)) {
            throw new BadRequestException("Billing cycle must be MONTHLY or YEARLY.");
        }
        SubscriptionPlan plan = planRepository.findById(req.planId())
                .orElseThrow(() -> new BadRequestException("Unknown plan."));
        if (!Boolean.TRUE.equals(plan.getIsActive())) {
            throw new BadRequestException("Plan '" + plan.getName() + "' is not available.");
        }
        long count = employeeCount();
        if (plan.getMaxEmployees() != null && count > plan.getMaxEmployees()) {
            throw new BadRequestException("Plan '" + plan.getName() + "' allows up to " + plan.getMaxEmployees()
                    + " employees but you currently have " + count + ".");
        }
        LocalDate today = LocalDate.now();
        subscriptionRepository.findFirstByStatusOrderByIdDesc("ACTIVE").ifPresent(old -> {
            if (old.getPlanId().equals(plan.getId()) && cycle.equals(old.getBillingCycle())) {
                throw new BadRequestException("You are already on this plan and billing cycle.");
            }
            old.setStatus("REPLACED");
            old.setEndDate(today);
            subscriptionRepository.save(old);
        });
        subscriptionRepository.save(CompanySubscription.builder()
                .planId(plan.getId()).billingCycle(cycle).startDate(today)
                .endDate("YEARLY".equals(cycle) ? today.plusYears(1) : today.plusMonths(1))
                .status("ACTIVE").build());
        return current();
    }

    @Transactional
    public CurrentPlanResponse cancel() {
        CompanySubscription sub = subscriptionRepository.findFirstByStatusOrderByIdDesc("ACTIVE")
                .orElseThrow(() -> new BadRequestException("There is no active subscription to cancel."));
        sub.setStatus("CANCELLED");
        sub.setEndDate(LocalDate.now());
        subscriptionRepository.save(sub);
        return current();
    }

    // ── helpers ─────────────────────────────────────────────────────────────────

    private long employeeCount() {
        return employeeRepository.findAll().stream()
                .filter(e -> !"DELETED".equalsIgnoreCase(e.getStatus()))
                .count();
    }

    private record Validated(String name, String code) {}

    private Validated validate(PlanRequest req, SubscriptionPlan existing) {
        String name = req == null ? null : clean(req.name());
        String code = req == null ? null : clean(req.code());
        if (name == null || code == null) throw new BadRequestException("Plan name and code are required.");
        if (name.length() > 100 || code.length() > 40) throw new BadRequestException("Plan name (max 100) or code (max 40) is too long.");
        code = code.toUpperCase();
        if (req.priceMonthly() == null || req.priceYearly() == null
                || req.priceMonthly().signum() < 0 || req.priceYearly().signum() < 0) {
            throw new BadRequestException("Monthly and yearly prices are required and can't be negative.");
        }
        if (req.priceMonthly().compareTo(MAX_PRICE) > 0 || req.priceYearly().compareTo(MAX_PRICE) > 0) {
            throw new BadRequestException("Price is too large.");
        }
        if (req.maxEmployees() != null && req.maxEmployees() < 1) {
            throw new BadRequestException("Employee cap must be at least 1 (or empty for unlimited).");
        }
        if (req.features() != null && req.features().length() > 1000) {
            throw new BadRequestException("Features text is too long (max 1000 characters).");
        }
        if ((existing == null || !name.equalsIgnoreCase(existing.getName())) && planRepository.existsByNameIgnoreCase(name)) {
            throw new BadRequestException("A plan named '" + name + "' already exists.");
        }
        if ((existing == null || !code.equalsIgnoreCase(existing.getCode())) && planRepository.existsByCodeIgnoreCase(code)) {
            throw new BadRequestException("A plan with code '" + code + "' already exists.");
        }
        return new Validated(name, code);
    }

    private static String clean(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}