package rs.elfak.jajac.geowarfare;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

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

import rs.elfak.jajac.geowarfare.activities.MainActivity;
import rs.elfak.jajac.geowarfare.models.UserModel;
import rs.elfak.jajac.geowarfare.providers.UserProvider;

import static com.google.android.gms.common.api.GoogleApiClient.*;

public class BackgroundLocationService extends Service implements
        ConnectionCallbacks,
        OnConnectionFailedListener,
        LocationListener {

    public static final String TAG = BackgroundLocationService.class.getSimpleName();

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
        mGoogleApiClient.connect();
        return super.onStartCommand(intent, flags, startId);
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
        try {
            LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
        } catch (SecurityException e) {
            // We're not handling the exception here because we won't start the service
            // if we don't have location permission from the user
            e.printStackTrace();
        }
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
