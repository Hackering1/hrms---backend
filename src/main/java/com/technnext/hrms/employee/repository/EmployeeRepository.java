package com.technnext.hrms.employee.repository;
import com.technnext.hrms.employee.entity.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;
public interface EmployeeRepository extends JpaRepository<Employee, UUID> {
    Optional<Employee> findByEmployeeCode(String employeeCode);
    boolean existsByEmployeeCode(String employeeCode);
    Optional<Employee> findByUserId(UUID userId);
    // File-access check: employees whose profile photo url embeds a given stored-file id.
    java.util.List<Employee> findByProfilePhotoUrlContaining(String fileIdFragment);
}