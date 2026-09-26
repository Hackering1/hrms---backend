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
        saveComponents(structure.getId(), req.components());
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

        structureComponentRepository.deleteBySalaryStructureId(id);
        saveComponents(id, req.components());
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

    private void saveComponents(Integer structureId, List<SalaryStructureRequest.ComponentLine> lines) {
        for (SalaryStructureRequest.ComponentLine line : lines) {
            if (line.salaryComponentId() == null) {
                throw new BadRequestException("Every component line needs a salaryComponentId.");
            }
            componentRepository.findById(line.salaryComponentId())
                    .orElseThrow(() -> new BadRequestException("Unknown salary component id: " + line.salaryComponentId()));

            String calcType = line.calculationType() == null ? "FLAT" : line.calculationType();
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
            boolean isBasic = c != null && "BASIC".equals(c.getCode());
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
                    c != null ? c.getIsStatutory() : false,
                    r.getDisplayOrder(),
                    belowBasicFloor
            );
        }).collect(Collectors.toList());

        return new SalaryStructureResponse(s.getId(), s.getName(), s.getDescription(), s.getIsActive(), lines);
    }
}