// LIBRERIAS
#include <SoftwareSerial.h>
SoftwareSerial BTserial(10, 11);  // RX | TX

// PINES ------------------------------------------
// Pines sensores ---------------------------------
#define PIN_SENSOR_PULSADOR 2
#define PIN_SENSOR_MOVIMIENTO 5
#define PIN_SENSOR_CORRIENTE A0

// Pines actuadores -------------------------------
#define PIN_ACTUADOR_BUZZER 3
#define PIN_ACTUADOR_LED 6
#define PIN_ACTUADOR_RELE 4

// VALORES ----------------------------------------
// Brillo led -------------------------------------
#define BRILLO_0_POR_CIENTO 0
#define BRILLO_50_POR_CIENTO 128
#define BRILLO_100_POR_CIENTO 255

// Frecuencias Buzzer -----------------------------
#define FRECUENCIA_BUZZER 494

// Temporizadores----------------------------------
#define TMP_EVENTOS_MILI 1000
#define TMP_AUSENCIA_MILI 10000
#define TMP_PARPADEO_LED 200
#define TMP_BUZZER 400
#define TMP_BUZZER2 500

// Características Sensor de corriente-------------
#define CORRIENTE_FANTASMA 150
#define SIN_CORRIENTE 0.00

// ESTADOS ----------------------------------------
// Estados del embebido ---------------------------
enum estado_e {
  ESTADO_CONECTADO,
  ESTADO_DESCONECTADO,
  ESTADO_SUSPENDIDO,
  ESTADO_CONSUMO_DESPERDICIADO,
  ESTADO_DETECTANDO_INACTIVIDAD,
  ESTADO_AUSENCIA,
  ESTADO_CONSUMO_FANTASMA

};

// Estados del sensor presencia -------------------
enum estado_sensor_movimiento {
  ESTADO_SENSOR_AUSENCIA,
  ESTADO_SENSOR_PRESENCIA
};

// Estados del sensor corriente -------------------
enum estado_sensor_corriente {
  ESTADO_SENSOR_CORRIENTE_FANTASMA,
  ESTADO_SENSOR_CON_CORRIENTE
};

// Estados del sensor pulsador --------------------
enum estado_sensor_pulsador {
  ESTADO_SENSOR_ENCENDIDO,
  ESTADO_SENSOR_APAGADO
};

// Estados del actuador Relé ----------------------
enum estado_actuador_rele {
  ESTADO_ACTUADOR_ACTIVADO,
  ESTADO_ACTUADOR_DESACTIVADO
};

// Estados del actuador led
enum estado_actuador_led {
  ESTADO_PRENDIDO,
  ESTADO_APAGADO
};

// EVENTOS ---------------------------------------
enum evento_e {
  EVENTO_BOTON_PULSADO,
  EVENTO_PRESENCIA_DETECTADA,
  EVENTO_AUSENCIA_DETECTADA,
  EVENTO_TV_PRENDIDA,
  EVENTO_TV_APAGADA,
  EVENTO_TIMEOUT_INACTIVIDAD,
  EVENTO_DESCONEXION_MANUAL,
  EVENTO_CONEXION_MANUAL,
  EVENTO_CONTINUE
};

// estructura de eventos --------------------------
typedef struct evento_s {
  evento_e tipo;
} evento_t;

// SENSORES ---------------------------------------

typedef struct sensor_movimiento_s {
  int pin;
  int valor;
  int estado;
} sensor_movimiento_t;

typedef struct sensor_corriente_s {
  int pin;
  int estado;
  int valor;
} sensor_corriente_t;

typedef struct sensor_pulsador_s {
  int pin;
  int estado;
} sensor_pulsador_t;

// ACTUADORES ------------------------------------

typedef struct actuador_led_s {
  int pin;
  int brillo;
  int estado;
} actuador_led_t;

typedef struct actuador_buzzer_s {
  int pin;
  int estado;
} actuador_buzzer_t;

