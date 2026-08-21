package backend.preparation.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import backend.exception.UnauthorizedActionException;
import backend.preparation.application.PreparationAttachmentNotFoundException;
import backend.preparation.application.PreparationCaseNotFoundException;
import backend.preparation.application.ProfessionalAttachmentStorageUnavailableException;
import backend.preparation.application.port.in.DownloadProfessionalAttachmentCommand;
import backend.preparation.application.port.out.PreparedAttachmentContent;
import backend.preparation.application.port.out.PreparationAttachmentRepository;
import backend.preparation.application.port.out.PreparationCaseCapability;
import backend.preparation.application.port.out.PreparationCaseRepository;
import backend.preparation.application.port.out.PrivateAttachmentContentReader;
import backend.preparation.application.port.out.ProfessionalPreparationAuthorization;
import backend.preparation.domain.AttachmentDigest;
import backend.preparation.domain.AttachmentId;
import backend.preparation.domain.AttachmentMediaType;
import backend.preparation.domain.AttachmentStorageKey;
import backend.preparation.domain.OrganizationId;
import backend.preparation.domain.PatientReference;
import backend.preparation.domain.PreparationAttachment;
import backend.preparation.domain.PreparationCase;
import backend.preparation.domain.PreparationCaseId;

class DownloadProfessionalAttachmentServiceTest {

    private static final OrganizationId ORGANIZATION_ID = new OrganizationId("synthetic-org");
    private static final PreparationCaseId CASE_ID = new PreparationCaseId(
            UUID.fromString("10000000-0000-0000-0000-000000000001"));
    private static final AttachmentStorageKey STORAGE_KEY = new AttachmentStorageKey(
            UUID.fromString("20000000-0000-0000-0000-000000000001"));
    private static final AttachmentDigest DIGEST = new AttachmentDigest("a".repeat(64));
    private static final Instant NOW = Instant.parse("2026-08-21T10:00:00Z");

    private ProfessionalPreparationAuthorization authorization;
    private PreparationCaseRepository preparationCases;
    private PreparationAttachmentRepository attachments;
    private PrivateAttachmentContentReader contentReader;
    private DownloadProfessionalAttachmentService service;

    @BeforeEach
    void setUp() {
        authorization = mock(ProfessionalPreparationAuthorization.class);
        preparationCases = mock(PreparationCaseRepository.class);
        attachments = mock(PreparationAttachmentRepository.class);
        contentReader = mock(PrivateAttachmentContentReader.class);
        service = new DownloadProfessionalAttachmentService(
                authorization,
                preparationCases,
                attachments,
                contentReader,
                event -> { },
                Clock.fixed(NOW, ZoneOffset.UTC));
        when(authorization.requireAccess(
                ORGANIZATION_ID, PreparationCaseCapability.DOWNLOAD_ATTACHMENT)).thenReturn(42L);
    }

    @Test
    void authorizesThenLocksTenantCaseAndAttachmentBeforePreparingVerifiedContent() {
        var preparationCase = preparationCase();
        var attachment = attachment();
        PreparedAttachmentContent content = mock(PreparedAttachmentContent.class);
        when(content.sizeBytes()).thenReturn(42L);
        when(preparationCases.findByIdForUpdate(CASE_ID, ORGANIZATION_ID))
                .thenReturn(Optional.of(preparationCase));
        when(attachments.findByCaseForUpdate(CASE_ID, ORGANIZATION_ID))
                .thenReturn(Optional.of(attachment));
        when(contentReader.openVerified(STORAGE_KEY, 42L, DIGEST)).thenReturn(content);

        var result = service.download(command());

        assertThat(result.displayFilename()).isEqualTo("synthetic-result.pdf");
        assertThat(result.mediaType()).isEqualTo(AttachmentMediaType.PDF);
        assertThat(result.content()).isSameAs(content);
        assertThat(result.toString()).doesNotContain("synthetic-result.pdf", DIGEST.value());
        assertThat(command().toString()).doesNotContain(ORGANIZATION_ID.value(), CASE_ID.value().toString());
        var ordered = inOrder(authorization, preparationCases, attachments, contentReader);
        ordered.verify(authorization).requireAccess(
                ORGANIZATION_ID, PreparationCaseCapability.DOWNLOAD_ATTACHMENT);
        ordered.verify(preparationCases).findByIdForUpdate(CASE_ID, ORGANIZATION_ID);
        ordered.verify(attachments).findByCaseForUpdate(CASE_ID, ORGANIZATION_ID);
        ordered.verify(contentReader).openVerified(STORAGE_KEY, 42L, DIGEST);
    }

