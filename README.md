# Electric Vehicle Journey Companion

An independent, experimental Android application for planning and following electric-vehicle journeys.

## Features

- Road navigation with route alternatives and spoken maneuver guidance.
- Charging station discovery, route-based charging candidates, and estimated energy needs.
- A map-focused cockpit with weather, trip progress, and available vehicle telemetry.
- Optional media and radio access, subject to platform and playback restrictions.
- An explicitly labelled debug simulation mode for testing navigation without real vehicle data.

## Development

The active Android implementation is maintained on the `feature/ev-cockpit` branch. Source code, automated tests, and build workflows are available in this repository.

## Safety and data quality

This software is experimental. Estimates are not measurements; unavailable sensor values remain unavailable. Navigation, charging, speech, and vehicle diagnostics require real-device validation before operational use. Do not interact with video controls while driving.

## Licensing

Third-party software and media components retain their original copyrights and licence requirements. See [LICENSE](LICENSE) and [NOTICE.md](NOTICE.md).