typedef struct actuador_rele_s {
  int pin;
  int estado;
} actuador_rele_t;


// VARIABLES GLOBALES -----------------------------

estado_e estado_actual;
evento_t evento;

sensor_movimiento_t sensor_movimiento;
sensor_corriente_t sensor_corriente;
sensor_pulsador_t sensor_pulsador;

actuador_led_t actuador_led;
actuador_buzzer_t actuador_buzzer;
actuador_rele_t actuador_rele;


unsigned long tiempo_anterior;
unsigned long tiempo_actual;

bool contador = false;
unsigned long tiempo_inicio_contador;
unsigned long tiempo_actual_contador;

unsigned long tiempo_parpadeo_anterior;
unsigned long tiempo_parpadeo_actual;

// ================================================
// CAPTURA DE EVENTOS ------------------------------

void leer_sensor_corriente() {
  float corriente;

  corriente = analogRead(sensor_corriente.pin);

  if (corriente >= SIN_CORRIENTE && corriente <= CORRIENTE_FANTASMA) {
    sensor_corriente.estado = ESTADO_SENSOR_CORRIENTE_FANTASMA;
    evento.tipo = EVENTO_TV_APAGADA;
  }
  else {
    sensor_corriente.estado = ESTADO_SENSOR_CON_CORRIENTE;
    evento.tipo = EVENTO_TV_PRENDIDA;
    contador = false;
  }
}

void leer_sensor_movimiento() {
  int lectura;

  lectura = digitalRead(sensor_movimiento.pin);

  sensor_movimiento.valor = lectura;

  if (lectura == HIGH) {
    sensor_movimiento.estado = ESTADO_SENSOR_PRESENCIA;
    evento.tipo = EVENTO_PRESENCIA_DETECTADA;
  }

  else if (lectura == LOW) {
    sensor_movimiento.estado = ESTADO_SENSOR_AUSENCIA;
    evento.tipo = EVENTO_AUSENCIA_DETECTADA;
  }
}

bool leer_pulsador() {
  int lectura;

  lectura = digitalRead(sensor_pulsador.pin);

  if (sensor_pulsador.estado == ESTADO_SENSOR_APAGADO) {
    if (lectura == HIGH) {
      evento.tipo = EVENTO_BOTON_PULSADO;
      sensor_pulsador.estado = ESTADO_SENSOR_ENCENDIDO;
      return true;
    }
  } else if (sensor_pulsador.estado == ESTADO_SENSOR_ENCENDIDO) {
    if (lectura == LOW) {
      sensor_pulsador.estado = ESTADO_SENSOR_APAGADO;
    }
  }

  return false;
}

bool leer_serial() {
  if (BTserial.available() > 0) {
    char lectura = BTserial.read();
    if (lectura == 'C') {
      evento.tipo = EVENTO_CONEXION_MANUAL;
      return true;
    } else if (lectura == 'D') {
      evento.tipo = EVENTO_DESCONEXION_MANUAL;
      return true;
    } 
  }

  return false;
}


void verificar_actividad() {
  if (contador) {
    tiempo_actual_contador = millis();
    if ((tiempo_actual_contador - tiempo_inicio_contador) > TMP_AUSENCIA_MILI) {
      evento.tipo = EVENTO_TIMEOUT_INACTIVIDAD;
      contador = false;

    } else
      evento.tipo = EVENTO_CONTINUE;
  }
}

int indice = 0;
void (*verificar_sensor[3])() = { leer_sensor_movimiento, leer_sensor_corriente, verificar_actividad };

void tomar_evento() {
  if (leer_pulsador() == false && leer_serial() == false) {
    tiempo_actual = millis();
    if ((tiempo_actual - tiempo_anterior) > TMP_EVENTOS_MILI) {

      verificar_sensor[indice]();
      indice = (indice + 1) % 3;
      tiempo_anterior = tiempo_actual;
    } else {
      evento.tipo = EVENTO_CONTINUE;
    }
  }
}


