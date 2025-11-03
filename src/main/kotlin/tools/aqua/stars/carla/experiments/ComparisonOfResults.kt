package tools.aqua.stars.carla.experiments

import tools.aqua.stars.core.metric.serialization.SerializableFailedMonitorsResult
import java.io.File
import kotlinx.serialization.json.Json
import org.jetbrains.letsPlot.Stat
import org.jetbrains.letsPlot.export.ggsave
import org.jetbrains.letsPlot.geom.geomBar
import org.jetbrains.letsPlot.geom.geomHLine
import org.jetbrains.letsPlot.letsPlot
import org.jetbrains.letsPlot.pos.positionDodge
import tools.aqua.stars.core.metric.serialization.SerializableTSCOccurrenceResult
import tools.aqua.stars.core.metric.serialization.tsc.SerializableTSCNode
import tools.aqua.stars.core.metric.utils.ApplicationConstantsHolder
import tools.aqua.stars.core.metric.utils.plotDataAsBarChart
import tools.aqua.stars.core.metric.utils.plotDataAsBarChartWithOneLine

class ComparisonOfResults {

    fun loadMonitorFile(path: String): SerializableFailedMonitorsResult {
        val jsonString = File(path).readText(Charsets.UTF_8)
        val json = Json { ignoreUnknownKeys = true }
        val serializableFailedMonitorsResult =
            json.decodeFromString(SerializableFailedMonitorsResult.serializer(), jsonString)
        return serializableFailedMonitorsResult
    }

    fun loadTSCFile(path: String): SerializableTSCOccurrenceResult {
        val jsonString = File(path).readText(Charsets.UTF_8)
        val json = Json { ignoreUnknownKeys = true }
        val serializableTSCResult = json.decodeFromString(SerializableTSCOccurrenceResult.serializer(), jsonString)
        return serializableTSCResult
    }

    fun buildDifferenceBetweenTwoFailedMonitorsResults(
        countedResult1: Map<String, Int>,
        countedResult2: Map<String, Int>
    ): Map<String, Int> {
        val difference = mutableMapOf<String, Int>()

        //build map with all keys and values of file1
        for (label in countedResult1.keys.plus(countedResult2.keys)) {
            difference[label] = countedResult2[label] ?: 0
        }
        //subtract values of file2
        for (label in difference.keys) {
            difference.compute(label) { _, l -> l?.minus(countedResult1[label] ?: 0) }
        }

        return difference
    }

    fun buildPercentageDifferenceBetweenTwoFailedMonitorsResults(
        countedResult1: Map<String, Int>,
        countedResult2: Map<String, Int>
    ): Map<String, Double> {
        val difference = mutableMapOf<String, Double>()

        //build map with all keys and values of file1
        for (label in countedResult1.keys.plus(countedResult2.keys)) {
            difference[label] = countedResult2[label]?.toDouble() ?: 0.0
        }
        //subtract values of file2
        for (label in difference.keys) {
            difference.compute(label) { _, l -> l?.div(countedResult1[label] ?: -1) }
        }

        return difference
    }

    fun countMonitors(result: SerializableFailedMonitorsResult): Map<String, Int> {
        val counts = mutableMapOf<String, Int>()
        for (monitor in result.value) {
            counts.compute(monitor.monitorLabel) { _, l -> l?.plus(1) ?: 1 }
        }
        return counts
    }

    fun loadScenarioFile(path: String): SerializableTSCOccurrenceResult {
        val jsonString = File(path).readText(Charsets.UTF_8)
        val json = Json { ignoreUnknownKeys = true }
        val serializableTSCOccurenceResult =
            json.decodeFromString(SerializableTSCOccurrenceResult.serializer(), jsonString)
        return serializableTSCOccurenceResult
    }

    fun convertTSCResultToMap(result: SerializableTSCOccurrenceResult): Map<SerializableTSCNode, Int> {
        val map = mutableMapOf<SerializableTSCNode, Int>()

        for (value in result.value) {
            map[value.tscInstance] = value.segmentIdentifiers.size
        }

        return map
    }

