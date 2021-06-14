package no.nav.dokgen.handlebars

import com.github.jknack.handlebars.Context
import com.github.jknack.handlebars.Handlebars
import com.github.jknack.handlebars.Helper
import com.github.jknack.handlebars.Options
import java.io.IOException
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.*

interface CustomHelpers {
    class SwitchHelper() : Helper<Any> {

        @Throws(IOException::class)
        override fun apply(variable: Any, options: Options): Any? {
            val variabelNavn: MutableList<String> = ArrayList()
            val variabelVerdier: MutableList<Any> = ArrayList()
            variabelNavn.add("__condition_fulfilled")
            variabelVerdier.add(0)
            variabelNavn.add("__condition_variable")
            variabelVerdier.add(if (options.hash.isEmpty()) variable else options.hash)
            val ctx = Context.newBlockParamContext(options.context, variabelNavn, variabelVerdier)
            val resultat: String = options.fn.apply(ctx)

            val antall = ctx["__condition_fulfilled"] as Int
            if (Integer.valueOf(1) == antall) {
                return resultat
            }
            return null
        }
    }

    class CaseHelper() : Helper<Any> {

        @Throws(IOException::class)
        override fun apply(caseKonstant: Any, options: Options): Any {
            val konstant: Any = if (options.hash.isEmpty()) caseKonstant else options.hash
            val model = options.context.model() as MutableMap<String, Any>
            val condition_variable = model["__condition_variable"]
            if (konstant == condition_variable) {
                val antall = model["__condition_fulfilled"] as Int?
                if (antall != null) {
                    val nyAntall = antall.inc()
                    model.put("__condition_fulfilled", nyAntall)
                }
                return options.fn()
            }
            return options.inverse()
        }
    }


    /**
     * Allows to create a table with a set number of columns from only td cells
     * Useful if you have to render table cells but you don't know how many cells you will have.
     *
     * Example:
     * {{#table [columns=2] [class=""]]}}
     *      <td>This is one cell</td>
     *      <td>This is another cell</td>
     *      <td>This is a third cell</td>
     * {{/table}}
     *
     * will render a table with two tr rows with two cells in each
     *
     * +----------------------+----------------------+
     * | This is one cell     | This is another cell |
     * +----------------------+----------------------+
     * | This is a third cell |                      |
     * +----------------------+----------------------+
     *
     * Only supports td elements, if you want th you can give the header cells their own css classes.
     * You can also supply an optional class parameter to the helper which will be added to the table.
     *
     */
    class TableHelper() : Helper<Any> {

        override fun apply(context: Any, options: Options): Any {
            val columnCount = options.hash<Int>("columns", 2)
            val tableContents = options.fn(context)
            val cells = tableContents.trim()
                .split("</td>")
                .filter { it.isNotEmpty() }
                .map { "$it</td>" }

            val wrappedInRows = mutableListOf("<tr>")
            cells.forEachIndexed { index, cell ->
                run {
                    if (index > 0 && index % columnCount == 0) {
                        wrappedInRows += "</tr><tr>"
                    }
                    wrappedInRows += cell
                }
            }

            // If there are fewer cells than columns, the cells will stretch unless we do this
            val missingCellsInLastRow = cells.count() % columnCount
            wrappedInRows += "<td></td>".repeat(missingCellsInLastRow)

            wrappedInRows += "</tr>"

            val classParam = options.hash<String>("class", "")
            val classString = if (classParam.isNotEmpty()) "class=$classParam" else ""
            return "<table ${classString}>${wrappedInRows.joinToString("")}</table>"
        }
    }

    /**
     * Allows simple addition inside a template
     *
     * {{add 3 4}} prints 7 for example. Mostly useful when printing index
     * in loops. For example:
     * {{#each context.questions as | question |}}
     *     {{add @index 1}}. {{question.prompt}}
     * {{/each}}
     *
     * Will print every question prompt in an array of unknown size along with its index incremented by 1
     */
    class AdditionHelper(): Helper<Int> {
        override fun apply(leftOperand: Int, options: Options): Any {
            return leftOperand + options.param<Int>(0)
        }
    }

    /**
     * Allows converting ISO8601 to be formatted as a norwegian dd.mm.yyyy string
     *
     * {{norwegian-date 2020-02-01}} prints 01.02.2020
     */
    class NorwegianDateHelper(): Helper<String> {
        override fun apply(isoFormattedDate: String, options: Options): Any {
            return isoFormattedDate.split('-').reversed().joinToString(separator = ".")
        }
    }

    /**
     * Allows to divide a template parameter with a given number and round the resultvalue to the nearest krone
     */
    class DivideHelper() : Helper<Int> {
        override fun apply(antall: Int, options: Options): Any {
            val beløp = options.param<Int>(0)

            val value = BigDecimal.valueOf(beløp.toLong()).divide(BigDecimal.valueOf(antall.toLong()), 0, RoundingMode.HALF_UP)

            return value.toInt();
        }
    }

    /**
     * Format an int with thousand seperator, ex: 10000 will be 10 000
     */
    class FormatKronerHelper : Helper<Int> {
        override fun apply(kroner: Int, options: Options?): Any {
            val formatter = NumberFormat.getInstance(Locale.US) as DecimalFormat
            val symbols = formatter.decimalFormatSymbols
            symbols.groupingSeparator = ' '
            formatter.decimalFormatSymbols = symbols
            return formatter.format(kroner)
        }
    }

    /**
     * Removes trailing zeroes from decimals, ex: 10.0 becomes 10 and 90.20 becomes 90.2, while 100.3 remains the same
     */
    class TrimDecimalHelper : Helper<Double> {
        override fun apply(decimal: Double, options: Options?): Any {
            return BigDecimal.valueOf(decimal).stripTrailingZeros().toPlainString()
        }
    }

    /**
     * Replaces various newlines with <br> tags
     */

    class BreakLineHelper : Helper<String> {
        override fun apply(text : String, options: Options) : String {
            val sanitizedText = Handlebars.Utils.escapeExpression(text)
            val withLineBreak = sanitizedText.toString()
                .replace("\\r\\n", "<br/>")
                .replace("\\n", "<br/>")
                .replace("\r\n", "<br/>")
                .replace("\n", "<br/>")
            return Handlebars.SafeString(withLineBreak).toString()
        }
    }
}