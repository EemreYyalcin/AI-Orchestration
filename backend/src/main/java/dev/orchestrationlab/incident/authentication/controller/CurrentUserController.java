package dev.orchestrationlab.incident.authentication.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import dev.orchestrationlab.incident.authentication.application.ApplicationPrincipal;
import dev.orchestrationlab.incident.user.application.CurrentUserService;
import dev.orchestrationlab.incident.user.application.CurrentUserService.CurrentUser;

@RestController
public class CurrentUserController {
    private final CurrentUserService currentUsers;

    public CurrentUserController(CurrentUserService currentUsers) { this.currentUsers = currentUsers; }

    @GetMapping("/api/me")
    public ResponseEntity<CurrentUser> me(@AuthenticationPrincipal ApplicationPrincipal principal) {
        if (principal == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        var user = currentUsers.find(principal.getUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(user);
    }
}
