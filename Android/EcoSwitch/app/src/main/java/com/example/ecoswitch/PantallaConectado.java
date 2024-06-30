package com.example.ecoswitch;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;


//******************************************** Hilo principal del Activity**************************************
public class PantallaConectado extends AppCompatActivity implements SensorEventListener
{
    public enum Estado {
        ESTADO_CONECTADO,
        ESTADO_DESCONECTADO,
        ESTADO_SUSPENDIDO,
        ESTADO_CONSUMO_DESPERDICIADO,
        ESTADO_DETECTANDO_INACTIVIDAD,
        ESTADO_AUSENCIA,
        ESTADO_CONSUMO_FANTASMA
    }

    Button btnSuspender;
    Button btnActivar;
    TextView txtEstadoEcoSwitch;
    TextView txtEstadoDispositivo;
    TextView txtDescripcionExtra;

    ImageView ImgPrendido;
    ImageView ImgApagado;
    ImageView ImgFantasma;
    ImageView ImgLogo;

    Handler bluetoothIn;
    final int handlerState = 0;

    private BluetoothAdapter btAdapter = null;
    private BluetoothSocket btSocket = null;
    private final StringBuilder recDataString = new StringBuilder();

    private ConnectedThread mConnectedThread;

    private static final UUID BTMODULEUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    // MAC address del HC05
    private static String address = "00:22:06:01:9C:DA";

    private static final String CHANNEL_ID = "12345";

