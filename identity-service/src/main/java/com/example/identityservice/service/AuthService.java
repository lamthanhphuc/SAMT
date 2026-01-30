package com.example.identityservice.service;

import com.example.identityservice.dto.*;
import com.example.identityservice.entity.User;
import com.example.identityservice.exception.AccountLockedException;
import com.example.identityservice.exception.EmailAlreadyExistsException;
import com.example.identityservice.exception.InvalidCredentialsException;
import com.example.identityservice.exception.PasswordMismatchException;
import com.example.identityservice.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Authentication service.
 * @see docs/Identity-Service-Package-Structure.md
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final AuditService auditService;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            RefreshTokenService refreshTokenService,
            AuditService auditService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.auditService = auditService;
    }

    /**
     * UC-REGISTER: Register new user and return tokens.
     * 
     * Steps (from SRS.md):
     * 1. Validate input (email, password, name) - done by Bean Validation
     * 2. Check passwords match
     * 3. Check email uniqueness in database
     * 4. Hash password with BCrypt (strength 10)
     * 5. Create user with status = ACTIVE
     * 6. Generate access token & refresh token
     * 7. Return tokens and user info
     *
     * @param request RegisterRequest with user details
     * @return RegisterResponse with user info and tokens
     * @throws PasswordMismatchException if passwords don't match (400)
     * @throws EmailAlreadyExistsException if email already registered (409)
     */
    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        // Step 2: Check passwords match (Alternate Flow A4)
        if (!request.passwordsMatch()) {
            throw new PasswordMismatchException();
        }

        // Step 4: Hash password with BCrypt
        String passwordHash = passwordEncoder.encode(request.password());

        // Step 5: Create user with status = ACTIVE
        User user = new User();
        user.setEmail(request.email());
        user.setPasswordHash(passwordHash);
        user.setFullName(request.fullName());
        user.setRole(User.Role.valueOf(request.role())); // STUDENT -> STUDENT
        user.setStatus(User.Status.ACTIVE);

        // Step 3: Save user - DB UNIQUE constraint handles race condition
        try {
            user = userRepository.save(user);
        } catch (DataIntegrityViolationException ex) {
            throw new EmailAlreadyExistsException();
        }

        // Step 6: Generate tokens
        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = refreshTokenService.createRefreshToken(user);

        // Audit: User created
        auditService.logUserCreated(user);

        // Step 7: Return user info and tokens
        UserDto userDto = UserDto.fromEntity(user);
        return RegisterResponse.of(userDto, accessToken, refreshToken);
    }

    /**
     * UC-LOGIN: Authenticate user and return tokens.
     * 
     * CRITICAL SECURITY: Password validation BEFORE status check (anti-enumeration).
     * 
     * Steps (from IMPLEMENTATION_GUIDE.md):
     * 1. Find user by email
     * 2. Validate password FIRST (constant-time BCrypt)
     * 3. Check account status AFTER password validated
     * 4. Generate access token (JWT, 15 min TTL)
     * 5. Generate refresh token (UUID, 7 days TTL)
     * 6. Persist refresh token in database
     * 7. Return both tokens
     *
     * @param request LoginRequest with email and password
     * @return LoginResponse with tokens
     * @throws InvalidCredentialsException if email not found or password incorrect (401)
     * @throws AccountLockedException if account status = LOCKED (403)
     */
    @Transactional
    public LoginResponse login(LoginRequest request) {
        // Step 1: Find user by email
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> {
                    // Audit: Login failed - user not found
                    auditService.logLoginFailure(request.email(), "User not found");
                    return new InvalidCredentialsException();
                });

        // Step 2: Validate password FIRST (CRITICAL - anti-enumeration)
        boolean passwordValid = passwordEncoder.matches(request.password(), user.getPasswordHash());
        if (!passwordValid) {
            // Audit: Login failed - wrong password
            auditService.logLoginFailure(request.email(), "Invalid password");
            throw new InvalidCredentialsException();
        }
        
        // Step 3: Check account status AFTER password validated
        if (user.getStatus() == User.Status.LOCKED) {
            // Audit: Login denied - account locked
            auditService.logLoginDenied(user, "Account is locked");
            throw new AccountLockedException();
        }

        // Step 4: Generate access token (15 min TTL)
        String accessToken = jwtService.generateAccessToken(user);

        // Step 5-6: Generate and persist refresh token (7 days TTL)
        String refreshToken = refreshTokenService.createRefreshToken(user);

        // Audit: Login success
        auditService.logLoginSuccess(user);

        // Return response with expiresIn = 900 seconds (15 minutes)
        return LoginResponse.of(accessToken, refreshToken, 900);
    }
}
