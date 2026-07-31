package com.technnext.hrms.employee.service;

import com.technnext.hrms.employee.dto.EmployeeContactDto;
import com.technnext.hrms.employee.dto.EmployeeContactUpsertRequest;
import com.technnext.hrms.employee.entity.EmployeeContact;
import com.technnext.hrms.employee.repository.EmployeeContactRepository;
import com.technnext.hrms.employee.repository.EmployeeRepository;
import com.technnext.hrms.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EmployeeContactService {

    private final EmployeeContactRepository contactRepository;
    private final EmployeeRepository employeeRepository;

    /** Null if the employee simply hasn't filled in contact details yet — not an error. */
    public EmployeeContactDto getByEmployeeId(UUID employeeId) {
        return contactRepository.findByEmployeeId(employeeId)
                .map(EmployeeContactDto::from)
                .orElse(null);
    }

    @Transactional
    public EmployeeContactDto upsert(UUID employeeId, EmployeeContactUpsertRequest req) {
        if (!employeeRepository.existsById(employeeId)) {
            throw new ResourceNotFoundException("Employee not found: " + employeeId);
        }
        EmployeeContact c = contactRepository.findByEmployeeId(employeeId)
                .orElseGet(() -> EmployeeContact.builder().employeeId(employeeId).build());

        c.setPersonalEmail(req.personalEmail());
        c.setOfficialEmail(req.officialEmail());
        c.setPhonePrimary(req.phonePrimary());
        c.setPhoneSecondary(req.phoneSecondary());
        c.setEmergencyName(req.emergencyName());
        c.setEmergencyPhone(req.emergencyPhone());
        c.setEmergencyRelation(req.emergencyRelation());
        c.setAddressLine1(req.addressLine1());
        c.setAddressLine2(req.addressLine2());
        c.setCity(req.city());
        c.setState(req.state());
        c.setCountry(req.country());
        c.setPincode(req.pincode());
        c.setPermAddressLine1(req.permAddressLine1());
        c.setPermAddressLine2(req.permAddressLine2());
        c.setPermCity(req.permCity());
        c.setPermState(req.permState());
        c.setPermPincode(req.permPincode());

        return EmployeeContactDto.from(contactRepository.save(c));
    }
}