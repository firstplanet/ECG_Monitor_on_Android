package com.experiment.chickenjohn.materialdemo;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

/**
 * Below is the copyright information.
 * <p>
 * Copyright (C) 2016 chickenjohn
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * You may contact the author by email:
 * chickenjohn93@outlook.com
 * Created by chickenjohn on 2016/3/12.
 */

public class bluetoothManager {
    //variables for bluetooth
    private boolean CONNECT_STATE = false;
    private static BluetoothAdapter myBtAdapter = BluetoothAdapter.getDefaultAdapter();
    private static BluetoothDevice myBtDevice;
    private clientThread myBtClientThread;
    private BluetoothSocket myBtSocket;
    public String btAddress;
    public bluetoothReceiver btReceiver = new bluetoothReceiver();
    //variables for data
    private android.os.Handler uiRefreshHandler;
    private int receiveCounter = 0;
    private final int SHOWED_DATA = 0;
    private final int BEATRATE_DATA = 1;
    private final int PI_DATA = 2;
    private final int SPO2_DATA = 3;
    private int dataType = 0;

    //pass the handler into this class by using
    //constructor so that messages can be passed
    //to the main thread
    public bluetoothManager(android.os.Handler handler) {
        uiRefreshHandler = handler;
    }

    public void enableBluetooth() {
        if (!myBtAdapter.isEnabled()) {
            myBtAdapter.enable();
        }
        myBtAdapter.startDiscovery();
    }

    public void disableBluetooth() {
        if (myBtAdapter.isEnabled()) {
            myBtAdapter.disable();
        }
    }

    /* A bluetoothreceiver can receive bluetooth broadcasts
     * emitted by the system. However you should first
     * register bluetoothReceiver at the beginning. The register
     * method is regBtReceiver() at the bottom of this file.
     */
    public class bluetoothReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            //target device will be filtered by its name.
            //Change it if you need.
            String targetName = "HC-05";

            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice currentDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (currentDevice.getName().equalsIgnoreCase(targetName)) {
                    btAddress = currentDevice.getAddress();
                    myBtDevice = myBtAdapter.getRemoteDevice(btAddress);
                    Toast.makeText(context, "找到设备:" + myBtDevice.getName(), Toast.LENGTH_LONG).show();
                    CONNECT_STATE = true;
                    myBtClientThread = new clientThread();
                    myBtClientThread.start();
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                if (!isConnected()) {
                    Toast.makeText(context, "搜索结束，没有找到设备", Toast.LENGTH_LONG).show();
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(action)) {
                Toast.makeText(context, "搜索开始", Toast.LENGTH_LONG).show();
            } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
                CONNECT_STATE = false;
                Toast.makeText(context, "连接断开，正在重试连接", Toast.LENGTH_LONG).show();
                Message uiRefreshMessage = Message.obtain();
                uiRefreshMessage.what = 1;
                uiRefreshHandler.sendMessage(uiRefreshMessage);
            }
        }
    }

    private class clientThread extends Thread {
        public void run() {
            try {
                //you must cancelDiscovery() before trying to connect.
                myBtAdapter.cancelDiscovery();
                myBtSocket = myBtDevice.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"));
                myBtSocket.connect();
                new connectThread().start();
                Message uiRefreshMessage = Message.obtain();
                uiRefreshMessage.what = 0;
                uiRefreshHandler.sendMessage(uiRefreshMessage);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private class connectThread extends Thread {
        public void run() {
            InputStream mmInStream = null;
            try {
                mmInStream = myBtSocket.getInputStream();
            } catch (IOException e1) {
                e1.printStackTrace();
            }
            while (true) {
                try {
                    /* pop the instream stack if the stack
                     * contains two bytes or more.
                     * This guarantees that every time
                     * two bytes of the data will be poped.
                     */
                    if ((mmInStream.available()) >= 2) {
                        byte[] buf_data = new byte[2];
                        mmInStream.read(buf_data);
                        handleBtData(buf_data);
                    }
                } catch (IOException e) {
                    try {
                        mmInStream.close();
                    } catch (IOException e1) {
                        e1.printStackTrace();
                    }
                    break;
                }
            }
        }
    }

    /* Handle received data here
     * the data has 16 bits, first two bits figure
     * out the type of the data, the rest 14 bits
     * is the real data and the format is 14-bit
     * integer complement.
     */
    public void handleBtData(byte[] data) {
        int dataInInt;
        switch (0xc0 & data[1]) {
            case 0x00:
                dataType = SHOWED_DATA;
                break;
            case 0x40:
                dataType = BEATRATE_DATA;
                break;
            case 0x80:
                dataType = PI_DATA;
                break;
            case 0xc0:
                dataType = SPO2_DATA;
                break;
            default:
                break;
        }

        //decode the data, change the bytes into integer
        if(0x20 == (0x20 & data[1])){
            dataInInt = ((0x3f & (~data[1])) << 8) | (0xff & ((~data[0]) + 1));
            dataInInt = - dataInInt;
        } else {
            dataInInt = ((0x3f & ((int) data[1])) << 8) | (0xff & (int) data[0]);
        }
        Log.i("data",Integer.toString(dataInInt));

        //send message to the main thread to handle the decoded data.
        Message uiRefreshMessage = Message.obtain();
        switch (dataType) {
            case SHOWED_DATA:
                uiRefreshMessage.what = 2;
                uiRefreshMessage.arg1 = dataInInt;
                uiRefreshMessage.arg2 = receiveCounter;
                uiRefreshHandler.sendMessage(uiRefreshMessage);
                receiveCounter += 1;
                break;
            case BEATRATE_DATA:
                uiRefreshMessage.what = 3;
                uiRefreshMessage.arg1 = dataInInt;
                uiRefreshHandler.sendMessage(uiRefreshMessage);
                break;
            case PI_DATA:
                uiRefreshMessage.what = 4;
                uiRefreshMessage.arg1 = dataInInt;
                uiRefreshHandler.sendMessage(uiRefreshMessage);
                break;
            case SPO2_DATA:
                uiRefreshMessage.what = 5;
                uiRefreshMessage.arg1 = dataInInt;
                uiRefreshHandler.sendMessage(uiRefreshMessage);
                break;
            default:
                break;
        }

    }

    //Registration of Broadcast Receiver. Invoke this method
    //in the main thread when the app starts.
    public IntentFilter regBtReceiver() {
        IntentFilter bluetoothBroadcastFilter = new IntentFilter();
        bluetoothBroadcastFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        bluetoothBroadcastFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        bluetoothBroadcastFilter.addAction(BluetoothDevice.ACTION_FOUND);
        bluetoothBroadcastFilter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        return bluetoothBroadcastFilter;
    }

    public boolean isConnected() {
        return CONNECT_STATE;
    }
}
