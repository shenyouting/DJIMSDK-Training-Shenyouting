package com.dji.training.task.djitraining;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;
import com.amap.api.maps2d.AMap;
import com.amap.api.maps2d.AMap.OnMapClickListener;
import com.amap.api.maps2d.CameraUpdate;
import com.amap.api.maps2d.CameraUpdateFactory;
import com.amap.api.maps2d.MapView;
import com.amap.api.maps2d.model.BitmapDescriptorFactory;
import com.amap.api.maps2d.model.LatLng;
import com.amap.api.maps2d.model.Marker;
import com.amap.api.maps2d.model.MarkerOptions;
import com.dji.training.task.djitraining.bis.CameraBis;
import com.dji.training.task.djitraining.utils.ToastUtils;

import dji.common.error.DJIError;
import dji.common.flightcontroller.FlightControllerState;
import dji.common.mission.waypoint.Waypoint;
import dji.common.mission.waypoint.WaypointAction;
import dji.common.mission.waypoint.WaypointActionType;
import dji.common.mission.waypoint.WaypointMission;
import dji.common.mission.waypoint.WaypointMissionFinishedAction;
import dji.common.mission.waypoint.WaypointMissionFlightPathMode;
import dji.common.mission.waypoint.WaypointMissionHeadingMode;
import dji.common.useraccount.UserAccountState;
import dji.common.util.CommonCallbacks;
import dji.sdk.base.BaseProduct;
import dji.sdk.flightcontroller.FlightController;
import dji.sdk.mission.waypoint.WaypointMissionOperator;
import dji.sdk.products.Aircraft;
import dji.sdk.sdkmanager.DJISDKManager;
import dji.sdk.useraccount.UserAccountManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import static com.dji.training.task.djitraining.utils.ToastUtils.setResultToToast;

public class MissionActivity extends FragmentActivity implements View.OnClickListener, OnMapClickListener {
    protected static final String TAG = "MissionActivity";
    private CameraBis cameraBis;
    private MapView mapView;
    private AMap aMap;

    private Button locate, edit, camera, photo, hide;
    private Button add, clear, config, upload, start, stop;
    private View buttonContainer;
    private View cameraButtonContainer;
    private TextView flightModeTV;
    private TextView gpsNumTV;
    private TextView vsTV;
    private TextView hsTV;
    private TextView altitudeTV;
    private TextureView textureView;
    private boolean isAdd = false;

    private double droneLocationLat = 181, droneLocationLng = 181;
    private final Map<Integer, Marker> mMarkers = new ConcurrentHashMap<Integer, Marker>();
    private Marker droneMarker = null;

    private float altitude = 100.0f;
    private float mSpeed = 10.0f;
    private float droneHeading;

    private List<Waypoint> waypointList = new ArrayList<>();

    public static WaypointMission.Builder waypointMissionBuilder;
    private FlightController mFlightController;
    private WaypointMissionOperator instance;
    private WaypointMissionFinishedAction mFinishedAction = WaypointMissionFinishedAction.NO_ACTION;
    private WaypointMissionHeadingMode mHeadingMode = WaypointMissionHeadingMode.AUTO;


    @Override
    protected void onResume() {
        super.onResume();
        initFlightController();
        BaseProduct product = DJIApplication.getProductInstance();
        if (product != null && product.isConnected()) {
            onProductConnectionChange(DJIApplication.ConnectivityChangeEvent.ProductConnected);
        }
        if(cameraBis !=null){
            cameraBis.startPreview();
        }
    }


    @Override
    protected void onDestroy() {
        EventBus.getDefault().unregister(this);
        cameraBis.destory();
        super.onDestroy();
    }