    fun buildPercentageDifferenceBetweenTwoTSCOccurenceResults(
        tscOccurrenceResult1: SerializableTSCOccurrenceResult,
        tscOccurrenceResult2: SerializableTSCOccurrenceResult
    ): Map<SerializableTSCNode, Double> {
        val difference = mutableMapOf<SerializableTSCNode, Double>()

        val countResult1 = convertTSCResultToMap(tscOccurrenceResult1)
        val countResult2 = convertTSCResultToMap(tscOccurrenceResult2)

        //build map with all keys and values of file1
        for (tsc in countResult1.keys.plus(countResult2.keys)) {
            difference[tsc] = countResult2[tsc]?.toDouble() ?: 0.0
        }
        //divide values of file2 by values of file1
        for (tsc in difference.keys) {
            val value1 = countResult1[tsc]?.toDouble() ?: -1.0
            val value2 = difference[tsc] ?: 0.0
            difference[tsc] = value2 / value1
        }
        return difference
    }

    fun buildInstanceOccurence(result: SerializableTSCOccurrenceResult): List<Int> {
        return convertTSCResultToMap(result).values.sortedDescending()
    }

    fun plotInstanceOccurenceDifference(path1: String, path2: String) {
        val occurenceResult1 = loadTSCFile(path1)
        val occurenceResult2 = loadTSCFile(path2)

        val sortedListInstanceOccurence1 = buildInstanceOccurence(occurenceResult1)
        val sortedListInstanceOccurence2 = buildInstanceOccurence(occurenceResult2)

        val plotData1 = mapOf(
            "instance indices" to ((1..sortedListInstanceOccurence1.size).map { it -> it.toString() }),
            "instance count" to sortedListInstanceOccurence1
        )

        val plotData2 = mapOf(
            "instance indices" to ((1..sortedListInstanceOccurence2.size).map { it -> it.toString() }),
            "instance count" to sortedListInstanceOccurence2
        )

        val combinedData = mapOf(
            "instance indices" to (plotData1["instance indices"]!! + plotData2["instance indices"]!!),
            "instance count" to (plotData1["instance count"]!! + plotData2["instance count"]!!),
            "gruppe" to List(plotData1["instance indices"]!!.size) { "Dataset1" } +
                    List(plotData2["instance indices"]!!.size) { "Dataset2" }
        )

        val plot = letsPlot(combinedData) {
            x = "instance indices"
            y = "instance count"
            color = "gruppe"   // automatisch Farben zuweisen
        } + geomBar(position = positionDodge(), stat = Stat.identity)

        ggsave(
            plot = plot,
            filename = "occurenceDifference.png",
            path = "${ApplicationConstantsHolder.logFolder}/${ApplicationConstantsHolder.applicationStartTimeString}/plots/".also {
                File(it).mkdirs()
            })

    }

    fun plotInstanceOccurencePercentageDifferenceTop10(path1: String, path2: String) {
        val tscResult1 = loadTSCFile(path1)
        val tscResult2 = loadTSCFile(path2)

        val tscDifference =
            buildPercentageDifferenceBetweenTwoTSCOccurenceResults(tscResult1, tscResult2).filter { it -> it.value > 0 }
                .toList().sortedByDescending { it.second }.take(10).toMap()

        println("-----------------------------------------------")
        println("Top 10 instsance occurences:")

        for (tsc in tscDifference.keys) {
            println(tscDifference[tsc].toString() + "\n" + tsc.toString(1) + "\n")
        }

        val plotData = mutableMapOf(
            "instance index" to ((1..tscDifference.size).map { it -> it.toString() }),
            "relative difference" to tscDifference.values
        )
        val plot = letsPlot(plotData) {
            x = "instance index"
            y = "relative difference"
        }
        plotDataAsBarChart(
            plot = plot,
            folder = "difference_plots",
            fileName = "instanceOccurencePercentageDifferenceTop10"
        )
    }

