package backend.preparation.adapter.config;

import java.time.Clock;
import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;

import backend.preparation.application.port.in.CreatePreparationCaseUseCase;
import backend.preparation.application.port.in.ResolvePatientAccessSessionUseCase;
import backend.preparation.application.port.out.PatientAccessSessionIdGenerator;
import backend.preparation.application.port.out.PatientAccessSessionRepository;
import backend.preparation.application.port.out.PatientInvitationExchangeGuard;
import backend.preparation.application.port.out.PatientInvitationIdGenerator;
import backend.preparation.application.port.out.PatientInvitationRepository;
import backend.preparation.application.port.out.PreparationCaseAuthorization;
import backend.preparation.application.port.out.PreparationCaseIdGenerator;
import backend.preparation.application.port.out.PreparationCaseRepository;
import backend.preparation.application.port.out.SecureTokenDigester;
import backend.preparation.application.port.out.SecureTokenGenerator;
import backend.preparation.application.service.CreatePreparationCaseService;
import backend.preparation.application.service.EndPatientAccessSessionService;
import backend.preparation.application.service.ExchangePatientInvitationService;
import backend.preparation.application.service.IssuePatientInvitationService;
import backend.preparation.application.service.RevokePatientInvitationService;
import backend.preparation.application.service.ResolvePatientAccessSessionService;

@Configuration
public class PreparationApplicationConfiguration {

    @Bean
    Clock preparationClock() {
        return Clock.systemUTC();
    }

    @Bean
    CreatePreparationCaseUseCase createPreparationCaseUseCase(
            PreparationCaseAuthorization authorization,
            PreparationCaseRepository repository,
            PreparationCaseIdGenerator idGenerator,
            Clock preparationClock) {
        CreatePreparationCaseService service = new CreatePreparationCaseService(
                authorization,
                repository,
                idGenerator,
                preparationClock);
        return new TransactionalCreatePreparationCaseUseCase(service);
    }

    @Bean
    TransactionalPatientInvitationUseCases patientInvitationUseCases(
            PreparationCaseAuthorization authorization,
            PreparationCaseRepository preparationCases,
            PatientInvitationRepository invitations,
            PatientAccessSessionRepository accessSessions,
            PatientInvitationIdGenerator invitationIdGenerator,
            PatientAccessSessionIdGenerator sessionIdGenerator,
            SecureTokenGenerator tokenGenerator,
            SecureTokenDigester tokenDigester,
            PatientInvitationExchangeGuard exchangeGuard,
            Clock preparationClock,
            @Value("${app.preparation.invitation-ttl:PT72H}") Duration invitationTtl) {
        var issue = new IssuePatientInvitationService(
                authorization,
                preparationCases,
                invitations,
                accessSessions,
                invitationIdGenerator,
                tokenGenerator,
                tokenDigester,
                preparationClock,
                invitationTtl);
        var revoke = new RevokePatientInvitationService(
                authorization,
                preparationCases,
                invitations,
                accessSessions,
                preparationClock);
        var exchange = new ExchangePatientInvitationService(
                invitations,
                preparationCases,
                accessSessions,
                sessionIdGenerator,
                tokenGenerator,
                tokenDigester,
                exchangeGuard,
                preparationClock);
        return new TransactionalPatientInvitationUseCases(issue, revoke, exchange);
    }

    @Bean
    TransactionalPatientSessionUseCases patientSessionUseCases(
            PreparationCaseRepository preparationCases,
            PatientInvitationRepository invitations,
            PatientAccessSessionRepository accessSessions,
            SecureTokenDigester tokenDigester,
            Clock preparationClock) {
        ResolvePatientAccessSessionUseCase resolve = new ResolvePatientAccessSessionService(
                accessSessions, tokenDigester, preparationClock);
        var end = new EndPatientAccessSessionService(
                preparationCases, invitations, accessSessions, preparationClock);
        return new TransactionalPatientSessionUseCases(resolve, end);
    }
}
