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
- `FLOWCODE.md`: de flowchart voor de Bluno, met uitleg

## Hoe het werkt

De Bluno heeft een Bluetoothchip die zich gedraagt als een seriële poort.
De pagina gebruikt Web Bluetooth en schrijft één teken naar de seriële
poort van de Bluno (service `DFB0`, characteristic `DFB1`):

- `U` (85) voor AAN
- `D` (68) voor UIT

In Flowcode lees je dat teken uit met `UART1::ReceiveChar`.