    fun csvInstanceOccurencePercentageDifferenceTop10(path1: String, path2: String, outputPath: String) {
        val tscResult1 = loadTSCFile(path1)
        val tscResult2 = loadTSCFile(path2)

        val tscDifference =
            buildPercentageDifferenceBetweenTwoTSCOccurenceResults(tscResult1, tscResult2).filter { it -> it.value > 0 }
                .toList().sortedByDescending { it.second }.take(10).toMap()

        println("-----------------------------------------------")
        println("Top 10 instsance occurences:")

        for (tsc in tscDifference.keys) {
            println(tscDifference[tsc].toString() + "\n" + tsc.toString(1) + "\n")
        }

        val file = File(outputPath)

        // CSV header
        file.printWriter().use { out ->
            out.println(
                "instanceIndex,percentage"
            )

            for ((index, tsc) in tscDifference.keys.withIndex()) {
                out.println(
                    listOf(
                        index+1,
                        tscDifference[tsc]
                    ).joinToString(",")
                )
            }
        }
        println("✅ CSV written to: ${file.absolutePath}")
    }

    fun plotInstanceOccurencePercentageDifferenceBottom10(path1: String, path2: String) {
        val tscResult1 = loadTSCFile(path1)
        val tscResult2 = loadTSCFile(path2)

        val tscDifference = buildPercentageDifferenceBetweenTwoTSCOccurenceResults(
            tscResult1,
            tscResult2
        ).filter { it -> it.value >= 0 }.toList().sortedBy { it.second }.take(10).toMap()

        println("-----------------------------------------------")
        println("Bottom 10 instsance occurences:")

        for (tsc in tscDifference.keys) {
            println(tscDifference[tsc].toString() + "\n" + tsc.toString(1) + "\n")
        }

        val plotData = mutableMapOf(
            "instance index" to ((1..tscDifference.size).map { it -> it.toString() }),
            "relative difference" to tscDifference.values
        )
        val plot = letsPlot(plotData) {
            x = "instance index"
            y = "relative difference"
        }
        plotDataAsBarChart(
            plot = plot,
            folder = "difference_plots",
            fileName = "instanceOccurencePercentageDifferenceBottom10"
        )
    }

    fun csvInstanceOccurencePercentageDifferenceBottom10(path1: String, path2: String, outputPath: String) {
        val tscResult1 = loadTSCFile(path1)
        val tscResult2 = loadTSCFile(path2)

        val tscDifference = buildPercentageDifferenceBetweenTwoTSCOccurenceResults(
            tscResult1,
            tscResult2
        ).filter { it -> it.value >= 0 }.toList().sortedBy { it.second }.take(10).toMap()

        println("-----------------------------------------------")
        println("Bottom 10 instsance occurences:")

        for (tsc in tscDifference.keys) {
            println(tscDifference[tsc].toString() + "\n" + tsc.toString(1) + "\n")
        }

        val file = File(outputPath)

        // CSV header
        file.printWriter().use { out ->
            out.println(
                "instanceIndex,percentage"
            )

            for ((index, tsc) in tscDifference.keys.withIndex()) {
                out.println(
                    listOf(
                        index+1,
                        tscDifference[tsc]
                    ).joinToString(",")
                )
            }
        }
        println("✅ CSV written to: ${file.absolutePath}")
    }

    fun plotInstanceOccurencePercentageDifferenceNewOnes(path1: String, path2: String) {
        val tscResult1 = loadTSCFile(path1)
        val tscResult2 = loadTSCFile(path2)

        val tscDifference =
            buildPercentageDifferenceBetweenTwoTSCOccurenceResults(tscResult1, tscResult2).filter { it -> it.value < 0 }
                .toList().sortedBy { it.second }.toMap()

        println("-----------------------------------------------")
        println("New instsance occurences:")

        for (tsc in tscDifference.keys) {
            println(tscDifference[tsc].toString() + "\n" + tsc.toString(1) + "\n")
        }

        val plotData = mutableMapOf(
            "instance index" to ((1..tscDifference.size).map { it -> it.toString() }),
            "instance count" to tscDifference.values.map { it -> -it })
        val plot = letsPlot(plotData) {
            x = "instance index"
            y = "instance count"
        }
        plotDataAsBarChart(
            plot = plot,
            folder = "difference_plots",
            fileName = "instanceOccurencePercentageDifferenceNewOnes"
        )
    }

