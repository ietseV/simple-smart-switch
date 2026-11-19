// Simple Smart Switch met TWEE servo's, voor de Bluno Beetle
// AAN stuurt 'U'    -> servo 1 draait van 0 naar 180 graden (en terug)
// UIT stuurt 'D'    -> servo 2 draait van 0 naar 180 graden (en terug)
// TESTEN stuurt 'V' -> Bluno antwoordt OK, servo's blijven stil
//
// Uploaden: Arduino IDE, board "Arduino Uno", kies de juiste COM-poort.
// Lukt uploaden niet ("not in sync")? Gebruik Arduino AVR Boards 1.8.5.

#include <Servo.h>

const int SERVO1_PIN = 2;    // Servo 1 op het schoolbordje: AAN
const int SERVO2_PIN = 3;    // Servo 2 op het schoolbordje: UIT (controleer dit!)

const int BEGINSTAND = 0;    // graden
const int EINDSTAND  = 180;  // graden
const int WACHTTIJD  = 800;  // milliseconden in de eindstand (en om terug te draaien)

// true  = na het draaien terug naar de beginstand
// false = in de eindstand blijven staan
const bool TERUGKEREN = true;

// Pulsbereik voor de volle 180 graden van een SG90.
// Bromt of kraakt een servo in een uiterste stand? Zet MIN_PULS hoger
// (bijv. 600) en/of MAX_PULS lager (bijv. 2300).
const int MIN_PULS   = 500;
const int MAX_PULS   = 2400;

Servo servo1;
Servo servo2;

void beweeg(Servo &s) {
  s.write(EINDSTAND);
  delay(WACHTTIJD);
  if (TERUGKEREN) {
    s.write(BEGINSTAND);
    delay(WACHTTIJD);
  }
}

void setup() {
  Serial.begin(115200);      // standaardsnelheid van de Bluno
  servo1.attach(SERVO1_PIN, MIN_PULS, MAX_PULS);
  servo2.attach(SERVO2_PIN, MIN_PULS, MAX_PULS);
  servo1.write(BEGINSTAND);
  delay(150);                // niet allebei tegelijk starten (stroom)
  servo2.write(BEGINSTAND);
  delay(WACHTTIJD);
}

void loop() {
  if (Serial.available() > 0) {
    char c = Serial.read();
    if (c == 'U') beweeg(servo1);       // AAN: servo 1
    if (c == 'D') beweeg(servo2);       // UIT: servo 2
    if (c == 'V') Serial.print("OK");   // test: servo's blijven stil
  }
}