    private void initUI() {
        locate = (Button) findViewById(R.id.locate);
        add = (Button) findViewById(R.id.add);
        clear = (Button) findViewById(R.id.clear);
        config = (Button) findViewById(R.id.config);
        upload = (Button) findViewById(R.id.upload);
        start = (Button) findViewById(R.id.start);
        stop = (Button) findViewById(R.id.stop);
        edit = (Button) findViewById(R.id.edit);
        camera = (Button) findViewById(R.id.camera);

        buttonContainer = findViewById(R.id.side_button_container);
        cameraButtonContainer = findViewById(R.id.camera_button_container);
        edit.setOnClickListener(this);
        camera.setOnClickListener(this);
        locate.setOnClickListener(this);
        add.setOnClickListener(this);
        clear.setOnClickListener(this);
        config.setOnClickListener(this);
        upload.setOnClickListener(this);
        start.setOnClickListener(this);
        stop.setOnClickListener(this);

        findViewById(R.id.camera_mode).setOnClickListener(this);
        findViewById(R.id.add_drone_position).setOnClickListener(this);
        photo = findViewById(R.id.shoot_photo);
        photo.setOnClickListener(this);
        hide = findViewById(R.id.hide);
        hide.setOnClickListener(this);

        flightModeTV = (TextView)findViewById(R.id.flight_mode_show);
        gpsNumTV = (TextView)findViewById(R.id.gps_num);
        vsTV = (TextView)findViewById(R.id.vertical_speed_show);
        hsTV = (TextView)findViewById(R.id.horizontal_speed_show);
        altitudeTV = (TextView)findViewById(R.id.alti_show);
        textureView = (TextureView) findViewById(R.id.texture1);
        cameraBis =new CameraBis(this,textureView);
    }

    private void initMapView() {

        if (aMap == null) {
            aMap = mapView.getMap();
            aMap.setOnMapClickListener(this);// add the listener for click for amap object
        }

        LatLng shenzhen = new LatLng(22.5362, 113.9454);
        aMap.addMarker(new MarkerOptions().position(shenzhen).title("Marker in Shenzhen"));
        aMap.moveCamera(CameraUpdateFactory.newLatLng(shenzhen));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_mission);
        EventBus.getDefault().register(this);
        mapView = (MapView) findViewById(R.id.map);
        mapView.onCreate(savedInstanceState);
        initMapView();
        initUI();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onProductConnectionChange(DJIApplication.ConnectivityChangeEvent event) {
        if (event == DJIApplication.ConnectivityChangeEvent.CameraConnect) {
            initFlightController();
        } else if (event == DJIApplication.ConnectivityChangeEvent.ProductConnected) {
            loginAccount();
        } else if (event == DJIApplication.ConnectivityChangeEvent.CameraDisconnect) {
            ToastUtils.showToast("Camera Disconnected");
        } else if (event == DJIApplication.ConnectivityChangeEvent.ProductDisconnected) {
            ToastUtils.showToast("Product Disconnected");
        }
    }

    private void loginAccount() {
        //todo 调用MSDK登录逻辑
        UserAccountManager.getInstance().logIntoDJIUserAccount(this,
                new CommonCallbacks.CompletionCallbackWith<UserAccountState>() {
                    @Override
                    public void onSuccess(final UserAccountState userAccountState) {
                        com.blankj.utilcode.util.ToastUtils.showLong("Login Success");
                    }
                    @Override
                    public void onFailure(DJIError error) {
                        com.blankj.utilcode.util.ToastUtils.showLong("Login Error:"
                                + error.getDescription());
                    }
                });
    }

    private void initFlightController() {
        //todo 初始化FlightController模块
        BaseProduct product = DJIApplication.getProductInstance();
        if (product != null && product.isConnected()) {
            if (product instanceof Aircraft) {
                mFlightController = ((Aircraft) product).getFlightController();
            }
        }
        if (mFlightController != null) {
            mFlightController.setStateCallback(
                    new FlightControllerState.Callback() {
                        @Override
                        public void onUpdate(FlightControllerState djiFlightControllerCurrentState) {
                            droneLocationLat = djiFlightControllerCurrentState.getAircraftLocation().getLatitude();
                            droneLocationLng = djiFlightControllerCurrentState.getAircraftLocation().getLongitude();
                            droneHeading= mFlightController.getCompass().getHeading();
                             updateDroneLocation();
                             updateOsd(djiFlightControllerCurrentState);
                        }
                    });
        }
    }


