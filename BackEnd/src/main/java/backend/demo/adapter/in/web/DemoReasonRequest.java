package backend.demo.adapter.in.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DemoReasonRequest(
        @NotBlank @Size(max = 4000) String reason) {

    @Override
    public String toString() {
        return "DemoReasonRequest[reason=<redacted>]";
    }
}
