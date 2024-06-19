package com.example.ecoswitch;

//

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.app.Activity;

import java.util.UUID;


import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
//
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.annotation.RequiresApi;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {
    private static final UUID BTMODULEUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private Button btnIngresar;

    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothDevice device;
    private BluetoothSocket btSocket = null;
    private String address;
    Handler BluetoothIn;
    final private int handlerState = 0;
    private ConnectedThread mConnectedThread;
    private final StringBuilder recDataString = new StringBuilder();

    public static final int MULTIPLE_PERMISSIONS = 10;

    //se crea un array de String con los permisos a solicitar en tiempo de ejecucion
    //Esto se debe realizar a partir de Android 6.0, ya que con verdiones anteriores
    //con solo solicitarlos en el Manifest es suficiente
    String[] permissions = new String[]{
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        btnIngresar = (Button) findViewById(R.id.button2);

        // Obtén el adaptador Bluetooth
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        //defino el Handler de comunicacion entre el hilo Principal  el secundario.
        //El hilo secundario va a mostrar informacion al layout atraves utilizando indeirectamente a este handler
        BluetoothIn = Handler_Msg_Hilo_Principal();

        btnIngresar.setOnClickListener(btEmparejar);

    }

    private final View.OnClickListener btEmparejar = new View.OnClickListener() {
        @SuppressLint("MissingPermission")
        @Override
        public void onClick(View v) {
            goToSecondActivity(v);
            /*new Thread(new Runnable() {
                public void run() {
                    goToSecondActivity(v);
                }
            }).start();*/ // traté de ejecutarlo en un hilo diferente pero tampoco funciona
        }
    };

    public void goToSecondActivity(View view) {

        address = "b4:05:a1:e3:d3:f2"; // MAC address del embebido: "00:22:06:01:9C:DA", ahora está conectado un celu

        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            // El adaptador Bluetooth no está disponible o no está activado
            Toast.makeText(getApplicationContext(), "Bluetooth no está disponible", Toast.LENGTH_SHORT).show();
            return;
        }

        // Obtén el dispositivo Bluetooth por su MAC address
        device = mBluetoothAdapter.getRemoteDevice(address);

        // Intento conectarme al dispositivo
        boolean connected = connectToDevice(device);

        if (connected) {
            Toast.makeText(getApplicationContext(), "Conectado al dispositivo Bluetooth", Toast.LENGTH_SHORT).show();

            // Navegamos a otra pantalla
            Intent intent = new Intent(this, PantallaConectado.class);
            startActivity(intent);
        } else {
            Toast.makeText(getApplicationContext(), "Error al conectar al dispositivo Bluetooth", Toast.LENGTH_SHORT).show();
        }
    }

    @SuppressLint("MissingPermission")
    private boolean connectToDevice(BluetoothDevice device) {

        try {

            btSocket = createBtSocket(device);

        } catch (IOException e) {

            Toast.makeText(getApplicationContext(), "La creación del socket falló", Toast.LENGTH_SHORT).show();
            return false;
        }

        try {
            
            btSocket.connect();
            return true;
        }
        catch (IOException e)
        {
            try
            {
                btSocket.close();
                return false;
            }
            catch (IOException e2)
            {
                return false;
            }
        }
    }

    //Metodo que crea el socket bluethoot
    @SuppressLint("MissingPermission")
    private BluetoothSocket createBtSocket(BluetoothDevice device) throws IOException {

        return  device.createRfcommSocketToServiceRecord(BTMODULEUUID);
    }

    @SuppressLint("MissingPermission")
    @Override
    //Cada vez que se detecta el evento OnResume se establece la comunicacion con el HC05, creando un
    //socketBluethoot
    public void onResume() {
        super.onResume();

        //Obtengo el parametro, aplicando un Bundle, que me indica la Mac Adress del HC05
        //Intent intent=getIntent();
        //Bundle extras=intent.getExtras();

        //String address = extras.getString("Direccion_Bluethoot");
        address = "b4:05:a1:e3:d3:f2"; // MAC address del embebido: "00:22:06:01:9C:DA", ahora está conectado un celu

        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);

        //se realiza la conexion del Bluethoot crea y se conectandose a atraves de un socket
        try
        {
            btSocket = createBtSocket(device);
        }
        catch (IOException e)
        {
            Toast.makeText(getApplicationContext(), "Error al conectar al dispositivo Bluetooth", Toast.LENGTH_SHORT).show();
        }
        // Establish the Bluetooth socket connection.
        try
        {
            btSocket.connect();
        }
        catch (IOException e)
        {
            try
            {
                btSocket.close();
            }
            catch (IOException e2)
            {
                //insert code to deal with this
            }
        }

        //Una establecida la conexion con el Hc05 se crea el hilo secundario, el cual va a recibir
        // los datos de Arduino atraves del bluethoot
        mConnectedThread = new ConnectedThread(btSocket);
        mConnectedThread.start();

        //I send a character when resuming.beginning transmission to check device is connected
        //If it is not an exception will be thrown in the write method and finish() will be called
        //mConnectedThread.write("x");
    }

    //******************************************** Hilo secundario del Activity**************************************
    //*************************************** recibe los datos enviados por el HC05**********************************

    private class ConnectedThread extends Thread
    {
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        //Constructor de la clase del hilo secundario
        public ConnectedThread(BluetoothSocket socket)
        {
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            try
            {
                //Create I/O streams for connection
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) { }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        //metodo run del hilo, que va a entrar en una espera activa para recibir los msjs del HC05
        public void run()
        {
            byte[] buffer = new byte[256];
            int bytes;

            //el hilo secundario se queda esperando mensajes del HC05
            while (true)
            {
                try
                {
                    //se leen los datos del Bluethoot
                    bytes = mmInStream.read(buffer);
                    String readMessage = new String(buffer, 0, bytes);

                    //se muestran en el layout de la activity, utilizando el handler del hilo
                    // principal antes mencionado
                    BluetoothIn.obtainMessage(handlerState, bytes, -1, readMessage).sendToTarget();
                } catch (IOException e) {
                    break;
                }
            }
        }

        public void write(String input) {
            byte[] msgBuffer = input.getBytes();           //converts entered String into bytes
            try {
                mmOutStream.write(msgBuffer);                //write bytes over BT connection via outstream
            } catch (IOException e) {
                //if you cannot write, close the application
                Toast.makeText(getApplicationContext(), "La conexion fallo", Toast.LENGTH_SHORT).show();
                finish();

            }
        }
    }

    //Handler que sirve que permite mostrar datos en el Layout al hilo secundario
    private Handler Handler_Msg_Hilo_Principal ()
    {
        return  new Handler(Looper.getMainLooper()) {
            public void handleMessage(@NonNull android.os.Message msg)
            {
                //si se recibio un msj del hilo secundario
                if (msg.what == handlerState)
                {
                    //voy concatenando el msj
                    String readMessage = (String) msg.obj;
                    recDataString.append(readMessage);
                    int endOfLineIndex = recDataString.indexOf("\r\n");

                    //cuando recibo toda una linea la muestro en el layout
                    if (endOfLineIndex > 0)
                    {
                        //String dataInPrint = recDataString.substring(0, endOfLineIndex);
                        recDataString.delete(0, recDataString.length());
                    }
                }
            }
        };

    }
}