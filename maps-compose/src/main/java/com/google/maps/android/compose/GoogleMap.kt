// Copyright 2021 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.maps.android.compose

import android.content.ComponentCallbacks
import android.content.res.Configuration
import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.LocationSource
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.IndoorBuilding
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.PointOfInterest
import com.google.maps.android.ktx.awaitMap
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

/**
 * A compose container for a [MapView].
 *
 * @param modifier - Modifier to be applied to the GoogleMap
 * @param googleMapOptionsFactory - The block for creating the [GoogleMapOptions] provided when the
 * map is created
 * @param mapPropertiesState - the [MapPropertiesState] to be used to set properties of the map
 * @param cameraPositionState - the [CameraPositionState] to be used to control or observe the map's
 * camera state
 * @param locationSource - the [LocationSource] to be used to provide location data
 * @param onIndoorBuildingFocused - lambda to be invoked when an indoor building is focused
 * @param onIndoorLevelActivated - lambda to be invoked when an level is activated in an indoor
 * building
 * @param onMapClick - lambda invoked when the map is clicked
 * @param onMapLoaded - lambda invoked when the map is finished loading
 * @param onMyLocationButtonClick - lambda invoked when the my location button is clicked
 * @param onMyLocationClick - lambda invoked when the my location dot is clicked
 * @param onPOIClick - lambda invoked when a POI is clicked
 * @param content - the content of the map
 */
@Composable
fun GoogleMap(
    modifier: Modifier = Modifier,
    googleMapOptionsFactory: () -> GoogleMapOptions = { GoogleMapOptions() },
    uiSettingsState: UISettingsState = rememberUISettingsState(),
    mapPropertiesState: MapPropertiesState = rememberMapPropertiesState(),
    cameraPositionState: CameraPositionState = rememberCameraPositionState(),
    locationSource: LocationSource? = null,
    onIndoorBuildingFocused: () -> Unit = {},
    onIndoorLevelActivated: (IndoorBuilding) -> Unit = {},
    onMapClick: (LatLng) -> Unit = {},
    onMapLongClick: (LatLng) -> Unit = {},
    onMapLoaded: () -> Unit = {},
    onMyLocationButtonClick: () -> Boolean = { false },
    onMyLocationClick: () -> Unit = {},
    onPOIClick: (PointOfInterest) -> Unit = {},
    content: (@Composable () -> Unit)? = null,
) {
    val context = LocalContext.current
    val mapView = remember {
        MapView(context, googleMapOptionsFactory())
    }

    AndroidView(
        modifier = modifier,
        factory = {
            mapView
        }
    )

    MapLifecycle(mapView)

    val currentOnMapClick by rememberUpdatedState(onMapClick)
    val currentOnMapLongClick by rememberUpdatedState(onMapLongClick)
    val currentOnMapLoaded by rememberUpdatedState(onMapLoaded)
    val currentOnMyLocationButtonClick by rememberUpdatedState(onMyLocationButtonClick)
    val currentOnMyLocationClick by rememberUpdatedState(onMyLocationClick)
    val currentOnPOIClick by rememberUpdatedState(onPOIClick)
    val currentOnIndoorBuildingFocused by rememberUpdatedState(onIndoorBuildingFocused)
    val currentOnIndoorLevelActivated by rememberUpdatedState(onIndoorLevelActivated)
    val currentUiSettingsState by rememberUpdatedState(uiSettingsState)
    val currentMapPropertiesState by rememberUpdatedState(mapPropertiesState)
    val currentLocationSource by rememberUpdatedState(locationSource)
    val currentCameraPositionState by rememberUpdatedState(cameraPositionState)
    LaunchedEffect(Unit) {
        val map = mapView.awaitMap()

        // Event listeners
        launch {
            snapshotFlow {
                map.setClickListeners(
                    onIndoorBuildingFocused = currentOnIndoorBuildingFocused,
                    onIndoorLevelActivated = currentOnIndoorLevelActivated,
                    onMapClick = currentOnMapClick,
                    onMapLongClick = currentOnMapLongClick,
                    onMapLoaded = currentOnMapLoaded,
                    onMyLocationButtonClick = currentOnMyLocationButtonClick,
                    onMyLocationClick = currentOnMyLocationClick,
                    onPOIClick = currentOnPOIClick
                )
            }.collect()
        }

        // UISettings
        map.uiSettings.applyState(currentUiSettingsState)
        launch {
            snapshotFlow {
                map.uiSettings.applyState(currentUiSettingsState)
            }.collect()
        }

        // Map Properties
        map.applyMapPropertiesState(currentMapPropertiesState, currentLocationSource)
        launch {
            snapshotFlow {
                map.applyMapPropertiesState(currentMapPropertiesState, currentLocationSource)
            }.collect()
        }

        // FIXME(chrisarriola): Rework camera position state API
        map.applyCameraPositionState(currentCameraPositionState)
        launch {
            mutableSnapshotFlow {
                map.applyCameraPositionState(currentCameraPositionState)
            }.collect()
        }
    }

    // Child APIs
    if (content != null) {
        val parentComposition = rememberCompositionContext()
        val currentContent by rememberUpdatedState(content)
        LaunchedEffect(Unit) {
            val map = mapView.awaitMap()
            disposingComposition {
                map.newComposition(parentComposition) {
                    currentContent.invoke()
                }
            }
        }
    }
}

