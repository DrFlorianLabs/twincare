package backend.clinicalcontext.adapter.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import backend.clinicalcontext.application.port.in.GetClinicalContextUseCase;
import backend.clinicalcontext.application.port.in.RecordPatientDeclaredReasonFactUseCase;
import backend.clinicalcontext.application.port.in.ReviewClinicalFactUseCase;
import backend.clinicalcontext.application.port.out.ClinicalFactIdGenerator;
import backend.clinicalcontext.application.port.out.ClinicalFactRepository;
import backend.clinicalcontext.application.port.out.ClinicalFactReviewAuthorization;
import backend.clinicalcontext.application.service.GetClinicalContextService;
import backend.clinicalcontext.application.service.RecordPatientDeclaredReasonFactService;
import backend.clinicalcontext.application.service.ReviewClinicalFactService;
import backend.preparation.application.port.out.PreparationAttachmentRepository;
import backend.preparation.application.port.out.PreparationAuditEventStore;
import backend.preparation.application.port.out.PreparationCaseCapability;
import backend.preparation.application.port.out.ProfessionalPreparationAuthorization;
import backend.preparation.application.port.out.PreparationCaseRepository;

@Configuration
public class ClinicalContextApplicationConfiguration {

    @Bean
    RecordPatientDeclaredReasonFactUseCase recordPatientDeclaredReasonFactUseCase(
            ClinicalFactRepository facts,
            ClinicalFactIdGenerator idGenerator) {
        var service = new RecordPatientDeclaredReasonFactService(facts, idGenerator);
        return new TransactionalRecordPatientDeclaredReasonFactUseCase(service);
    }

    @Bean
    TransactionalClinicalContextUseCases clinicalContextUseCases(
            ProfessionalPreparationAuthorization preparationAuthorization,
            ClinicalFactReviewAuthorization reviewAuthorization,
            PreparationCaseRepository preparationCases,
            ClinicalFactRepository facts,
            PreparationAttachmentRepository attachments,
            PreparationAuditEventStore auditEvents,
            Clock preparationClock) {
        GetClinicalContextUseCase get = new GetClinicalContextService(
                owner -> preparationAuthorization.requireAccess(
                        new backend.preparation.domain.OrganizationId(owner.value()),
                        PreparationCaseCapability.VIEW_PREPARATION),
                preparationCases,
                facts,
                attachments,
                preparationClock);
        ReviewClinicalFactUseCase review = new ReviewClinicalFactService(
                reviewAuthorization,
                preparationCases,
                facts,
                auditEvents,
                preparationClock);
        return new TransactionalClinicalContextUseCases(get, review);
    }
}
