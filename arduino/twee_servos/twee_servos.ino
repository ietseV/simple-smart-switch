// Simple Smart Switch met TWEE servo's, voor de Bluno Beetle
// AAN stuurt 'U'    -> beide servo's draaien van 0 naar 180 graden (en blijven daar)
// UIT stuurt 'D'    -> beide servo's draaien terug naar 0 graden
// TESTEN stuurt 'V' -> Bluno antwoordt OK, servo's blijven stil
//
// Uploaden: Arduino IDE, board "Arduino Uno", kies de juiste COM-poort.
// Lukt uploaden niet ("not in sync")? Gebruik Arduino AVR Boards 1.8.5.

#include <Servo.h>

const int SERVO1_PIN = 2;    // Servo 1 op het schoolbordje
const int SERVO2_PIN = 3;    // Servo 2 op het schoolbordje (controleer dit!)

const int HOEK_UIT   = 0;    // beginstand
const int HOEK_AAN   = 180;  // stand bij AAN

// Pulsbereik voor de volle 180 graden van een SG90.
// Bromt of kraakt een servo in een uiterste stand? Zet MIN_PULS hoger
// (bijv. 600) en/of MAX_PULS lager (bijv. 2300).
const int MIN_PULS   = 500;
const int MAX_PULS   = 2400;

// Servo 2 start iets later dan servo 1. Zo trekken ze niet allebei
// tegelijk veel stroom (dan kan de Bluno opnieuw opstarten).
const int VERTRAGING = 150;  // milliseconden

Servo servo1;
Servo servo2;

void zetBeide(int hoek) {
  servo1.write(hoek);
  delay(VERTRAGING);
  servo2.write(hoek);
}

void setup() {
  Serial.begin(115200);      // standaardsnelheid van de Bluno
  servo1.attach(SERVO1_PIN, MIN_PULS, MAX_PULS);
  servo2.attach(SERVO2_PIN, MIN_PULS, MAX_PULS);
  zetBeide(HOEK_UIT);        // beginstand: 0 graden
}

void loop() {
  if (Serial.available() > 0) {
    char c = Serial.read();
    if (c == 'U') zetBeide(HOEK_AAN);   // AAN: naar 180 graden
    if (c == 'D') zetBeide(HOEK_UIT);   // UIT: terug naar 0 graden
    if (c == 'V') Serial.print("OK");   // test: servo's blijven stil
  }
}
