package no.nav.dokgen.log;

import java.util.UUID;

public final class IdUtils {

    private IdUtils() {
    }

    public static String generateId() {
        UUID uuid = UUID.randomUUID();
        return Long.toHexString(uuid.getMostSignificantBits()) + Long.toHexString(uuid.getLeastSignificantBits());
    }
}