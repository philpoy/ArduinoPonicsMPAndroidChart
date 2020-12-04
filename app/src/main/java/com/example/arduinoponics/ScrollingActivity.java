package com.example.arduinoponics;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;

import com.ekn.gruzer.gaugelibrary.ArcGauge;
import com.ekn.gruzer.gaugelibrary.Range;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.google.android.material.appbar.CollapsingToolbarLayout;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Set;
import java.util.UUID;

public class ScrollingActivity extends AppCompatActivity implements View.OnClickListener{
    //app items
    SwitchCompat btswitch;
    TextView date, serialM, graphdescription;
    Button selectDate;
    ArcGauge GaugeTemp,GaugeHum;
    LineChart lineChart;
    LineDataSet aTempLine = new LineDataSet(null,null);
    LineDataSet humLine = new LineDataSet(null,null);
    ArrayList<ILineDataSet>iLineDataSets = new ArrayList<>();
    LineData finalGraph;
    /*GraphView graph;
    private LineGraphSeries<DataPoint> aTemp,hum,wTemp,flowRate,pH;
    SimpleDateFormat sdformat = new SimpleDateFormat("HH:mm:ss");
    SimpleDateFormat rtformat = new SimpleDateFormat("HH:mm:ss");*/

    //for BtConnection
    private static final UUID PORT_UUID = UUID.fromString("00001101-0000-1000-8000-0805f9b34fb"); //SPP UUID service
    private ConnectedThread mConnectedThread;
    private BluetoothAdapter btAdapter = null;
    private BluetoothSocket btSocket =null;
    private static final String TAG = "ThIS";
    private BluetoothDevice devices;

    private StringBuilder sb = new StringBuilder(); //string builder for incoming bytes
    Handler msgH; //Incoming Bytes Handler (Message Processor)
    final int RECEIVED = 0; // Status for Handler

