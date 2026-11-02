# Electric Road Companion — English Project Guide

**Routes, charging, media, and vehicle data for electric driving.**

This page is the English introduction to the current application. See the [main README](README.md) for scope, usage limitations, source-generated screen previews, and APK installation instructions.

## Basic usage

Open the application and select an origin and destination under **Route**. The OSRM route is drawn, with alternatives listed when available. Weather forecasts displayed on the map are model data obtained only from verified sources.

Under **Stations**, inspect stations by connector type and explore stops near the route. Station availability and pricing are not presented as confirmed unless verified through an authoritative data service.

SoC, temperature, voltage, and other indicators under **Vehicle Data** use only actual, calibration-validated, fresh OBD readings. A paired Bluetooth adapter alone does not establish that these readings are available; indicators remain empty when readings are missing.

**Route progress** is calculated by projecting an adequately accurate GPS fix received within the last 30 seconds onto the actual calculated route. Stale or off-route GPS does not produce a fabricated percentage. The vehicle symbol stays within the progress bar's bounds.

## Safety

Restrictions on distracting video or browser content for the driver while the vehicle is moving are not removed. External media services apply their own subscription, DRM, and usage terms. Do not put service keys in source code.

## Developer notes

The [map, OBD, and GPS integration guide](docs/ELECTRIC_ROAD_COMPANION_INTEGRATION.md) describes the architecture adapted to this project. Fixed speed, battery, and range values in sample code are not live measurements and are not used.

Third-party licenses and copyright attribution are retained in [LICENSE](LICENSE) and [NOTICE.md](NOTICE.md). The current release requires real-device testing.

[Turkish guide](README_TR.md).
