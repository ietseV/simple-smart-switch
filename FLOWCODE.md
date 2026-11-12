# Flowcode voor de Bluno Beetle

Dit is jullie eigen flowchart, met een paar aanpassingen.

## Afspraak met de webpagina

| Knop | Teken | Getal in Flowcode | Servo |
|------|-------|-------------------|-------|
| AAN  | `U`   | 85                | 45° omhoog, daarna terug naar de beginstand |
| UIT  | `D`   | 68                | 45° omlaag, daarna terug naar de beginstand |

## De flowchart

```
BEGIN
│
├─ Component Macro   Servo_Controller1::Initialise()
├─ Component Macro   Servo_Controller1::EnableServo(0)
├─ Component Macro   Servo_Controller1::SetAngle(0, 0)        ← NIEUW: beginstand
├─ Delay             500 ms                                   ← NIEUW
├─ Component Macro   UART1::Initialise()
├─ Component Macro   UART1::ChangeHWBaud(7)                   (7 = 115200 baud)
│
└─ Loop  While 1
   │
   ├─ Component Macro   X = UART1::ReceiveChar(100)
   │
   ├─ Decision  If X = 85 ?            (U = AAN)
   │   Yes:
   │   ├─ Component Macro   Servo_Controller1::SetAngle(0, 45)    ← was 90
   │   ├─ Delay             500 ms                                ← was 1 ms
   │   ├─ Component Macro   Servo_Controller1::SetAngle(0, 0)     ← NIEUW: terug
   │   └─ Delay             500 ms                                ← NIEUW
   │
   └─ Decision  If X = 68 ?            (D = UIT)
       Yes:
       ├─ Component Macro   Servo_Controller1::SetAngle(0, -45)   ← was -90
       ├─ Delay             500 ms                                ← NIEUW
       ├─ Component Macro   Servo_Controller1::SetAngle(0, 0)     ← NIEUW: terug
       └─ Delay             500 ms                                ← NIEUW
END
```

De variabele `X` is van het type **BYTE**. Als er niets binnenkomt, geeft
`ReceiveChar(100)` na 100 ms het getal 255 terug. Dat is geen 85 of 68,
dus er gebeurt dan niets. Zo hoort het.

## Wat er veranderd is en waarom

1. **Beginstand bij het opstarten** (`SetAngle(0, 0)`). Eerst stond er geen
   beginstand in, dus de servo stond na het aanzetten ergens willekeurig.
2. **Terug naar de beginstand.** Eerst bleef de servo in de uiterste stand
   staan. Nu gaat hij na een halve seconde terug.
3. **45° in plaats van 90°.** Bij `-90` tot `90` is 0 het midden.
   `SetAngle(0, 90)` ging dus helemaal naar het eind.
4. **Delay 500 ms in plaats van 1 ms.** Een SG90 doet ongeveer 0,1 seconde
   over 60°. Met 1 ms krijgt de servo geen tijd om te bewegen. Is 500 ms te
   kort of te lang? Pas het getal gerust aan.

**Let op het bereik van de servo.** Kijk in de eigenschappen van
`Servo_Controller1` welk bereik er bij de hoek staat. Jullie gebruikten
-90 en 90, dus waarschijnlijk is het **-90 tot 90** en dan kloppen de
getallen hierboven. Staat het bereik op **0 tot 180**? Gebruik dan:
beginstand **90**, omhoog **135**, omlaag **45**.

Draait de servo de verkeerde kant op? Wissel dan 45 en -45 om.

## Waarom het nu misschien nog niet werkte

Loop deze punten na in Flowcode:

- **Chip:** onder *Build → Project Options* moet de chip **ATMEGA328P** zijn,
  met kloksnelheid **16000000** (16 MHz). Klopt de klok niet, dan kloppen de
  baudrate en de servotiming ook niet, en komt er alleen onzin binnen.
- **UART1:** stel bij de eigenschappen *Channel* in op het
  **hardwarekanaal (Channel 1)**, met baudrate **115200**. De Bluetoothchip
  van de Bluno zit vast op de pinnen RX/TX (D0/D1).
- **Servopin:** de servo mag **niet** op D0 of D1 zitten, want daar zit de
  Bluetooth. Op de Beetle kun je bijvoorbeeld D2, D3, D4 of D5 gebruiken.
- **Programmeren:** de Bluno Beetle programmeer je zoals een *Arduino Uno*
  (via USB, avrdude, programmer `arduino`, 115200 baud). Kies in Flowcode
  het Arduino Uno-profiel of een vergelijkbaar profiel. Haal tijdens het
  programmeren de Bluetoothverbinding met de telefoon weg.
- **Stroom:** een SG90 kan bij het bewegen veel stroom trekken. Als de Bluno
  opnieuw opstart of de verbinding wegvalt zodra de servo beweegt, geef de
  servo dan een eigen 5V-voeding (en verbind de GND's met elkaar).

## De Bluno weer schoonmaken (alleen als het echt niet werkt)

Normaal is dit niet nodig: de webpagina zet zelf het wachtwoord en de
baudrate goed. Wil je toch een schone start?

1. Sluit de Bluno via USB aan op de computer.
2. Open de Arduino Serial Monitor op **115200 baud**, met *No line ending*.
3. Typ `+++` en verstuur. Je krijgt "Enter AT Mode".
4. Zet de regeleinde-instelling op *Both NL & CR*.
5. Typ `AT+SETTING=DEFPERIPHERAL` en verstuur. De Bluno staat dan weer op de
   fabrieksinstellingen als randapparaat.
6. Typ `AT+EXIT` en verstuur.

Programmeer daarna de Flowcode opnieuw.