    fun csvInstanceOccurencePercentageDifferenceNewOnes(path1: String, path2: String, outputPath: String) {
        val tscResult1 = loadTSCFile(path1)
        val tscResult2 = loadTSCFile(path2)

        val tscDifference =
            buildPercentageDifferenceBetweenTwoTSCOccurenceResults(tscResult1, tscResult2).filter { it -> it.value < 0 }
                .toList().sortedBy { it.second }.toMap()

        println("-----------------------------------------------")
        println("New instsance occurences:")

        for (tsc in tscDifference.keys) {
            println(tscDifference[tsc].toString() + "\n" + tsc.toString(1) + "\n")
        }

        val negated = tscDifference.mapValues { (_, value) -> -value }

        val file = File(outputPath)

        // CSV header
        file.printWriter().use { out ->
            out.println(
                "instanceIndex,percentage"
            )

            for ((index, tsc) in tscDifference.keys.withIndex()) {
                out.println(
                    listOf(
                        index+1,
                        negated[tsc]
                    ).joinToString(",")
                )
            }
        }
        println("✅ CSV written to: ${file.absolutePath}")
    }

    fun plotFailedMonitorPercentageDifference(path1: String, path2: String) {
        val result1 = loadMonitorFile(path1)
        val result2 = loadMonitorFile(path2)

        val countedResult1 = countMonitors(result1)
        val countedResult2 = countMonitors(result2)

        val percentageDifference =
            buildPercentageDifferenceBetweenTwoFailedMonitorsResults(countedResult1, countedResult2).toList()
                .sortedByDescending { it.second }.toMap()

        println("-------------")
        println("monitor differences:")
        for (key in percentageDifference.keys) {
            println(key + " " + percentageDifference[key])
        }

        val plotData = mutableMapOf("monitor labels" to percentageDifference.keys.map { s ->
            s.split(Regex("\\s+")) // Trenne an Leerzeichen
                .joinToString(" ") { word ->
                    if (word.all { it.isDigit() }) {
                        word                     // Wenn Wort nur aus Zahlen besteht → unverändert
                    } else {
                        word.firstOrNull()?.toString() ?: "" // Sonst: nur erster Buchstabe
                    }
                }
        }, "relative difference" to percentageDifference.values)
        val plot = letsPlot(plotData) {
            x = "monitor labels"
            y = "relative difference"
        }
        plotDataAsBarChartWithOneLine(
            plot = plot,
            folder = "difference_plots",
            fileName = "monitorPercentageDifference"
        )
    }

