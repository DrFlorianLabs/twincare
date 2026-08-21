package backend.preparation.adapter.in.web;

import java.net.URI;
import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import backend.exception.BadRequestException;
import backend.preparation.application.port.in.IssuePatientInvitationCommand;
import backend.preparation.application.port.in.IssuePatientInvitationUseCase;
import backend.preparation.application.port.in.RevokePatientInvitationCommand;
import backend.preparation.application.port.in.RevokePatientInvitationUseCase;
import backend.preparation.domain.OrganizationId;
import backend.preparation.domain.PatientInvitationId;
import backend.preparation.domain.PreparationCaseId;

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/preparation-cases/{caseId}/invitations")
public class PatientInvitationController {

    private final IssuePatientInvitationUseCase issueInvitation;
    private final RevokePatientInvitationUseCase revokeInvitation;

    public PatientInvitationController(
            IssuePatientInvitationUseCase issueInvitation,
            RevokePatientInvitationUseCase revokeInvitation) {
        this.issueInvitation = issueInvitation;
        this.revokeInvitation = revokeInvitation;
    }

    @PostMapping
    public ResponseEntity<IssuePatientInvitationResponse> issue(
            @PathVariable String organizationId,
            @PathVariable UUID caseId) {
        IssuePatientInvitationCommand command;
        try {
            command = new IssuePatientInvitationCommand(
                    new OrganizationId(organizationId),
                    new PreparationCaseId(caseId));
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("Requête d'invitation patient invalide");
        }
        var result = issueInvitation.issue(command);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{invitationId}")
                .buildAndExpand(result.invitationId().value())
                .toUri();
        return ResponseEntity.created(location)
                .cacheControl(CacheControl.noStore())
                .body(new IssuePatientInvitationResponse(
                        result.invitationId().value(),
                        result.token().value(),
                        result.expiresAt()));
    }

    @DeleteMapping("/{invitationId}")
    public ResponseEntity<Void> revoke(
            @PathVariable String organizationId,
            @PathVariable UUID caseId,
            @PathVariable UUID invitationId) {
        RevokePatientInvitationCommand command;
        try {
            command = new RevokePatientInvitationCommand(
                    new OrganizationId(organizationId),
                    new PreparationCaseId(caseId),
                    new PatientInvitationId(invitationId));
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("Requête de révocation d'invitation invalide");
        }
        revokeInvitation.revoke(command);
        return ResponseEntity.noContent()
                .cacheControl(CacheControl.noStore())
                .build();
    }
}
