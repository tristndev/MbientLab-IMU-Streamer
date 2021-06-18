package com.mbientlab.metawear.tutorial.starter;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.NetworkOnMainThreadException;
import android.os.Process;
import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

public class UDPHandlerThread extends HandlerThread {
    private static final String TAG = "UPDHandlerThread";

    // Tasks
    public static final int SETUP_SOCKET = 0;
    public static final int UDP_SEND_SENSOR_VALS = 1;

    private Handler handler;

    private DatagramSocket ds;
    private InetAddress servAddr;
    private String rawIPaddress;
    private int servPort;

    private boolean socketSetupDone = false;

    DeviceSetupActivityFragment.UIHandler uiHandler;
    Context context;

    public UDPHandlerThread(String ipaddress, int port, DeviceSetupActivityFragment.UIHandler uiHandler, Context context) {
        super("UDPHandlerThread", Process.THREAD_PRIORITY_DEFAULT);

        this.uiHandler = uiHandler;
        this.context = context;

        servPort = port;
        rawIPaddress = ipaddress;

        Log.d(TAG, "New thread constructed. With ip " + ipaddress + " on port " + port);
    }

    private void setupSocket() {
        try {
            ds = new DatagramSocket();
            servAddr = InetAddress.getByName(rawIPaddress);
            uiHandler.sendMessage(Message.obtain(uiHandler,
                    DeviceSetupActivityFragment.UIHandler.UPDATE_STATE,
                    context.getResources().getString(R.string.socketStatusConnected)));
            socketSetupDone = true;
        } catch (SocketException e) {
            uiHandler.sendMessage(Message.obtain(uiHandler,
                    DeviceSetupActivityFragment.UIHandler.UPDATE_STATE,
                    context.getResources().getString(R.string.socketStatusError) + ": SocketException"));
            e.printStackTrace();
        } catch (UnknownHostException e) {
            uiHandler.sendMessage(Message.obtain(uiHandler,
                    DeviceSetupActivityFragment.UIHandler.UPDATE_STATE,
                    context.getResources().getString(R.string.socketStatusError) + ": UnknownHostException"));
            e.printStackTrace();
        }
    }

    @SuppressLint("HandlerLeak")
    @Override
    protected void onLooperPrepared() {
        setupHandler();
    }

    @SuppressLint("HandlerLeak")
    private void setupHandler() {
        handler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                switch(msg.what) {
                    case UDP_SEND_SENSOR_VALS:
                        //Log.d(TAG, "Received euler: " + msg.obj.toString());
                        DatagramPacket dp = new DatagramPacket(msg.obj.toString().getBytes(),
                                msg.obj.toString().length(), servAddr, servPort);
                        try {
                            if (socketSetupDone && ds != null) {
                                ds.send(dp);
                            } else {
                                setupSocket();
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        break;
                    case SETUP_SOCKET:
                        setupSocket();
                        break;
                }
            }
        };
    }

    public Handler getHandler() {
        return handler;
    }

    @Override
    public boolean quit() {
        if (ds != null) {
            ds.close();
        }

        uiHandler.handleMessage(Message.obtain(uiHandler,
                DeviceSetupActivityFragment.UIHandler.UPDATE_STATE,
                context.getResources().getString(R.string.socketStatusDisconnected)));

        return super.quit();
    }
}