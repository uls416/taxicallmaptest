package com.taxicall.taxiapptest_groovydsl;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.location.Address;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;

import com.caverock.androidsvg.BuildConfig;

import org.osmdroid.api.IMapController;
import org.osmdroid.bonuspack.kml.KmlDocument;
import org.osmdroid.bonuspack.kml.KmlFeature;
import org.osmdroid.bonuspack.kml.KmlFolder;
import org.osmdroid.bonuspack.kml.KmlPlacemark;
import org.osmdroid.bonuspack.kml.KmlTrack;
import org.osmdroid.bonuspack.kml.LineStyle;
import org.osmdroid.bonuspack.kml.Style;
import org.osmdroid.bonuspack.location.GeocoderNominatim;
import org.osmdroid.bonuspack.location.POI;
import org.osmdroid.bonuspack.routing.OSRMRoadManager;
import org.osmdroid.bonuspack.routing.Road;
import org.osmdroid.bonuspack.routing.RoadManager;
import org.osmdroid.bonuspack.routing.RoadNode;
import org.osmdroid.config.Configuration;
import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.mapsforge.MapsForgeTileSource;
import org.osmdroid.tileprovider.MapTileProviderBase;
import org.osmdroid.tileprovider.tilesource.ITileSource;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.util.NetworkLocationIgnorer;
import org.osmdroid.util.TileSystem;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.FolderOverlay;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Marker.OnMarkerDragListener;
import org.osmdroid.views.overlay.Overlay;
import org.osmdroid.views.overlay.Polygon;
import org.osmdroid.views.overlay.Polyline;
import org.osmdroid.views.overlay.ScaleBarOverlay;
import org.osmdroid.views.overlay.TilesOverlay;
import org.osmdroid.views.overlay.infowindow.BasicInfoWindow;
import org.osmdroid.views.overlay.infowindow.InfoWindow;
import org.osmdroid.views.overlay.infowindow.MarkerInfoWindow;
import org.osmdroid.views.overlay.mylocation.DirectedLocationOverlay;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Stack;

/**
 @noinspection ALL, unchecked
 */