    @Test
    void deniedAccessStopsBeforeAnyCaseOrStorageLookup() {
        when(authorization.requireAccess(
                ORGANIZATION_ID, PreparationCaseCapability.DOWNLOAD_ATTACHMENT))
                .thenThrow(new UnauthorizedActionException("forbidden"));

        assertThatThrownBy(() -> service.download(command()))
                .isInstanceOf(UnauthorizedActionException.class);
        verify(preparationCases, never()).findByIdForUpdate(CASE_ID, ORGANIZATION_ID);
        verify(attachments, never()).findByCaseForUpdate(CASE_ID, ORGANIZATION_ID);
        verify(contentReader, never()).openVerified(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void missingTenantScopedCaseAndMissingAttachmentStayDistinctFromStorageFailure() {
        when(preparationCases.findByIdForUpdate(CASE_ID, ORGANIZATION_ID))
                .thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.download(command()))
                .isInstanceOf(PreparationCaseNotFoundException.class);

        when(preparationCases.findByIdForUpdate(CASE_ID, ORGANIZATION_ID))
                .thenReturn(Optional.of(preparationCase()));
        when(attachments.findByCaseForUpdate(CASE_ID, ORGANIZATION_ID))
                .thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.download(command()))
                .isInstanceOf(PreparationAttachmentNotFoundException.class);

        verify(contentReader, never()).openVerified(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void closesPreparedContentIfResultMetadataCannotBeConstructed() {
        PreparedAttachmentContent content = mock(PreparedAttachmentContent.class);
        when(content.sizeBytes()).thenReturn(41L);
        when(preparationCases.findByIdForUpdate(CASE_ID, ORGANIZATION_ID))
                .thenReturn(Optional.of(preparationCase()));
        when(attachments.findByCaseForUpdate(CASE_ID, ORGANIZATION_ID))
                .thenReturn(Optional.of(attachment()));
        when(contentReader.openVerified(STORAGE_KEY, 42L, DIGEST)).thenReturn(content);

        assertThatThrownBy(() -> service.download(command()))
                .isInstanceOf(IllegalArgumentException.class);
        verify(content).close();
    }

    @Test
    void storageFailureIsTranslatedToTheProfessionalGenericFailure() {
        when(preparationCases.findByIdForUpdate(CASE_ID, ORGANIZATION_ID))
                .thenReturn(Optional.of(preparationCase()));
        when(attachments.findByCaseForUpdate(CASE_ID, ORGANIZATION_ID))
                .thenReturn(Optional.of(attachment()));
        when(contentReader.openVerified(STORAGE_KEY, 42L, DIGEST))
                .thenThrow(new ProfessionalAttachmentStorageUnavailableException(
                        new java.io.IOException("synthetic internal path failure")));

        assertThatThrownBy(() -> service.download(command()))
                .isInstanceOf(ProfessionalAttachmentStorageUnavailableException.class)
                .hasMessage("Professional preparation attachment storage is unavailable");
    }

    private DownloadProfessionalAttachmentCommand command() {
        return new DownloadProfessionalAttachmentCommand(ORGANIZATION_ID, CASE_ID);
    }

    private PreparationCase preparationCase() {
        return PreparationCase.create(
                CASE_ID,
                ORGANIZATION_ID,
                new PatientReference("synthetic-patient"),
                Optional.empty(),
                Optional.of(NOW.plusSeconds(3600)),
                NOW);
    }

    private PreparationAttachment attachment() {
        return PreparationAttachment.uploadedByPatient(
                new AttachmentId(UUID.fromString("30000000-0000-0000-0000-000000000001")),
                ORGANIZATION_ID,
                CASE_ID,
                STORAGE_KEY,
                "synthetic-result.pdf",
                AttachmentMediaType.PDF,
                42L,
                DIGEST,
                NOW);
    }
}
