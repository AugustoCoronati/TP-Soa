package com.example.ecoswitch;

import java.util.ArrayList;
import java.util.List;

import android.Manifest;
import android.accessibilityservice.TouchInteractionController;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;

import android.content.Intent;
import android.content.IntentFilter;

import android.content.pm.PackageManager;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.widget.Button;

import android.widget.Toast;

import android.bluetooth.BluetoothAdapter;

import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

@RequiresApi(api = Build.VERSION_CODES.S)
public class MainActivity extends AppCompatActivity /*implements SensorEventListener*/ {
    private Button btnIngresar;
    private Button btnConectar;

    private BluetoothAdapter mBluetoothAdapter;

    public static final int MULTIPLE_PERMISSIONS = 10;

    String[] permissions = new String[]{
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.READ_PHONE_STATE,
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        btnIngresar = (Button) findViewById(R.id.button2);
        btnConectar= (Button) findViewById(R.id.button4);

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (checkPermissions()) {
            enableComponent();
        }
    }

    protected void enableComponent() {
        if (mBluetoothAdapter == null) {
            showUnsupported();
        }
        else
        {
            btnConectar.setOnClickListener(btnConectarListener);
            btnIngresar.setOnClickListener(btnIngresarListener);

            //se determina si esta activado el bluetooth
            if (mBluetoothAdapter.isEnabled())
            {
                showToast("Bluetooth Activado");
                showEnabled();
            }
            else
            {
                showToast("Bluetooth Desactivado");
                showDisabled();
            }

        }

        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);

        registerReceiver(mReceiver, filter);
    }

    private void showEnabled() {

        btnConectar.setVisibility(View.GONE);
        btnIngresar.setVisibility(View.VISIBLE);

    }

    private void showDisabled() {

        btnConectar.setVisibility(View.VISIBLE);
        btnIngresar.setVisibility(View.GONE);
    }


    @SuppressLint("MissingPermission")
    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(mReceiver);

        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }


    private void showUnsupported() {
        showToast("Bluetooth no es soportado por el dispositivo móvil");
        btnIngresar.setEnabled(false);
    }

    private void showToast(String message) {
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);

                if (state == BluetoothAdapter.STATE_ON) {
                    showToast("Bluetooth Activado");
                    showEnabled();

                } else if (state == BluetoothAdapter.STATE_OFF) {
                    showToast("Bluetooth Desactivado");
                    showDisabled();

                }
            }
        }
    };

    private final View.OnClickListener btnConectarListener = new View.OnClickListener() {
        @SuppressLint("MissingPermission")
        @Override
        public void onClick(View v) {
            if (mBluetoothAdapter.isEnabled()) {
                mBluetoothAdapter.disable();

                showDisabled();
            } else {
                Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);

                startActivityForResult(intent, 1000);
            }
        }
    };


    private final View.OnClickListener btnIngresarListener = new View.OnClickListener() {
        @SuppressLint("MissingPermission")
        @Override
        public void onClick(View v) {
            // Crear un Intent para iniciar la nueva Activity
            Intent intent = new Intent(MainActivity.this, PantallaConectado.class);
            startActivity(intent);
        }
    };


    private boolean checkPermissions() {
        int result;
        List<String> listPermissionsNeeded = new ArrayList<>();

        for (String p : permissions) {
            result = ContextCompat.checkSelfPermission(this, p);
            if (result != PackageManager.PERMISSION_GRANTED) {
                listPermissionsNeeded.add(p);
            }
        }
        if (!listPermissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this, listPermissionsNeeded.toArray(new String[listPermissionsNeeded.size()]), MULTIPLE_PERMISSIONS);
            return false;
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case MULTIPLE_PERMISSIONS: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    enableComponent();
                } else {
                    String perStr = "";
                    for (String per : permissions) {
                        perStr += "\n" + per;
                    }
                    Toast.makeText(this, "ATENCIÓN: La aplicación no funcionará correctamente debido a la falta de permisos", Toast.LENGTH_LONG).show();
                }
                return;
            }
        }
    }
}
