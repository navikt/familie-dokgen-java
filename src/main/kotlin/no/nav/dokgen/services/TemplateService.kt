package no.nav.dokgen.services

import com.fasterxml.jackson.databind.JsonNode
import com.github.jknack.handlebars.Context
import com.github.jknack.handlebars.Handlebars
import com.github.jknack.handlebars.JsonNodeValueResolver
import com.github.jknack.handlebars.Template
import com.github.jknack.handlebars.context.FieldValueResolver
import com.github.jknack.handlebars.context.JavaBeanValueResolver
import com.github.jknack.handlebars.context.MapValueResolver
import com.github.jknack.handlebars.context.MethodValueResolver
import com.github.jknack.handlebars.helper.ConditionalHelpers
import com.github.jknack.handlebars.helper.StringHelpers
import com.github.jknack.handlebars.io.FileTemplateLoader
import no.nav.dokgen.controller.api.CreateDocumentRequest
import no.nav.dokgen.exceptions.DokgenNotFoundException
import no.nav.dokgen.handlebars.CustomHelpers
import no.nav.dokgen.resources.TemplateResource
import no.nav.dokgen.util.DocFormat
import no.nav.dokgen.util.FileStructureUtil.getFormatSchema
import no.nav.dokgen.util.FileStructureUtil.getTemplatePath
import no.nav.dokgen.util.FileStructureUtil.getTemplateRootPath
import no.nav.dokgen.util.FileStructureUtil.getTemplateSchemaPath
import org.everit.json.schema.ValidationException
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.safety.Whitelist
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.util.function.Function
import java.util.stream.Collectors

