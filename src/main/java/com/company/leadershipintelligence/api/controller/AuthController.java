package com.company.leadershipintelligence.api.controller;

import com.company.leadershipintelligence.api.request.AuthRequests;
import com.company.leadershipintelligence.api.response.AccountResponse;
import com.company.leadershipintelligence.api.response.CsrfResponse;
import com.company.leadershipintelligence.api.security.UserPrincipal;
import com.company.leadershipintelligence.domain.exception.ScoringErrorCode;
import com.company.leadershipintelligence.domain.exception.ScoringException;
import com.company.leadershipintelligence.service.identity.AccountService;
import com.company.leadershipintelligence.service.identity.AccountView;
import com.company.leadershipintelligence.service.identity.AuthenticationService;
import com.company.leadershipintelligence.service.identity.ClientInfo;
import com.company.leadershipintelligence.service.identity.MembershipService;
import com.company.leadershipintelligence.service.identity.PasswordService;
import com.company.leadershipintelligence.service.identity.SecurityAudit;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Accounts and sessions (decisions D65 to D68, D73): registration, email verification, sign-in, sign-out, the current user,
 * and password reset and change. The session holds the user and nothing else. Registration, resend and reset requests answer
 * the same whether or not the email is known. Every state-changing call needs a CSRF token (fetched from {@code /auth/csrf}).
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication")
public class AuthController {

    private final AccountService accounts;
    private final AuthenticationService authentication;
    private final MembershipService memberships;
    private final PasswordService passwords;
    private final SecurityAudit audit;
    private final SecurityContextRepository contexts;
    private final CookieCsrfTokenRepository csrfTokens;

    public AuthController(AccountService accounts, AuthenticationService authentication, MembershipService memberships, PasswordService passwords, SecurityAudit audit,
                          SecurityContextRepository contexts, CookieCsrfTokenRepository csrfTokens) {
        this.accounts = accounts;
        this.authentication = authentication;
        this.memberships = memberships;
        this.passwords = passwords;
        this.audit = audit;
        this.contexts = contexts;
        this.csrfTokens = csrfTokens;
    }

    @Operation(summary = "The CSRF token to send in the X-XSRF-TOKEN header of every state-changing request (also set as the XSRF-TOKEN cookie)")
    @GetMapping("/csrf")
    public CsrfResponse csrf(CsrfToken token) {
        return new CsrfResponse(token.getHeaderName(), token.getToken());
    }

    @Operation(summary = "Create an account; a verification email is sent. Answers 202 whether or not the email was already known")
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void register(@Valid @RequestBody AuthRequests.Register body, HttpServletRequest request) {
        accounts.register(body.email(), body.password(), body.displayName(), client(request));
    }

    @Operation(summary = "Confirm an email address with the token from the verification email")
    @PostMapping("/verify-email")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void verifyEmail(@Valid @RequestBody AuthRequests.VerifyEmail body, HttpServletRequest request) {
        accounts.verifyEmail(body.token(), body.password(), client(request));
    }

    @Operation(summary = "Send a new verification email to a pending account. Always 202")
    @PostMapping("/resend-verification")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void resend(@Valid @RequestBody AuthRequests.EmailOnly body, HttpServletRequest request) {
        accounts.resendVerification(body.email(), client(request));
    }

    @Operation(summary = "Sign in with email and password; starts a server-side session and sets the session cookie")
    @PostMapping("/login")
    public AccountResponse login(@Valid @RequestBody AuthRequests.Login body, HttpServletRequest request, HttpServletResponse response) {
        ClientInfo from = client(request);
        AccountView account = authentication.authenticate(body.email(), body.password(), from);
        // a new session for every sign-in: whatever session came with the request is ended first (session fixation)
        HttpSession old = request.getSession(false);
        if (old != null) {
            old.invalidate();
        }
        request.getSession(true);
        List<GrantedAuthority> authorities = account.platformAdmin() ? List.of(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN")) : List.of();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(new UserPrincipal(account.id()), null, authorities));
        contexts.saveContext(context, request, response);
        // a new CSRF token for the new session
        csrfTokens.saveToken(csrfTokens.generateToken(request), request, response);
        // recorded only now that the session exists, so the audit never shows a sign-in that did not happen
        audit.success("LOGIN", account.id(), from);
        return response(account);
    }

    @Operation(summary = "Sign out: ends the session. Idempotent")
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request) {
        Authentication current = SecurityContextHolder.getContext().getAuthentication();
        if (current != null && current.getPrincipal() instanceof UserPrincipal user) {
            audit.success("LOGOUT", user.userId(), client(request));
        }
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
    }

    @Operation(summary = "The signed-in user and the companies they may open")
    @GetMapping("/me")
    public AccountResponse me(@AuthenticationPrincipal UserPrincipal user, HttpServletRequest request) {
        try {
            return response(authentication.current(user.userId()));
        } catch (ScoringException e) {
            // only when the account was disabled or removed after the session started; any other failure (a database
            // outage, for example) is passed on and leaves the session alone
            if (e.code() == ScoringErrorCode.AUTHENTICATION_FAILED) {
                HttpSession session = request.getSession(false);
                if (session != null) {
                    session.invalidate();
                }
            }
            throw e;
        }
    }

    @Operation(summary = "Accept an invitation to a company with the token from the invitation email (needs a session whose verified email is the invited one)")
    @PostMapping("/invitations/accept")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void acceptInvitation(@AuthenticationPrincipal UserPrincipal user, @Valid @RequestBody AuthRequests.TokenOnly body, HttpServletRequest request) {
        memberships.accept(user.userId(), body.token(), client(request));
    }

    @Operation(summary = "Ask for a password reset email. Always 202")
    @PostMapping("/password/forgot")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void forgot(@Valid @RequestBody AuthRequests.EmailOnly body, HttpServletRequest request) {
        passwords.requestReset(body.email(), client(request));
    }

    @Operation(summary = "Set a new password with the token from the reset email; ends every session of the user")
    @PostMapping("/password/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reset(@Valid @RequestBody AuthRequests.ResetPassword body, HttpServletRequest request) {
        passwords.reset(body.token(), body.newPassword(), client(request));
    }

    @Operation(summary = "Change the password of the signed-in user; ends their other sessions")
    @PostMapping("/password/change")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void change(@AuthenticationPrincipal UserPrincipal user,
                       @Valid @RequestBody AuthRequests.ChangePassword body, HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        passwords.change(user.userId(), body.currentPassword(), body.newPassword(), session == null ? null : session.getId(), client(request));
    }

    private AccountResponse response(AccountView account) {
        return new AccountResponse(account.id(), account.email(), account.displayName(), account.platformAdmin(),
                memberships.companies(account.id()).stream().map(m -> new AccountResponse.CompanyRef(m.tenantId(), m.tenantName())).toList());
    }

    private static ClientInfo client(HttpServletRequest request) {
        return new ClientInfo(request.getRemoteAddr(), request.getHeader("User-Agent"));
    }
}
