package com.technnext.hrms.payroll.service;

import com.technnext.hrms.common.exception.BadRequestException;
import com.technnext.hrms.common.exception.ResourceNotFoundException;
import com.technnext.hrms.payroll.dto.SalaryStructureRequest;
import com.technnext.hrms.payroll.dto.SalaryStructureResponse;
import com.technnext.hrms.payroll.entity.SalaryComponent;
import com.technnext.hrms.payroll.entity.SalaryStructure;
import com.technnext.hrms.payroll.entity.SalaryStructureComponent;
import com.technnext.hrms.payroll.repository.SalaryComponentRepository;
import com.technnext.hrms.payroll.repository.SalaryStructureComponentRepository;
import com.technnext.hrms.payroll.repository.SalaryStructureRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SalaryStructureService {

    private final SalaryStructureRepository structureRepository;
    private final SalaryStructureComponentRepository structureComponentRepository;
    private final SalaryComponentRepository componentRepository;

    @Transactional(readOnly = true)
    public List<SalaryStructureResponse> getAll() {
        return structureRepository.findAll().stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public SalaryStructureResponse getById(Integer id) {
        SalaryStructure s = structureRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Salary structure not found: " + id));
        return toResponse(s);
    }

    @Transactional
    public SalaryStructureResponse create(SalaryStructureRequest req) {
        SalaryStructure structure = SalaryStructure.builder()
                .name(req.name())
                .description(req.description())
                .isActive(req.isActive() == null ? true : req.isActive())
                .build();
        structure = structureRepository.save(structure);
        saveComponents(structure.getId(), req.components(), java.util.Set.of());
        return getById(structure.getId());
    }

    @Transactional
    public SalaryStructureResponse update(Integer id, SalaryStructureRequest req) {
        SalaryStructure structure = structureRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Salary structure not found: " + id));
        structure.setName(req.name());
        structure.setDescription(req.description());
        if (req.isActive() != null) structure.setIsActive(req.isActive());
        structureRepository.save(structure);

        // Lines already on the structure stay tolerated even if their component was deactivated since;
        // only NEWLY added lines must point at an active component.
        java.util.Set<Integer> existingComponentIds = structureComponentRepository
                .findBySalaryStructureIdOrderByDisplayOrder(id).stream()
                .map(SalaryStructureComponent::getSalaryComponentId)
                .collect(Collectors.toSet());
        // Validate BEFORE deleting so a rejected request leaves the old lines untouched
        // (the transaction would roll back anyway, this just keeps the intent explicit).
        structureComponentRepository.deleteBySalaryStructureId(id);
        saveComponents(id, req.components(), existingComponentIds);
        return getById(id);
    }

    @Transactional
    public void delete(Integer id) {
        SalaryStructure structure = structureRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Salary structure not found: " + id));
        // Soft-deactivate — employees may already be assigned to this structure historically.
        structure.setIsActive(false);
        structureRepository.save(structure);
    }

    private static final java.util.Set<String> CALC_TYPES = java.util.Set.of(
            "FLAT", "PERCENT_OF_CTC", "PERCENT_OF_BASIC", "PERCENT_OF_GROSS", "REMAINDER");

    private void saveComponents(Integer structureId, List<SalaryStructureRequest.ComponentLine> lines,
                                java.util.Set<Integer> existingComponentIds) {
        if (lines == null) {
            throw new BadRequestException("A salary structure needs at least one component line.");
        }
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        for (SalaryStructureRequest.ComponentLine line : lines) {
            if (line.salaryComponentId() == null) {
                throw new BadRequestException("Every component line needs a salaryComponentId.");
            }
            // The CTC split is keyed by component id, so a repeated component would silently
            // overwrite its earlier line and pay a different amount than the structure shows.
            if (!seen.add(line.salaryComponentId())) {
                throw new BadRequestException("Component id " + line.salaryComponentId() + " appears more than once in this structure.");
            }
            SalaryComponent component = componentRepository.findById(line.salaryComponentId())
                    .orElseThrow(() -> new BadRequestException("Unknown salary component id: " + line.salaryComponentId()));
            if (!existingComponentIds.contains(line.salaryComponentId()) && Boolean.FALSE.equals(component.getIsActive())) {
                throw new BadRequestException("Component '" + component.getName() + "' is inactive and cannot be added to a structure.");
            }

            String calcType = line.calculationType() == null ? "FLAT" : line.calculationType();
            if (!CALC_TYPES.contains(calcType)) {
                throw new BadRequestException("Unknown calculation type '" + calcType + "' for component '" + component.getName() + "'.");
            }
            boolean percentType = "PERCENT_OF_CTC".equals(calcType) || "PERCENT_OF_BASIC".equals(calcType) || "PERCENT_OF_GROSS".equals(calcType);
            if (percentType && line.percentage() != null
                    && (line.percentage().signum() <= 0 || line.percentage().compareTo(new java.math.BigDecimal("100")) > 0)) {
                throw new BadRequestException("Percentage for component '" + component.getName() + "' must be greater than 0 and at most 100.");
            }
            if (line.flatAmount() != null && line.flatAmount().signum() < 0) {
                throw new BadRequestException("Flat amount for component '" + component.getName() + "' cannot be negative.");
            }
            if (("PERCENT_OF_CTC".equals(calcType) || "PERCENT_OF_BASIC".equals(calcType) || "PERCENT_OF_GROSS".equals(calcType)) && line.percentage() == null) {
                throw new BadRequestException("Component id " + line.salaryComponentId() + " needs a percentage for calculation type " + calcType + ".");
            }

            SalaryStructureComponent row = SalaryStructureComponent.builder()
                    .salaryStructureId(structureId)
                    .salaryComponentId(line.salaryComponentId())
                    .calculationType(calcType)
                    .percentage(line.percentage())
                    .flatAmount(line.flatAmount())
                    .displayOrder(line.displayOrder() == null ? 0 : line.displayOrder())
                    .build();
            structureComponentRepository.save(row);
        }
    }

    private SalaryStructureResponse toResponse(SalaryStructure s) {
        List<SalaryStructureComponent> rows = structureComponentRepository.findBySalaryStructureIdOrderByDisplayOrder(s.getId());
        Map<Integer, SalaryComponent> componentsById = componentRepository.findAllById(
                rows.stream().map(SalaryStructureComponent::getSalaryComponentId).toList()
        ).stream().collect(Collectors.toMap(SalaryComponent::getId, c -> c));

        List<SalaryStructureResponse.ComponentLine> lines = rows.stream().map(r -> {
            SalaryComponent c = componentsById.get(r.getSalaryComponentId());
            boolean isBasic = c != null && "Basic Salary".equals(c.getName());
            // Wage-code floor: Basic must be at least 50% of the CTC/Gross split it's
            // defined against (PERCENT_OF_CTC or PERCENT_OF_GROSS). Flag only applies
            // to the Basic component and only when it's percentage-based.
            Boolean belowBasicFloor = null;
            if (isBasic
                    && ("PERCENT_OF_CTC".equals(r.getCalculationType()) || "PERCENT_OF_GROSS".equals(r.getCalculationType()))
                    && r.getPercentage() != null) {
                belowBasicFloor = r.getPercentage().compareTo(new java.math.BigDecimal("50")) < 0;
            }
            return new SalaryStructureResponse.ComponentLine(
                    r.getSalaryComponentId(),
                    c != null ? c.getName() : "(unknown)",
                    c != null ? c.getCode() : null,
                    c != null ? c.getComponentType() : null,
                    r.getCalculationType(),
                    r.getPercentage(),
                    r.getFlatAmount(),
                    Boolean.TRUE.equals(c != null ? c.getIsStatutory() : null),
                    r.getDisplayOrder(),
                    belowBasicFloor
            );
        }).collect(Collectors.toList());

        return new SalaryStructureResponse(s.getId(), s.getName(), s.getDescription(), s.getIsActive(), lines);
    }
}