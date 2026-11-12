# Simple Smart Switch

Een webpagina om een **Bluno Beetle** met een **SG90-servo** via Bluetooth
aan en uit te zetten vanaf een Android-telefoon.

- **AAN**: de servo gaat 45° omhoog en dan terug naar de beginstand
- **UIT**: de servo gaat 45° omlaag en dan terug naar de beginstand

## Gebruiken

1. Zet Bluetooth en Locatie aan op de telefoon.
2. Open de pagina in **Chrome** op Android:
   https://ietsev.github.io/simple-smart-switch/
3. Tik op **Verbinden**, kies **Bluno** in de lijst en tik op *Koppelen*.
4. Tik op **AAN** of **UIT**.

Vraagt Chrome om toestemming voor "Apparaten in de buurt"? Kies dan *Toestaan*.

## Bestanden

- `index.html`: de webpagina (alles in één bestand)
- `flowcode/simple_smart_switch.fcfx`: het Flowcode-bestand voor de Bluno
- `FLOWCODE.md`: uitleg bij de Flowcode (lees eerst "Dit moet je eerst nog zelf instellen")

## Hoe het werkt

De Bluno heeft een Bluetoothchip die zich gedraagt als een seriële poort.
De pagina gebruikt Web Bluetooth en schrijft één teken naar de seriële
poort van de Bluno (service `DFB0`, characteristic `DFB1`):

- `U` (85) voor AAN
- `D` (68) voor UIT

In Flowcode lees je dat teken uit met `UART1::ReceiveChar`.

## Arduino IDE (in plaats van Flowcode)

Lukt Flowcode niet? Gebruik dan `arduino/simple_smart_switch/simple_smart_switch.ino`:

1. Installeer de gratis Arduino IDE (arduino.cc).
2. Open het bestand.
3. Zet bovenaan `SERVO_PIN` op de pin van de servo.
4. Kies bij *Tools → Board* **Arduino Uno** en bij *Tools → Port* de poort van de Bluno.
5. Klik op **Uploaden** (de pijl naar rechts).
