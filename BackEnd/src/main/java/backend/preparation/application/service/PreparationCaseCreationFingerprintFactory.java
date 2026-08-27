package backend.preparation.application.service;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Optional;

import backend.preparation.application.port.in.CreatePreparationCaseCommand;
import backend.preparation.application.port.out.PreparationCaseCreationFingerprint;
import backend.preparation.domain.AppointmentReference;

final class PreparationCaseCreationFingerprintFactory {

    private static final byte SERIALIZATION_VERSION = 1;

    private PreparationCaseCreationFingerprintFactory() {
    }

    static PreparationCaseCreationFingerprint from(CreatePreparationCaseCommand command) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return new PreparationCaseCreationFingerprint(toHex(digest.digest(serialize(command))));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static byte[] serialize(CreatePreparationCaseCommand command) {
        try {
            var bytes = new ByteArrayOutputStream();
            try (var output = new DataOutputStream(bytes)) {
                output.writeByte(SERIALIZATION_VERSION);
                writeString(output, command.patientReference().value());
                writeOptionalString(output, command.appointmentReference().map(AppointmentReference::value));
                writeOptionalInstant(output, command.scheduledAt());
            }
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Creation request serialization failed", exception);
        }
    }

    private static void writeOptionalString(DataOutputStream output, Optional<String> value) throws IOException {
        output.writeBoolean(value.isPresent());
        if (value.isPresent()) {
            writeString(output, value.get());
        }
    }

    private static void writeOptionalInstant(DataOutputStream output, Optional<Instant> value) throws IOException {
        output.writeBoolean(value.isPresent());
        if (value.isPresent()) {
            output.writeLong(value.get().getEpochSecond());
            output.writeInt(value.get().getNano());
        }
    }

    private static void writeString(DataOutputStream output, String value) throws IOException {
        byte[] utf8 = value.getBytes(StandardCharsets.UTF_8);
        output.writeInt(utf8.length);
        output.write(utf8);
    }

    private static String toHex(byte[] bytes) {
        return java.util.HexFormat.of().formatHex(bytes);
    }
}
