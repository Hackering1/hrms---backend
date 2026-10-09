package com.technnext.hrms.expense.repository;

import com.technnext.hrms.expense.entity.ExpenseCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ExpenseCategoryRepository extends JpaRepository<ExpenseCategory, Integer> {
    List<ExpenseCategory> findAllByOrderByNameAsc();
    List<ExpenseCategory> findByIsActiveTrueOrderByNameAsc();
    boolean existsByNameIgnoreCase(String name);
}