void actualizar_rele(int valor) {
  digitalWrite(actuador_rele.pin, valor);
}

void actualizar_led(int porcentaje_pwm) {
  analogWrite(actuador_led.pin, porcentaje_pwm);
}

void sonar_alarma(int tiempo) {
  tone(actuador_buzzer.pin, FRECUENCIA_BUZZER, tiempo);
}

void titilar_led() {
  tiempo_parpadeo_actual = millis();

  if (tiempo_parpadeo_actual - tiempo_parpadeo_anterior >= TMP_PARPADEO_LED) {
    if (actuador_led.estado == ESTADO_APAGADO) {
      digitalWrite(actuador_led.pin, HIGH);
      actuador_led.estado = ESTADO_PRENDIDO;
      tiempo_parpadeo_anterior = millis();
    } else {
      digitalWrite(actuador_led.pin, LOW);
      actuador_led.estado = ESTADO_APAGADO;
      tiempo_parpadeo_anterior = millis();
    }
  }
}


// ================================================
// INICIALIZACIÓN ---------------------------------

void start() {
  Serial.begin(9600);
  BTserial.begin(9600);

  // Asignamos los pines a los sensores correspondientes
  sensor_movimiento.pin = PIN_SENSOR_MOVIMIENTO;
  pinMode(sensor_movimiento.pin, INPUT);

  sensor_corriente.pin = PIN_SENSOR_CORRIENTE;
  pinMode(sensor_corriente.pin, INPUT);

  sensor_pulsador.pin = PIN_SENSOR_PULSADOR;
  pinMode(sensor_pulsador.pin, INPUT);

  // Asignamos los pines a los actuadores correspondientes

  actuador_led.pin = PIN_ACTUADOR_LED;
  pinMode(actuador_led.pin, OUTPUT);

  actuador_buzzer.pin = PIN_ACTUADOR_BUZZER;
  pinMode(actuador_buzzer.pin, OUTPUT);

  actuador_rele.pin = PIN_ACTUADOR_RELE;
  pinMode(actuador_rele.pin, OUTPUT);

  // Inicializo el estado del embebido
  estado_actual = ESTADO_CONECTADO;
  actualizar_rele(HIGH);
  actualizar_led(BRILLO_100_POR_CIENTO);

  sensor_pulsador.estado = ESTADO_SENSOR_APAGADO;

  // Inicializo el temporizador
  tiempo_anterior = millis();
}

