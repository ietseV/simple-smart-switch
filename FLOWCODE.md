# Flowcode voor de Bluno Beetle

Het kant-en-klare bestand staat in `flowcode/simple_smart_switch.fcfx`.
Het is jullie eigen bestand, met een aangepast stroomschema.

## Afspraak met de webpagina

| Knop | Teken | Getal in Flowcode | Servo |
|------|-------|-------------------|-------|
| AAN  | `U`   | 85                | 45° omhoog, daarna terug naar de beginstand |
| UIT  | `D`   | 68                | 45° omlaag, daarna terug naar de beginstand |

## Dit moet je eerst nog zelf instellen in Flowcode

Het oorspronkelijke bestand stond op een **PIC-chip** (PIC16F, E-blocks2).
Op de Bluno Beetle zit een **ATmega328P**, dezelfde chip als op een Arduino Uno.
Dit stel je in Flowcode zelf om, want Flowcode rekent daarbij de instellingen
van de onderdelen opnieuw uit.

1. Open `simple_smart_switch.fcfx` in Flowcode.
2. Ga naar **Build → Project Options → Choose a Target**.
   Kies **Arduino → Arduino Uno**, of **AVR → ATMEGA328P**.
3. Zet de kloksnelheid (*Clock speed*) op **16000000** (16 MHz).
4. Klik op **UART1** en zet bij de eigenschappen:
   - *Channel*: het hardwarekanaal (**Channel 1**)
   - *Baud rate*: **115200**
   (Het stond op 9600. De Bluno praat standaard op 115200.)
5. Klik op **Servo_Controller1** en kies bij *pin0* de pin waar de servo op zit.
   Dit mag **niet** D0 of D1 zijn, want daar zit de Bluetooth.
6. Kies **Build → Compile to chip** met de Bluno via USB aangesloten.
   Heb je de telefoon verbonden? Verbreek dan eerst de Bluetoothverbinding.

## Het stroomschema

```
BEGIN
├─ Servo_Controller1::Initialise()
├─ Servo_Controller1::EnableServo(0)
├─ Servo_Controller1::SetAngle(0, 0)        ← nieuw: beginstand
├─ Delay 500 ms                             ← nieuw
├─ UART1::Initialise()
└─ Loop While 1
   ├─ X = UART1::ReceiveChar(100)
   ├─ If X = 85 (U = AAN)
   │    SetAngle(0, 45) → Delay 500 ms → SetAngle(0, 0) → Delay 500 ms
   └─ If X = 68 (D = UIT)
        SetAngle(0, -45) → Delay 500 ms → SetAngle(0, 0) → Delay 500 ms
END
```

## Wat er veranderd is en waarom

1. **Beginstand bij het opstarten** (`SetAngle(0, 0)`). Bij een bereik van
   -90 tot 90 graden is 0 het midden.
2. **45° in plaats van 90°**, en daarna **terug naar 0**. Eerst bleef de servo
   in de uiterste stand staan.
3. **Delay 500 ms in plaats van 1 ms.** Met 1 ms heeft de servo geen tijd om
   te bewegen. Is 500 ms te kort of te lang? Pas het getal gerust aan.
4. **`ChangeHWBaud(7)` is weggehaald.** De baudrate stel je nu één keer goed
   in bij de eigenschappen van UART1 (stap 4 hierboven). Zo is er geen
   verwarring over welk nummer bij welke snelheid hoort.

Draait de servo de verkeerde kant op? Wissel dan 45 en -45 om.

## Werkt het nog steeds niet?

- **Stroom:** een SG90 kan bij het bewegen veel stroom trekken. Start de Bluno
  opnieuw op of valt de verbinding weg zodra de servo beweegt? Geef de servo
  dan een eigen 5V-voeding en verbind de GND's met elkaar.
- **Bluno terugzetten naar de fabrieksinstellingen** (meestal niet nodig):
  1. Sluit de Bluno via USB aan en open de Arduino Serial Monitor op
     **115200 baud**, met *No line ending*.
  2. Typ `+++` en verstuur. Je krijgt "Enter AT Mode".
  3. Zet de regeleinde-instelling op *Both NL & CR*.
  4. Typ `AT+SETTING=DEFPERIPHERAL` en verstuur.
  5. Typ `AT+EXIT` en verstuur.
  6. Zet daarna de Flowcode er opnieuw op.
