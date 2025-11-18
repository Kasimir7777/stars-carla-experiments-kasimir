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

package tools.aqua.stars.carla.experiments.validation

import tools.aqua.stars.carla.experiments.*
import tools.aqua.stars.core.validation.manuallyLabelledFile
import tools.aqua.stars.data.av.dataclasses.TickDataUnitSeconds
import tools.aqua.stars.importer.carla.loadSegments

val simulationRuns = ExperimentConfiguration.getSimulationRuns("data_from_kasimir/predicate_test_files/cross_red_light/cross_red_redlight")
val segments = loadSegments(simulationRuns).toList()

val manualTests =
    manuallyLabelledFile(segments) {
      predicate(didCrossRedLight) { interval(TickDataUnitSeconds(0.0), TickDataUnitSeconds(7.0)) }
    }

val collisionTest =
    manuallyLabelledFile(loadSegments(ExperimentConfiguration.getSimulationRuns("data_from_kasimir/collisions/collision/simulation-runs")).toList()) {
        predicate(collision) { interval(TickDataUnitSeconds(0.0), TickDataUnitSeconds(10.0)) }
    }

val noCollisionTest =
    manuallyLabelledFile(loadSegments(ExperimentConfiguration.getSimulationRuns("data_from_kasimir/collisions/no-collision/simulation-runs")).toList()) {
        predicate(noCollisions) { interval(TickDataUnitSeconds(0.0), TickDataUnitSeconds(10.0)) }
    }

val saveDistanceToLeadingVehicleTest =
    manuallyLabelledFile(loadSegments(ExperimentConfiguration.getSimulationRuns("data_from_kasimir/predicate_test_files/distance/save_distance")).toList()) {
        predicate(keepsDistanceToLeadingVehicle) { interval(TickDataUnitSeconds(0.0), TickDataUnitSeconds(100.0)) }
    }

val distanceToLeadingVehicleTooSmallTest =
    manuallyLabelledFile(loadSegments(ExperimentConfiguration.getSimulationRuns("data_from_kasimir/predicate_test_files/distance/close_distance")).toList()) {
        predicate(distanceToLeadingVehicleTooSmall) { interval(TickDataUnitSeconds(0.0), TickDataUnitSeconds(10.0)) }
    }

val obeysKeepRightRuleTest =
    manuallyLabelledFile(loadSegments(ExperimentConfiguration.getSimulationRuns("data_from_kasimir/predicate_test_files/keep_right_rule/obeys_keep_right_rule")).toList()) {
        predicate(obeysKeepRightRule) { interval(TickDataUnitSeconds(0.0), TickDataUnitSeconds(10.0)) }
    }

val breaksKeepRightRuleTest =
    manuallyLabelledFile(loadSegments(ExperimentConfiguration.getSimulationRuns("data_from_kasimir/predicate_test_files/keep_right_rule/breaks_keep_right_rule")).toList()) {
        predicate(breaksKeepRightRule) { interval(TickDataUnitSeconds(0.0), TickDataUnitSeconds(10.0)) }
    }
