// Simple Smart Switch voor de Bluno Beetle
// AAN stuurt 'U' -> servo STAP graden de ene kant op en terug naar de beginstand
// UIT stuurt 'D' -> servo STAP graden de andere kant op en terug naar de beginstand
// TESTEN stuurt 'V' -> Bluno antwoordt OK, servo blijft stil
//
// Uploaden: Arduino IDE, board "Arduino Uno", kies de juiste COM-poort.
// Lukt uploaden niet ("not in sync")? Gebruik Arduino AVR Boards 1.8.5.

#include <Servo.h>

const int SERVO_PIN = 2;     // pin waar de servo op zit (niet 0 of 1!)
const int STAP      = 100;   // graden naar elke kant vanaf de beginstand
const int WACHTTIJD = 700;   // milliseconden in de uiterste stand

// De servo wordt aangestuurd met pulsen (in microseconden).
// 1500 = midden (beginstand). Ongeveer 10 microseconden = 1 graad.
const int MIDDEN    = 1500;  // beginstand
const int PER_GRAAD = 10;    // microseconden per graad (SG90: ongeveer 10)

// Veilige grenzen. Bromt of kraakt de servo in een uiterste stand?
// Dan drukt hij tegen zijn eindstop: zet MIN_PULS hoger en/of MAX_PULS lager.
const int MIN_PULS  = 450;
const int MAX_PULS  = 2550;

Servo servo;

void zetHoek(int gradenVanafMidden) {
  int puls = MIDDEN + gradenVanafMidden * PER_GRAAD;
  puls = constrain(puls, MIN_PULS, MAX_PULS);
  servo.writeMicroseconds(puls);
}

void beweeg(int gradenVanafMidden) {
  zetHoek(gradenVanafMidden);
  delay(WACHTTIJD);
  zetHoek(0);                // terug naar de beginstand
  delay(WACHTTIJD);
}

void setup() {
  Serial.begin(115200);      // standaardsnelheid van de Bluno
  servo.attach(SERVO_PIN, MIN_PULS, MAX_PULS);
  zetHoek(0);                // beginstand
  delay(WACHTTIJD);
}

void loop() {
  if (Serial.available() > 0) {
    char c = Serial.read();
    if (c == 'U') beweeg(-STAP);          // AAN
    if (c == 'D') beweeg(STAP);           // UIT
    if (c == 'V') Serial.print("OK");     // test: servo blijft stil
  }
}
