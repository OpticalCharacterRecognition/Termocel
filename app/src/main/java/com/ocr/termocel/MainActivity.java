package com.ocr.termocel;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.database.Cursor;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.telephony.SmsManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.activeandroid.ActiveAndroid;
import com.activeandroid.query.Delete;
import com.activeandroid.query.Select;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.model.LatLng;
import com.ocr.termocel.model.Microlog;
import com.ocr.termocel.model.Temperature;
import com.ocr.termocel.receivers.MessageReceiver;
import com.ocr.termocel.utilities.AndroidBus;
import com.squareup.otto.Bus;

import java.util.List;

import butterknife.Bind;
import butterknife.ButterKnife;


public class MainActivity extends AppCompatActivity implements
        TabFragment.OnFragmentInteractionListener,
        DetailFragment.OnFragmentInteractionListener,
        GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, LocationListener {

    private final String TAG = MainActivity.class.getSimpleName();

    public static final String EXTRA_TELEPHONE_NUMBER = "EXTRA_TELEPHONE_NUMBER";

    private GoogleApiClient mGoogleApiClient;
    private LocationRequest mLocationRequest;
    private final static int CONNECTION_FAILURE_RESOLUTION_REQUEST = 9000;

    /**
     * This project number comes
     * from the Google Developers Console.
     */
    static final String PROJECT_NUMBER = "1079934054910";
    public static final String PROPERTY_REG_ID = "registration_id";
    private static final String PROPERTY_APP_VERSION = "appVersion";
    private final static int PLAY_SERVICES_RESOLUTION_REQUEST = 9000;


    private LatLng mLatLng;



    public static Bus bus;

    private int selectedId;
    private Microlog microlog;

    private String mContactName;
    private String mTelephoneNumber;

    private final String STATUS = "S";

    SmsManager smsManager;

    @Bind(R.id.toolbar)
    Toolbar toolbar;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ButterKnife.bind(this);

        bus = new AndroidBus();
        bus.register(this);

        /** toolBar **/
        setUpToolBar();

//        if (savedInstanceState == null) {
//            /**
//             * This dummy fragment is to prevent the transition error when popping the fragment backStack
//             * Error: Attempt to invoke virtual method 'boolean android.support.v4.app.Fragment.getAllowReturnTransitionOverlap()' on a null object reference
//             * https://code.google.com/p/android/issues/detail?id=82832
//             */
//            FragmentManager fragmentManager = getSupportFragmentManager();
//
//            fragmentManager.beginTransaction()
//                    .add(R.id.container, new Fragment())
//                    .addToBackStack("dummy")
//                    .commit();
//
//            // on first time display view for first nav item
//            displayView(0);
//        }

        checkForLocationServicesEnabled();

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();

        // Create the LocationRequest object
        mLocationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(10 * 1000)        // 10 seconds, in milliseconds
                .setFastestInterval(1 * 1000); // 1 second, in milliseconds


        Intent intent = getIntent();
        boolean comesFromReceiver = intent.getBooleanExtra(Constants.EXTRA_COMES_FROM_RECEIVER, false);
        if (comesFromReceiver) {
            mTelephoneNumber = intent.getStringExtra(MessageReceiver.EXTRA_PHONE_NUMBER);
            microlog = getMicrologByPhoneNumber(mTelephoneNumber);
            mContactName = microlog.name;
        } else {
            selectedId = intent.getIntExtra(Constants.EXTRA_SELECTED_ID, 0);
            microlog = getMicrologs().get(selectedId);
            mTelephoneNumber = microlog.sensorPhoneNumber;
            mContactName = microlog.name;
        }
        new SearchForSMSHistory().execute(mTelephoneNumber);


        bus = new AndroidBus();
        bus.register(this);

        smsManager = SmsManager.getDefault();


    }

    /**
     * checks for the GPS to be enabled
     */
    private void checkForLocationServicesEnabled() {
        LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        boolean gps_enabled = false;
        boolean network_enabled = false;

        try {
            gps_enabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
        } catch (Exception ex) {
        }

        try {
            network_enabled = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        } catch (Exception ex) {
        }

        if (!gps_enabled && !network_enabled) {
            // notify user
            android.app.AlertDialog.Builder dialog = new android.app.AlertDialog.Builder(this);
            dialog.setTitle(getResources().getString(R.string.gps_network_not_enabled));
            dialog.setMessage(getResources().getString(R.string.gps_network_not_enabled_message));
            dialog.setPositiveButton(getResources().getString(R.string.open_location_settings), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface paramDialogInterface, int paramInt) {
                    // TODO Auto-generated method stub
                    Intent myIntent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                    startActivity(myIntent);
                    //get gps
                }
            });
            dialog.setNegativeButton(getString(R.string.dialog_cancel), new DialogInterface.OnClickListener() {

                @Override
                public void onClick(DialogInterface paramDialogInterface, int paramInt) {
                    // TODO Auto-generated method stub

                }
            });
            dialog.show();
        }
    }


    @Override
    protected void onResume() {
        super.onResume();
    }


    public void getUpdatedSensorInfo(String telephoneNumber) {
        try {
//            Log.d("SMS send", "sending message to " + telephoneNumber);
            smsManager.sendTextMessage(telephoneNumber, null, STATUS, null, null);
            Toast.makeText(this, getString(R.string.toast_message_send), Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.toast_message_not_send), Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }


    /**
     * A Microlog is kind of the main object of this app, from this we get temperatures
     *
     * @return a sensor list
     */
    public List<Microlog> getMicrologs() {
        return new Select().from(Microlog.class).execute();
    }

    public Microlog getMicrologByPhoneNumber(String phoneNumber) {
        return new Select().from(Microlog.class).where("sensorPhoneNumber = ?", phoneNumber).executeSingle();
    }

    public List<Temperature> getTemperatureList(String telephoneNumber) {
        return new Select().from(Temperature.class).where("sensorPhoneNumber = ?", telephoneNumber).orderBy("timestamp ASC").execute();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_telephones) {
            Intent intent = new Intent(this, TelephoneChangeActivity.class);
            intent.putExtra(EXTRA_TELEPHONE_NUMBER, mTelephoneNumber);
            startActivity(intent);
        } else if (id == R.id.action_alerts) {
            Intent intent = new Intent(this, SetPointsActivity.class);
            intent.putExtra(EXTRA_TELEPHONE_NUMBER, mTelephoneNumber);
            startActivity(intent);
        } else if (id == R.id.action_history) {
            Intent intent = new Intent(this, HistoryActivity.class);
            intent.putExtra(EXTRA_TELEPHONE_NUMBER, mTelephoneNumber);
            startActivity(intent);
        }

        return super.onOptionsItemSelected(item);
    }


    /**
     * sets up the top bar
     */
    private void setUpToolBar() {
        setSupportActionBar(toolbar);
        setActionBarTitle(getString(R.string.app_name), null, false);
        getSupportActionBar().setElevation(0f);
        getSupportActionBar().setDisplayShowTitleEnabled(true);
        // enabling action bar app icon and behaving it as toggle button
        getSupportActionBar().setDefaultDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);
    }

    /**
     * Gets called from the fragments onResume and its because only the first doesn't have the up
     * button on the actionBar
     *
     * @param title          The title to show on the ActionBar
     * @param subtitle       The subtitle to show on the ActionBar
     * @param showNavigateUp if true, shows the up button
     */
    public void setActionBarTitle(String title, String subtitle, boolean showNavigateUp) {
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(title);
            if (subtitle != null) {
                getSupportActionBar().setSubtitle(subtitle);
            } else {
                getSupportActionBar().setSubtitle(null);
            }
            if (showNavigateUp) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            } else {
                getSupportActionBar().setDisplayHomeAsUpEnabled(false);
            }
        }
    }

    @Override
    public void onConnected(Bundle bundle) {
        Log.d(TAG, "Location services connected.");
        Location location = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
        if (location == null) {
            Log.d(TAG, "onConnected requesting Loc");
            LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
        } else {
            Log.d(TAG, "onConnected has last location");
            handleNewLocation(location);
        }
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.d(TAG, "Location services suspended. Please reconnect.");
    }

    @Override
    public void onLocationChanged(Location location) {
        Log.d(TAG, "onLocationChanged location changed");
        handleNewLocation(location);
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        if (connectionResult.hasResolution()) {
            try {
                // Start an Activity that tries to resolve the error
                connectionResult.startResolutionForResult(this, CONNECTION_FAILURE_RESOLUTION_REQUEST);
            } catch (IntentSender.SendIntentException e) {
                e.printStackTrace();
            }
        } else {
            Log.d(TAG, "Location services connection failed with code " + connectionResult.getErrorCode());
        }
    }

    /**
     * gets the location and then asks Map fragment to update it
     *
     * @param location current loc
     */
    private void handleNewLocation(Location location) {
        Log.d(TAG, location.toString());

        double currentLatitude = location.getLatitude();
        double currentLongitude = location.getLongitude();
        mLatLng = new LatLng(currentLatitude, currentLongitude);

        Log.e(TAG, "ACCURACY " + String.valueOf(location.getAccuracy()));

//        getLocationsFromBackend(mLatLng);

    }

    @Override
    public void onFragmentInteraction(Uri uri) {

    }

    /**
     * AsyncTask for getting the sms history from the phone and get it into a local database
     */
    private class SearchForSMSHistory extends AsyncTask<String, Void, Void> {

        boolean micrologIdSaved = false;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected Void doInBackground(String... strings) {
            String telephoneToSearch = strings[0];


            Uri uri = Uri.parse("content://sms");
            Cursor cursor = getContentResolver().query(uri, null, null, null, null);
            eraseTemperaturesFromLocalDB();

            if (cursor.moveToFirst()) {
                for (int i = 0; i < cursor.getCount(); i++) {
                    String number = cursor.getString(cursor.getColumnIndexOrThrow("address"));
                    if (number.length() > 10) {
                        number = number.substring(number.length() - 10);
                    }
                    if (number.equalsIgnoreCase(telephoneToSearch)) {
                        String body = cursor.getString(cursor.getColumnIndexOrThrow("body"));
                        String date = cursor.getString(cursor.getColumnIndexOrThrow("date"));
                        String type = cursor.getString(cursor.getColumnIndexOrThrow("type"));
                        if (body.contains("MICROLOG") && type.equalsIgnoreCase("1")) {
                            formatSMSMessage(telephoneToSearch, body, date);
                        }
                    }
                    cursor.moveToNext();
                }
            }
            cursor.close();
            return null;
        }

        private void formatSMSMessage(String telephoneToSearch, String sms, String date) {
            String[] spitedMessage = sms.split(" ");

            try {
                String micrologId = spitedMessage[1].substring(1, 3);
                String stateTemp = spitedMessage[2].substring(0, 3);
                String stateString = "";
                if (stateTemp.equalsIgnoreCase("NOR")) {
                    stateString = "Normal";
                } else if (stateTemp.equalsIgnoreCase("ATN")) {
                    stateString = "Atencion";
                } else if (stateTemp.equalsIgnoreCase("ADV")) {
                    stateString = "Advertencia";
                } else if (stateTemp.equalsIgnoreCase("ALA")) {
                    stateString = "Alarma";
                }
                String temperatureString = spitedMessage[5].substring(0, 2);
                String relativeHumidityString = spitedMessage[8];
//                Log.d(TAG, micrologId + " " + stateString + " " + temperatureString + " " + relativeHumidityString);

                if (!micrologIdSaved) { // we just want to do this the first time
                    saveMicrologID(telephoneToSearch, micrologId, stateString);
                }
                saveTemperature(micrologId, stateString, temperatureString, relativeHumidityString, telephoneToSearch, Long.parseLong(date));
            } catch (NumberFormatException e) {
                e.printStackTrace();
//                Log.e(TAG, "SMS must not be well formatted");
            }

        }

        private void saveTemperature(String micrologId, String stateString, String temperatureString, String relativeHumidityString, String temporalAddress, long date) {
            ActiveAndroid.beginTransaction();
            try {
                Temperature temperature = new Temperature(
                        temporalAddress,
                        micrologId,
                        stateString,
                        Double.parseDouble(temperatureString),
                        Double.parseDouble(relativeHumidityString),
                        date
                );
                temperature.save();
                ActiveAndroid.setTransactionSuccessful();
            } catch (NumberFormatException e) {
                e.printStackTrace();
            } finally {
                ActiveAndroid.endTransaction();
            }
        }

        /**
         * Database erase
         * Erases the db so we don't have to check if the reading already exists and don't put duplicates
         */
        private void eraseTemperaturesFromLocalDB() {
            List<Temperature> tempList = new Select().from(Temperature.class).execute();
            if (tempList != null && tempList.size() > 0) {
                ActiveAndroid.beginTransaction();
                try {
                    new Delete().from(Temperature.class).execute();
                    ActiveAndroid.setTransactionSuccessful();
                } catch (Exception e) {
//                    Logger.e(e, "error deleting existing db");
                } finally {
                    ActiveAndroid.endTransaction();
                }
            }

        }

        private void saveMicrologID(String temporalAddress, String micrologId, String stateString) {
            Microlog microlog = new Select().
                    from(Microlog.class).
                    where("sensorPhoneNumber = ?", temporalAddress).
                    executeSingle();

            if (microlog != null) {
                microlog.sensorId = micrologId;
                microlog.lastState = stateString;
                microlog.save();
                micrologIdSaved = true;
            }
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            //TODO maybe make a bus call ?
//            getExistingTemperaturesForMain();

        }
    }
}
