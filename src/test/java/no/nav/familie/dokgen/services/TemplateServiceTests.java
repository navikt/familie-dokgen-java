package no.nav.familie.dokgen.services;

import no.nav.familie.dokgen.util.MalUtil;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Java6Assertions.catchThrowable;

public class TemplateServiceTests {
    private static final String MALNAVN = "lagretMal";
    private static final String MARKDOWN = "# Hei, {{name}}";
    private static final String MARKDOWN_CONTENT = "\"" + MARKDOWN + "\"";
    private static final String INTERLEAVING_FIELDS = "{\"name\": \"Peter\"}";
    private static final String GYLDIG_PAYLOAD = lagTestPayload(MARKDOWN_CONTENT, INTERLEAVING_FIELDS);

    private static String lagTestPayload(String markdownContent, String interleavingFields) {
        return "{\"markdownContent\": " + markdownContent +
                ", \"interleavingFields\": " + interleavingFields +
                ", \"useTestSet\": false}";
    }

    private static final String TOM_JSON = "{}";

    private TemplateService malService;
    private Path contentRoot;
    private Path templateRoot;

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Before
    public void setUp() throws IOException {
        contentRoot = temporaryFolder.getRoot().toPath();
        templateRoot = contentRoot.resolve("templates/");
        Files.createDirectories(templateRoot);
        malService = new TemplateService(contentRoot, new DokumentGeneratorService(contentRoot), new JsonService(contentRoot));
    }

    @Test
    public void skalHentAlleMalerReturnererTomtSett() {
        assertThat(malService.hentAlleMaler()).isEmpty();
    }

    @Test
    public void skalHentAlleMaler() throws IOException {
        Files.createDirectory(templateRoot.resolve("MAL1"));

        assertThat(malService.hentAlleMaler()).containsExactly("MAL1");
    }

    @Test
    public void skalReturnereExceptionVedHentingAvMal() {

        // expect
        Throwable thrown = catchThrowable(() -> malService.hentMal("IkkeEksisterendeMal"));

        // when
        assertThat(thrown).isInstanceOf(RuntimeException.class).hasMessage("Kan ikke finne mal med navn IkkeEksisterendeMal");
    }

    @Test
    public void skalReturnereMal() throws IOException {
        Path malPath = templateRoot.resolve("MAL");
        Files.createDirectories(malPath);
        Files.write(malPath.resolve("MAL.hbs"), "maldata".getBytes());


        // expect
        malService.hentMal("MAL");

        // when
        assertThat(malService.hentMal("MAL")).isEqualTo("maldata");
    }

    @Test
    public void skalReturnereIllegalArgumentExceptionVedLagreMalUtenPayloadUtenMarkdown() {

        // expect
        Throwable thrown = catchThrowable(() -> malService.lagreMal("tomPayload", "{}"));

        assertThat(thrown).isInstanceOf(IllegalArgumentException.class).hasMessage("Kan ikke hente markdown for payload={}");
    }

    @Test
    public void skalLagreMal() throws IOException {

        // expect
        malService.lagreMal(MALNAVN, GYLDIG_PAYLOAD);

        String malData = Files.readString(MalUtil.hentMal(contentRoot, MALNAVN));
        assertThat(malData).isEqualTo(MARKDOWN);
    }

    @Test
    public void skal_overskrive_alt_ved_lagring_av_nytt_slankere_innhold() throws IOException {
        skalLagreMal();

        malService.lagreMal(MALNAVN, lagTestPayload("\"" + MARKDOWN.substring(3) + "\"", INTERLEAVING_FIELDS));

        String malData = Files.readString(MalUtil.hentMal(contentRoot, MALNAVN));
        assertThat(malData).isEqualTo(MARKDOWN.substring(3));
    }

    @Test
    public void skalHenteHtml() throws IOException {
        String malNavn = "html";

        FileUtils.writeStringToFile(MalUtil.hentJsonSchemaForMal(contentRoot, malNavn).toFile(),
                TOM_JSON,
                StandardCharsets.UTF_8
        );

        // expect
        malService.lagreMal(malNavn, GYLDIG_PAYLOAD);
        String html = malService.lagHtml(malNavn, GYLDIG_PAYLOAD);
        assertThat(html).contains("<h1>Hei, Peter</h1>");
    }

    @Test
    public void skalHentePdf() throws IOException {
        String malNavn = "pdf";

        FileUtils.writeStringToFile(MalUtil.hentJsonSchemaForMal(contentRoot, malNavn).toFile(),
                TOM_JSON,
                StandardCharsets.UTF_8
        );

        // expect
        malService.lagreMal(malNavn, GYLDIG_PAYLOAD);
        byte[] pdf = malService.lagPdf(malNavn, GYLDIG_PAYLOAD);
        assertThat(pdf[1]).isEqualTo((byte) 0x50);//P
        assertThat(pdf[2]).isEqualTo((byte) 0x44);//D
        assertThat(pdf[3]).isEqualTo((byte) 0x46);//F
    }
}