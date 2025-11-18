package tools.aqua.stars.carla.experiments

/*
 * Copyright 2025 The STARS Carla Experiments Authors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.File
import tools.aqua.stars.core.metric.serialization.SerializableTSCOccurrenceResult
import tools.aqua.stars.core.metric.serialization.tsc.SerializableTSCNode

fun main() {
    val validInstancesResultList = mutableListOf<SerializableTSCOccurrenceResult>()

    validInstancesResultList.add(loadSerializableResult(
        "serialized-results/100v50w_with_walkers_no_manipulation/valid-tsc-instances-per-tsc/layer 1+2+4.json"))
    validInstancesResultList.add(loadSerializableResult(
        "serialized-results/100v50w_with_walkers_speed-100_50p/valid-tsc-instances-per-tsc/layer 1+2+4.json"))
    validInstancesResultList.add(loadSerializableResult(
        "serialized-results/100v50w_with_walkers_distance_to_leading_vehicle0/valid-tsc-instances-per-tsc/layer 1+2+4.json"))
    validInstancesResultList.add(loadSerializableResult(
        "serialized-results/100v50w_with_walkers_ignore_lights100/valid-tsc-instances-per-tsc/layer 1+2+4.json"))
    validInstancesResultList.add(loadSerializableResult(
        "serialized-results/100v50w_with_walkers_ignore_side0505/valid-tsc-instances-per-tsc/layer 1+2+4.json"))
    validInstancesResultList.add(loadSerializableResult(
        "serialized-results/100v50w_with_walkers_ignore_signs100/valid-tsc-instances-per-tsc/layer 1+2+4.json"))
    validInstancesResultList.add(loadSerializableResult(
        "serialized-results/100v50w_with_walkers_ignore_vehicles100/valid-tsc-instances-per-tsc/layer 1+2+4.json"))
    validInstancesResultList.add(loadSerializableResult(
        "serialized-results/100v50w_with_walkers_ignore_walkers100/valid-tsc-instances-per-tsc/layer 1+2+4.json"))
    validInstancesResultList.add(loadSerializableResult(
        "serialized-results/100v50w_with_walkers_keep_right_rule100/valid-tsc-instances-per-tsc/layer 1+2+4.json"))
    validInstancesResultList.add(loadSerializableResult(
        "serialized-results/100v50w_with_walkers_random_left_change100/valid-tsc-instances-per-tsc/layer 1+2+4.json"))
    validInstancesResultList.add(loadSerializableResult(
        "serialized-results/100v50w_with_walkers_random_right_change100/valid-tsc-instances-per-tsc/layer 1+2+4.json"))
    validInstancesResultList.add(loadSerializableResult(
        "serialized-results/100v50w_with_walkers_bad_drivers/valid-tsc-instances-per-tsc/layer 1+2+4.json"))
    validInstancesResultList.add(loadSerializableResult(
        "serialized-results/100v50w_with_walkers_overtaking/valid-tsc-instances-per-tsc/layer 1+2+4.json"))

    val listShit : MutableList<TSCMultipleInstancesWithCount> = mutableListOf()
    val listShit2 : MutableList<TSCMultipleInstancesWithCountNoSegment> = mutableListOf()

    var indexOuter = 0
    for (validInstancesResult in validInstancesResultList) {
        val validInstances =
            validInstancesResult.value.flatMap { result ->
                result.segmentIdentifiers.map { identifier -> identifier to result.tscInstance }
            }

        val parsed =
            validInstances.map { (key, value) ->
                // Extract the first float inside "Segment[(...s.."
                val segment = key.substringAfter("Segment[(").substringBefore("s").toFloat()

                // Extract the "from ..." part and get map name and seed
                val fromFull = key.substringAfter("from ").substringBefore(" with")
                val from = fromFull.substringAfter("Maps_").substringBefore("_seed")
                val seed = fromFull.substringAfter("_seed").toIntOrNull() ?: 0

                // Extract entity
                val entity = key.substringAfter("entity ").substringBefore(" ")

                SegmentIdentifier(segment, from, seed, entity, key) to value
            }

        val sorted = parsed.sortedBy { it.first }

        val seenNodes = mutableSetOf<SerializableTSCNode>()

        val sortedInstancesWithUniqueCounts =
            sorted.mapIndexed { index, (segmentIdentifier, tscInstance) ->
                seenNodes.add(tscInstance)
                if (index < listShit2.size) {
                    listShit2[index].tscInstances[indexOuter] = (tscInstance)
                    listShit2[index].tscInstanceCounts[indexOuter] = (index + 1)
                    listShit2[index].uniqueTSCInstancesCounts[indexOuter] = seenNodes.size
                    listShit2[index].possibleTSCInstances[indexOuter] = 360
                } else {
                    val newElement : TSCMultipleInstancesWithCountNoSegment = TSCMultipleInstancesWithCountNoSegment(
                        arrayOfNulls(validInstancesResultList.size),
                        arrayOfNulls(validInstancesResultList.size),
                        arrayOfNulls(validInstancesResultList.size),
                        arrayOfNulls(validInstancesResultList.size)
                    )
                    newElement.tscInstances[indexOuter] = tscInstance
                    newElement.tscInstanceCounts[indexOuter] = (index + 1)
                    newElement.uniqueTSCInstancesCounts[indexOuter] = seenNodes.size
                    newElement.possibleTSCInstances[indexOuter] = 360
                    listShit2.add(
                        newElement
                    )

                }
            }
        indexOuter++


    }

    for ((index, tscMultipleInstanceWithCountNoSegment) in listShit2.withIndex()) {
        listShit.add(
            TSCMultipleInstancesWithCount(
            index + 1,
            tscMultipleInstanceWithCountNoSegment.tscInstances,
            tscMultipleInstanceWithCountNoSegment.tscInstanceCounts,
            tscMultipleInstanceWithCountNoSegment.uniqueTSCInstancesCounts,
            tscMultipleInstanceWithCountNoSegment.possibleTSCInstances,
        )
        )
    }

    writeMultipleInstancesResultsToCsv(slice(listShit), "serialized-results/combined_coverages.csv")

    val s = ""
}

fun slice(list: MutableList<TSCMultipleInstancesWithCount>) : List<TSCMultipleInstancesWithCount> {
    return list.filterIndexed { index, _ -> index == 0 || index == list.size - 1 || index % 100 == 0}
}

fun writeMultipleInstancesResultsToCsv(results: List<TSCMultipleInstancesWithCount>, outputPath: String) {
    val file = File(outputPath)

    // CSV header
    file.printWriter().use { out ->
        out.println(
            "segmentIndex,100v50w_with_walkers_no_manipulation,100v50w_with_walkers_speed-100_50p,100v50w_with_walkers_distance_to_leading_vehicle0,100v50w_with_walkers_ignore_lights100,100v50w_with_walkers_ignore_side0505,100v50w_with_walkers_ignore_signs100,100v50w_with_walkers_ignore_vehicles100,100v50w_with_walkers_ignore_walkers100,100v50w_with_walkers_keep_right_rule100,100v50w_with_walkers_random_left_change100,100v50w_with_walkers_random_right_change100,100v50w_with_walkers_bad_drivers,100v50w_with_walkers_overtaking")

        // Each row
        results.forEach { result ->
            out.println(
                listOf(
                    result.segmentIndex,
                    ((result.uniqueTSCInstancesCounts[0]?.toFloat() ?: -1f) / (result.possibleTSCInstances[0]?: 1) * 100)
                        .toInt(),
                    ((result.uniqueTSCInstancesCounts[1]?.toFloat() ?: -1f) / (result.possibleTSCInstances[1]?: 1) * 100)
                        .toInt(),
                    ((result.uniqueTSCInstancesCounts[2]?.toFloat() ?: -1f) / (result.possibleTSCInstances[2]?: 1) * 100)
                        .toInt(),
                    ((result.uniqueTSCInstancesCounts[3]?.toFloat() ?: -1f) / (result.possibleTSCInstances[3]?: 1) * 100)
                        .toInt(),
                    ((result.uniqueTSCInstancesCounts[4]?.toFloat() ?: -1f) / (result.possibleTSCInstances[4]?: 1) * 100)
                        .toInt(),
                    ((result.uniqueTSCInstancesCounts[5]?.toFloat() ?: -1f) / (result.possibleTSCInstances[5]?: 1) * 100)
                        .toInt(),
                    ((result.uniqueTSCInstancesCounts[6]?.toFloat() ?: -1f) / (result.possibleTSCInstances[6]?: 1) * 100)
                        .toInt(),
                    ((result.uniqueTSCInstancesCounts[7]?.toFloat() ?: -1f) / (result.possibleTSCInstances[7]?: 1) * 100)
                        .toInt(),
                    ((result.uniqueTSCInstancesCounts[8]?.toFloat() ?: -1f) / (result.possibleTSCInstances[8]?: 1) * 100)
                        .toInt(),
                    ((result.uniqueTSCInstancesCounts[9]?.toFloat() ?: -1f) / (result.possibleTSCInstances[9]?: 1) * 100)
                        .toInt(),
                    ((result.uniqueTSCInstancesCounts[10]?.toFloat() ?: -1f) / (result.possibleTSCInstances[10]?: 1) * 100)
                        .toInt(),
                    ((result.uniqueTSCInstancesCounts[11]?.toFloat() ?: -1f) / (result.possibleTSCInstances[11]?: 1) * 100)
                        .toInt(),
                    ((result.uniqueTSCInstancesCounts[12]?.toFloat() ?: -1f) / (result.possibleTSCInstances[12]?: 1) * 100)
                        .toInt(),

                )
                    .joinToString(","))
        }
    }

    println("✅ CSV written to: ${file.absolutePath}")
}


data class TSCInstancesWithCount(
    val segmentIndex: Int,
    val tscInstance: SerializableTSCNode,
    val tscInstanceCount: Int,
    val uniqueTSCInstancesCount: Int,
    val possibleTSCInstances: Int
)

data class TSCMultipleInstancesWithCount(
    val segmentIndex: Int,
    val tscInstances: Array<SerializableTSCNode?>,
    val tscInstanceCounts: Array<Int?>,
    val uniqueTSCInstancesCounts: Array<Int?>,
    val possibleTSCInstances: Array<Int?>
)

data class TSCMultipleInstancesWithCountNoSegment(
    val tscInstances: Array<SerializableTSCNode?>,
    val tscInstanceCounts: Array<Int?>,
    val uniqueTSCInstancesCounts: Array<Int?>,
    val possibleTSCInstances: Array<Int?>
)
