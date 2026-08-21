package backend.preparation.adapter.in.web;

import java.time.Instant;

public record PatientSessionResponse(String status, Instant expiresAt) {
}
