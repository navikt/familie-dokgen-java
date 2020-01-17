package no.nav.dokgen.log;

public enum NavHttpHeaders {
    NAV_PERSONIDENT("Nav-Personident"),

    NAV_CALL_ID("Nav-Call-Id"),

    NAV_CONSUMER_ID("Nav-Consumer-Id");

    private final String header;

    NavHttpHeaders(String header) {
        this.header = header;
    }

    public String asString() {
        return header;
    }
}