    fun csvFailedMonitorPercentageDifference(path1: String, path2: String, outputPath: String) {
        val result1 = loadMonitorFile(path1)
        val result2 = loadMonitorFile(path2)

        val countedResult1 = countMonitors(result1)
        val countedResult2 = countMonitors(result2)

        val percentageDifference =
            buildPercentageDifferenceBetweenTwoFailedMonitorsResults(countedResult1, countedResult2).toList()
                .sortedByDescending { it.second }.toMap()

        println("-------------")
        println("monitor differences:")
        for (key in percentageDifference.keys) {
            println(key + " " + percentageDifference[key])
        }

        percentageDifference.keys.map { s ->
            s.split(Regex("\\s+")) // Trenne an Leerzeichen
                .joinToString(" ") { word ->
                    if (word.all { it.isDigit() }) {
                        word                     // Wenn Wort nur aus Zahlen besteht → unverändert
                    } else {
                        word.firstOrNull()?.toString() ?: "" // Sonst: nur erster Buchstabe
                    }
                }
        }

        val shortened = percentageDifference.mapKeys { (key, _) ->  key.split(Regex("\\s+")) // Trenne an Leerzeichen
            .joinToString(" ") { word ->
                if (word.all { it.isDigit() }) {
                    word                     // Wenn Wort nur aus Zahlen besteht → unverändert
                } else {
                    word.firstOrNull()?.toString() ?: "" // Sonst: nur erster Buchstabe
                }
            }
        }

        val file = File(outputPath)

        // CSV header
        file.printWriter().use { out ->
            out.println(
                "instanceIndex,percentage"
            )

            for (monitor in shortened.keys) {
                out.println(
                    listOf(
                        monitor,
                        shortened[monitor]
                    ).joinToString(",")
                )
            }
        }
        println("✅ CSV written to: ${file.absolutePath}")
    }

    fun csvFailedMonitorTotal(path1: String, outputPath: String) {
        val result1 = loadMonitorFile(path1)

        val countedResult1 = countMonitors(result1).toList()
            .sortedByDescending { it.second }.toMap()

        println("-------------")
        println("monitor differences:")
        for (key in countedResult1.keys) {
            println(key + " " + countedResult1[key])
        }

        val shortened = countedResult1.mapKeys { (key, _) ->  key.split(Regex("\\s+")) // Trenne an Leerzeichen
            .joinToString(" ") { word ->
                if (word.all { it.isDigit() }) {
                    word                     // Wenn Wort nur aus Zahlen besteht → unverändert
                } else {
                    word.firstOrNull()?.toString() ?: "" // Sonst: nur erster Buchstabe
                }
            }
        }

        val file = File(outputPath)

        // CSV header
        file.printWriter().use { out ->
            out.println(
                "instanceIndex,percentage"
            )

            for (monitor in shortened.keys) {
                out.println(
                    listOf(
                        monitor,
                        shortened[monitor]
                    ).joinToString(",")
                )
            }
        }
        println("✅ CSV written to: ${file.absolutePath}")
    }

    fun plotUniqueMonitorOccurenceProgression(path1: String, path2: String) {
        val result1 = loadMonitorFile(path1)
        val result2 = loadMonitorFile(path2)

    }


}

