package backend.preparation.application.port.in;

public record ResolvePatientAccessSessionCommand(String sessionToken) {

    @Override
    public String toString() {
        return "ResolvePatientAccessSessionCommand[sessionToken=<redacted>]";
    }
}
