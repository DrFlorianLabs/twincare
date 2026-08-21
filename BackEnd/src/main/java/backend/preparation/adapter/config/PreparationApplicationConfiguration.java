package backend.preparation.adapter.config;

import java.time.Clock;
import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;

import backend.preparation.application.port.in.CreatePreparationCaseUseCase;
import backend.preparation.application.port.in.PutPatientAttachmentUseCase;
import backend.preparation.application.port.in.ResolvePatientAccessSessionUseCase;
import backend.preparation.application.port.out.AttachmentIdGenerator;
import backend.preparation.application.port.out.AttachmentStorageTransaction;
import backend.preparation.application.port.out.PatientAccessSessionIdGenerator;
import backend.preparation.application.port.out.PatientAccessSessionRepository;
import backend.preparation.application.port.out.PatientInvitationExchangeGuard;
import backend.preparation.application.port.out.PatientInvitationIdGenerator;
import backend.preparation.application.port.out.PatientInvitationRepository;
import backend.preparation.application.port.out.PreparationCaseAuthorization;
import backend.preparation.application.port.out.PreparationCaseIdGenerator;
import backend.preparation.application.port.out.PreparationCaseRepository;
import backend.preparation.application.port.out.PreparationAttachmentRepository;
import backend.preparation.application.port.out.PrivateAttachmentStorage;
import backend.preparation.application.port.out.ProfessionalPreparationQueryRepository;
import backend.preparation.application.port.out.ProfessionalPreparationAuthorization;
import backend.preparation.application.port.out.SecureTokenDigester;
import backend.preparation.application.port.out.SecureTokenGenerator;
import backend.preparation.application.service.CreatePreparationCaseService;
import backend.preparation.application.service.CommitStagedPatientAttachmentService;
import backend.preparation.application.service.DeletePatientAttachmentService;
import backend.preparation.application.service.EndPatientAccessSessionService;
import backend.preparation.application.service.ExchangePatientInvitationService;
import backend.preparation.application.service.GetPatientAttachmentService;
import backend.preparation.application.service.IssuePatientInvitationService;
import backend.preparation.application.service.RecordPatientReasonService;
import backend.preparation.application.service.PatientAttachmentScopeLock;
import backend.preparation.application.service.ProfessionalPreparationQueryService;
import backend.preparation.application.service.PutPatientAttachmentCoordinator;
import backend.preparation.application.service.RevokePatientInvitationService;
import backend.preparation.application.service.ResolvePatientAccessSessionService;
import backend.preparation.domain.PreparationReadinessPolicy;

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
        var patientReason = new RecordPatientReasonService(
                preparationCases, invitations, accessSessions, preparationClock);
        return new TransactionalPatientSessionUseCases(resolve, end, patientReason);
    }

    @Bean
    TransactionalPatientAttachmentUseCases transactionalPatientAttachmentUseCases(
            PreparationCaseRepository preparationCases,
            PatientInvitationRepository invitations,
            PatientAccessSessionRepository accessSessions,
            PreparationAttachmentRepository attachments,
            AttachmentIdGenerator attachmentIdGenerator,
            AttachmentStorageTransaction attachmentStorageTransaction,
            Clock preparationClock) {
        var scopeLock = new PatientAttachmentScopeLock(
                preparationCases,
                invitations,
                accessSessions,
                attachments,
                preparationClock);
        var commit = new CommitStagedPatientAttachmentService(
                scopeLock,
                attachments,
                attachmentIdGenerator,
                attachmentStorageTransaction);
        var get = new GetPatientAttachmentService(scopeLock);
        var delete = new DeletePatientAttachmentService(
                scopeLock, attachments, attachmentStorageTransaction);
        return new TransactionalPatientAttachmentUseCases(commit, get, delete);
    }

    @Bean
    PutPatientAttachmentUseCase putPatientAttachmentUseCase(
            PrivateAttachmentStorage storage,
            TransactionalPatientAttachmentUseCases transactionalUseCases) {
        return new PutPatientAttachmentCoordinator(storage, transactionalUseCases);
    }

    @Bean
    TransactionalProfessionalPreparationQueries professionalPreparationQueries(
            ProfessionalPreparationAuthorization authorization,
            ProfessionalPreparationQueryRepository repository) {
        var service = new ProfessionalPreparationQueryService(
                authorization,
                repository,
                new PreparationReadinessPolicy());
        return new TransactionalProfessionalPreparationQueries(service, service);
    }
}