@Service
class TemplateService @Autowired internal constructor(
    @param:Value("\${path.content.root:./content/}") private val contentRoot: Path,
    documentGeneratorService: DocumentGeneratorService,
    jsonService: JsonService
) {
    private val handlebars: Handlebars
    private val documentGeneratorService: DocumentGeneratorService
    private val jsonService: JsonService
    fun compileInLineTemplate(templateContent: String?): Template? {
        return Result.runCatching {
            handlebars.compileInline(templateContent)
        }.fold(
            onSuccess = { it },
            onFailure = { e ->
                LOG.error("Kompilering av templates feiler", e)
                null
            }
        )
    }

    fun compileInlineAndApply(templateContent: String?, data: Context?): String? {
        return Result.runCatching {
            handlebars.compileInline(templateContent).apply(data)
        }.fold(
            onSuccess = {it},
            onFailure = {
                LOG.warn("Kompilering av malinnhold feilet: $templateContent")
                templateContent
            }
        )
    }

    @Throws(ValidationException::class, IOException::class)
    private fun getCompiledTemplate(templateResource: TemplateResource, mergeFields: JsonNode): String? {
        val template = compileInLineTemplate(templateResource.content)
        jsonService.validereJson(getTemplateSchemaPath(contentRoot, templateResource.name), mergeFields)
        return template?.apply(with(mergeFields))
    }

    private fun with(model: JsonNode?): Context {
        return Context
            .newBuilder(model)
            .resolver(
                JsonNodeValueResolver.INSTANCE,
                JavaBeanValueResolver.INSTANCE,
                FieldValueResolver.INSTANCE,
                MapValueResolver.INSTANCE,
                MethodValueResolver.INSTANCE
            ).build()
    }

    fun listTemplates(): List<String> {
        return Result.runCatching {
            Files.list(getTemplateRootPath(contentRoot)).use { paths ->
                paths
                    .filter { path: Path? -> Files.isDirectory(path) }
                    .filter { p: Path -> !p.toFile().isHidden }
                    .map { x: Path -> x.fileName.toString() }
                    .collect(Collectors.toList())
            }
        }.fold(
            onSuccess = {it},
            onFailure = {e ->
                LOG.error("List av templates feiler", e)
                emptyList()
            }
        )
    }

    fun getTemplate(templateName: String): TemplateResource {
        return getTemplate(templateName, getTemplatePath(contentRoot, templateName))
    }

    fun getTemplate(templateName: String, variation: String): TemplateResource {
        return getTemplate(templateName, getTemplatePath(contentRoot, templateName, variation))
    }

    private fun getTemplate(templateName: String, templatePath: Path): TemplateResource {
        return try {
            val content = Files.readString(templatePath, StandardCharsets.UTF_8)
            val resource = TemplateResource(name = templateName, content= content)
            resource
        } catch (e: NoSuchFileException) {
            throw DokgenNotFoundException("Kan ikke finne mal med navn $templateName")
        } catch (e: IOException) {
            throw RuntimeException("Kan ikke hente mal $templateName", e)
        }
    }

    fun createPdf(templateName: String, payload: String?): ByteArray {
        val templateResource = getTemplate(templateName)
        return createPdf(templateResource, payload)
    }

    fun createPdf(templateName: String, payload: String?, variation: String): ByteArray {
         val templateResource = getTemplate(templateName, variation)
        return createPdf(templateResource, payload)
    }

    fun createHtml(templateName: String, payload: String?): String {
        val templateResource = getTemplate(templateName)
        return createHtml(templateResource, payload)
    }

    fun createHtml(templateName: String, payload: String?, variation: String): String {
        val templateResource = getTemplate(templateName, variation)
        return createHtml(templateResource, payload)
    }

    fun createMarkdown(templateName: String, mergefields: String): String? {
        val templateResource = getTemplate(templateName)
        return createMarkdown(templateName, mergefields, templateResource)
    }

    fun createMarkdown(templateName: String, mergefields: String, variation: String): String? {
        val templateResource = getTemplate(templateName, variation)
        return createMarkdown(templateName, mergefields, templateResource)
    }

    private fun createMarkdown(templateName: String, mergefields: String, templateResource: TemplateResource): String? {
        return try {
            getCompiledTemplate(templateResource, jsonService.getJsonFromString(mergefields))
        } catch (e: IOException) {
            throw RuntimeException("Kunne ikke lage Markdown, templateName={} $templateName", e)
        }
    }

    fun createPdf(templateResource: TemplateResource, payload: String?): ByteArray {
        return try {
            convertToPdf(templateResource, jsonService.getJsonFromString(payload))
        } catch (e: IOException) {
            throw RuntimeException("Kunne ikke lage pdf, templateName={} " + templateResource.name, e)
        }
    }

    fun createHtml(templateResource: TemplateResource, payload: String?): String {
        return try {
            convertToHtml(templateResource, jsonService.getJsonFromString(payload))
        } catch (e: IOException) {
            throw RuntimeException("Kunne ikke lage HTML, templateName={} " + templateResource.name, e)
        }
    }

    fun createDocument(request: CreateDocumentRequest, templateName: String): Document {
        return try {
            val template = getCompiledTemplate(request, templateName)
            val headerFields =
                if (request.headerFields == null || !request.isIncludeHeader) null else jsonService.getJsonFromString(
                    request.headerFields
                )
            convertToDocument(request.docFormat!!, template, request.isIncludeHeader, headerFields)
        } catch (e: IOException) {
            throw RuntimeException("Kunne ikke mappe header-felter til Json", e)
        }
    }

    private fun getCompiledTemplate(request: CreateDocumentRequest, templateName: String): TemplateResource {
        return try {
            TemplateResource(templateName).apply{
            if (request.templateContent != null) {
                // use precompiled or custom template content
                if (request.isPrecompiled) {
                    compiledContent = request.templateContent
                } else {
                    val mergeFields = jsonService.getJsonFromString(request.mergeFields)
                    compiledContent = compileInlineAndApply(request.templateContent, with(mergeFields))
                }
            } else {
                // load and compile template from file.
                val mergeFields = jsonService.getJsonFromString(request.mergeFields)
                compiledContent = getCompiledTemplate(getTemplate(templateName), mergeFields)
            }}
        } catch (e: IOException) {
            throw RuntimeException("Kunne ikke lage dokument med angitt flettefelt-json", e)
        }
    }

    fun saveTemplate(templateName: String, payload: String) {
        val malPath = getTemplatePath(contentRoot, templateName)
        saveTemplate(templateName, payload, malPath)
    }

    fun saveTemplate(templateName: String, payload: String, variation: String) {
        val malPath = getTemplatePath(contentRoot, templateName, variation)
        saveTemplate(templateName, payload, malPath)
    }

    private fun saveTemplate(templateName: String, payload: String, malPath: Path) {
        try {
            val jsonContent = jsonService.getJsonFromString(payload)
            val settings = Document.OutputSettings()
            settings.prettyPrint(false)
            val markdownContent1 = jsonContent["markdownContent"]
            if (markdownContent1 != null) {
                val markdownContent = jsonContent["markdownContent"].textValue()
                val strippedHtmlSyntax = Jsoup.clean(
                    markdownContent,
                    "",
                    Whitelist.none(),
                    settings
                )
                Files.createDirectories(malPath.parent)
                Files.write(malPath, strippedHtmlSyntax.toByteArray(StandardCharsets.UTF_8))
            } else {
                throw IllegalArgumentException("Kan ikke hente markdown for payload=$payload")
            }
        } catch (e: IOException) {
            throw RuntimeException("Feil ved lagring av mal=$templateName payload=$payload", e)
        }
    }

    @Throws(IOException::class)
    private fun convertToHtml(template: TemplateResource, mergeFields: JsonNode): String {
        template.compiledContent = getCompiledTemplate(template, mergeFields)
        val styledHtml = convertToDocument(DocFormat.HTML, template, false, null)
        return styledHtml.html()
    }

    @Throws(IOException::class)
    private fun convertToPdf(template: TemplateResource, mergeFields: JsonNode): ByteArray {
        template.compiledContent = getCompiledTemplate(template, mergeFields)
        val styledHtml = convertToDocument(DocFormat.PDF, template, true, mergeFields)
        return generatePdf(styledHtml)
    }

    fun generatePdf(document: Document?): ByteArray {
        val outputStream = ByteArrayOutputStream()
        documentGeneratorService.genererPDF(document, outputStream)
        return outputStream.toByteArray()
    }

    private fun convertToDocument(
        format: DocFormat,
        template: TemplateResource,
        wrapDocument: Boolean,
        headerFields: JsonNode?
    ): Document {
        val styledHtml = documentGeneratorService.appendHtmlMetadata(template.compiledContent!!, format)
        if (wrapDocument) {
            documentGeneratorService.wrapDocument(
                styledHtml,
                format,
                headerFunction(format, template.name, headerFields)
            )
        }
        return styledHtml
    }

    private fun headerFunction(
        format: DocFormat,
        templateName: String,
        mergeFields: JsonNode?
    ): Function<String?, String?> {
        return Function { header: String? ->
            validateIfRequired(mergeFields, format)
            compileInlineAndApply(header, with(mergeFields).combine("templateName", templateName))
        }
    }

    private fun validateIfRequired(mergeFields: JsonNode?, format: DocFormat) {
        try {
            jsonService.validereJson(getFormatSchema(contentRoot, format), mergeFields)
        } catch (ignore: FileNotFoundException) {
            // This header does not require validation
        } catch (e: Exception) {
            throw RuntimeException("Feil ved validering av header-felter", e)
        }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(TemplateService::class.java)
    }

    init {
        handlebars = if (Files.exists(getTemplateRootPath(contentRoot))) Handlebars(
            FileTemplateLoader(
                getTemplateRootPath(
                    contentRoot
                ).toFile()
            )
        ) else Handlebars()
        handlebars.registerHelper("eq", ConditionalHelpers.eq)
        handlebars.registerHelper("neq", ConditionalHelpers.neq)
        handlebars.registerHelper("gt", ConditionalHelpers.gt)
        handlebars.registerHelper("gte", ConditionalHelpers.gte)
        handlebars.registerHelper("lt", ConditionalHelpers.lt)
        handlebars.registerHelper("lte", ConditionalHelpers.lte)
        handlebars.registerHelper("and", ConditionalHelpers.and)
        handlebars.registerHelper("or", ConditionalHelpers.or)
        handlebars.registerHelper("not", ConditionalHelpers.not)
        handlebars.registerHelper("switch", CustomHelpers.SwitchHelper() )
        handlebars.registerHelper("case", CustomHelpers.CaseHelper())
        handlebars.registerHelper("table", CustomHelpers.TableHelper())
        handlebars.registerHelper("add", CustomHelpers.AdditionHelper())
        handlebars.registerHelper("norwegian-date", CustomHelpers.NorwegianDateHelper())
        handlebars.registerHelper("divide", CustomHelpers.DivideHelper())
        handlebars.registerHelper("format-kroner", CustomHelpers.FormatKronerHelper())
        handlebars.registerHelper("trim-decimal", CustomHelpers.TrimDecimalHelper())
        handlebars.registerHelper("break-lines", CustomHelpers.BreakLineHelper())
        handlebars.registerHelpers(StringHelpers::class.java)
        this.documentGeneratorService = documentGeneratorService
        this.jsonService = jsonService
    }
}