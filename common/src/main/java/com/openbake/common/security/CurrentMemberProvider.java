package com.openbake.common.security;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentMemberProvider {

    public Long getId() {
        return (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    public boolean hasAuthority(String authority) {
        return SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(authority));
    }
}
