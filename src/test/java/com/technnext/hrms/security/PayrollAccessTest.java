package com.technnext.hrms.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class PayrollAccessTest {
    private final CurrentUserService currentUser = new CurrentUserService(null, null);

    @Test
    void onlyPayrollAdministratorsCanManagePayroll() {
        assertFalse(currentUser.canManagePayroll(principal("ROLE_EMPLOYEE")));
        assertFalse(currentUser.canManagePayroll(principal("ROLE_MANAGER")));
        assertTrue(currentUser.canManagePayroll(principal("ROLE_FINANCE_ADMIN")));
        assertTrue(currentUser.canManagePayroll(principal("ROLE_SUPER_ADMIN")));
    }

    private CustomUserDetails principal(String authority) {
        CustomUserDetails principal = mock(CustomUserDetails.class);
        doReturn(List.of(new SimpleGrantedAuthority(authority))).when(principal).getAuthorities();
        return principal;
    }
}
