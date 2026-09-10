package com.technnext.hrms.payroll.repository;

import com.technnext.hrms.payroll.entity.PfSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PfSettingsRepository extends JpaRepository<PfSettings, Integer> {
    // Most recently effective settings row (there should typically be exactly one, but
    // this tolerates a history of revisions if Finance updates the ceiling over time).
    List<PfSettings> findAllByOrderByEffectiveFromDesc();
}