    /**
     * 更新OSD数据
     */
    private void updateOsd(FlightControllerState djiFlightControllerCurrentState){
        String modeName= djiFlightControllerCurrentState.getFlightMode().name();
        String gpsLev=djiFlightControllerCurrentState.getGPSSignalLevel().name();
        float x=djiFlightControllerCurrentState.getVelocityX();
        float y=djiFlightControllerCurrentState.getVelocityY();
        float h=djiFlightControllerCurrentState.getAircraftLocation().getAltitude();
        ToastUtils.setResultToText(flightModeTV,String.format( getString(R.string.mode_name),modeName));
        ToastUtils.setResultToText(gpsNumTV,String.format( getString(R.string.gps_lev),gpsLev));
        ToastUtils.setResultToText(vsTV,String.format( getString(R.string.fl_vs),x));
        ToastUtils.setResultToText(hsTV,String.format( getString(R.string.fl_hs),y));
        ToastUtils.setResultToText(altitudeTV,String.format( getString(R.string.fl_alt),h));
    }

    @Override
    public void onMapClick(LatLng point) {
        if (isAdd == true) {
            Log.d(TAG, "mark new wp longitude:" + point.longitude + " latitude:"+point.latitude);
            markWaypoint(point);
            Waypoint mWaypoint = new Waypoint(point.latitude, point.longitude, altitude);
            //Add Waypoints to Waypoint arraylist;
            if(waypointMissionBuilder==null){
                waypointMissionBuilder = new WaypointMission.Builder();
            }
            mWaypoint.gimbalPitch=-90;
            WaypointAction action=new WaypointAction(WaypointActionType.START_TAKE_PHOTO,0);
            mWaypoint.addAction(action);

            waypointList.add(mWaypoint);
            waypointMissionBuilder.waypointList(waypointList).waypointCount(waypointList.size());
            //todo 建一个waypoint点，添加到waypointMissionBuilder，思考，如何设置经纬度，Action以及gimbal pitch角度
        } else {
            setResultToToast("Cannot Add Waypoint");
        }
    }

    public static boolean checkGpsCoordination(double latitude, double longitude) {
        return (latitude > -90 && latitude < 90 && longitude > -180 && longitude < 180) && (latitude != 0f
            && longitude != 0f);
    }


