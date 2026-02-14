package net.wigle.wigleandroid;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import org.maplibre.android.MapLibre;
import org.maplibre.android.camera.CameraPosition;
import org.maplibre.android.camera.CameraUpdateFactory;
import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.location.LocationComponent;
import org.maplibre.android.location.LocationComponentActivationOptions;
import org.maplibre.android.location.modes.CameraMode;
import org.maplibre.android.location.modes.RenderMode;
import org.maplibre.android.maps.MapView;
import org.maplibre.android.maps.MapLibreMap;
import org.maplibre.android.maps.OnMapReadyCallback;
import org.maplibre.android.maps.Style;

public class VectorMapActivity extends AppCompatActivity {
    private MapView mapView;
    private MapLibreMap mapLibreMap;
    private Style loadedStyle;
    private FloatingActionButton fabLocate;
    private static final int REQUEST_LOCATION_PERMISSION = 1001;
    private boolean trackingEnabled = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize MapLibre
        MapLibre.getInstance(this);

        setContentView(R.layout.activity_vector_map);

        mapView = findViewById(R.id.mapView);
        fabLocate = findViewById(R.id.btn_locate);
        fabLocate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleTracking();
            }
        });
        mapView.getMapAsync(new OnMapReadyCallback() {
            @Override
            public void onMapReady(@NonNull MapLibreMap mapLibreMap) {
                VectorMapActivity.this.mapLibreMap = mapLibreMap;
                try {
                    final String styleUrl = "https://tiles.wifidb.net/styles/WDB_OSM/style.json";
                    mapLibreMap.setStyle(styleUrl, new Style.OnStyleLoaded() {
                        @Override
                        public void onStyleLoaded(@NonNull Style style) {
                            loadedStyle = style;
                            // default camera until we get location
                            mapLibreMap.setCameraPosition(new CameraPosition.Builder()
                                    .target(new LatLng(37.0, -122.0))
                                    .zoom(10.0)
                                    .build());
                            attemptEnableLocationComponent();
                        }
                    });
                } catch (Exception ex) {
                    // log but avoid crashing during style load
                }
            }
        });
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void attemptEnableLocationComponent() {
        if (mapLibreMap == null || loadedStyle == null) return;

        if (!hasLocationPermission()) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_LOCATION_PERMISSION);
            return;
        }

        try {
            LocationComponent locationComponent = mapLibreMap.getLocationComponent();
            LocationComponentActivationOptions options = LocationComponentActivationOptions.builder(this, loadedStyle)
                    .useDefaultLocationEngine(true)
                    .build();
            locationComponent.activateLocationComponent(options);
            locationComponent.setLocationComponentEnabled(true);
            locationComponent.setCameraMode(CameraMode.TRACKING);
            locationComponent.setRenderMode(RenderMode.COMPASS);

            // center on last known location if available
            Location last = locationComponent.getLastKnownLocation();
            if (last != null) {
                mapLibreMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(last.getLatitude(), last.getLongitude()), 15.0));
            }
            trackingEnabled = true;
            updateFabIcon();
        } catch (Exception ex) {
            Toast.makeText(this, "Unable to enable location component", Toast.LENGTH_SHORT).show();
        }
    }

    private void toggleTracking() {
        if (mapLibreMap == null) return;
        LocationComponent lc = mapLibreMap.getLocationComponent();
        if (lc == null || !lc.isLocationComponentEnabled()) {
            attemptEnableLocationComponent();
            return;
        }

        if (trackingEnabled) {
            lc.setCameraMode(CameraMode.NONE);
            trackingEnabled = false;
        } else {
            lc.setCameraMode(CameraMode.TRACKING);
            Location last = lc.getLastKnownLocation();
            if (last != null) {
                mapLibreMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(last.getLatitude(), last.getLongitude()), 16.0));
            }
            trackingEnabled = true;
        }
        updateFabIcon();
    }

    private void updateFabIcon() {
        if (fabLocate == null) return;
        if (trackingEnabled) {
            fabLocate.setImageResource(android.R.drawable.ic_menu_mylocation);
        } else {
            fabLocate.setImageResource(android.R.drawable.ic_menu_mylocation);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (mapView != null) mapView.onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mapView != null) mapView.onResume();
    }

    @Override
    protected void onPause() {
        if (mapView != null) mapView.onPause();
        super.onPause();
    }

    @Override
    protected void onStop() {
        if (mapView != null) mapView.onStop();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        if (mapView != null) mapView.onDestroy();
        super.onDestroy();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        if (mapView != null) mapView.onLowMemory();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mapView != null) mapView.onSaveInstanceState(outState);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_LOCATION_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                attemptEnableLocationComponent();
            } else {
                Toast.makeText(this, "Location permission required to show current location", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
