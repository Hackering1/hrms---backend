package com.technnext.hrms.payroll.repository;

import com.technnext.hrms.payroll.entity.PfSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PfSettingsRepository extends JpaRepository<PfSettings, Integer> {
    // Most recently effective settings row (there should typically be exactly one, but
    // this tolerates a history of revisions if Finance updates the ceiling over time).
    List<PfSettings> findAllByOrderByEffectiveFromDesc();

    // Revisions already in force on the given date, newest first (id breaks a same-date tie
    // in favour of the most recently saved revision).
    @org.springframework.data.jpa.repository.Query(
            "SELECT p FROM PfSettings p WHERE p.effectiveFrom <= :asOf ORDER BY p.effectiveFrom DESC, p.id DESC")
    List<PfSettings> findEffectiveAsOf(@org.springframework.data.repository.query.Param("asOf") java.time.LocalDate asOf);
}