import jetbrains.letsPlot.*
import jetbrains.letsPlot.geom.geomLine
import jetbrains.letsPlot.label.ggtitle
import jetbrains.letsPlot.scale.scaleXDateTime
import jetbrains.letsPlot.scale.scaleYContinuous
import jetbrains.letsPlot.tooltips.layerTooltips
import org.jetbrains.kotlinx.dataframe.Column
import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.api.*
import org.jetbrains.kotlinx.dataframe.io.read
import java.text.SimpleDateFormat
import java.util.*

fun main() {
    // Column Accessors
    val size by column<Int>("Size")
    val region by column<String>("Region")
    val state by column<String>("State")
    val priceHistory by column<Pair<Date, Double?>>("Price History")
    val date by column<Date>("Date")
    val price by column<Double?>("Price")

    //Read the housing data csv
    val df = DataFrame.read("src/main/resources/state_housing_data.csv")

    //These columns contain price data and need to be sanitized
    val dataColumns = df.columnNames().subList(5, df.columnNames().size)

    //Create a DataFrame of each Price History event with formatted dates
    val yyyyMmDd = SimpleDateFormat("yyyy-MM-dd")
    val snapshot = df.select(dataColumns).map { dataRow ->
        val t = dataRow.transpose()
        val tDate = t.getColumn(0).rename(date).map { yyyyMmDd.parse(it.toString()) }
        val tPrice = t.getColumn(1).rename(price)
        dataFrameOf(tDate, tPrice)
    }

    //Create a Price History Column
    val history = columnOf(*snapshot.toTypedArray()).named(priceHistory)

    val finalMap = df
        .rename("SizeRank").into(size)
        .rename("RegionName").into(region)
        .rename("StateName").into(state)
        .select(size, region, state)
        .add(history)
        .sortBy(size)

    //Format the final map to a single dimension, and filter/track certain states as plot points
    val trackStates = arrayOf("CA", "MA", "RI", "HI", "FL", "NY", "WV", "DC", "WA", "CO")
    val plotPoints = finalMap
        //Expand each Price History table as a row entry
        .explode(priceHistory, dropEmpty = true)
        //Update Price History -> [Date, Price] as column entries
        .flatten()
        //Choose states to track
        .filter { trackStates.contains(get(state)) }

    //Tooltip Formatting
    val tooltipDateFormat = "%b %Y"
    val tooltipPriceFormat = "$,"
    val tooltips = layerTooltips()
        .format(price.name(), tooltipPriceFormat)
        .format(date.name(), tooltipDateFormat)
        .line(region.tooltipVar())
        .line(price.tooltipVar())

    //Plot data and format
    var p = letsPlot(plotPoints.toMap())
    p += geomLine(alpha = .7, size = 2, tooltips = tooltips)
    { x = date.name(); y = price.name(); color = state.name(); }
    p += ggtitle("\nZillow SFR/Condo Average Sale Price\n")
    p += scaleYContinuous(format = tooltipPriceFormat)
    p += scaleXDateTime(format = tooltipDateFormat)
    p += DataTheme.darkModeTheme
    p + ggsize(900, 800)
    p.show()
}

object DataTheme {
    private const val darcula = "#393939"
    private const val lightGrey = "light_gray"
    private const val darkGrey = "#696969"

    //Dark Mode Theme Def
    val darkModeTheme = theme(
        line = elementLine(color = lightGrey, size = 2),
        axisTicks = elementLine(color = darcula, size = 1),
        axis = elementRect(color = lightGrey),
        axisTitle = "blank",
        axisLineX = elementRect(color = lightGrey),
        axisLineY = elementRect(color = lightGrey),
        axisTextX = elementText(color = darkGrey),
        axisTextY = elementText(color = lightGrey),
        axisTooltip = elementRect(color = lightGrey, fill = darcula),
        panelGridMajor=elementLine(color = darkGrey, size = .3),
        plotBackground = elementRect(color = lightGrey, fill = darcula),
        title = elementText(color = lightGrey),
        tooltip = elementRect(color = lightGrey, fill = darcula),
        tooltipText = elementText(color = lightGrey),
    ).legendPositionNone()
}

fun Column.tooltipVar() : String {
    return "@${this.name()}"
}
