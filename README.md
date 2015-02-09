Sunrise is a Bluetooth LE-controllable bedside light.

It's built from:

- An Ikea frosted glass bedside lamp with the fittings discarded: £4.50.
- A Maple Mini clone (which is a STM32F103 ARM Cortex M3 microcontroller): £3.11.
- 60 WS2812B RGB LEDs (with integrated PWM controllers): £7.89.
- A HM-11 Bluetooth LE to TTL UART bridge: £ 5.61.

Notes
-----

The Maple Mini clone I have has a diode to prevent high V<sub>in</sub> voltage flowing upstream
to the USB-attached programming host.  This also prevents use of the V<sub>in</sub> pin to
get V<sub>USB</sub> to power other devices at moderate currents.  This diode is removed.
That said, it's quite an abuse to source ~1.5A through the Maple Mini.

