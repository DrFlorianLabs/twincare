package backend.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import backend.dao.AppUtilisateurRepository;
import backend.dao.DossierPatientRepository;
import backend.exception.UnauthorizedActionException;
import backend.security.JwtService;

@ExtendWith(MockitoExtension.class)
class AuthServiceAuthorizationTest {

    @Mock
    private AppUtilisateurRepository utilisateurRepository;
    @Mock
    private DossierPatientRepository dossierRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;
    @Mock
    private CustomUserDetailsService userDetailsService;
    @Mock
    private SecurityHelperService securityHelperService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                utilisateurRepository,
                dossierRepository,
                passwordEncoder,
                jwtService,
                userDetailsService,
                securityHelperService);
    }

    @Test
    void provisioningRequiresStaffAtTheServiceBoundary() {
        doThrow(new UnauthorizedActionException("Accès interdit"))
                .when(securityHelperService).requireStaff();

        assertThatThrownBy(() -> authService.register(null))
                .isInstanceOf(UnauthorizedActionException.class);

        verify(securityHelperService).requireStaff();
        verifyNoInteractions(utilisateurRepository, dossierRepository);
    }

    @Test
    void passwordResetRequiresAdminAtTheServiceBoundary() {
        doThrow(new UnauthorizedActionException("Accès interdit"))
                .when(securityHelperService).requireAdmin();

        assertThatThrownBy(() -> authService.resetPassword(null))
                .isInstanceOf(UnauthorizedActionException.class);

        verify(securityHelperService).requireAdmin();
        verifyNoInteractions(utilisateurRepository);
    }
}
