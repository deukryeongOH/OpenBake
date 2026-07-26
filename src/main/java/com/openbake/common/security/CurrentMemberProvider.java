package com.openbake.common.security;

import com.openbake.member.domain.Role;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentMemberProvider {

    public Long getId() {
        return (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    public boolean hasRole(Role role) {
        return SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_" + role.name()));
    }
}