// ================================================
void fsm() {

  // Sensar movimiento y corriente
  tomar_evento();

  switch (estado_actual) {
    case ESTADO_CONECTADO:  // ------------------------------
      switch (evento.tipo) {
        case EVENTO_BOTON_PULSADO:

          BTserial.write("ESTADO_CONECTADO\n");
          break;

        case EVENTO_AUSENCIA_DETECTADA:

          BTserial.write("ESTADO_CONECTADO\n");
          estado_actual = ESTADO_AUSENCIA;
          break;

        case EVENTO_DESCONEXION_MANUAL:

          BTserial.write("ESTADO_CONECTADO\n");
          break;

        case EVENTO_CONEXION_MANUAL:

          BTserial.write("ESTADO_CONECTADO\n");
          break;

        case EVENTO_TV_APAGADA:
          BTserial.write("ESTADO_CONSUMO_FANTASMA\n");
          actualizar_led(BRILLO_50_POR_CIENTO);
          contador = false;
          estado_actual = ESTADO_CONSUMO_FANTASMA;
          break;

        default:
          EVENTO_CONTINUE;
          break;
      }
      break;
    case ESTADO_AUSENCIA:  // ----------------------------
      switch (evento.tipo) {
        case EVENTO_BOTON_PULSADO:

          break;

        case EVENTO_PRESENCIA_DETECTADA:

          actualizar_rele(HIGH);
          actualizar_led(BRILLO_100_POR_CIENTO);
          estado_actual = ESTADO_CONECTADO;
          contador = false;
          BTserial.write("ESTADO_CONECTADO\n");
          break;

        case EVENTO_DESCONEXION_MANUAL:
          break;

        case EVENTO_TV_APAGADA:

          contador = true;
          tiempo_inicio_contador = millis();
          titilar_led();
          estado_actual = ESTADO_DETECTANDO_INACTIVIDAD;
          BTserial.write("ESTADO_DETECTANDO_INACTIVIDAD\n");
          break;

        case EVENTO_TV_PRENDIDA:

          estado_actual = ESTADO_CONSUMO_DESPERDICIADO;
          actualizar_led(BRILLO_50_POR_CIENTO);
          BTserial.write("ESTADO_CONSUMO_DESPERDICIADO\n");
          sonar_alarma(TMP_BUZZER2);
          break;

        case EVENTO_CONEXION_MANUAL:
          break;

        default:
          EVENTO_CONTINUE;
          break;
      }
      break;
    case ESTADO_CONSUMO_DESPERDICIADO:  // ----------------------------
      switch (evento.tipo) {
        case EVENTO_BOTON_PULSADO:

          BTserial.write("ESTADO_CONSUMO_DESPERDICIADO\n");
          break;

        case EVENTO_PRESENCIA_DETECTADA:

          actualizar_rele(HIGH);
          actualizar_led(BRILLO_100_POR_CIENTO);
          contador = false;
          BTserial.write("ESTADO_CONECTADO\n");
          estado_actual = ESTADO_CONECTADO;
          break;

        case EVENTO_DESCONEXION_MANUAL:

          BTserial.write("ESTADO_CONSUMO_DESPERDICIADO\n");
          break;

        case EVENTO_TV_APAGADA:

          contador = true;
          tiempo_inicio_contador = millis();
          tiempo_parpadeo_anterior = millis();
          titilar_led();
          estado_actual = ESTADO_DETECTANDO_INACTIVIDAD;
          BTserial.write("ESTADO_DETECTANDO_INACTIVIDAD\n");
          break;

        case EVENTO_CONEXION_MANUAL:

          BTserial.write("CONSUMO_DESPERDICIADO\n");
          break;

        default:
          EVENTO_CONTINUE;
          break;
      }
      break;
    case ESTADO_DESCONECTADO:  
      switch (evento.tipo) {
        case EVENTO_BOTON_PULSADO:

          actualizar_rele(HIGH);
          actualizar_led(BRILLO_100_POR_CIENTO);
          contador = false;
          BTserial.write("ESTADO_CONECTADO\n");
          estado_actual = ESTADO_CONECTADO;
          break;

        case EVENTO_PRESENCIA_DETECTADA:

          actualizar_rele(HIGH);
          actualizar_led(BRILLO_100_POR_CIENTO);
          contador = false;
          BTserial.write("ESTADO_CONECTADO\n");        
          estado_actual = ESTADO_CONECTADO;
          break;

        case EVENTO_CONEXION_MANUAL:

          actualizar_rele(HIGH);
          actualizar_led(BRILLO_100_POR_CIENTO);
          contador = false;
          BTserial.write("ESTADO_CONECTADO\n");          
          estado_actual = ESTADO_CONECTADO;
          break;

        case EVENTO_DESCONEXION_MANUAL:

          BTserial.write("ESTADO_SUSPENDIDO\n");
          estado_actual = ESTADO_SUSPENDIDO;
          break;

        default:
          EVENTO_CONTINUE;
          break;
      }
      break;
    case ESTADO_SUSPENDIDO:  
      switch (evento.tipo) {
        case EVENTO_BOTON_PULSADO:

          actualizar_rele(HIGH);
          actualizar_led(BRILLO_100_POR_CIENTO);
          contador = false;
          BTserial.write("ESTADO_CONECTADO\n");
          estado_actual = ESTADO_CONECTADO;
          break;

        case EVENTO_CONEXION_MANUAL:

          actualizar_rele(HIGH);
          actualizar_led(BRILLO_100_POR_CIENTO);
          contador = false;
          BTserial.write("ESTADO_CONECTADO\n");
          estado_actual = ESTADO_CONECTADO;
          break;

        case EVENTO_DESCONEXION_MANUAL:
          
          break;

        default:
          EVENTO_CONTINUE;
          break;
      }
      break;
    case ESTADO_DETECTANDO_INACTIVIDAD:
      switch (evento.tipo) {
        case EVENTO_BOTON_PULSADO:

          actualizar_rele(LOW);
          actualizar_led(BRILLO_0_POR_CIENTO);
          estado_actual = ESTADO_SUSPENDIDO;
          contador = false;
          break;

        case EVENTO_DESCONEXION_MANUAL:

          actualizar_rele(LOW);
          actualizar_led(BRILLO_0_POR_CIENTO);
          estado_actual = ESTADO_SUSPENDIDO;
          contador = false;
          break;

        case EVENTO_TV_PRENDIDA:

          BTserial.write("ESTADO_CONSUMO_DESPERDICIADO\n");
          estado_actual = ESTADO_CONSUMO_DESPERDICIADO;
          sonar_alarma(TMP_BUZZER2);
          actualizar_led(BRILLO_50_POR_CIENTO);
          contador = false;
          break;

        case EVENTO_PRESENCIA_DETECTADA:
          BTserial.write("ESTADO_CONECTADO\n");
          estado_actual = ESTADO_CONECTADO;
          actualizar_rele(HIGH);
          actualizar_led(BRILLO_100_POR_CIENTO);
          contador = false;
          break;

        case EVENTO_TIMEOUT_INACTIVIDAD:

          actualizar_rele(LOW);
          actualizar_led(BRILLO_0_POR_CIENTO);
          sonar_alarma(TMP_BUZZER);
          BTserial.write("ESTADO_DESCONECTADO\n");
          estado_actual = ESTADO_DESCONECTADO;
          contador = false;
          break;

        case EVENTO_CONEXION_MANUAL:

          BTserial.write("ESTADO_DETECTANDO_INACTIVIDAD\n");
          break;

        default:
          EVENTO_CONTINUE;
          titilar_led();
          break;
      }
      break;

    case ESTADO_CONSUMO_FANTASMA:
      switch (evento.tipo) {
        case EVENTO_BOTON_PULSADO:

          actualizar_rele(LOW);
          actualizar_led(BRILLO_0_POR_CIENTO);
          estado_actual = ESTADO_SUSPENDIDO;
          contador = false;
          break;

        case EVENTO_DESCONEXION_MANUAL:

          actualizar_rele(LOW);
          actualizar_led(BRILLO_0_POR_CIENTO);
          estado_actual = ESTADO_SUSPENDIDO;
          contador = false;
          break;

        case EVENTO_TV_PRENDIDA:

          actualizar_rele(HIGH);
          actualizar_led(BRILLO_100_POR_CIENTO);
          contador = false;
          BTserial.write("ESTADO_CONECTADO\n");
          estado_actual = ESTADO_CONECTADO;
          break;

        case EVENTO_AUSENCIA_DETECTADA:
          BTserial.write("ESTADO_DETECTANDO_INACTIVIDAD\n");
          estado_actual = ESTADO_DETECTANDO_INACTIVIDAD;
          contador = true;
          tiempo_inicio_contador = millis();
          titilar_led();
          break;

        case EVENTO_CONEXION_MANUAL:

          BTserial.write("ESTADO_CONSUMO_FANTASMA\n");
          break;

        default:
          EVENTO_CONTINUE;
          break;
      }
      break;
  }
}

// ================================================
// ARDUINO SETUP ---------------------------------

void setup() {
  start();
}

// ARDUINO LOOP -----------------------------------
void loop() {
  fsm();
}
