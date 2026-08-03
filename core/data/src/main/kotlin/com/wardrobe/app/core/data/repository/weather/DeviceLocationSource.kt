package com.wardrobe.app.core.data.repository.weather

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import com.wardrobe.app.core.domain.repository.Location
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The device's last-known coarse location (phase-1-architecture.md Section
 * 18/24) — plain `LocationManager`, not Google Play Services' Fused Location
 * client, since a one-shot "whatever the OS already has cached" read doesn't
 * need continuous updates or Play Services' own dependency footprint.
 * Returns `null` (never throws) when the `ACCESS_COARSE_LOCATION` permission
 * hasn't been granted or the OS has no last-known fix yet — the caller
 * ([WeatherLocationResolver]) falls back to a manually-set location.
 */
@Singleton
class DeviceLocationSource
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        @SuppressLint("MissingPermission")
        fun lastKnownLocation(): Location? {
            // The permission check immediately above is the guard Lint's
            // MissingPermission detector normally asks for; it's suppressed
            // here only because the detector doesn't reliably trace across
            // the `mapNotNull` lambda below, not because the check is
            // missing at runtime.
            val permissionGranted =
                context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED
            if (!permissionGranted) return null
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            return locationManager
                ?.allProviders
                ?.mapNotNull { provider -> runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull() }
                ?.maxByOrNull { it.time }
                ?.let { Location(it.latitude, it.longitude) }
        }
    }
