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
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

/*********************************************************************************************************
 * Activity que muestra realiza la comunicacion con Arduino
 **********************************************************************************************************/

//******************************************** Hilo principal del Activity**************************************
public class PantallaConectado extends Activity
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

    Handler bluetoothIn;
    final int handlerState = 0; //used to identify handler message

    private BluetoothAdapter btAdapter = null;
    private BluetoothSocket btSocket = null;
    private final StringBuilder recDataString = new StringBuilder();

    private ConnectedThread mConnectedThread;

    // SPP UUID service  - Funciona en la mayoria de los dispositivos
    private static final UUID BTMODULEUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    // String for MAC address del Hc05
    private static String address = "00:22:06:01:9C:DA";

    private static final String CHANNEL_ID = "12345";


    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pantalla_conectado);

        //Se definen los componentes del layout
        btnSuspender=findViewById(R.id.button3);
        btnActivar=findViewById(R.id.button);
        txtEstadoDispositivo=findViewById(R.id.textView3);
        txtEstadoEcoSwitch=findViewById(R.id.textView2);
        txtDescripcionExtra=findViewById(R.id.textView4);
        ImgPrendido =findViewById(R.id.imageView2);
        ImgApagado =findViewById(R.id.imageView3);
        ImgFantasma =findViewById(R.id.imageView4);

        //obtengo el adaptador del bluetooth
        btAdapter = BluetoothAdapter.getDefaultAdapter();

        //defino el Handler de comunicacion entre el hilo Principal y el secundario.
        //El hilo secundario va a mostrar informacion al layout utilizando indirectamente a este handler
        bluetoothIn = Handler_Msg_Hilo_Principal();

        //defino los handlers para los botones Activar y Suspender
        btnSuspender.setOnClickListener(btnSuspenderListener);
        btnActivar.setOnClickListener(btnActivarListener);

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
        Intent intent = new Intent(this, MainActivity.class);
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
    //Cada vez que se detecta el evento OnResume se establece la comunicacion con el HC05, creando un
    //socketBluethoot
    public void onResume() {
        super.onResume();

        BluetoothDevice device = btAdapter.getRemoteDevice(address);

        //se realiza la conexion del Bluetooth crea y se conectandose a traves de un socket
        try
        {
            btSocket = createBluetoothSocket(device);
        }
        catch (IOException e)
        {
            showToast("La creación del Socket falló");
        }
        // Establish the Bluetooth socket connection.
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
                //insert code to deal with this
            }
        }

        //Una establecida la conexion con el Hc05 se crea el hilo secundario, el cual va a recibir
        // los datos de Arduino atraves del bluethoot
        mConnectedThread = new ConnectedThread(btSocket);
        mConnectedThread.start();

        //I send a character when resuming.beginning transmission to check device is connected
        //If it is not an exception will be thrown in the write method and finish() will be called
        mConnectedThread.write("x");
    }


    @Override
    //Cuando se ejecuta el evento onPause se cierra el socket Bluetooth, para no recibiendo datos
    public void onPause()
    {
        super.onPause();
        try
        {
            //Don't leave Bluetooth sockets open when leaving activity
            btSocket.close();
        } catch (IOException e2) {
            //insert code to deal with this
        }
    }

    //Metodo que crea el socket bluethoot
    @SuppressLint("MissingPermission")
    private BluetoothSocket createBluetoothSocket(BluetoothDevice device) throws IOException {

        return  device.createRfcommSocketToServiceRecord(BTMODULEUUID);
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
                    int endOfLineIndex = recDataString.indexOf("\n");

                    //cuando recibo toda una linea la muestro en el layout
                    if (endOfLineIndex > 0)
                    {
                        String dataInPrint = recDataString.substring(0, endOfLineIndex);


                        //ACA CREO QUE HAY QUE IMPLEMENTAR TODA LA LOGICA PARA CAMBIAR LOS TEXTOS Y FOTO
                        procesarEstado(dataInPrint);

                        recDataString.delete(0, recDataString.length());
                    }
                }
            }
        };

    }
    //proceso estados
    private void procesarEstado(String estado) {
        Estado estadoNumero = Estado.valueOf(estado);
        switch (estadoNumero) {
            case ESTADO_DESCONECTADO:
                btnSuspender.setVisibility(View.VISIBLE); //se puede suspender
                btnActivar.setVisibility(View.VISIBLE); //se puede activar

                txtEstadoEcoSwitch.setText(R.string.estado_desconectado); //cambio estado
                txtEstadoDispositivo.setText(R.string.dispositivo_apagado); //cambio estado
                txtDescripcionExtra.setVisibility(View.GONE); //saco lo de abajo

                ImgApagado.setVisibility(View.VISIBLE); //pongo fotito apagado
                ImgPrendido.setVisibility(View.GONE); //pongo fotito apagado
                ImgFantasma.setVisibility((View.GONE));
                break;

            case ESTADO_DETECTANDO_INACTIVIDAD:
                btnSuspender.setVisibility(View.VISIBLE); //se puede suspender
                btnActivar.setVisibility(View.GONE); //no se puede activar pq esta activo

                txtEstadoEcoSwitch.setText(R.string.detectando_inactividad); //cambio estado
                txtEstadoDispositivo.setText(R.string.dispositivo_apagado); //cambio estado
                txtDescripcionExtra.setVisibility(View.VISIBLE); //pongo el detalle
                txtDescripcionExtra.setText(R.string.descripcion_extra_detectando_inactividad);

                ImgApagado.setVisibility(View.GONE); //pongo fotito fantasma
                ImgPrendido.setVisibility(View.GONE);
                ImgFantasma.setVisibility((View.VISIBLE));
                break;
            case ESTADO_CONECTADO:
                btnSuspender.setVisibility(View.GONE); //no se puede suspender pq tv prendida
                btnActivar.setVisibility(View.GONE); //no se puede activar pq esta activo

                txtEstadoEcoSwitch.setText(R.string.estado_conectado); //cambio estado
                txtEstadoDispositivo.setText(R.string.dispositivo_encendido); //cambio estado
                txtDescripcionExtra.setVisibility(View.GONE); //saco el detalle

                ImgApagado.setVisibility(View.GONE); //pongo foto feliz
                ImgPrendido.setVisibility(View.VISIBLE);
                ImgFantasma.setVisibility((View.GONE));
                break;
            case ESTADO_CONSUMO_DESPERDICIADO:
                btnSuspender.setVisibility(View.GONE); //no se puede suspender pq tv prendida
                btnActivar.setVisibility(View.GONE); //no se puede activar pq esta activo

                txtEstadoEcoSwitch.setText(R.string.estado_conectado); //cambio estado
                txtEstadoDispositivo.setText(R.string.dispositivo_encendido); //cambio estado
                txtDescripcionExtra.setVisibility(View.VISIBLE); //pongo el detalle
                txtDescripcionExtra.setText(R.string.descripcion_extra_consum_desper);

                ImgApagado.setVisibility(View.GONE); //pongo foto feliz
                ImgPrendido.setVisibility(View.VISIBLE);
                ImgFantasma.setVisibility((View.GONE));


                //aca va notificacion (Logica del sensor de proximidad, hacer sonar alarma en el celu)
                sendNotification("EcoSwicth", String.valueOf(R.string.descripcion_extra_consum_desper));
                break;
            case ESTADO_CONSUMO_FANTASMA:
                btnSuspender.setVisibility(View.VISIBLE); //se puede suspender
                btnActivar.setVisibility(View.GONE); //no se puede activar pq esta activo

                txtEstadoEcoSwitch.setText(R.string.estado_conectado); //cambio estado
                txtEstadoDispositivo.setText(R.string.dispositivo_apagado); //cambio estado
                txtDescripcionExtra.setVisibility(View.GONE); //no pongo el detalle

                ImgApagado.setVisibility(View.VISIBLE); //pongo foto apagada
                ImgPrendido.setVisibility(View.GONE);
                ImgFantasma.setVisibility((View.GONE));
                break;


        }

    }

    // Ejemplo de método showToast para mostrar mensajes Toast
    private void showToast(String message) {
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
    }

    //Listener del boton Suspender
    private final View.OnClickListener btnSuspenderListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            mConnectedThread.write("D");
            btnSuspender.setVisibility(View.GONE); //no se suspender
            txtEstadoEcoSwitch.setText(R.string.estado_suspendido); //cambio estado
            txtEstadoDispositivo.setText(R.string.dispositivo_apagado); //cambio estado
            txtDescripcionExtra.setVisibility(View.GONE); //saco lo de abajo
            btnActivar.setVisibility(View.VISIBLE); //se puede conectar
            ImgApagado.setVisibility(View.VISIBLE); //pongo fotito apagado
            ImgPrendido.setVisibility(View.GONE); //pongo fotito apagado
            ImgFantasma.setVisibility((View.GONE));

        }
    };

    //Listener del boton Activar
    private final View.OnClickListener btnActivarListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            mConnectedThread.write("C");
            btnSuspender.setVisibility(View.VISIBLE); //se puede suspender
            btnActivar.setVisibility(View.GONE); //no se puede activar pq esta activo

            txtEstadoEcoSwitch.setText(R.string.estado_conectado); //cambio estado
            txtEstadoDispositivo.setText(R.string.dispositivo_encendido); //cambio estado
            txtDescripcionExtra.setVisibility(View.GONE); //saco lo de abajo

            ImgApagado.setVisibility(View.GONE); //pongo fotito prendido
            ImgPrendido.setVisibility(View.VISIBLE); //pongo fotito prendido
            ImgFantasma.setVisibility((View.GONE));
        }
    };


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
                    bluetoothIn.obtainMessage(handlerState, bytes, -1, readMessage).sendToTarget();
                } catch (IOException e) {
                    break;
                }
            }
        }


        //write method
        public void write(String input) {
            byte[] msgBuffer = input.getBytes();           //converts entered String into bytes
            try {
                mmOutStream.write(msgBuffer);                //write bytes over BT connection via outstream
            } catch (IOException e) {
                //if you cannot write, close the application
                showToast("La conexion fallo");
                finish();

            }
        }
    }

}




/*
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class PantallaConectado extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_pantalla_conectado);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }
}

 */