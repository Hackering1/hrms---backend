package com.technnext.hrms.letter.service;

import com.technnext.hrms.common.exception.ResourceNotFoundException;
import com.technnext.hrms.employee.entity.Employee;
import com.technnext.hrms.employee.repository.EmployeeRepository;
import com.technnext.hrms.letter.dto.GenerateLetterRequest;
import com.technnext.hrms.letter.dto.LetterPreview;
import com.technnext.hrms.letter.entity.GeneratedLetter;
import com.technnext.hrms.letter.entity.LetterTemplate;
import com.technnext.hrms.letter.repository.GeneratedLetterRepository;
import com.technnext.hrms.letter.repository.LetterTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GeneratedLetterService {

    private final GeneratedLetterRepository generatedRepo;
    private final LetterTemplateRepository templateRepo;
    private final EmployeeRepository employeeRepo;

    public List<GeneratedLetter> getByEmployee(UUID employeeId) {
        return generatedRepo.findByEmployeeIdOrderByLetterDateDesc(employeeId);
    }

    public LetterPreview preview(Integer templateId, UUID employeeId) {
        LetterTemplate template = templateRepo.findById(templateId)
                .orElseThrow(() -> new ResourceNotFoundException("LetterTemplate", templateId));
        Employee emp = employeeRepo.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee", employeeId));
        return new LetterPreview(template.getLetterType(), fillPlaceholders(template.getTemplateBody(), emp));
    }

    public GeneratedLetter generate(GenerateLetterRequest req) {
        LetterTemplate template = templateRepo.findById(req.templateId())
                .orElseThrow(() -> new ResourceNotFoundException("LetterTemplate", req.templateId()));
        if (!employeeRepo.existsById(req.employeeId()))
            throw new ResourceNotFoundException("Employee", req.employeeId());

        GeneratedLetter letter = GeneratedLetter.builder()
                .employeeId(req.employeeId())
                .templateId(req.templateId())
                .letterType(template.getLetterType())
                .letterDate(req.letterDate() != null ? req.letterDate() : LocalDate.now())
                .generatedBy(req.generatedBy())
                .build();
        return generatedRepo.save(letter);
    }

    public void delete(Integer id) {
        if (!generatedRepo.existsById(id)) throw new ResourceNotFoundException("GeneratedLetter", id);
        generatedRepo.deleteById(id);
    }

    private String fillPlaceholders(String body, Employee e) {
        String fullName = (e.getFirstName() != null ? e.getFirstName() : "")
                + (e.getLastName() != null ? " " + e.getLastName() : "");
        return body
                .replace("{firstName}", safe(e.getFirstName()))
                .replace("{lastName}", safe(e.getLastName()))
                .replace("{fullName}", fullName.trim())
                .replace("{employeeCode}", safe(e.getEmployeeCode()))
                .replace("{designation}", e.getDesignation() != null ? safe(e.getDesignation().getName()) : "")
                .replace("{department}", e.getDepartment() != null ? safe(e.getDepartment().getName()) : "")
                .replace("{branch}", e.getBranch() != null ? safe(e.getBranch().getName()) : "")
                .replace("{dateOfJoining}", e.getDateOfJoining() != null ? e.getDateOfJoining().toString() : "")
                .replace("{employmentType}", safe(e.getEmploymentType()));
    }

    private String safe(String s) { return s != null ? s : ""; }
}