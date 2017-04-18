package com.googlemapapitest;

import android.Manifest;
import android.content.DialogInterface;
import android.location.Location;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.places.PlaceLikelihood;
import com.google.android.gms.location.places.PlaceLikelihoodBuffer;
import com.google.android.gms.location.places.Places;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.maps.GeoApiContext;
import com.google.maps.GeocodingApi;
import com.google.maps.errors.ApiException;
import com.google.maps.model.GeocodingResult;
import com.gun0912.tedpermission.PermissionListener;
import com.gun0912.tedpermission.TedPermission;

import java.io.IOException;
import java.util.ArrayList;

import static com.google.android.gms.location.LocationServices.FusedLocationApi;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback,View.OnClickListener,
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener {
    private GoogleMap mMap;
    private GoogleApiClient mGoogleApiClient; // For get my location
    private GeoApiContext geoContext;
    private Marker locationMarker;


    private Button findLocationButton;
    private Button customLocationButton;
    private TextView latTextView;
    private TextView lngTextView;
    private TextView addrTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);


        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        // 다른 Sample에서는 GoogleApiClient의 Connection이 확인된 뒤 맵을 불러온다.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        findLocationButton = (Button)findViewById(R.id.find_my_location_button);
        customLocationButton = (Button)findViewById(R.id.custom_location_button);
        latTextView = (TextView)findViewById(R.id.lat_textview);
        lngTextView = (TextView)findViewById(R.id.lng_textview);
        addrTextView = (TextView)findViewById(R.id.addr_textview);
        geoContext = new GeoApiContext().setApiKey(getString(R.string.google_maps_key));

        //자신의 위치를 불러오기 위해 사용되는 GoogleAPI.
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .enableAutoManage(this, this)
                .addConnectionCallbacks(this /*ConnectionCallbacks*/)
                .addApi(LocationServices.API)
                .addApi(Places.GEO_DATA_API)
                .addApi(Places.PLACE_DETECTION_API)
                .build();
        mGoogleApiClient.connect();

        findLocationButton.setOnClickListener(this);
        customLocationButton.setOnClickListener(this);
    }


    /**
     * 버튼의 사용가능여부를 변경한다.
     * @param view        버튼 뷰
     * @param activate    동작할 방식. if true then activate else deactivate
     */
    private void locationButtonToggleUI(Button view, boolean activate){
        if(activate){
            view.setEnabled(true);
        } else{
            view.setEnabled(false);
        }
    }

    /**
     * 경,위도와 현재위치를 업데이트한다.
     * @param lat 경
     * @param lng 위
     */
    private void writeLocationInformation(final double lat, final double lng){
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    /*PendingResult<PlaceLikelihoodBuffer> result = Places.PlaceDetectionApi
                            .getCurrentPlace(mGoogleApiClient, null);
                    result.setResultCallback(new ResultCallback<PlaceLikelihoodBuffer>() {
                        @Override
                        public void onResult(PlaceLikelihoodBuffer likelyPlaces) {
                            for (PlaceLikelihood placeLikelihood : likelyPlaces) {
                                Log.i("ABC", String.format("Place '%s' , Address '%s' has likelihood: %g",
                                        placeLikelihood.getPlace().getName(),
                                        placeLikelihood.getPlace().getAddress(),
                                        placeLikelihood.getLikelihood()));
                            }
                            likelyPlaces.release();
                        }
                    });*/

                    final GeocodingResult[] results = GeocodingApi.reverseGeocode(geoContext, new com.google.maps.model.LatLng(lat, lng))
                            .await();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            latTextView.setText("Lat : " + lat);
                            lngTextView.setText("Lng : " + lng);
                            addrTextView.setText("addr : " + results[0].formattedAddress);
                        }
                    });
                } catch (ApiException e) {
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }


    private void moveCameraTo(LatLng location){
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(location, 12));
        if(locationMarker == null){
            locationMarker = mMap.addMarker(
                    new MarkerOptions().flat(true).draggable(false)
                            .position(location)
                            .title("Location"));
        }
        else locationMarker.setPosition(location);
    }

    /**
     * 현재위치를 찾는다. 현재위치가 검색되면 자동으로 카메라 이동, 정보업데이트가 이루어진다.
     */
    private void findMyLocation(){
        locationButtonToggleUI(findLocationButton,false);

        new TedPermission(this)
                .setPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                .setPermissionListener(new PermissionListener() {
                    /**
                     * 필요한 모든 권한이 허용된 경우 현재위치를 검색하는 동작을 진행한다.
                     */
                    @Override
                    public void onPermissionGranted() {
                        if(!mMap.isMyLocationEnabled())
                            mMap.setMyLocationEnabled(true);

                        Location myLocation = FusedLocationApi.getLastLocation(mGoogleApiClient);
                        moveCameraTo(new LatLng(myLocation.getLatitude(), myLocation.getLongitude()));
                        writeLocationInformation(myLocation.getLatitude(), myLocation.getLongitude());
                        locationButtonToggleUI(findLocationButton,true);
                    }

                    /**
                     * 권한이 거부된 경우 아래의 메세지를 띄운다.
                     */
                    @Override
                    public void onPermissionDenied(ArrayList<String> deniedPermissions) {
                        mMap.setMyLocationEnabled(false);
                        String permissions = "";
                        for (int i = 0 ; i < deniedPermissions.size() ; i++){
                            permissions += deniedPermissions.get(i);
                            if(i < deniedPermissions.size() - 1){
                                permissions += ",";
                            }
                        }
                        final String finalPermissions = permissions;
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(MapsActivity.this, "권한이 없습니다.\n필요권한 : " + finalPermissions, Toast.LENGTH_SHORT).show();
                                locationButtonToggleUI(findLocationButton,true);
                            }
                        });
                    }
                })
                .check();
    }

    /**
     * 사용자 입력의 경,위도로 이동한다.
     * 입력 후에는 자동으로 카메라이동, 정보업데이트가 이루어진다.
     */
    private void findCustomLocation() {
        LayoutInflater inflater = this.getLayoutInflater();
        final View dialogView = inflater.inflate(R.layout.dialog_customlocation, null);

        new AlertDialog.Builder(this)
                .setView(dialogView)
                .setPositiveButton("확인", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        EditText latEdit = (EditText) dialogView.findViewById(R.id.dialog_lat_edittext);
                        EditText lngEdit = (EditText) dialogView.findViewById(R.id.dialog_lng_edittext);

                        float customLat = Float.valueOf(latEdit.getText().toString());
                        float customLng = Float.valueOf(lngEdit.getText().toString());

                        moveCameraTo(new LatLng(customLat, customLng));
                        writeLocationInformation(customLat, customLng);
                    }
                })
                .setNegativeButton("취소", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        dialogInterface.cancel();
                    }
                })
                .create().show();
    }


    /****************************************************************************************
     * Interface Methods
     ****************************************************************************************/

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.moveCamera(CameraUpdateFactory.zoomBy(11));
    }

    //구글 API 접속이 완료되면 현재위치를 검색한다.
    //아마 실제 서비스에서는 onMapReady -> onConnected 순의 동기처리를 확실히 해야할 것이다.
    @Override
    public void onConnected(@Nullable Bundle bundle) {
        findMyLocation();
    }

    @Override
    public void onConnectionSuspended(int i) { }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) { }

    @Override
    public void onClick(View view) {
        switch(view.getId()){
            case R.id.find_my_location_button:
                findMyLocation();
                break;
            case R.id.custom_location_button:
                findCustomLocation();
                break;
        }
    }
}