fun main(args: Array<String>) {
    val pathMonitorFile1 = "serialized-results/100v50w_with_walkers_no_manipulation/failed-monitors/full TSC.json"
    val pathMonitorFile2 = "serialized-results/100v50w_with_walkers_ignore_lights100/failed-monitors/full TSC.json"
    val pathTSCFile1 = "serialized-results/100v50w_with_walkers_no_manipulation/valid-tsc-instances-per-tsc/layer 1+2+4.json"
    val pathTSCFile2 = "serialized-results/100v50w_with_walkers_ignore_lights100/valid-tsc-instances-per-tsc/layer 1+2+4.json"
    val comparisonOfResults = ComparisonOfResults()

    //comparisonOfResults.plotFailedMonitorPercentageDifference(pathMonitorFile1, pathMonitorFile2)

    /*val result1 = comparisonOfResults.loadMonitorFile(pathMonitorFile1)
    val result2 = comparisonOfResults.loadMonitorFile(pathMonitorFile2)
    val countedResult1 = comparisonOfResults.countMonitors(result1)
    val countedResult2 = comparisonOfResults.countMonitors(result2)
    //val difference = comparisonOfResults.buildDifferenceBetweenTwoFailedMonitorsResults(countedResult1, countedResult2)
    val percentageDifference = comparisonOfResults.buildPercentageDifferenceBetweenTwoFailedMonitorsResults(countedResult1, countedResult2)

    val plotData = mutableMapOf("monitor labels" to percentageDifference.keys.map { s -> s.split(Regex("\\s+")) // Trenne an Leerzeichen
        .joinToString(" ") { word ->
            if (word.all { it.isDigit() }) {
                word                     // Wenn Wort nur aus Zahlen besteht → unverändert
            } else {
                word.firstOrNull()?.toString() ?: "" // Sonst: nur erster Buchstabe
            }
        } }, "percentage difference" to percentageDifference.values)
    val plot = letsPlot(plotData) {
        x = "monitor labels"
        y = "percentage difference"
    }
    plotDataAsBarChart(plot = plot, folder = "difference_plots", fileName = "testfile")*/

    /*val tscResult1 = comparisonOfResults.loadTSCFile("serialized-results/100v50w_with_walkers_no_manipulation/valid-tsc-instances-per-tsc/layer 1+2+4.json")
    val tscResult2 = comparisonOfResults.loadTSCFile("serialized-results/100v50w_with_walkers_ignore_lights100/valid-tsc-instances-per-tsc/layer 1+2+4.json")
    val tscDifference = comparisonOfResults.buildPercentageDifferenceBetweenTwoTSCOccurenceResults(tscResult1, tscResult2).filter { it -> it.value > 0 }.toList().sortedByDescending { it.second }.take(10).toMap()

    val plotData = mutableMapOf("tsc labels" to tscDifference.keys.map { k -> k.toString() }, "percentage difference" to tscDifference.values)
    val plot = letsPlot(plotData) {
        x = "tsc labels"
        y = "percentage difference"
    } + scaleYContinuous(breaks=listOf(0.0,1.0,2.0,3.0))
    plotDataAsBarChart(plot = plot, folder = "difference_plots", fileName = "testfile")*/

    //comparisonOfResults.plotInstanceOccurenceDifference(pathMonitorFile1, pathMonitorFile2)

    comparisonOfResults.csvFailedMonitorTotal(
        pathMonitorFile1,
        "serialized-results/no_manipulation_monitor_count.csv"
    )

    comparisonOfResults.csvInstanceOccurencePercentageDifferenceTop10(
        pathTSCFile1,
        pathTSCFile2,
        "serialized-results/ignore_lights_instance_occurence_percentage_difference_top10.csv"
    )

    comparisonOfResults.csvInstanceOccurencePercentageDifferenceBottom10(
        pathTSCFile1,
        pathTSCFile2,
        "serialized-results/ignore_lights_instance_occurence_percentage_difference_bot10.csv"
    )

    comparisonOfResults.csvInstanceOccurencePercentageDifferenceNewOnes(
        pathTSCFile1,
        pathTSCFile2,
        "serialized-results/ignore_lights_instance_occurence_percentage_difference_new_ones.csv"
    )

    comparisonOfResults.csvFailedMonitorPercentageDifference(
        pathMonitorFile1,
        pathMonitorFile2,
        "serialized-results/monitor_difference.csv"
    )

    /*comparisonOfResults.plotInstanceOccurencePercentageDifferenceTop10(
        "serialized-results/100v50w_with_walkers_no_manipulation/valid-tsc-instances-per-tsc/layer 1+2+4.json",
        "serialized-results/100v50w_with_walkers_ignore_lights100/valid-tsc-instances-per-tsc/layer 1+2+4.json"
    )
    comparisonOfResults.plotInstanceOccurencePercentageDifferenceBottom10(
        "serialized-results/100v50w_with_walkers_no_manipulation/valid-tsc-instances-per-tsc/layer 1+2+4.json",
        "serialized-results/100v50w_with_walkers_ignore_lights100/valid-tsc-instances-per-tsc/layer 1+2+4.json"
    )
    comparisonOfResults.plotInstanceOccurencePercentageDifferenceNewOnes(
        "serialized-results/100v50w_with_walkers_no_manipulation/valid-tsc-instances-per-tsc/layer 1+2+4.json",
        "serialized-results/100v50w_with_walkers_ignore_lights100/valid-tsc-instances-per-tsc/layer 1+2+4.json"
    )*/

    print("yay")
}