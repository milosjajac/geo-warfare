package rs.elfak.jajac.geowarfare.services;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;

import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
import com.firebase.geofire.GeoQuery;
import com.firebase.geofire.GeoQueryEventListener;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import rs.elfak.jajac.geowarfare.Constants;
import rs.elfak.jajac.geowarfare.R;
import rs.elfak.jajac.geowarfare.activities.MainActivity;
import rs.elfak.jajac.geowarfare.models.UserModel;
import rs.elfak.jajac.geowarfare.providers.UserProvider;

public class BackgroundLocationService extends Service implements
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        LocationListener {

    public static final String TAG = BackgroundLocationService.class.getSimpleName();

    private int mStartId;
    private LocationRequest mLocationRequest;
    private GoogleApiClient mGoogleApiClient;

    private FirebaseUser mUser;
    private Location mMyLocation;
    private GeoFire mUsersGeoFire;
    private GeoQuery mUsersGeoQuery;

    @Override
    public void onCreate() {
        super.onCreate();

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();

        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(5000);
        mLocationRequest.setFastestInterval(1000);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        mUser = FirebaseAuth.getInstance().getCurrentUser();
        mUsersGeoFire = new GeoFire(FirebaseDatabase.getInstance().getReference().child("usersGeoFire"));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        mStartId = startId;
        mGoogleApiClient.connect();

        // We want this service to restart if the system disabled it because of low
        // memory at the very moment, so we return sticky.
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
        mGoogleApiClient.disconnect();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        boolean serviceEnabled = sharedPref.getBoolean(getString(R.string.pref_background_service_key), true);

        // If the user disabled the service in settings, or we have no location permission, we stop the service
        if (!serviceEnabled ||
                (ActivityCompat.checkSelfPermission(this,
                        android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this,
                        android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)) {

            // Just stop the service if there's no location permission
            stopSelf(mStartId);
            return;
        }
        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

    @Override
    public void onLocationChanged(Location loc) {
        GeoLocation geoLoc = new GeoLocation(loc.getLatitude(), loc.getLongitude());

        if (mMyLocation == null) {
            mUsersGeoQuery = mUsersGeoFire.queryAtLocation(geoLoc, 0.050);
            addUserGeoQueryEventListener();
        } else {
            mUsersGeoFire.setLocation(mUser.getUid(), geoLoc);
            mUsersGeoQuery.setCenter(geoLoc);
        }

        mMyLocation = loc;
    }

    private void addUserGeoQueryEventListener() {
        mUsersGeoQuery.addGeoQueryEventListener(new GeoQueryEventListener() {
            @Override
            public void onKeyEntered(String key, GeoLocation location) {
                UserProvider.getInstance().getUserById(key).addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        UserModel nearbyUser = dataSnapshot.getValue(UserModel.class);
                        NotificationCompat.Builder builder =
                                new NotificationCompat.Builder(BackgroundLocationService.this)
                                        .setSmallIcon(R.drawable.ic_sword)
                                        .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher))
                                        .setContentTitle("User " + nearbyUser.fullName + " is nearby!")
                                .setContentText("Touch to see what they're up to.");

                        Intent intent = new Intent(getApplicationContext(), MainActivity.class);
                        PendingIntent resultPendingIntent = PendingIntent.getActivity(getApplicationContext(), 0,
                                intent, PendingIntent.FLAG_UPDATE_CURRENT);

                        builder.setContentIntent(resultPendingIntent);
                        NotificationManager notificationManager =
                                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

                        notificationManager.notify(1, builder.build());
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {

                    }
                });
            }

            @Override
            public void onKeyExited(String key) {

            }

            @Override
            public void onKeyMoved(String key, GeoLocation location) {

            }

            @Override
            public void onGeoQueryReady() {

            }

            @Override
            public void onGeoQueryError(DatabaseError error) {

            }
        });
    }
}
