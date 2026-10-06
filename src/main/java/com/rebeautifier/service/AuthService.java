package com.rebeautifier.service;

import com.rebeautifier.domain.User;
import com.rebeautifier.repository.UserRepository;
import com.rebeautifier.security.AppUserDetails;
import com.rebeautifier.web.dto.LoginRequest;
import com.rebeautifier.web.dto.RegisterRequest;
import com.rebeautifier.web.dto.UserResponse;
import com.rebeautifier.web.error.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
    }

    @Transactional
    public UserResponse register(RegisterRequest request,
                                 HttpServletRequest httpRequest,
                                 HttpServletResponse httpResponse) {
        if (userRepository.existsByUsernameIgnoreCase(request.username())) {
            throw ApiException.conflict("Username is already taken");
        }
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw ApiException.conflict("Email is already registered");
        }

        User user = new User();
        user.setUsername(request.username().trim());
        user.setEmail(request.email().trim().toLowerCase());
        user.setDisplayName(request.displayName().trim());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        userRepository.save(user);

        authenticateAndPersist(request.username(), request.password(), httpRequest, httpResponse);
        return toResponse(user);
    }

    @Transactional(readOnly = true)
    public UserResponse login(LoginRequest request,
                              HttpServletRequest httpRequest,
                              HttpServletResponse httpResponse) {
        authenticateAndPersist(request.usernameOrEmail(), request.password(), httpRequest, httpResponse);
        AppUserDetails principal = currentPrincipal();
        return new UserResponse(principal.getId(), principal.getUsername(), principal.getEmail(), principal.getDisplayName());
    }

    public void logout(HttpServletRequest request, HttpServletResponse response) {
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        response.setStatus(HttpServletResponse.SC_NO_CONTENT);
    }

    @Transactional(readOnly = true)
    public UserResponse me() {
        AppUserDetails principal = currentPrincipal();
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> ApiException.notFound("User not found"));
        return toResponse(user);
    }

    @Transactional
    public void changePassword(String currentPassword, String newPassword) {
        User user = requireCurrentUser();
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw ApiException.badRequest("Current password is incorrect");
        }
        if (currentPassword.equals(newPassword)) {
            throw ApiException.badRequest("New password must be different from the current password");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public User requireCurrentUser() {
        AppUserDetails principal = currentPrincipal();
        return userRepository.findById(principal.getId())
                .orElseThrow(() -> ApiException.notFound("User not found"));
    }

    private void authenticateAndPersist(String usernameOrEmail,
                                        String password,
                                        HttpServletRequest request,
                                        HttpServletResponse response) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(usernameOrEmail, password));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }

    private AppUserDetails currentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AppUserDetails details)) {
            throw ApiException.forbidden("Not authenticated");
        }
        return details;
    }

    private UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getEmail(), user.getDisplayName());
    }
}