    // Update the drone location based on states from MCU.
    private void updateDroneLocation() {
        LatLng pos = new LatLng(droneLocationLat, droneLocationLng);
        //Create MarkerOptions object
        final MarkerOptions markerOptions = new MarkerOptions();
        markerOptions.position(pos);
        markerOptions.icon(BitmapDescriptorFactory.fromResource(R.drawable.aircraft));
        markerOptions.anchor(0.5f, 0.5f);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (droneMarker != null) {
                    droneMarker.remove();
                }
                if (checkGpsCoordination(droneLocationLat, droneLocationLng)) {
                    droneMarker = aMap.addMarker(markerOptions);
                    droneMarker.setRotateAngle(droneHeading * -1.0f);
                }
            }
        });
    }

    private void markWaypoint(LatLng point) {
        //Create MarkerOptions object
        MarkerOptions markerOptions = new MarkerOptions();
        markerOptions.position(point);
        markerOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE));
        Marker marker = aMap.addMarker(markerOptions);
        mMarkers.put(mMarkers.size(), marker);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.locate: {
                updateDroneLocation();
                cameraUpdate(); // Locate the drone's place
                break;
            }
            case R.id.add: {
                enableDisableAdd();
                break;
            }
            case R.id.add_drone_position: {
                LatLng pos = new LatLng(droneLocationLat, droneLocationLng);
                onMapClick(pos);
                break;
            }
            case R.id.clear: {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        aMap.clear();
                    }
                });
                updateDroneLocation();
                break;
            }
            case R.id.config: {
                showSettingDialog();
                break;
            }
            case R.id.upload: {
                uploadWayPointMission();
                //todo 调用MSDK上传航点任务接口
                break;
            }
            case R.id.start: {
                startWaypointMission();
                //todo 调用MSDK开始航点任务接口
                break;
            }
            case R.id.stop: {
                stopWaypointMission();
                //todo 调用MSDK停止航点任务接口
                break;
            }
            case R.id.edit:
                String content = edit.getText().toString();
                if (content.contains("Edit")) {
                    edit.setText("Back");
                    buttonContainer.setVisibility(View.VISIBLE);
                    cameraButtonContainer.setVisibility(View.GONE);
                    camera.setText("Camera");
                } else {
                    edit.setText("Edit");
                    buttonContainer.setVisibility(View.GONE);
                }
                break;
            case R.id.camera:
                String cameraContent = camera.getText().toString();
                if (cameraContent.contains("Camera")) {
                    camera.setText("Back");
                    cameraButtonContainer.setVisibility(View.VISIBLE);
                    buttonContainer.setVisibility(View.GONE);
                    edit.setText("Edit");
                } else {
                    camera.setText("Camera");
                    cameraButtonContainer.setVisibility(View.GONE);
                }
                break;
            case R.id.camera_mode:
                cameraBis.setCameraMode();
                break;
            case R.id.shoot_photo:
                cameraBis.shootPhoto();
                break;
            case R.id.hide:
                String hideButtonContent = hide.getText().toString();
                if (hideButtonContent.contains("Hide")) {
                    hide.setText("Show");
                    textureView.setVisibility(View.GONE);
                } else {
                    textureView.setVisibility(View.VISIBLE);
                    hide.setText("Hide");
                }
                break;
            default:
                break;
        }
    }

    private void cameraUpdate() {
        LatLng pos = new LatLng(droneLocationLat, droneLocationLng);
        if(checkGpsCoordination(droneLocationLat,droneLocationLng)){
            float zoomlevel = (float) 18.0;
            CameraUpdate cu = CameraUpdateFactory.newLatLngZoom(pos, zoomlevel);
            aMap.moveCamera(cu);
        }else{
            ToastUtils.showToast("无法定位");
        }
    }

    private void enableDisableAdd() {
        if (isAdd == false) {
            isAdd = true;
            add.setText("Exit");
        } else {
            isAdd = false;
            add.setText("Add");
        }
    }

    private void showSettingDialog() {
        LinearLayout wayPointSettings =
            (LinearLayout) getLayoutInflater().inflate(R.layout.dialog_waypointsetting, null);

        final TextView wpAltitudeTV = (TextView) wayPointSettings.findViewById(R.id.altitude);
        RadioGroup speedRG = (RadioGroup) wayPointSettings.findViewById(R.id.speed);
        RadioGroup actionAfterFinishedRG = (RadioGroup) wayPointSettings.findViewById(R.id.actionAfterFinished);
        RadioGroup headingRG = (RadioGroup) wayPointSettings.findViewById(R.id.heading);

        speedRG.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if (checkedId == R.id.lowSpeed) {
                    mSpeed = 3.0f;
                } else if (checkedId == R.id.MidSpeed) {
                    mSpeed = 5.0f;
                } else if (checkedId == R.id.HighSpeed) {
                    mSpeed = 10.0f;
                }
            }
        });

        actionAfterFinishedRG.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                Log.d(TAG, "Select finish action");
                if (checkedId == R.id.finishNone) {
                    mFinishedAction = WaypointMissionFinishedAction.NO_ACTION;
                } else if (checkedId == R.id.finishGoHome) {
                    mFinishedAction = WaypointMissionFinishedAction.GO_HOME;
                } else if (checkedId == R.id.finishAutoLanding) {
                    mFinishedAction = WaypointMissionFinishedAction.AUTO_LAND;
                } else if (checkedId == R.id.finishToFirst) {
                    mFinishedAction = WaypointMissionFinishedAction.GO_FIRST_WAYPOINT;
                }
            }
        });

        headingRG.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                Log.d(TAG, "Select heading");

                if (checkedId == R.id.headingNext) {
                    mHeadingMode = WaypointMissionHeadingMode.AUTO;
                } else if (checkedId == R.id.headingInitDirec) {
                    mHeadingMode = WaypointMissionHeadingMode.USING_INITIAL_DIRECTION;
                } else if (checkedId == R.id.headingRC) {
                    mHeadingMode = WaypointMissionHeadingMode.CONTROL_BY_REMOTE_CONTROLLER;
                } else if (checkedId == R.id.headingWP) {
                    mHeadingMode = WaypointMissionHeadingMode.USING_WAYPOINT_HEADING;
                }
            }
        });

        new AlertDialog.Builder(this).setTitle("")
                                     .setView(wayPointSettings)
                                     .setPositiveButton("Finish", new DialogInterface.OnClickListener() {
                                         @Override
                                         public void onClick(DialogInterface dialog, int id) {
                                             String altitudeString = wpAltitudeTV.getText().toString();
                                             altitude = Integer.parseInt(nulltoIntegerDefalt(altitudeString));
                                             Log.e(TAG, "altitude " + altitude);
                                             Log.e(TAG, "speed " + mSpeed);
                                             Log.e(TAG, "mFinishedAction " + mFinishedAction);
                                             Log.e(TAG, "mHeadingMode " + mHeadingMode);
                                             configWayPointMission();
                                         }
                                     })
                                     .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {

                                         @Override
                                         public void onClick(DialogInterface dialog, int id) {
                                             dialog.cancel();
                                         }
                                     })
                                     .create()
                                     .show();
    }

    private String nulltoIntegerDefalt(String value) {
        if (!isIntValue(value)) {
            value = "0";
        }
        return value;
    }

    private boolean isIntValue(String val) {
        try {
            val = val.replace(" ", "");
            Integer.parseInt(val);
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    private void configWayPointMission() {
        //todo 配置航点任务
        if (waypointMissionBuilder == null){
            waypointMissionBuilder = new WaypointMission.Builder().finishedAction(mFinishedAction)
                    .headingMode(mHeadingMode)
                    .autoFlightSpeed(mSpeed)
                    .maxFlightSpeed(mSpeed)
                    .flightPathMode(WaypointMissionFlightPathMode.NORMAL);
        }else{
            waypointMissionBuilder.finishedAction(mFinishedAction)
                    .headingMode(mHeadingMode)
                    .autoFlightSpeed(mSpeed)
                    .maxFlightSpeed(mSpeed)
                    .flightPathMode(WaypointMissionFlightPathMode.NORMAL);
        }
        if (waypointMissionBuilder.getWaypointList().size() > 0){
            for (int i=0; i< waypointMissionBuilder.getWaypointList().size(); i++){
                waypointMissionBuilder.getWaypointList().get(i).altitude = altitude;
            }
            setResultToToast("Set Waypoint attitude successfully");
        }
        DJIError error = getWaypointMissionOperator().loadMission(waypointMissionBuilder.build());
        if (error == null) {
            setResultToToast("loadWaypoint succeeded");
        } else {
            setResultToToast("loadWaypoint failed " + error.getDescription());
        }
    }
    public WaypointMissionOperator getWaypointMissionOperator(){
        if (instance == null){
            instance = DJISDKManager.getInstance().getMissionControl().getWaypointMissionOperator();
        }
        return instance;
    }

    private void uploadWayPointMission(){
        getWaypointMissionOperator().uploadMission(new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError error) {
                if (error == null) {
                    setResultToToast("Mission upload successfully!");
                } else {
                    setResultToToast("Mission upload failed, error: " + error.getDescription() + " retrying...");
                    getWaypointMissionOperator().retryUploadMission(null);
                }
            }
        });
    }
    private void startWaypointMission(){
        getWaypointMissionOperator().startMission(new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError error) {
                setResultToToast("Mission Start: " + (error == null ? "Successfully" : error.getDescription()));
            }
        });
    }
    private void stopWaypointMission(){
        getWaypointMissionOperator().stopMission(new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError error){
                setResultToToast("Mission Stop: " + (error == null ? "Successfully" : error.getDescription()));
            }
        });
    }
}