private fun GoogleMap.setClickListeners(
    onIndoorBuildingFocused: () -> Unit = {},
    onIndoorLevelActivated: (IndoorBuilding) -> Unit = {},
    onMapClick: (LatLng) -> Unit = {},
    onMapLongClick: (LatLng) -> Unit = {},
    onMapLoaded: () -> Unit = {},
    onMyLocationButtonClick: () -> Boolean = { false },
    onMyLocationClick: () -> Unit = {},
    onPOIClick: (PointOfInterest) -> Unit = {},
) {
    setOnMapClickListener { onMapClick(it) }
    setOnMapLongClickListener { onMapLongClick(it) }
    setOnMapLoadedCallback { onMapLoaded() }
    setOnMyLocationButtonClickListener { onMyLocationButtonClick() }
    setOnMyLocationClickListener { onMyLocationClick() }
    setOnPoiClickListener { onPOIClick(it) }
    setOnIndoorStateChangeListener(object : GoogleMap.OnIndoorStateChangeListener {
        override fun onIndoorBuildingFocused() {
            onIndoorBuildingFocused()
        }

        override fun onIndoorLevelActivated(building: IndoorBuilding) {
            onIndoorLevelActivated(building)
        }
    })
}

private suspend inline fun disposingComposition(factory: () -> Composition) {
    val composition = factory()
    try {
        awaitCancellation()
    } finally {
        composition.dispose()
    }
}

private fun GoogleMap.newComposition(
    parent: CompositionContext,
    content: @Composable () -> Unit
): Composition = Composition(MapApplier(this), parent).apply {
    setContent(content)
}

/**
 * Registers lifecycle observers to the local [MapView].
 */
@Composable
private fun MapLifecycle(mapView: MapView) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(context, lifecycle, mapView) {
        val mapLifecycleObserver = mapView.lifecycleObserver()
        val callbacks = mapView.componentCallbacks()

        lifecycle.addObserver(mapLifecycleObserver)
        context.registerComponentCallbacks(callbacks)

        onDispose {
            lifecycle.removeObserver(mapLifecycleObserver)
            context.unregisterComponentCallbacks(callbacks)
        }
    }
}

private fun MapView.lifecycleObserver(): LifecycleEventObserver =
    LifecycleEventObserver { _, event ->
        when (event) {
            Lifecycle.Event.ON_CREATE -> this.onCreate(Bundle())
            Lifecycle.Event.ON_START -> this.onStart()
            Lifecycle.Event.ON_RESUME -> this.onResume()
            Lifecycle.Event.ON_PAUSE -> this.onPause()
            Lifecycle.Event.ON_STOP -> this.onStop()
            Lifecycle.Event.ON_DESTROY -> this.onDestroy()
            else -> throw IllegalStateException()
        }
    }

private fun MapView.componentCallbacks(): ComponentCallbacks =
    object: ComponentCallbacks {
        override fun onConfigurationChanged(config: Configuration) { }

        override fun onLowMemory() {
            this@componentCallbacks.onLowMemory()
        }
    }

/**
 * Same implementation as [snapshotFlow] with the exception of mutable snapshots taken.
 *
 * @see androidx.compose.runtime.snapshotFlow
 */
internal fun <T> mutableSnapshotFlow(
    block: () -> T
): Flow<T> = flow {
    // Objects read the last time block was run
    val readSet = mutableSetOf<Any>()
    val readObserver: (Any) -> Unit = { readSet.add(it) }

    // This channel may not block or lose data on a trySend call.
    val appliedChanges = Channel<Set<Any>>(Channel.UNLIMITED)

    // Register the apply observer before running for the first time
    // so that we don't miss updates.
    val unregisterApplyObserver = Snapshot.registerApplyObserver { changed, _ ->
        appliedChanges.trySend(changed)
    }

    try {
        var lastValue = Snapshot.withMutableSnapshot(block)
            Snapshot.takeMutableSnapshot(readObserver).run {
            try {
                enter(block).also { apply().check() }
            } finally {
                dispose()
            }
        }
        emit(lastValue)

        while (true) {
            var found = false
            var changedObjects = appliedChanges.receive()

            // Poll for any other changes before running block to minimize the number of
            // additional times it runs for the same data
            while (true) {
                // Assumption: readSet will typically be smaller than changed
                found = found || readSet.intersects(changedObjects)
                changedObjects = appliedChanges.tryReceive().getOrNull() ?: break
            }

            if (found) {
                readSet.clear()
                val newValue = Snapshot.takeMutableSnapshot(readObserver).run {
                    try {
                        enter(block).also { apply().check() }
                    } finally {
                        dispose()
                    }
                }

                if (newValue != lastValue) {
                    lastValue = newValue
                    emit(newValue)
                }
            }
        }
    } finally {
        unregisterApplyObserver.dispose()
    }

}

private fun <T> Set<T>.intersects(other: Set<T>): Boolean =
    if (size < other.size) any { it in other } else other.any { it in this }
