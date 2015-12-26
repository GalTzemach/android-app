package com.perrchick.someapplication;

import android.app.FragmentTransaction;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.perrchick.someapplication.uiexercises.SensorsFragment;
import com.perrchick.someapplication.utilities.PerrFuncs;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class SomeActivityWithMap extends AppCompatActivity {
    private static final String TAG = SomeActivityWithMap.class.getSimpleName();


    final String formatForGeocodeFromAddress = "https://maps.googleapis.com/maps/api/geocode/json?address=%s&key=%s";
    final String formatForAddressFromGeocode = "https://maps.googleapis.com/maps/api/geocode/json?latlng=%f,%f&key=%s";
    final String formatForAutocompletePlacesSearch = "https://maps.googleapis.com/maps/api/place/autocomplete/json?input=%s&types=address&language=iw&key=%s";
    final String apiKey = "AIzaSyDC5LC2DDP6Vi11nVw53q7uAyxyVhOfbxw"; // Different from the Maps API key

    private GoogleMap googleMap;
    private TextView lblZoom;
    private EditText txtAddress;
    private SeekBar zoomSlider;
    private Spinner actionsDropdownList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_some_activity_with_map);
        PerrFuncs.hideActionBarOfActivity(this);

        if (isGoogleMapsInstalled()) {
            // Add the Google Maps fragment dynamically
            final FragmentTransaction transaction = getFragmentManager().beginTransaction();
            MapFragment mapFragment = MapFragment.newInstance();
            transaction.add(R.id.mapsPlaceHolder, mapFragment);
            transaction.commit();

            mapFragment.getMapAsync(new OnMapReadyCallback() {
                @Override
                public void onMapReady(GoogleMap googleMap) {
                    setGoogleMap(googleMap);
                }
            });
        } else {
            // Notify the user he should install GoogleMaps (after installing Google Play Services)
            FrameLayout mapsPlaceHolder = (FrameLayout) findViewById(R.id.mapsPlaceHolder);
            TextView errorMessageTextView = new TextView(getApplicationContext());
            errorMessageTextView.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
            errorMessageTextView.setText("No GoogleMaps installed on this device");
            errorMessageTextView.setTextColor(Color.RED);
            mapsPlaceHolder.addView(errorMessageTextView);
        }

        this.actionsDropdownList = (Spinner)findViewById(R.id.spinner_maps_actions);
        final String[] actionValues = new String[]{"Go to Afeka", "Copy current location", "Put marker"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, actionValues);
        actionsDropdownList.setAdapter(adapter);
        actionsDropdownList.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                PerrFuncs.toast(actionValues[position]);
                LatLng cameraTarget = googleMap.getCameraPosition().target;
                String geoLocationString = cameraTarget.latitude + ", " + cameraTarget.longitude;
                switch (position) {
                    case 0: // take camera to Afeka
                        takeCameraToAfeka(null);
                        break;
                    case 1: // Copy current target to clipboard
                    {
                        ClipboardManager clipboardManager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                        ClipData clipData = ClipData.newPlainText("geocode", geoLocationString);
                        clipboardManager.setPrimaryClip(clipData); //text/plain
                    }
                    break;
                    case 2: // Put marker
                    {
                        //Marker marker = Marker
                    }
                    googleMap.addMarker(new MarkerOptions().title("marker (" + SensorsFragment.getCounterValue() + ")").draggable(true).position(cameraTarget).snippet(geoLocationString));
                    break;
                    default:
                        break;
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Do nothing...
            }
        });

        txtAddress = (EditText) findViewById(R.id.txtAddress);
        lblZoom = (TextView) findViewById(R.id.lblZoomLevel);

        zoomSlider = (SeekBar) findViewById(R.id.seekBar);
        zoomSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                //txtAddress.setText(progress + "");
                if (progress > 0) {
                    if (getGoogleMap() != null) {
                        GoogleMap map = getGoogleMap();

                        LatLng current = map.getCameraPosition().target;
                        float zoom = 18f * (float) progress / 100f;
                        map.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(current.latitude, current.longitude), zoom));
                        lblZoom.setText(zoom + "");
                    }
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        findViewById(R.id.btnSearch).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                btnSearchPressed();
            }
        });
    }

    private void btnSearchPressed() {
        final OkHttpClient client = new OkHttpClient();
        client.setReadTimeout(20, TimeUnit.SECONDS);

        String searchAddressUrl = String.format(formatForGeocodeFromAddress, this.txtAddress.getText().toString(), apiKey);
        final Request request = new Request.Builder()
                .url(searchAddressUrl)
                .build();

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final Response response = client.newCall(request).execute();
                    String jsonData = response.body().string();
                    JSONObject jsonObject = new JSONObject(jsonData);

                    String responseStatus = jsonObject.getString("status");
                    PerrFuncs.toast(responseStatus); // The status we get in the response from Google

                    if (responseStatus.equals("OK")) {
                        // All good
                        Log.v(TAG, jsonObject.toString());
                        JSONObject locationJson = jsonObject.getJSONArray("results").getJSONObject(0).getJSONObject("geometry").getJSONObject("location");
                        final double lat = Double.parseDouble(locationJson.get("lat").toString());
                        final double lng = Double.parseDouble(locationJson.get("lng").toString());
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(lat, lng), 18f));
                            }
                        });
                    } else {
                        // Try to show the error message, if any, if not it will jump to the catch clause
                        PerrFuncs.toast(jsonObject.getString("error_message"));
                    }
                    // The image's data is here
                } catch (IOException e) {
                    e.printStackTrace(); // Will print the stack trace in the "locgcat"
                } catch (JSONException e) {
                    Log.e(TAG, "Error in parsing the JSON");
                    PerrFuncs.toast("Failed to parse the JSON");
                    e.printStackTrace();
                }
            }
        }).start();
    }

    public boolean isGoogleMapsInstalled() {
        try {
            ApplicationInfo info = getPackageManager().getApplicationInfo("com.google.android.apps.maps", 0 );
            return info != null;
        }
        catch(PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    /**
     * Sets and configures the map
     * @param googleMap
     */
    public void setGoogleMap(GoogleMap googleMap) {
        this.googleMap = googleMap;

        boolean isAllowedToUseLocation = true; //PerrFuncs.hasPermissionForLocationServices(getApplicationContext())
        if (isAllowedToUseLocation) {
            try {
                // Allow to (try to) set
                googleMap.setMyLocationEnabled(true);
                takeCameraToAfeka(new GoogleMap.CancelableCallback() {
                    @Override
                    public void onFinish() {

                    }

                    @Override
                    public void onCancel() {

                    }
                });
            } catch (SecurityException exception) {
                PerrFuncs.toast("Error getting location");
            }
        } else {
            PerrFuncs.toast("Location is blocked in this app");
        }
    }

    private void takeCameraToAfeka(GoogleMap.CancelableCallback callback) {
        takeMapToStreet("Bnei Efraim 218, Tel Aviv", callback);
    }

    private void takeMapToStreet(String address, final GoogleMap.CancelableCallback callback) {
        if (getGoogleMap() != null) {
            getGoogleMap().animateCamera(CameraUpdateFactory.newLatLng(new LatLng(32.1165, 34.8176)), new GoogleMap.CancelableCallback() {
                @Override
                public void onFinish() {
                    zoomSlider.setProgress(95);
                    if (callback != null) {
                        callback.onFinish();
                    }
                }

                @Override
                public void onCancel() {
                    if (callback != null) {
                        callback.onCancel();
                    }
                }
            });
        }
    }

    public GoogleMap getGoogleMap() {
        return googleMap;
    }
}