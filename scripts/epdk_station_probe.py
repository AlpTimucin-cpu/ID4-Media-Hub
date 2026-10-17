#!/usr/bin/env python3
"""Read-only, one-request schema discovery for EPDK's published REST service.

EPDK publishes GET /sarjIstasyonlari, with a JSON string in the request body
in its Swagger 2.0 document (unusual, so do not guess POST or private APIs).
This diagnostic NEVER persists raw station records or account information.
It only prints top-level JSON type, collection sizes and safe FIELD NAMES.
"""
import json
import sys
from urllib.request import Request, urlopen
from urllib.error import HTTPError, URLError

URL = "https://apigateway.epdk.gov.tr/sarjIstasyonlari"
FILTER = {
    "lisansNo": "", "sarjIstasyonuNo": "", "markaAdi": "",
    "yesilSarjIstasyonuMu": "", "sarjIstasyonuAdi": "",
    "hizmetSekli": ""
}
MAX_BYTES = 20_000_000


def inspect_shape(root):
    print("root_type:", type(root).__name__)
    if isinstance(root, list):
        print("records:", len(root))
        if root and isinstance(root[0], dict):
            print("fields:", ", ".join(sorted(root[0])[:55]))
    elif isinstance(root, dict):
        print("root_keys:", ", ".join(sorted(root)[:55]))
        for name, value in root.items():
            if isinstance(value, list):
                print("array:", name, "length:", len(value))
                if value and isinstance(value[0], dict):
                    print("record_fields:", ", ".join(sorted(value[0])[:55]))
            elif isinstance(value, dict):
                print("object:", name, "keys:", ", ".join(sorted(value)[:40]))
    else:
        print("unexpected data type")


def main():
    # The GET body matches Swagger, not an undocumented internal API.
    request = Request(
        URL, data=json.dumps(FILTER).encode("utf-8"), method="GET",
        headers={"Accept": "application/json", "Content-Type": "application/json",
                 "User-Agent": "ID4-Yol-Arkadasim/0.60 (official EPDK API integration)"})
    try:
        with urlopen(request, timeout=25) as response:
            print("http_status:", response.status)
            print("content_type:", response.headers.get("Content-Type", "unknown").split(";")[0])
            payload = response.read(MAX_BYTES + 1)
            if len(payload) > MAX_BYTES:
                print("response_exceeds_limit")
                return 2
            inspect_shape(json.loads(payload.decode("utf-8")))
            return 0
    except HTTPError as error:
        print("http_status:", error.code)
    except (URLError, ValueError, TimeoutError) as error:
        print("request_failed:", type(error).__name__)
    return 1


if __name__ == "__main__":
    sys.exit(main())
