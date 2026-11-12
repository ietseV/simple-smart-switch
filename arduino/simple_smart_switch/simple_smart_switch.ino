// Simple Smart Switch voor de Bluno Beetle
// AAN op de webpagina stuurt 'U' -> servo 45 graden omhoog en terug
// UIT op de webpagina stuurt 'D' -> servo 45 graden omlaag en terug
// TESTEN op de webpagina stuurt 'V' -> Bluno antwoordt OK, servo blijft stil
//
// Uploaden: Arduino IDE, board "Arduino Uno", kies de juiste COM-poort.

#include <Servo.h>

const int SERVO_PIN  = 3;    // pin waar de servo op zit (niet 0 of 1!)
const int BEGINSTAND = 90;   // midden
const int STAP       = 45;   // graden omhoog/omlaag
const int WACHTTIJD  = 500;  // milliseconden in de uiterste stand

Servo servo;

void beweeg(int hoek) {
  servo.write(hoek);
  delay(WACHTTIJD);
  servo.write(BEGINSTAND);
  delay(WACHTTIJD);
}

void setup() {
  Serial.begin(115200);       // standaardsnelheid van de Bluno
  servo.attach(SERVO_PIN);
  servo.write(BEGINSTAND);
  delay(WACHTTIJD);
}

void loop() {
  if (Serial.available() > 0) {
    char c = Serial.read();
    if (c == 'U') beweeg(BEGINSTAND + STAP);   // AAN
    if (c == 'D') beweeg(BEGINSTAND - STAP);   // UIT
    if (c == 'V') Serial.print("OK");          // test: servo blijft stil
  }
}