public class MainActivity extends Activity implements MapEventsReceiver, LocationListener, SensorEventListener, MapView.OnFirstLayoutListener {
    protected MapView map;
    protected GeoPoint startPoint, destinationPoint;
    protected ArrayList<GeoPoint> viaPoints;
    protected static int START_INDEX=-2, DEST_INDEX=-1;
    protected FolderOverlay mItineraryMarkers;
    //for departure, destination and viapoints
    protected Marker markerStart, markerDestination;
    protected ViaPointInfoWindow mViaPointInfoWindow;
    protected DirectedLocationOverlay myLocationOverlay;
    //MyLocationNewOverlay myLocationNewOverlay;
    protected LocationManager mLocationManager;
    protected boolean mTrackingMode;
    Button mTrackingModeButton;
    float mAzimuthAngleSpeed = 0.0f;
    protected Polygon mDestinationPolygon; //enclosing polygon of destination location
    public static Road[] mRoads;  //made static to pass between activities
    protected int mSelectedRoad;
    protected Polyline[] mRoadOverlays;
    protected FolderOverlay mRoadNodeMarkers;
    int mWhichRouteProvider;
    public static ArrayList<POI> mPOIs; //made static to pass between activities
    public static KmlDocument mKmlDocument; //made static to pass between activities
    public static Stack<KmlFeature> mKmlStack; //passed between activities, top is the current KmlFeature to edit.
    public static KmlFolder mKmlClipboard; //passed between activities. Folder for multiple items selection.
    boolean mIsRecordingTrack;
    static String SHARED_PREFS_APPKEY = "taxiapptest";
    static String PREF_LOCATIONS_KEY = "PREF_LOCATIONS";
    boolean mNightMode;
    static final String userAgent = BuildConfig.APPLICATION_ID+"/"+BuildConfig.VERSION_NAME;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this));
        MapsForgeTileSource.createInstance(getApplication());

        LayoutInflater inflater = (LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        @SuppressLint("InflateParams") View v = inflater.inflate(R.layout.activity_main, null);
        setContentView(v);

        SharedPreferences prefs = getSharedPreferences("taxiapptest", MODE_PRIVATE);

        map = (MapView) v.findViewById(R.id.mapView);

        String tileProviderName = prefs.getString("TILE_PROVIDER", "Mapnik");
        mNightMode = prefs.getBoolean("NIGHT_MODE", false);
        if ("rendertheme-v4".equals(tileProviderName)) {
        } else {
            try {
                ITileSource tileSource = TileSourceFactory.getTileSource(tileProviderName);
                map.setTileSource(tileSource);
            } catch (IllegalArgumentException e) {
                map.setTileSource(TileSourceFactory.MAPNIK);
            }
        }
        if (mNightMode)
            map.getOverlayManager().getTilesOverlay().setColorFilter(TilesOverlay.INVERT_COLORS);

        map.setTilesScaledToDpi(true);
        map.setMultiTouchControls(true);
        map.setMinZoomLevel(1.0);
        map.setMaxZoomLevel(21.0);
        map.setVerticalMapRepetitionEnabled(false);
        map.setScrollableAreaLimitLatitude(TileSystem.MaxLatitude,-TileSystem.MaxLatitude, 0/*map.getHeight()/2*/);

        IMapController mapController = map.getController();

        //To use MapEventsReceiver methods, we add a MapEventsOverlay:
        MapEventsOverlay overlay = new MapEventsOverlay(this);
        map.getOverlays().add(overlay);

        mLocationManager = (LocationManager)getSystemService(LOCATION_SERVICE);

        //map prefs:
        mapController.setZoom((double)prefs.getFloat("MAP_ZOOM_LEVEL_F", 5));
        mapController.setCenter(new GeoPoint((double) prefs.getFloat("MAP_CENTER_LAT", 48.5f),
                (double)prefs.getFloat("MAP_CENTER_LON", 2.5f)));

        myLocationOverlay = new DirectedLocationOverlay(this);
        map.getOverlays().add(myLocationOverlay);

        if (savedInstanceState == null){
            Location location = null;
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                location = mLocationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (location == null)
                    location = mLocationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            }
            if (location != null) {
                //location known:
                onLocationChanged(location);
            } else {
                //no location known: hide myLocationOverlay
                myLocationOverlay.setEnabled(false);
            }
            startPoint = null;
            destinationPoint = null;
            viaPoints = new ArrayList<>();
        } else {
            myLocationOverlay.setLocation((GeoPoint)savedInstanceState.getParcelable("location"));
            //TODO: restore other aspects of myLocationOverlay...
            startPoint = savedInstanceState.getParcelable("start");
            destinationPoint = savedInstanceState.getParcelable("destination");
            viaPoints = savedInstanceState.getParcelableArrayList("viapoints");
        }

        ScaleBarOverlay scaleBarOverlay = new ScaleBarOverlay(map);
        map.getOverlays().add(scaleBarOverlay);

        // Itinerary markers:
        mItineraryMarkers = new FolderOverlay();
        mItineraryMarkers.setName(getString(R.string.itinerary_markers_title));
        map.getOverlays().add(mItineraryMarkers);
        mViaPointInfoWindow = new ViaPointInfoWindow(R.layout.itinerary_bubble, map);
        updateUIWithItineraryMarkers();

        //Tracking system:
        mTrackingModeButton = (Button)findViewById(R.id.buttonTrackingMode);
        mTrackingModeButton.setOnClickListener(view -> {
            mTrackingMode = !mTrackingMode;
            updateUIWithTrackingMode();
        });
        if (savedInstanceState != null){
            mTrackingMode = savedInstanceState.getBoolean("tracking_mode");
            updateUIWithTrackingMode();
        } else
            mTrackingMode = false;

        mIsRecordingTrack = false; //TODO restore state

        AutoCompleteOnPreferences departureText = (AutoCompleteOnPreferences) findViewById(R.id.editDeparture);
        departureText.setPrefKeys(SHARED_PREFS_APPKEY, PREF_LOCATIONS_KEY);

        Button searchDepButton = (Button)findViewById(R.id.buttonSearchDep);
        searchDepButton.setOnClickListener(view -> handleSearchButton(START_INDEX, R.id.editDeparture));

        AutoCompleteOnPreferences destinationText = (AutoCompleteOnPreferences) findViewById(R.id.editDestination);
        destinationText.setPrefKeys(SHARED_PREFS_APPKEY, PREF_LOCATIONS_KEY);

        Button searchDestButton = (Button)findViewById(R.id.buttonSearchDest);
        searchDestButton.setOnClickListener(view -> handleSearchButton(DEST_INDEX, R.id.editDestination));


        View searchPanel = findViewById(R.id.search_panel);
        searchPanel.setVisibility(prefs.getInt("PANEL_VISIBILITY", View.VISIBLE));

        registerForContextMenu(searchDestButton);
        //context menu for clicking on the map is registered on this button.
        //(a little bit strange, but if we register it on mapView, it will catch map drag events)

        //Route and Directions
        mRoadNodeMarkers = new FolderOverlay();
        mRoadNodeMarkers.setName("Route Steps");
        map.getOverlays().add(mRoadNodeMarkers);

        if (savedInstanceState != null){
            //STATIC mRoad = savedInstanceState.getParcelable("road");
            updateUIWithRoads(mRoads);
        }

        checkPermissions();

    }

    void checkPermissions() {
        List<String> permissions = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (!permissions.isEmpty()) {
            String[] params = permissions.toArray(new String[0]);
            int REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS = 124;
            ActivityCompat.requestPermissions(this, params, REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS);
        } // else: We already have permissions, so handle as normal
    }
    void setViewOn(BoundingBox bb){
        if (bb != null){
            map.zoomToBoundingBox(bb, true);
        }
    }

    //--- Stuff for setting the mapview on a box at startup:
    BoundingBox mInitialBoundingBox = null;
    public void onFirstLayout(View v, int left, int top, int right, int bottom) {
        if (mInitialBoundingBox != null)
            map.zoomToBoundingBox(mInitialBoundingBox, false);
    }
    void savePrefs(){
        SharedPreferences prefs = getSharedPreferences("OSMNAVIGATOR", MODE_PRIVATE);
        SharedPreferences.Editor ed = prefs.edit();
        ed.putFloat("MAP_ZOOM_LEVEL_F", (float)map.getZoomLevelDouble());
        GeoPoint c = (GeoPoint) map.getMapCenter();
        ed.putFloat("MAP_CENTER_LAT", (float)c.getLatitude());
        ed.putFloat("MAP_CENTER_LON", (float)c.getLongitude());
        View searchPanel = findViewById(R.id.search_panel);
        ed.putInt("PANEL_VISIBILITY", searchPanel.getVisibility());
        MapTileProviderBase tileProvider = map.getTileProvider();
        String tileProviderName = tileProvider.getTileSource().name();
        ed.putString("TILE_PROVIDER", tileProviderName);
        ed.putBoolean("NIGHT_MODE", mNightMode);
        ed.putInt("ROUTE_PROVIDER", mWhichRouteProvider);
        ed.apply();
    }

    /**
     * callback to store activity status before a restart (orientation change for instance)
     */
    @Override protected void onSaveInstanceState (Bundle outState){
        outState.putParcelable("location", myLocationOverlay.getLocation());
        outState.putBoolean("tracking_mode", mTrackingMode);
        outState.putParcelable("start", startPoint);
        outState.putParcelable("destination", destinationPoint);
        outState.putParcelableArrayList("viapoints", viaPoints);

        savePrefs();
    }
    boolean startLocationUpdates(){
        boolean result = false;
        for (final String provider : mLocationManager.getProviders(true)) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                mLocationManager.requestLocationUpdates(provider, 2 * 1000, 0.0f, this);
                result = true;
            }
        }
        return result;
    }

    @Override protected void onResume() {
        super.onResume();
        boolean isOneProviderEnabled = startLocationUpdates();
        myLocationOverlay.setEnabled(isOneProviderEnabled);
    }

    @Override protected void onPause() {
        super.onPause();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mLocationManager.removeUpdates(this);
        }
        savePrefs();
    }

    // Canlı Takip kodu
    void updateUIWithTrackingMode(){
        if (mTrackingMode){
            mTrackingModeButton.setBackgroundResource(R.drawable.btn_tracking_on);
            if (myLocationOverlay.isEnabled()&& myLocationOverlay.getLocation() != null){
                map.getController().animateTo(myLocationOverlay.getLocation());
            }
            map.setMapOrientation(-mAzimuthAngleSpeed);
            mTrackingModeButton.setKeepScreenOn(true);
        } else {
            mTrackingModeButton.setBackgroundResource(R.drawable.btn_tracking_off);
            map.setMapOrientation(0.0f);
            mTrackingModeButton.setKeepScreenOn(false);
        }
    }

    //------------- Geocoding and Reverse Geocoding

    /**
     * Reverse Geocoding
     */
    public String getAddress(GeoPoint p){
        GeocoderNominatim geocoder = new GeocoderNominatim(userAgent);
        String theAddress;
        try {
            double dLatitude = p.getLatitude();
            double dLongitude = p.getLongitude();
            List<Address> addresses = geocoder.getFromLocation(dLatitude, dLongitude, 1);
            StringBuilder sb = new StringBuilder();
            if (addresses.size() > 0) {
                Address address = addresses.get(0);
                int n = address.getMaxAddressLineIndex();
                for (int i=0; i<=n; i++) {
                    if (i!=0)
                        sb.append(", ");
                    sb.append(address.getAddressLine(i));
                }
                theAddress = sb.toString();
            } else {
                theAddress = null;
            }
        } catch (IOException e) {
            theAddress = null;
        }
        if (theAddress != null) {
            return theAddress;
        } else {
            return "";
        }
    }

    @SuppressLint("StaticFieldLeak")
    private class GeocodingTask extends AsyncTask<Object, Void, List<Address>> {
        int mIndex;
        protected List<Address> doInBackground(Object... params) {
            String locationAddress = (String)params[0];
            mIndex = (Integer)params[1];
            GeocoderNominatim geocoder = new GeocoderNominatim(userAgent);
            geocoder.setOptions(true); //ask for enclosing polygon (if any)
            try {
                BoundingBox viewbox = map.getBoundingBox();
                return geocoder.getFromLocationName(locationAddress, 1,
                        viewbox.getLatSouth(), viewbox.getLonEast(),
                        viewbox.getLatNorth(), viewbox.getLonWest(), false);
            } catch (Exception e) {
                return null;
            }
        }
        protected void onPostExecute(List<Address> foundAdresses) {
            if (foundAdresses == null) {
                Toast.makeText(getApplicationContext(), "Geocoding error", Toast.LENGTH_SHORT).show();
            } else if (foundAdresses.size() == 0) { //if no address found, display an error
                Toast.makeText(getApplicationContext(), "Address not found.", Toast.LENGTH_SHORT).show();
            } else {
                Address address = foundAdresses.get(0); //get first address
                String addressDisplayName = address.getExtras().getString("display_name");
                if (mIndex == START_INDEX){
                    startPoint = new GeoPoint(address.getLatitude(), address.getLongitude());
                    markerStart = updateItineraryMarker(markerStart, startPoint, START_INDEX,
                            R.string.departure, R.drawable.marker_departure, -1, addressDisplayName);
                    map.getController().setCenter(startPoint);
                } else if (mIndex == DEST_INDEX){
                    destinationPoint = new GeoPoint(address.getLatitude(), address.getLongitude());
                    markerDestination = updateItineraryMarker(markerDestination, destinationPoint, DEST_INDEX,
                            R.string.destination, R.drawable.marker_destination, -1, addressDisplayName);
                    map.getController().setCenter(destinationPoint);
                }
                getRoadAsync();
                //get and display enclosing polygon:
                Bundle extras = address.getExtras();
                if (extras != null && extras.containsKey("polygonpoints")){
                    ArrayList<GeoPoint> polygon = extras.getParcelableArrayList("polygonpoints");
                    updateUIWithPolygon(polygon, addressDisplayName);
                } else {
                    updateUIWithPolygon(null, "");
                }
            }
        }
    }

    /**
     * Arama yoluyla geopoint atama
     */
    public void handleSearchButton(int index, int editResId){
        EditText locationEdit = (EditText)findViewById(editResId);
        //Hide the soft keyboard:
        InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(locationEdit.getWindowToken(), 0);

        String locationAddress = locationEdit.getText().toString();

        if (locationAddress.equals("")){
            removePoint(index);
            map.invalidate();
            return;
        }

        Toast.makeText(this, "Searching:\n"+locationAddress, Toast.LENGTH_LONG).show();
        AutoCompleteOnPreferences.storePreference(this, locationAddress, SHARED_PREFS_APPKEY, PREF_LOCATIONS_KEY);
        new GeocodingTask().execute(locationAddress, index);
    }

    //add or replace the polygon overlay
    public void updateUIWithPolygon(ArrayList<GeoPoint> polygon, String name){
        List<Overlay> mapOverlays = map.getOverlays();
        int location = -1;
        if (mDestinationPolygon != null)
            location = mapOverlays.indexOf(mDestinationPolygon);
        mDestinationPolygon = new Polygon();
        mDestinationPolygon.setFillColor(0x15FF0080);
        mDestinationPolygon.setStrokeColor(0x800000FF);
        mDestinationPolygon.setStrokeWidth(5.0f);
        mDestinationPolygon.setTitle(name);
        BoundingBox bb = null;
        if (polygon != null){
            mDestinationPolygon.setPoints(polygon);
            bb = BoundingBox.fromGeoPoints(polygon);
        }
        if (location != -1)
            mapOverlays.set(location, mDestinationPolygon);
        else
            mapOverlays.add(1, mDestinationPolygon); //insert just above the MapEventsOverlay.
        setViewOn(bb);
        map.invalidate();
    }

    //Async task to reverse-geocode the marker position in a separate thread:
    @SuppressLint("StaticFieldLeak")
    private class ReverseGeocodingTask extends AsyncTask<Marker, Void, String> {
        Marker marker;
        protected String doInBackground(Marker... params) {
            marker = params[0];
            return getAddress(marker.getPosition());
        }
        protected void onPostExecute(String result) {
            marker.setSnippet(result);
            marker.showInfoWindow();
        }
    }

    //------------ Marker ve bilgi pop-up'ları
    class OnItineraryMarkerDragListener implements OnMarkerDragListener {
        @Override public void onMarkerDrag(Marker marker) {}
        @Override public void onMarkerDragEnd(Marker marker) {
            int index = (Integer)marker.getRelatedObject();
            if (index == START_INDEX)
                startPoint = marker.getPosition();
            else if (index == DEST_INDEX)
                destinationPoint = marker.getPosition();
            else
                viaPoints.set(index, marker.getPosition());
            //update location:
            new ReverseGeocodingTask().execute(marker);
            //update route:
            getRoadAsync();
        }

        @Override
        public void onMarkerDragStart(Marker marker) {
        }
    }
    final OnItineraryMarkerDragListener mItineraryListener = new OnItineraryMarkerDragListener();

    /** Update (or create if null) a marker in itineraryMarkers. */
    public Marker updateItineraryMarker(Marker marker, GeoPoint p, int index,int titleResId, int markerResId, int imageResId, String address) {
        if (marker == null){
            marker = new Marker(map);
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            marker.setInfoWindow(mViaPointInfoWindow);
            marker.setDraggable(true);
            marker.setOnMarkerDragListener(mItineraryListener);
            mItineraryMarkers.add(marker);
        }
        String title = getResources().getString(titleResId);
        marker.setTitle(title);
        marker.setPosition(p);
        Drawable icon = ResourcesCompat.getDrawable(getResources(), markerResId, null);
        marker.setIcon(icon);
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        if (imageResId != -1)
            marker.setImage(ResourcesCompat.getDrawable(getResources(), imageResId, null));
        marker.setRelatedObject(index);
        map.invalidate();
        if (address != null)
            marker.setSnippet(address);
        else
            //Start geocoding task to get the address and update the Marker description:
            new ReverseGeocodingTask().execute(marker);
        return marker;
    }
    public void removePoint(int index){
        if (index == START_INDEX){
            startPoint = null;
            if (markerStart != null){
                markerStart.closeInfoWindow();
                mItineraryMarkers.remove(markerStart);
                markerStart = null;
            }
        } else if (index == DEST_INDEX){
            destinationPoint = null;
            if (markerDestination != null){
                markerDestination.closeInfoWindow();
                mItineraryMarkers.remove(markerDestination);
                markerDestination = null;
            }
        } else {
            viaPoints.remove(index);
            updateUIWithItineraryMarkers();
        }
        getRoadAsync();
    }
    public void updateUIWithItineraryMarkers(){
        mItineraryMarkers.closeAllInfoWindows();
        mItineraryMarkers.getItems().clear();
        //Start marker:
        if (startPoint != null){
            markerStart = updateItineraryMarker(null, startPoint, START_INDEX,
                    R.string.departure, R.drawable.marker_departure, -1, null);
        }
        //Via-points markers if any:
        for (int index=0; index<viaPoints.size(); index++){
            updateItineraryMarker(null, viaPoints.get(index), index,
                    R.string.viapoint, R.drawable.marker_via, -1, null);
        }
        //Destination marker if any:
        if (destinationPoint != null){
            markerDestination = updateItineraryMarker(null, destinationPoint, DEST_INDEX,
                    R.string.destination, R.drawable.marker_destination, -1, null);
        }
    }

    //------------ Rota ve Yön tarifleri
    private void putRoadNodes(Road road){
        mRoadNodeMarkers.getItems().clear();
        Drawable icon = ResourcesCompat.getDrawable(getResources(), R.drawable.marker_node, null);
        int n = road.mNodes.size();
        MarkerInfoWindow infoWindow = new MarkerInfoWindow(org.osmdroid.bonuspack.R.layout.bonuspack_bubble, map);
        TypedArray iconIds = getResources().obtainTypedArray(R.array.direction_icons);
        for (int i=0; i<n; i++){
            RoadNode node = road.mNodes.get(i);
            String instructions = (node.mInstructions==null ? "" : node.mInstructions);
            Marker nodeMarker = new Marker(map);
            nodeMarker.setTitle(getString(R.string.step)+ " " + (i+1));
            nodeMarker.setSnippet(instructions);
            nodeMarker.setSubDescription(Road.getLengthDurationText(this, node.mLength, node.mDuration));
            nodeMarker.setPosition(node.mLocation);
            nodeMarker.setIcon(icon);
            nodeMarker.setInfoWindow(infoWindow); //use a shared infowindow.
            int iconId = iconIds.getResourceId(node.mManeuverType, R.drawable.ic_empty);
            if (iconId != R.drawable.ic_empty){
                Drawable image = ResourcesCompat.getDrawable(getResources(), iconId, null);
                nodeMarker.setImage(image);
            }
            nodeMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
            mRoadNodeMarkers.add(nodeMarker);
        }
        iconIds.recycle();
    }

    void selectRoad(int roadIndex){
        mSelectedRoad = roadIndex;
        putRoadNodes(mRoads[roadIndex]);
        // Rota bilgisini textview'lara aktarır
        TextView textView = (TextView)findViewById(R.id.routeInfo);
        textView.setText(mRoads[roadIndex].getLengthDurationText(this, -1));
        for (int i=0; i<mRoadOverlays.length; i++){
            Paint p = mRoadOverlays[i].getPaint();
            if (i == roadIndex) {
                p.setColor(Color.BLACK); //blue
                p.setStrokeWidth(12);
            }else {
                p.setColor(Color.GRAY); //grey
                p.setStrokeWidth(7);
            }
        }
        map.invalidate();
    }

    class RoadOnClickListener implements Polyline.OnClickListener{
        @Override public boolean onClick(Polyline polyline, MapView mapView, GeoPoint eventPos){
            int selectedRoad = (Integer)polyline.getRelatedObject();
            selectRoad(selectedRoad);
            polyline.setInfoWindowLocation(eventPos);
            polyline.showInfoWindow();
            return true;
        }
    }

    void updateUIWithRoads(Road[] roads){
        mRoadNodeMarkers.getItems().clear();
        TextView textView = (TextView)findViewById(R.id.routeInfo);
        textView.setText("");
        List<Overlay> mapOverlays = map.getOverlays();
        if (mRoadOverlays != null){
            for (Polyline mRoadOverlay : mRoadOverlays) mapOverlays.remove(mRoadOverlay);
            mRoadOverlays = null;
        }
        if (roads == null)
            return;
        if (roads[0].mStatus == Road.STATUS_TECHNICAL_ISSUE)
            Toast.makeText(map.getContext(), "Technical issue when getting the route", Toast.LENGTH_SHORT).show();
        else if (roads[0].mStatus > Road.STATUS_TECHNICAL_ISSUE) //functional issues
            Toast.makeText(map.getContext(), "No possible route here", Toast.LENGTH_SHORT).show();
        mRoadOverlays = new Polyline[roads.length];
        for (int i=0; i<roads.length; i++) {
            Polyline roadPolyline = RoadManager.buildRoadOverlay(roads[i]);
            mRoadOverlays[i] = roadPolyline;

            String routeDesc = roads[i].getLengthDurationText(this, -1);
            roadPolyline.setTitle(getString(R.string.route) + " - " + routeDesc);
            roadPolyline.setInfoWindow(new BasicInfoWindow(org.osmdroid.bonuspack.R.layout.bonuspack_bubble, map));
            roadPolyline.setRelatedObject(i);
            roadPolyline.setOnClickListener(new RoadOnClickListener());
            mapOverlays.add(1, roadPolyline);
            //we insert the road overlays at the "bottom", just above the MapEventsOverlay,
            //to avoid covering the other overlays.
        }
        selectRoad(0);
    }

    /**
     * Async task to get the road in a separate thread.
     */
    @SuppressLint("StaticFieldLeak")
    private class UpdateRoadTask extends AsyncTask<ArrayList<GeoPoint>, Void, Road[]> {

        private final Context mContext;

        public UpdateRoadTask(Context context) {
            this.mContext = context;
        }

        @SafeVarargs
        protected final Road[] doInBackground(ArrayList<GeoPoint>... params) {
            ArrayList<GeoPoint> waypoints = params[0];
            RoadManager roadManager;
            Locale locale = Locale.getDefault();

            roadManager = new OSRMRoadManager(mContext, userAgent);
            return roadManager.getRoads(waypoints);
        }

        protected void onPostExecute(Road[] result) {
            mRoads = result;
            updateUIWithRoads(result);
        }
    }

    // Harita üzerinde başlangıç ve bitişin arasına marker ekler
    public void addViaPoint(GeoPoint p){
        viaPoints.add(p);
        updateItineraryMarker(null, p, viaPoints.size() - 1,
                R.string.viapoint, R.drawable.marker_via, -1, null);
    }

    // Harita üzerinden basılı tutularak seçilen butonların işlemleri
    @Override public boolean onContextItemSelected(MenuItem item) {

        if (item.getItemId() == R.id.menu_departure){
            startPoint = new GeoPoint(mClickedGeoPoint);
            markerStart = updateItineraryMarker(markerStart, startPoint, START_INDEX,
                    R.string.departure, R.drawable.marker_departure, -1, null);
            getRoadAsync();
            return true;
        } else if (item.getItemId() == R.id.menu_destination) {
            destinationPoint = new GeoPoint(mClickedGeoPoint);
            markerDestination = updateItineraryMarker(markerDestination, destinationPoint, DEST_INDEX,
                    R.string.destination, R.drawable.marker_destination, -1, null);
            getRoadAsync();
            return true;
        } else if (item.getItemId() == R.id.menu_viapoint) {
            GeoPoint viaPoint = new GeoPoint(mClickedGeoPoint);
            addViaPoint(viaPoint);
            getRoadAsync();
            return true;
        } /*else if (item.getItemId() == menu_kmlpoint) {
            GeoPoint kmlPoint = new GeoPoint(mClickedGeoPoint);
            addKmlPoint(kmlPoint);
            return true;
        }*/ else {
            return super.onContextItemSelected(item);
        }
    }

    public void getRoadAsync(){
        mRoads = null;
        GeoPoint roadStartPoint = null;
        if (startPoint != null){
            roadStartPoint = startPoint;
        } else if (myLocationOverlay.isEnabled() && myLocationOverlay.getLocation() != null){
            //use my current location as itinerary start point:
            roadStartPoint = myLocationOverlay.getLocation();
        }
        if (roadStartPoint == null || destinationPoint == null){
            updateUIWithRoads(mRoads);
            return;
        }
        ArrayList<GeoPoint> waypoints = new ArrayList<>(2);
        waypoints.add(roadStartPoint);
        //add intermediate via points:
        waypoints.addAll(viaPoints);
        waypoints.add(destinationPoint);

        //noinspection unchecked
        new UpdateRoadTask(this).execute(waypoints);

    }


    //------------ Harita etkinlikleri
    GeoPoint mClickedGeoPoint;
    @Override public boolean longPressHelper(GeoPoint p) {
        mClickedGeoPoint = p;
        Button searchButton = (Button)findViewById(R.id.buttonSearchDest);
        openContextMenu(searchButton);
        return true;
    }
    @Override public boolean singleTapConfirmedHelper(GeoPoint p) {
        InfoWindow.closeAllInfoWindowsOn(map);
        return true;
    }
    //----------- Haritaya basılı tutulduğunda çalışan context menu
    @Override public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.map_menu, menu);
    }
    //------------ Harita tasarımı
    @Override public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.option_menu, menu);

        if (map.getTileProvider().getTileSource() == TileSourceFactory.MAPNIK) {
            if (!mNightMode)
                menu.findItem(R.id.menu_tile_mapnik).setChecked(true);
            else
                menu.findItem(R.id.menu_tile_mapnik_by_night).setChecked(true);
        }

        return true;
    }
    //------------ Harita etkinlikleri sonu

    //------------ Canlı Konum Takibi
    private final NetworkLocationIgnorer mIgnorer = new NetworkLocationIgnorer();
    long mLastTime = 0; // milliseconds
    double mSpeed = 0.0; // km/h
    @SuppressLint("SetTextI18n")
    @Override public void onLocationChanged(final Location pLoc) {
        long currentTime = System.currentTimeMillis();
        if (mIgnorer.shouldIgnore(pLoc.getProvider(), currentTime))
            return;
        double dT = currentTime - mLastTime;
        if (dT < 100.0){
            //Toast.makeText(this, pLoc.getProvider()+" dT="+dT, Toast.LENGTH_SHORT).show();
            return;
        }
        mLastTime = currentTime;

        GeoPoint newLocation = new GeoPoint(pLoc);
        if (!myLocationOverlay.isEnabled()){
            //we get the location for the first time:
            myLocationOverlay.setEnabled(true);
            map.getController().animateTo(newLocation);
        }

        GeoPoint prevLocation = myLocationOverlay.getLocation();
        myLocationOverlay.setLocation(newLocation);
        myLocationOverlay.setAccuracy((int)pLoc.getAccuracy());

        if (prevLocation != null && Objects.equals(pLoc.getProvider(), LocationManager.GPS_PROVIDER)){
            mSpeed = pLoc.getSpeed() * 3.6;
            long speedInt = Math.round(mSpeed);
            TextView speedTxt = (TextView)findViewById(R.id.speed);
            speedTxt.setText(speedInt + " km/h");

            //TODO: check if speed is not too small
            if (mSpeed >= 0.1){
                mAzimuthAngleSpeed = pLoc.getBearing();
                myLocationOverlay.setBearing(mAzimuthAngleSpeed);
            }
        }

        if (mTrackingMode){
            //keep the map view centered on current location:
            map.getController().animateTo(newLocation);
            map.setMapOrientation(-mAzimuthAngleSpeed);
        } else {
            //just redraw the location overlay:
            map.invalidate();
        }

        if (mIsRecordingTrack) {
            recordCurrentLocationInTrack(newLocation);
        }
    }
    static int[] TrackColor = {
            Color.CYAN-0x20000000, Color.BLUE-0x20000000, Color.MAGENTA-0x20000000, Color.RED-0x20000000, Color.YELLOW-0x20000000
    };
    KmlTrack createTrack() {
        KmlTrack t = new KmlTrack();
        KmlPlacemark p = new KmlPlacemark();
        p.mId = "my_track";
        p.mName = "My Track";
        p.mGeometry = t;
        mKmlDocument.mKmlRoot.add(p);
        //set a color to this track by creating a style:
        Style s = new Style();
        int color;
        try {
            color = Integer.parseInt("my_track");
            color = color % TrackColor.length;
            color = TrackColor[color];
        } catch (NumberFormatException e) {
            color = Color.GREEN-0x20000000;
        }
        s.mLineStyle = new LineStyle(color, 8.0f);
        p.mStyle = mKmlDocument.addStyle(s);
        return t;
    }
    void recordCurrentLocationInTrack(GeoPoint currentLocation) {
        //Find the KML track in the current KML structure - and create it if necessary:
        KmlTrack t;
        KmlFeature f = mKmlDocument.mKmlRoot.findFeatureId("my_track", false);
        if (f == null)
            t = createTrack();
        else if (!(f instanceof KmlPlacemark))
            //id already defined but is not a PlaceMark
            return;
        else {
            KmlPlacemark p = (KmlPlacemark)f;
            if (!(p.mGeometry instanceof KmlTrack))
                //id already defined but is not a Track
                return;
            else
                t = (KmlTrack) p.mGeometry;
        }

        //record in the track the current location at current time:
        t.add(currentLocation, new Date());

    }
    @Override public void onProviderDisabled(@NonNull String provider) {}
    @Override public void onProviderEnabled(@NonNull String provider) {}
    @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
    //------------ SensorEventListener implementation
    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {
        myLocationOverlay.setAccuracy(accuracy);
        map.invalidate();
    }
    @Override public void onSensorChanged(SensorEvent event) {
        event.sensor.getType();
    }
    //------------ Canlı Konum Takibi sonu
}