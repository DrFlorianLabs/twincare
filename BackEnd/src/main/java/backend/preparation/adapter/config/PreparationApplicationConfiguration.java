package backend.preparation.adapter.config;

import java.time.Clock;
import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import backend.preparation.application.port.in.CreatePreparationCaseUseCase;
import backend.preparation.application.port.in.ClosePreparationCaseUseCase;
import backend.preparation.application.port.in.GetProfessionalContextUseCase;
import backend.preparation.application.port.in.GetPatientReasonUseCase;
import backend.preparation.application.port.in.ListPreparationAuditEventsUseCase;
import backend.preparation.application.port.in.PutPatientAttachmentUseCase;
import backend.preparation.application.port.in.ResolvePatientAccessSessionUseCase;
import backend.preparation.application.port.out.AttachmentIdGenerator;
import backend.preparation.application.port.out.AttachmentStorageTransaction;
import backend.preparation.application.port.out.PatientAccessSessionIdGenerator;
import backend.preparation.application.port.out.PatientAccessSessionRepository;
import backend.preparation.application.port.out.PatientInvitationExchangeGuard;
import backend.preparation.application.port.out.PatientInvitationIdGenerator;
import backend.preparation.application.port.out.PatientInvitationRepository;
import backend.preparation.application.port.out.PatientReasonReader;
import backend.preparation.application.port.out.PreparationAuditEventReader;
import backend.preparation.application.port.out.PreparationAuditEventStore;
import backend.preparation.application.port.out.PreparationCaseClosureRepository;
import backend.preparation.application.port.out.PreparationCaseIdGenerator;
import backend.preparation.application.port.out.PreparationCaseRepository;
import backend.preparation.application.port.out.PreparationReadyConfirmationRepository;
import backend.preparation.application.port.out.PreparationTaskIdGenerator;
import backend.preparation.application.port.out.PreparationTaskRepository;
import backend.preparation.application.port.out.PreparationAttachmentRepository;
import backend.preparation.application.port.out.PrivateAttachmentStorage;
import backend.preparation.application.port.out.PrivateAttachmentContentReader;
import backend.preparation.application.port.out.ProfessionalContextSource;
import backend.preparation.application.port.out.ProfessionalPreparationQueryRepository;
import backend.preparation.application.port.out.ProfessionalPreparationAuthorization;
import backend.preparation.application.port.out.SecureTokenDigester;
import backend.preparation.application.port.out.SecureTokenGenerator;
import backend.preparation.application.service.CreatePreparationCaseService;
import backend.preparation.application.service.ClosePreparationCaseService;
import backend.preparation.application.service.CommitStagedPatientAttachmentService;
import backend.preparation.application.service.DeletePatientAttachmentService;
import backend.preparation.application.service.DownloadProfessionalAttachmentService;
import backend.preparation.application.service.EndPatientAccessSessionService;
import backend.preparation.application.service.ExchangePatientInvitationService;
import backend.preparation.application.service.GetPatientAttachmentService;
import backend.preparation.application.service.GetPatientReasonService;
import backend.preparation.application.service.GetProfessionalContextService;
import backend.preparation.application.service.IssuePatientInvitationService;
import backend.preparation.application.service.ListPreparationAuditEventsService;
import backend.preparation.application.service.RecordPatientReasonService;
import backend.preparation.application.service.PatientAttachmentScopeLock;
import backend.preparation.application.service.ProfessionalPreparationQueryService;
import backend.preparation.application.service.ProfessionalPreparationCommandService;
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
            ProfessionalPreparationAuthorization authorization,
            PreparationCaseRepository repository,
            PreparationCaseIdGenerator idGenerator,
            PreparationAuditEventStore auditEvents,
            Clock preparationClock,
            PlatformTransactionManager transactionManager) {
        CreatePreparationCaseService service = new CreatePreparationCaseService(
                authorization,
                repository,
                idGenerator,
                auditEvents,
                preparationClock);
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);
        transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return new TransactionalCreatePreparationCaseUseCase(
                service,
                transactions);
    }

    @Bean
    TransactionalPatientInvitationUseCases patientInvitationUseCases(
            ProfessionalPreparationAuthorization authorization,
            PreparationCaseRepository preparationCases,
            PatientInvitationRepository invitations,
            PatientAccessSessionRepository accessSessions,
            PatientInvitationIdGenerator invitationIdGenerator,
            PatientAccessSessionIdGenerator sessionIdGenerator,
            SecureTokenGenerator tokenGenerator,
            SecureTokenDigester tokenDigester,
            PatientInvitationExchangeGuard exchangeGuard,
            PreparationAuditEventStore auditEvents,
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
                auditEvents,
                preparationClock,
                invitationTtl);
        var revoke = new RevokePatientInvitationService(
                authorization,
                preparationCases,
                invitations,
                accessSessions,
                auditEvents,
                preparationClock);
        var exchange = new ExchangePatientInvitationService(
                invitations,
                preparationCases,
                accessSessions,
                sessionIdGenerator,
                tokenGenerator,
                tokenDigester,
                exchangeGuard,
                auditEvents,
                preparationClock);
        return new TransactionalPatientInvitationUseCases(issue, revoke, exchange);
    }

    @Bean
    TransactionalPatientSessionUseCases patientSessionUseCases(
            PreparationCaseRepository preparationCases,
            PatientInvitationRepository invitations,
            PatientAccessSessionRepository accessSessions,
            PatientReasonReader patientReasons,
            SecureTokenDigester tokenDigester,
            PreparationAuditEventStore auditEvents,
            Clock preparationClock) {
        ResolvePatientAccessSessionUseCase resolve = new ResolvePatientAccessSessionService(
                accessSessions, tokenDigester, preparationClock);
        GetPatientReasonUseCase getPatientReason = new GetPatientReasonService(
                patientReasons, preparationClock);
        var end = new EndPatientAccessSessionService(
                preparationCases, invitations, accessSessions, auditEvents, preparationClock);
        var patientReason = new RecordPatientReasonService(
                preparationCases, invitations, accessSessions, auditEvents, preparationClock);
        return new TransactionalPatientSessionUseCases(resolve, end, getPatientReason, patientReason);
    }

    @Bean
    TransactionalPatientAttachmentUseCases transactionalPatientAttachmentUseCases(
            PreparationCaseRepository preparationCases,
            PatientInvitationRepository invitations,
            PatientAccessSessionRepository accessSessions,
            PreparationAttachmentRepository attachments,
            AttachmentIdGenerator attachmentIdGenerator,
            AttachmentStorageTransaction attachmentStorageTransaction,
            PreparationAuditEventStore auditEvents,
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
                attachmentStorageTransaction,
                auditEvents);
        var get = new GetPatientAttachmentService(scopeLock);
        var delete = new DeletePatientAttachmentService(
                scopeLock, attachments, attachmentStorageTransaction, auditEvents);
        return new TransactionalPatientAttachmentUseCases(commit, get, delete);
    }

    @Bean
    PutPatientAttachmentUseCase putPatientAttachmentUseCase(
            PrivateAttachmentStorage storage,
            TransactionalPatientAttachmentUseCases transactionalUseCases) {
        return new PutPatientAttachmentCoordinator(storage, transactionalUseCases);
    }

    @Bean
    GetProfessionalContextUseCase professionalContextUseCase(ProfessionalContextSource source) {
        return new GetProfessionalContextService(source);
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

    @Bean
    TransactionalProfessionalPreparationCommands professionalPreparationCommands(
            ProfessionalPreparationAuthorization authorization,
            PreparationCaseRepository preparationCases,
            PreparationTaskRepository tasks,
            PreparationReadyConfirmationRepository confirmations,
            PreparationTaskIdGenerator taskIdGenerator,
            PreparationAuditEventStore auditEvents,
            Clock preparationClock) {
        var service = new ProfessionalPreparationCommandService(
                authorization,
                preparationCases,
                tasks,
                confirmations,
                taskIdGenerator,
                auditEvents,
                new PreparationReadinessPolicy(),
                preparationClock);
        return new TransactionalProfessionalPreparationCommands(service, service, service);
    }

    @Bean
    ClosePreparationCaseUseCase closePreparationCaseUseCase(
            ProfessionalPreparationAuthorization authorization,
            PreparationCaseRepository preparationCases,
            PreparationReadyConfirmationRepository confirmations,
            PreparationTaskRepository tasks,
            PatientInvitationRepository invitations,
            PatientAccessSessionRepository accessSessions,
            PreparationCaseClosureRepository closures,
            PreparationAuditEventStore auditEvents,
            Clock preparationClock) {
        return new TransactionalClosePreparationCaseUseCase(new ClosePreparationCaseService(
                authorization,
                preparationCases,
                confirmations,
                tasks,
                invitations,
                accessSessions,
                closures,
                auditEvents,
                preparationClock));
    }

    @Bean
    TransactionalProfessionalAttachmentDownload professionalAttachmentDownload(
            ProfessionalPreparationAuthorization authorization,
            PreparationCaseRepository preparationCases,
            PreparationAttachmentRepository attachments,
            PrivateAttachmentContentReader contentReader,
            PreparationAuditEventStore auditEvents,
            Clock preparationClock,
            PlatformTransactionManager transactionManager) {
        return new TransactionalProfessionalAttachmentDownload(
                new DownloadProfessionalAttachmentService(
                        authorization,
                        preparationCases,
                        attachments,
                        contentReader,
                        auditEvents,
                        preparationClock),
                new TransactionTemplate(transactionManager));
    }

    @Bean
    ListPreparationAuditEventsUseCase preparationAuditEventQueries(
            ProfessionalPreparationAuthorization authorization,
            PreparationCaseRepository preparationCases,
            PreparationAuditEventReader auditEvents) {
        return new TransactionalPreparationAuditEventQueries(
                new ListPreparationAuditEventsService(authorization, preparationCases, auditEvents));
    }
}
