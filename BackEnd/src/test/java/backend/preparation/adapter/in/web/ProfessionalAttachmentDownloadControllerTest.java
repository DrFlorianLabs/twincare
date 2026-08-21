package backend.preparation.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import backend.preparation.application.ProfessionalAttachmentStorageUnavailableException;
import backend.preparation.application.port.in.DownloadProfessionalAttachmentUseCase;
import backend.preparation.application.port.in.ProfessionalAttachmentDownloadResult;
import backend.preparation.application.port.out.PreparedAttachmentContent;
import backend.preparation.domain.AttachmentMediaType;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;

class ProfessionalAttachmentDownloadControllerTest {

    @Test
    void interruptedResponseDoesNotTriggerAMisleadingSecondHttpErrorAndClosesContent() throws Exception {
        DownloadProfessionalAttachmentUseCase useCase = mock(DownloadProfessionalAttachmentUseCase.class);
        PreparedAttachmentContent content = mock(PreparedAttachmentContent.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(content.sizeBytes()).thenReturn(42L);
        var result = new ProfessionalAttachmentDownloadResult(
                "synthetic-result.pdf", AttachmentMediaType.PDF, 42L, content);
        when(useCase.download(any())).thenReturn(result);
        when(response.getOutputStream()).thenReturn(mock(ServletOutputStream.class));
        org.mockito.Mockito.doThrow(new IOException("synthetic client disconnect"))
                .when(content)
                .transferTo(any());

        var controller = new ProfessionalAttachmentDownloadController(useCase);

        assertThatCode(() -> controller.download(
                "cabinet-a",
                UUID.randomUUID().toString(),
                response)).doesNotThrowAnyException();
        verify(content).close();
    }

    @Test
    void storageReadFailureRemainsVisibleToTheProfessionalErrorHandler() throws Exception {
        DownloadProfessionalAttachmentUseCase useCase = mock(DownloadProfessionalAttachmentUseCase.class);
        PreparedAttachmentContent content = mock(PreparedAttachmentContent.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(content.sizeBytes()).thenReturn(42L);
        var result = new ProfessionalAttachmentDownloadResult(
                "synthetic-result.pdf", AttachmentMediaType.PDF, 42L, content);
        when(useCase.download(any())).thenReturn(result);
        when(response.getOutputStream()).thenReturn(mock(ServletOutputStream.class));
        var storageFailure = new ProfessionalAttachmentStorageUnavailableException(
                new IOException("synthetic storage read failure"));
        org.mockito.Mockito.doThrow(storageFailure).when(content).transferTo(any());

        var controller = new ProfessionalAttachmentDownloadController(useCase);

        assertThatThrownBy(() -> controller.download(
                "cabinet-a",
                UUID.randomUUID().toString(),
                response)).isSameAs(storageFailure);
        verify(content).close();
    }
}