    private SensorManager sensorManager;
    private Sensor accelerometer;
    private static final float SHAKE_THRESHOLD = 12.0f;
    private static final int MIN_TIME_BETWEEN_SHAKES_MILLISECS = 1000;
    private long lastShakeTime;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pantalla_conectado);

        btnSuspender=findViewById(R.id.button3);
        btnActivar=findViewById(R.id.button);
        txtEstadoDispositivo=findViewById(R.id.textView3);
        txtEstadoEcoSwitch=findViewById(R.id.textView2);
        txtDescripcionExtra=findViewById(R.id.textView4);
        ImgPrendido =findViewById(R.id.imageView2);
        ImgApagado =findViewById(R.id.imageView3);
        ImgFantasma =findViewById(R.id.imageView4);
        ImgLogo = findViewById(R.id.imageView5);

        ImgFantasma.setVisibility(View.GONE);
        ImgPrendido.setVisibility(View.GONE);
        ImgApagado.setVisibility(View.GONE);
        ImgLogo.setVisibility(View.VISIBLE);
        txtEstadoEcoSwitch.setVisibility(View.GONE);
        txtEstadoDispositivo.setVisibility(View.GONE);
        txtDescripcionExtra.setVisibility(View.GONE);


        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        //obtengo el adaptador del bluetooth
        btAdapter = BluetoothAdapter.getDefaultAdapter();

        //defino el Handler de comunicacion entre el hilo Principal y el secundario.
        //El hilo secundario va a mostrar informacion al layout utilizando indirectamente a este handler
        bluetoothIn = Handler_Msg_Hilo_Principal();

        //defino los handlers para los botones Activar y Suspender
        btnSuspender.setOnClickListener(btnSuspenderListener);
        btnActivar.setOnClickListener(btnActivarListener);

        //sensor
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }

        createNotificationChannel();

    }
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "MiCanal";
            String description = "Canal para notificaciones";
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }
    private void sendNotification(String title, String content) {
        Intent intent = new Intent(this, PantallaConectado.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.enchufe__1_) //icono
                .setContentTitle(title)
                .setContentText(content)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true); // Elimina la notificación cuando se pulsa

        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(1, builder.build());
    }

    @SuppressLint("MissingPermission")
    @Override
    //Cada vez que se detecta el evento OnResume se establece la comunicacion con el HC05, creando un socketBluethoot
    public void onResume() {
        super.onResume();

        BluetoothDevice device = btAdapter.getRemoteDevice(address);

        try
        {
            btSocket = createBluetoothSocket(device);
        }
        catch (IOException e)
        {
            showToast("La creación del Socket falló");
        }
        try
        {
            btSocket.connect();
            showToast("BlueTooth Conectado");
        }
        catch (IOException e)
        {
            try
            {
                showToast("Error al conectar BlueTooth");
                btSocket.close();
            }
            catch (IOException e2)
            {
            }
        }

        //Una vez establecida la conexion con el HC05 se crea el hilo secundario, el cual va a recibir los datos de Arduino a traves del bluetooth
        mConnectedThread = new ConnectedThread(btSocket);
        mConnectedThread.start();

        //enviamos un caracter para chequear que esté conectado
        mConnectedThread.write("x");

        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        }

    }


    @Override
    //Cuando se ejecuta el evento onPause se cierra el socket Bluetooth
    public void onPause()
    {
        super.onPause();
        try
        {
            btSocket.close();
        } catch (IOException e2) {
        }

        if (accelerometer != null) {
            sensorManager.unregisterListener(this);
        }
    }

    //Metodo que crea el socket Bluetooth
    @SuppressLint("MissingPermission")
    private BluetoothSocket createBluetoothSocket(BluetoothDevice device) throws IOException {

        return  device.createRfcommSocketToServiceRecord(BTMODULEUUID);
    }

    //Handler que permite mostrar datos en el Layout al hilo secundario
    private Handler Handler_Msg_Hilo_Principal ()
    {
        return  new Handler(Looper.getMainLooper()) {
            public void handleMessage(@NonNull android.os.Message msg)
            {
                //si se recibio un mensaje del hilo secundario
                if (msg.what == handlerState)
                {
                    //voy concatenando el mensaje
                    String readMessage = (String) msg.obj;
                    recDataString.append(readMessage);
                    int endOfLineIndex = recDataString.indexOf("\n");

                    //cuando recibo toda una linea la muestro en el layout
                    if (endOfLineIndex > 0)
                    {
                        String dataInPrint = recDataString.substring(0, endOfLineIndex);

                        procesarEstado(dataInPrint);

                        recDataString.delete(0, recDataString.length());
                    }
                }
            }
        };

    }

    private void procesarEstado(String estado) {
        Estado estadoNumero = Estado.valueOf(estado);
        ImgLogo.setVisibility(View.GONE);
        txtEstadoEcoSwitch.setVisibility(View.VISIBLE);
        txtEstadoDispositivo.setVisibility(View.VISIBLE);

        switch (estadoNumero) {
            case ESTADO_DESCONECTADO:
                btnSuspender.setVisibility(View.VISIBLE); //se puede suspender
                btnActivar.setVisibility(View.VISIBLE); //se puede activar

                txtEstadoEcoSwitch.setText(R.string.estado_desconectado);
                txtEstadoDispositivo.setText(R.string.dispositivo_apagado);
                txtDescripcionExtra.setVisibility(View.GONE);

                ImgApagado.setVisibility(View.VISIBLE);
                ImgPrendido.setVisibility(View.GONE);
                ImgFantasma.setVisibility(View.GONE);
                break;

            case ESTADO_DETECTANDO_INACTIVIDAD:
                btnSuspender.setVisibility(View.VISIBLE); //se puede suspender
                btnActivar.setVisibility(View.GONE); //no se puede activar porque esta activo

                txtEstadoEcoSwitch.setText(R.string.detectando_inactividad);
                txtEstadoDispositivo.setText(R.string.dispositivo_apagado);
                txtDescripcionExtra.setVisibility(View.VISIBLE);
                txtDescripcionExtra.setText(R.string.descripcion_extra_detectando_inactividad);

                ImgApagado.setVisibility(View.GONE);
                ImgPrendido.setVisibility(View.GONE);
                ImgFantasma.setVisibility((View.VISIBLE));
                break;
            case ESTADO_CONECTADO:
                btnSuspender.setVisibility(View.GONE); //no se puede suspender porque el dispositivo esta prendido
                btnActivar.setVisibility(View.GONE); //no se puede activar porque ecoswitch esta activo

                txtEstadoEcoSwitch.setText(R.string.estado_conectado);
                txtEstadoDispositivo.setText(R.string.dispositivo_encendido);
                txtDescripcionExtra.setVisibility(View.GONE);

                ImgApagado.setVisibility(View.GONE);
                ImgPrendido.setVisibility(View.VISIBLE);
                ImgFantasma.setVisibility((View.GONE));
                break;
            case ESTADO_CONSUMO_DESPERDICIADO:
                btnSuspender.setVisibility(View.GONE); //no se puede suspender porque el dispositivo esta prendido
                btnActivar.setVisibility(View.GONE); //no se puede activar porque ecoswitch esta activo

                txtEstadoEcoSwitch.setText(R.string.estado_conectado);
                txtEstadoDispositivo.setText(R.string.dispositivo_encendido);
                txtDescripcionExtra.setVisibility(View.VISIBLE);
                txtDescripcionExtra.setText(R.string.descripcion_extra_consum_desper);

                ImgApagado.setVisibility(View.GONE);
                ImgPrendido.setVisibility(View.VISIBLE);
                ImgFantasma.setVisibility((View.GONE));

                sendNotification("EcoSwitch", "El dispositivo se encuentra encendido y nadie lo está utilizando.");
                break;
            case ESTADO_CONSUMO_FANTASMA:
                btnSuspender.setVisibility(View.VISIBLE); //se puede suspender
                btnActivar.setVisibility(View.GONE); //no se puede activar porque esta activo

                txtEstadoEcoSwitch.setText(R.string.estado_conectado);
                txtEstadoDispositivo.setText(R.string.dispositivo_apagado);
                txtDescripcionExtra.setVisibility(View.GONE);

                ImgApagado.setVisibility(View.GONE);
                ImgPrendido.setVisibility(View.VISIBLE);
                ImgFantasma.setVisibility((View.GONE));
                break;
        }

    }

    // Método showToast para mostrar mensajes Toast
    private void showToast(String message) {
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
    }


    private final View.OnClickListener btnSuspenderListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            mConnectedThread.write("D");
            btnSuspender.setVisibility(View.GONE); //no se puede suspender
            txtEstadoEcoSwitch.setText(R.string.estado_suspendido);
            txtEstadoDispositivo.setText(R.string.dispositivo_apagado);
            txtDescripcionExtra.setVisibility(View.GONE);
            btnActivar.setVisibility(View.VISIBLE);
            ImgApagado.setVisibility(View.VISIBLE);
            ImgPrendido.setVisibility(View.GONE);
            ImgFantasma.setVisibility((View.GONE));
            ImgLogo.setVisibility(View.GONE);

        }
    };


    private final View.OnClickListener btnActivarListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            mConnectedThread.write("C");
            btnSuspender.setVisibility(View.VISIBLE); //se puede suspender
            btnActivar.setVisibility(View.GONE); //no se puede activar porque EcoSwitch esta activo

            txtEstadoEcoSwitch.setText(R.string.estado_conectado);
            txtEstadoDispositivo.setText(R.string.dispositivo_encendido);
            txtDescripcionExtra.setVisibility(View.GONE);

            ImgApagado.setVisibility(View.GONE);
            ImgPrendido.setVisibility(View.VISIBLE);
            ImgFantasma.setVisibility((View.GONE));
            ImgLogo.setVisibility(View.GONE);
        }
    };

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];

            float acceleration = (float) Math.sqrt(x * x + y * y + z * z) - SensorManager.GRAVITY_EARTH;

            long currentTime = System.currentTimeMillis();
            if (acceleration > SHAKE_THRESHOLD && (currentTime - lastShakeTime) > MIN_TIME_BETWEEN_SHAKES_MILLISECS) {
                lastShakeTime = currentTime;
                onShake();
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }
    private void onShake() {
        // Función a ejecutar al detectar el shake
        if (btnActivar.getVisibility() == View.VISIBLE) {
            btnActivar.performClick();                              // Simula un clic en btnActivar
        } else if(btnSuspender.getVisibility() == View.VISIBLE){
            btnSuspender.performClick();                            // Simula un clic en Suspender
        }
        //Si estan los 2 botones activos, tiene prioridad el Activar con el shake
        else {      //Si el dispositivo está encendido, no se puede activar o suspender con el shake
            showToast("El dispositivo se encuentra encendido y no se puede suspender.");
        }
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
                //Crear I/O streams para la conexion
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) { }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        //Metodo run del hilo, que va a entrar en una espera activa para recibir los mensajes del HC05
        public void run()
        {
            byte[] buffer = new byte[256];
            int bytes;

            //el hilo secundario se queda esperando mensajes del HC05
            while (true)
            {
                try
                {
                    //se leen los datos del Bluetooth
                    bytes = mmInStream.read(buffer);
                    String readMessage = new String(buffer, 0, bytes);

                    //se muestran en el layout de la activity, utilizando el handler del hilo principal
                    bluetoothIn.obtainMessage(handlerState, bytes, -1, readMessage).sendToTarget();
                } catch (IOException e) {
                    break;
                }
            }
        }


        public void write(String input) {
            byte[] msgBuffer = input.getBytes();           //Convierte el String input en bytes
            try {
                mmOutStream.write(msgBuffer);                //Escribe los bytes por BT
            } catch (IOException e) {
                //if you cannot write, close the application
                showToast("La conexion fallo");
                finish();

            }
        }
    }

}