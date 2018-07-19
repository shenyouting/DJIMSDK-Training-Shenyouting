package com.dji.training.task.djitraining.bis;

import android.app.Activity;
import android.graphics.SurfaceTexture;
import android.view.TextureView;
import android.view.View;

import com.dji.training.task.djitraining.DJIApplication;
import com.dji.training.task.djitraining.utils.ToastUtils;

import dji.common.camera.SettingsDefinitions;
import dji.common.camera.SystemState;
import dji.common.error.DJIError;
import dji.common.util.CommonCallbacks;
import dji.midware.usb.P3.UsbAccessoryService;
import dji.sdk.camera.Camera;
import dji.sdk.camera.VideoFeeder;
import dji.sdk.codec.DJICodecManager;

/**
 * 图传
 */
public class CameraBis {
    private Activity activity;
    private TextureView mVideoSurface = null;
    private VideoFeeder.VideoDataCallback mReceivedVideoDataCallBack = null;

    // Codec for video live view
    private DJICodecManager mCodecManager = null;

    public CameraBis(Activity activity, TextureView mVideoSurface) {
        this.mVideoSurface = mVideoSurface;
        this.activity=activity;
        init();
    }

    private   void init(){
        mVideoSurface.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                if (mCodecManager == null) {
                    mCodecManager = new DJICodecManager(DJIApplication.getInstance(), surface, width, height,UsbAccessoryService.VideoStreamSource.Camera);
                }
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                if (mCodecManager != null) {
                    mCodecManager.cleanSurface();
                }
                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {

            }
        });


        mReceivedVideoDataCallBack = new VideoFeeder.VideoDataCallback() {
            @Override
            public void onReceive(byte[] videoBuffer, int size) {
                if (mCodecManager != null) {
                    mCodecManager.sendDataToDecoder(videoBuffer, size, UsbAccessoryService.VideoStreamSource.Fpv.getIndex());
                }
            }
        };
    }

    /**
     * 开始预览
     */
    public  void startPreview(){
        VideoFeeder.getInstance().getSecondaryVideoFeed().setCallback(mReceivedVideoDataCallBack);
    }

    /**
     * 销毁
     */
    public  void destory(){
        VideoFeeder.getInstance().getSecondaryVideoFeed().setCallback(null);
        if (mCodecManager != null) {
            mCodecManager.destroyCodec();
        }
    }

    /**
     * 设置拍照模式
     */
    public  void setCameraMode(){
        final Camera camera = DJIApplication.getCameraInstance();
        if(camera==null){
            ToastUtils.showToast("获取相机失败");
            return;
        }
        camera.setShootPhotoMode(SettingsDefinitions.ShootPhotoMode.SINGLE, new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError djiError) {
                if(djiError==null){
                    ToastUtils.showToast("设置模式成功");
                }else{
                    ToastUtils.showToast("设置模式失败："+djiError.getDescription());
                }
            }
        });
    }

    /**
     * 拍照
     */
    public  void shootPhoto(){
        final Camera camera = DJIApplication.getCameraInstance();
        if(camera==null){
            ToastUtils.showToast("获取相机失败");
            return;
        }
        camera.startShootPhoto(new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError djiError) {
                if (djiError == null) {
                    ToastUtils.showToast("拍照成功");
                } else {
                    ToastUtils.showToast("拍照失败:"+djiError.getDescription());
                }
            }
        });
    }


}
