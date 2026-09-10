package com.technnext.hrms.payroll.service;

import com.technnext.hrms.common.exception.BadRequestException;
import com.technnext.hrms.common.exception.ResourceNotFoundException;
import com.technnext.hrms.employee.entity.Employee;
import com.technnext.hrms.employee.repository.EmployeeRepository;
import com.technnext.hrms.payroll.dto.EmployeeSalaryAssignRequest;
import com.technnext.hrms.payroll.dto.EmployeeSalaryResponse;
import com.technnext.hrms.payroll.entity.*;
import com.technnext.hrms.payroll.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EmployeeSalaryService {

    private final EmployeeSalaryRepository employeeSalaryRepository;
    private final EmployeeSalaryComponentRepository employeeSalaryComponentRepository;
    private final SalaryStructureRepository salaryStructureRepository;
    private final SalaryStructureComponentRepository salaryStructureComponentRepository;
    private final SalaryComponentRepository salaryComponentRepository;
    private final EmployeeRepository employeeRepository;
    private final SalaryBreakupCalculator breakupCalculator;

    @Transactional(readOnly = true)
    public List<EmployeeSalaryResponse> history(UUID employeeId) {
        return employeeSalaryRepository.findByEmployeeIdOrderByEffectiveFromDesc(employeeId).stream()
                .map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Optional<EmployeeSalaryResponse> currentActive(UUID employeeId, LocalDate asOf) {
        return employeeSalaryRepository.findCurrentActive(employeeId, asOf).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public EmployeeSalary currentActiveEntity(UUID employeeId, LocalDate asOf) {
        return employeeSalaryRepository.findCurrentActive(employeeId, asOf)
                .orElseThrow(() -> new ResourceNotFoundException("No active salary structure assigned for this employee as of " + asOf));
    }

    /**
     * Assigns a new CTC/structure to an employee, closing out any previously
     * active assignment the day before this one starts (so history never overlaps).
     */
    @Transactional
    public EmployeeSalaryResponse assign(EmployeeSalaryAssignRequest req) {
        Employee employee = employeeRepository.findById(req.employeeId())
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + req.employeeId()));
        SalaryStructure structure = salaryStructureRepository.findById(req.salaryStructureId())
                .orElseThrow(() -> new ResourceNotFoundException("Salary structure not found: " + req.salaryStructureId()));
        if (Boolean.FALSE.equals(structure.getIsActive())) {
            throw new BadRequestException("Salary structure '" + structure.getName() + "' is not active.");
        }

        // Close out any assignment still open as of the new effectiveFrom.
        employeeSalaryRepository.findActiveAsOf(req.employeeId(), req.effectiveFrom()).forEach(prev -> {
            prev.setEffectiveTo(req.effectiveFrom().minusDays(1));
            employeeSalaryRepository.save(prev);
        });

        EmployeeSalary salary = EmployeeSalary.builder()
                .employeeId(req.employeeId())
                .salaryStructureId(req.salaryStructureId())
                .annualCtc(req.annualCtc())
                .effectiveFrom(req.effectiveFrom())
                .effectiveTo(null)
                .taxRegime(req.taxRegime() == null ? "NEW" : req.taxRegime())
                .pfApplicable(req.pfApplicable() == null ? true : req.pfApplicable())
                .ptApplicable(req.ptApplicable() == null ? true : req.ptApplicable())
                .tdsOverrideMonthly(req.tdsOverrideMonthly())
                .bankAccountNumber(req.bankAccountNumber() != null ? req.bankAccountNumber() : employee.getBankAccountNumber())
                .build();
        salary = employeeSalaryRepository.save(salary);

        computeAndStoreBreakup(salary, structure);

        return toResponse(salary);
    }

    private void computeAndStoreBreakup(EmployeeSalary salary, SalaryStructure structure) {
        List<SalaryStructureComponent> structureComponents =
                salaryStructureComponentRepository.findBySalaryStructureIdOrderByDisplayOrder(structure.getId());

        Map<Integer, SalaryComponent> componentsById = salaryComponentRepository.findAllById(
                structureComponents.stream().map(SalaryStructureComponent::getSalaryComponentId).toList()
        ).stream().collect(Collectors.toMap(SalaryComponent::getId, c -> c));

        // Statutory components (PF/PT/TDS/employer PF) are computed per payroll run,
        // not baked into the static CTC breakup.
        List<SalaryStructureComponent> earningComponents = structureComponents.stream()
                .filter(sc -> {
                    SalaryComponent c = componentsById.get(sc.getSalaryComponentId());
                    return c != null && !Boolean.TRUE.equals(c.getIsStatutory());
                })
                .toList();

        SalaryComponent basic = salaryComponentRepository.findAll().stream()
                .filter(c -> "BASIC".equals(c.getCode()))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("No salary component with code BASIC exists — required as the base for PERCENT_OF_BASIC components (e.g. HRA)."));

        List<SalaryBreakupCalculator.ComponentAmount> amounts =
                breakupCalculator.computeBreakup(salary.getAnnualCtc(), earningComponents, basic.getId());

        employeeSalaryComponentRepository.deleteByEmployeeSalaryId(salary.getId());
        for (SalaryBreakupCalculator.ComponentAmount a : amounts) {
            employeeSalaryComponentRepository.save(EmployeeSalaryComponent.builder()
                    .employeeSalaryId(salary.getId())
                    .salaryComponentId(a.salaryComponentId())
                    .monthlyAmount(a.monthlyAmount())
                    .annualAmount(a.annualAmount())
                    .build());
        }
    }

    private EmployeeSalaryResponse toResponse(EmployeeSalary salary) {
        Employee employee = employeeRepository.findById(salary.getEmployeeId()).orElse(null);
        SalaryStructure structure = salaryStructureRepository.findById(salary.getSalaryStructureId()).orElse(null);

        List<EmployeeSalaryComponent> rows = employeeSalaryComponentRepository.findByEmployeeSalaryId(salary.getId());
        Map<Integer, SalaryComponent> componentsById = salaryComponentRepository.findAllById(
                rows.stream().map(EmployeeSalaryComponent::getSalaryComponentId).toList()
        ).stream().collect(Collectors.toMap(SalaryComponent::getId, c -> c));

        BigDecimal monthlyGross = rows.stream().map(EmployeeSalaryComponent::getMonthlyAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        List<EmployeeSalaryResponse.ComponentBreakup> breakup = rows.stream().map(r -> {
            SalaryComponent c = componentsById.get(r.getSalaryComponentId());
            return new EmployeeSalaryResponse.ComponentBreakup(
                    c != null ? c.getName() : "(unknown)",
                    c != null ? c.getComponentType() : null,
                    r.getMonthlyAmount(),
                    r.getAnnualAmount()
            );
        }).collect(Collectors.toList());

        return new EmployeeSalaryResponse(
                salary.getId(),
                salary.getEmployeeId(),
                employee != null ? employee.getEmployeeCode() : null,
                employee != null ? employee.getFirstName() + " " + employee.getLastName() : null,
                salary.getSalaryStructureId(),
                structure != null ? structure.getName() : null,
                salary.getAnnualCtc(),
                monthlyGross,
                salary.getEffectiveFrom(),
                salary.getEffectiveTo(),
                salary.getTaxRegime(),
                salary.getPfApplicable(),
                salary.getPtApplicable(),
                salary.getTdsOverrideMonthly(),
                breakup
        );
    }
}