    @Override
    @SuppressLint("HandlerLeak")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scrolling);
        Toolbar toolbar =  findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        CollapsingToolbarLayout toolBarLayout =  findViewById(R.id.toolbar_layout);
        toolBarLayout.setTitle(getTitle());
        toolBarLayout.setContentDescription("Indoor Hydroponic Monitoring System");

        //initialize IDs
        serialM = findViewById(R.id.monitor);
        date = findViewById(R.id.date);
        selectDate = findViewById(R.id.sdate);
        selectDate.setEnabled(false);
        btswitch = findViewById(R.id.btSwitch);
        graphdescription = findViewById(R.id.graphdescription);
        btswitch.setClickable(false);

        lineChart = findViewById(R.id.lineChart);
        initializeChart();

        //inintialize Gauges
        initializeGaugeTemp();
        initializeGaugeHum();

        //HC-05 Connection:
        btswitch.setOnClickListener(this);
        btAdapter = BluetoothAdapter.getDefaultAdapter();
        checkBTstate();

        //select date button listener
        selectDate.setOnClickListener(this);

        //Message handler for received bytes
        msgH = new Handler() {
            public void handleMessage(Message msg) {
                if (msg.what == RECEIVED) {
                    String readMessage = (String) msg.obj;
                    sb.append(readMessage);                                                // append string using string builder
                    int endOfLineIndex = sb.indexOf("\r\n");                            // determine the end-of-line
                    if (endOfLineIndex > 0) {                                            // if end-of-line,
                        String appendedMsg = sb.substring(0, endOfLineIndex);               // extract string
                        sb.delete(0, sb.length());                                      // and clear

                        String splitBy = ","; //split CSV line
                        final String[] myUpdate = appendedMsg.split(splitBy);

                        if (myUpdate[0].equals("T1")) {//if first separated value equals T1
                            serialM.append(myUpdate[1]);//display 2nd value
                            /* wrap in try catch */
                            try {
                                date.setText(String.format("%s %s", myUpdate[2], myUpdate[3]));//display 3rd value and 4th value
                                GaugeTemp.setValue(Double.parseDouble(myUpdate[4]));
                                GaugeHum.setValue(Double.parseDouble(myUpdate[5]));

                                ArrayList<Entry> aTemp = new ArrayList<Entry>();
                                ArrayList<String> time = new ArrayList<>();
                                aTemp.add(new Entry(Float.parseFloat(myUpdate[4]),aTempLine.getEntryCount()));
                                aTempLine.setYVals(aTemp);
                                aTempLine.setLabel("Ambient Temp(C)");

                                ArrayList<Entry> hum = new ArrayList<Entry>();
                                hum.add(new Entry(Float.parseFloat(myUpdate[5]),humLine.getEntryCount()));
                                humLine.setYVals(hum);
                                humLine.setLabel("Humidity(%)");

                                time.add(humLine.getEntryCount()-1,myUpdate[3]);

                                aTempLine.setColor(Color.RED);
                                aTempLine.setAxisDependency(YAxis.AxisDependency.LEFT);
                                aTempLine.setLineWidth(3f);
                                aTempLine.setCubicIntensity(0.2f);
                                aTempLine.setValueTextSize(20f);

                                humLine.setColor(Color.CYAN);
                                humLine.setAxisDependency(YAxis.AxisDependency.LEFT);
                                humLine.setLineWidth(3f);
                                humLine.setCubicIntensity(0.2f);
                                humLine.setValueTextSize(20f);

                                iLineDataSets.clear();
                                iLineDataSets.add(aTempLine);
                                iLineDataSets.add(humLine);
                                finalGraph =new LineData(time, iLineDataSets);
                                //lineChart.clear();
                                lineChart.setData(finalGraph);

                                lineChart.moveViewToX(aTempLine.getEntryCount());
                                lineChart.moveViewToX(humLine.getEntryCount());
                                //finalGraph.notifyDataChanged();
                                lineChart.notifyDataSetChanged();
                                lineChart.invalidate();

                            } catch (Exception e) {//catch any errors
                                Log.e("update", e.toString());
                                Toast.makeText(getApplicationContext(), "RT error: " + e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                                date.setText("-");
                                GaugeTemp.setValue(0);
                                GaugeHum.setValue(0);
                            }
                        } /* end of type 1 msg if statement */

                        if (myUpdate[0].equals("T2")){ //if type 2 message is received
                            try {
                                graphdescription.setText(myUpdate[1]);
                                double mU3 = Double.parseDouble(myUpdate[3]);
                                double mU4 = Double.parseDouble(myUpdate[4]);
                                double mU5 = Double.parseDouble(myUpdate[5]);
                                double mU6 = Double.parseDouble(myUpdate[6]);
                                double mU7 = Double.parseDouble(myUpdate[7]);

                                serialM.append("Time: "+myUpdate[2]+", Ambient Temp(C): "+mU3+", Humidity: "+mU4+"%"+
                                        ", Water Temp(C): "+mU5+", Flow Rate(L/min): "+mU6+", pH: "+mU7+"\n");

                            }catch (Exception e){
                                Log.e("graph error:", e.toString());
                                Toast.makeText(getApplicationContext(), "graph error: " + e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                            } /* try catch end */
                        } /* T2 end */
                    }
                } /* end of if "handler state = RECEIVED" statement  */
            }
        };/* handler end */


    }


    private void initializeChart() {
        //customize chart
        lineChart.setDescription("");
        lineChart.setNoDataTextDescription("No Data as of the moment");

        //enable touch gesture
        lineChart.setTouchEnabled(true);

        //scaling and dragging
        lineChart.setDragEnabled(true);
        lineChart.setScaleEnabled(true);
        lineChart.setDrawGridBackground(true);
        lineChart.setPinchZoom(true);
    }

    private void initializeGaugeHum() {
        GaugeHum = findViewById(R.id.GaugeHum);
        Range rHum1 = new Range();
        rHum1.setColor(Color.parseColor("#FF0000"));//red
        rHum1.setFrom(0.0);
        rHum1.setTo(59.0);
        Range rHum2 = new Range();
        rHum2.setColor(Color.parseColor("#FF7F00"));//orange
        rHum2.setFrom(60.0);
        rHum2.setTo(79.0);
        Range rHum3 = new Range();
        rHum3.setColor(Color.parseColor("#FF00FF00"));//green
        rHum3.setFrom(80.0);
        rHum3.setTo(100.0);
        GaugeHum.addRange(rHum1);
        GaugeHum.addRange(rHum2);
        GaugeHum.addRange(rHum3);
        GaugeHum.setMaxValue(100);
        GaugeHum.setMinValue(10);
    }
    private void initializeGaugeTemp() {
        GaugeTemp = findViewById(R.id.GaugeTemp);
        Range rTemp = new Range();
        rTemp.setColor(Color.parseColor("#FF0000"));//red
        rTemp.setFrom(0.0);
        rTemp.setTo(12.0);
        Range rTemp2 = new Range();
        rTemp2.setColor(Color.parseColor("#FF0000"));//orange
        rTemp2.setFrom(13.0);
        rTemp2.setTo(16.0);
        Range rTemp3 = new Range();
        rTemp3.setColor(Color.parseColor("#FF00FF00"));//green
        rTemp3.setFrom(17.0);
        rTemp3.setTo(24.0);
        Range rTemp4 = new Range();
        rTemp4.setColor(Color.parseColor("#FF7F00"));//orange
        rTemp4.setFrom(25.0);
        rTemp4.setTo(29.0);
        Range rTemp5 = new Range();
        rTemp5.setColor(Color.parseColor("#FF0000"));//red
        rTemp5.setFrom(30.0);
        rTemp5.setTo(35.0);
        GaugeTemp.addRange(rTemp);
        GaugeTemp.addRange(rTemp2);
        GaugeTemp.addRange(rTemp3);
        GaugeTemp.addRange(rTemp4);
        GaugeTemp.addRange(rTemp5);
        GaugeTemp.setMaxValue(35);
        GaugeTemp.setMinValue(10);
    }

    //onClick event
    @Override
    public void onClick(View v) {
        if (v == selectDate){
            final Calendar c = Calendar.getInstance();
            int mYear = c.get(Calendar.YEAR);
            int mMonth = c.get(Calendar.MONTH);
            int mDay = c.get(Calendar.DAY_OF_MONTH);
            //create popup date picker
            DatePickerDialog datePickerDialog = new DatePickerDialog(this, new DatePickerDialog.OnDateSetListener() {
                @Override
                public void onDateSet(DatePicker view, int year, int monthOfYear, int dayOfMonth) {
                    try {
                        lineChart.removeAllViews();

                        if (dayOfMonth>10){
                            String sendDate = String.valueOf(year)+(monthOfYear+1)+(dayOfMonth)+".CSV";
                            serialM.append("\n"+sendDate+"\n");
                            mConnectedThread.write("2");//write command
                            mConnectedThread.write(sendDate);//write the selected date

                        }else {
                            String sendDate = String.valueOf(year)+(monthOfYear+1)+"0"+(dayOfMonth)+".CSV";
                            serialM.append("\n"+sendDate+"\n");
                            mConnectedThread.write("2");
                            mConnectedThread.write(sendDate);
                        }

                    }catch (Exception e){
                        serialM.append("null\n");
                        Log.e("date", e.toString());
                    }
                }
            }, mYear, mMonth, mDay);
            datePickerDialog.show();
        }

        if (v == btswitch){
            if (btswitch.isChecked()) {//if switched on
                if (BTFound()) {
                    try {
                        btSocket = createBluetoothSocket(devices);
                    } catch (Exception e) {
                        Toast.makeText(getApplicationContext(), "e createBtSocket: " + e.toString(), Toast.LENGTH_LONG).show();
                    }
                    //Establish BT Socket Conncection
                    try {
                        btSocket.connect();
                        selectDate.setEnabled(true);
                    } catch (Exception e) {
                        try {
                            btSocket.close();
                            selectDate.setEnabled(false);
                            btswitch.setChecked(false);
                        } catch (Exception e1) {
                            Toast.makeText(getApplicationContext(), "e Socket.close: " + e.toString(), Toast.LENGTH_LONG).show();
                        }
                        btswitch.setChecked(false);
                        Toast.makeText(getApplicationContext(), "e Socket.connect: " + e.toString(), Toast.LENGTH_LONG).show();
                    }
                    mConnectedThread = new ConnectedThread(btSocket);
                    mConnectedThread.start();
                    mConnectedThread.write("x");
                } else {
                    btswitch.setChecked(false);
                    Toast.makeText(getApplicationContext(), "Please reconnect", Toast.LENGTH_LONG).show();
                }
            }else {
                //pop-up alert alert dialog
                AlertDialog.Builder disco = new AlertDialog.Builder(ScrollingActivity.this);
                disco.setTitle("Disconnect bluetooth?").setCancelable(false)
                        .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                try {
                                    btSocket.close(); //close bluetooth socket with app
                                    selectDate.setEnabled(false);
                                } catch (Exception e) {
                                    Toast.makeText(getApplicationContext(),"e Socket.close: "+e.toString(),Toast.LENGTH_LONG).show();
                                    Log.d("socket:",e.toString());
                                }
                            }
                        })
                        .setNegativeButton("No", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                btswitch.setChecked(true);//switch remain on
                                dialog.cancel();
                            }
                        })
                        .show();
            }
        }
    }

    //for establishing connection
    private boolean BTFound() {
        boolean found = false;
        Set<BluetoothDevice> PairedDevices = btAdapter.getBondedDevices();
        for (BluetoothDevice bt_device : PairedDevices){
            String HC05_ADDRESS = "98:D3:61:FD:43:70";
            if (bt_device.getAddress().equals(HC05_ADDRESS)){
                devices = bt_device;
                found = true;
                break;
            }
        }
        return found;
    }
    private BluetoothSocket createBluetoothSocket(BluetoothDevice device) throws IOException {
        return device.createRfcommSocketToServiceRecord(PORT_UUID);
    }
    private void checkBTstate(){
        if(btAdapter == null){
            Toast.makeText(getApplicationContext(),"Enable Bluetooth first",Toast.LENGTH_LONG).show();
        }else {
            if (btAdapter.isEnabled()){
                Log.d(TAG, "...BT On");
            }else {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent,1);
            }
        }
    }

    //create new class for connect thread
    private class ConnectedThread extends Thread{
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        //creation of the connect thread
        public ConnectedThread(BluetoothSocket socket) {
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            try {
                //Create I/O streams for connection
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            }catch (Exception e){
                Toast.makeText(getApplicationContext(),"e getStream: "+e.toString(),Toast.LENGTH_LONG).show();
            }
            mmInStream=tmpIn;
            mmOutStream=tmpOut;
        }

        //onStrart of thread
        public void run(){
            byte[] buffer = new byte[256];
            int bytes;
            //keep looping to listen for recieved messages
            while(true){
                try {
                    bytes = mmInStream.read(buffer);//receive incoming bytes
                    String readMessage = new String(buffer,0,bytes); //convert to string
                    msgH.obtainMessage(RECEIVED,bytes,-1,readMessage).sendToTarget();//transfer received bytes to Handler
                }catch (IOException e){
                    break;
                }
            }
        }

        //write method
        public void write(String input){
            byte[] msgBuffer =input.getBytes();
            try{
                mmOutStream.write(msgBuffer);
            }catch (Exception e){
                Toast.makeText(getApplicationContext(),"Not connected: "+e.getLocalizedMessage(),Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_scrolling, menu);
        return true;
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    @Override
    public void onBackPressed() {
        AlertDialog.Builder exit = new AlertDialog.Builder(ScrollingActivity.this);

        exit.setMessage("Do you want to exit app?").setCancelable(false)
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        finishAffinity();
                        try {
                            btSocket.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                })
                .show();
    }


}