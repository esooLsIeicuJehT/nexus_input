#!/usr/bin/env python3
"""Render the actual WebUI with no KernelSU bridge; never simulate root observations."""

import argparse
import json
from pathlib import Path

from playwright.sync_api import sync_playwright


def capture(output: Path) -> None:
    root = Path(__file__).resolve().parents[1]
    source = root / "kernelsu-module/webroot/index.html"
    output.mkdir(parents=True, exist_ok=True)
    report = {
        "source": str(source.relative_to(root)),
        "scope": "Chromium layout and actual unavailable-bridge behavior; no root/hardware verification or design approval",
        "root_responses_injected": False,
        "viewports": [],
    }
    try:
        with sync_playwright() as playwright:
            browser = playwright.chromium.launch(headless=True)
            try:
                for name, width, height in [("phone", 390, 844), ("desktop", 1280, 900), ("compact", 320, 740)]:
                    page = browser.new_page(viewport={"width": width, "height": height}, device_scale_factor=1)
                    page.set_default_timeout(10_000)
                    errors = []
                    requests = []
                    page.on("pageerror", lambda error: errors.append(str(error)))
                    page.on("request", lambda request: requests.append(request.url))
                    states = {}
                    try:
                        page.goto(source.as_uri(), wait_until="load", timeout=15_000)
                        assert page.evaluate("typeof window.ksu") == "undefined", "Unexpected privilege bridge"
                        assert page.locator("#apk").inner_text() == "Not read", "Opening the page ran a probe"
                        assert page.locator("[data-page]:visible").count() == 1
                        page.screenshot(path=str(output / f"{name}-overview-initial.png"), full_page=True)
                        page.locator("#refresh").click()
                        assert "Unavailable" in page.locator("#apk").inner_text()
                        assert page.locator("#refresh").is_enabled()
                        page.screenshot(path=str(output / f"{name}-overview-missing-bridge.png"), full_page=True)

                        for route in ["controls", "runtime", "updates", "logs", "overview"]:
                            page.locator(f'.bottom-nav [data-route="{route}"]').click()
                            assert page.locator("[data-page]:visible").count() == 1
                            assert page.locator('.bottom-nav [aria-current="page"]').count() == 1
                            assert page.locator(f'[data-page="{route}"]').is_visible()
                            if route == "controls":
                                page.locator("#refreshCapabilities").click()
                                assert "unavailable" in page.locator("#controlStatus").inner_text().lower()
                                page.locator("summary").first.click()
                                assert page.locator("#applyCpuGovernor").is_disabled()
                                assert page.locator("#cpuPolicy").is_disabled()
                            if route == "updates":
                                page.locator("#checkUpdate").click()
                                assert "unavailable" in page.locator("#updateStatus").inner_text().lower()
                                assert page.locator("#installUpdate").is_disabled()
                            bounds = page.evaluate("({width:innerWidth,scrollWidth:document.documentElement.scrollWidth})")
                            assert bounds["scrollWidth"] <= bounds["width"], f"Horizontal overflow {name}/{route}: {bounds}"
                            states[route] = {"visible_pages": page.locator("[data-page]:visible").count(), "bounds": bounds}
                            page.screenshot(path=str(output / f"{name}-{route}-observed.png"), full_page=True)
                        assert errors == [], errors
                        assert all(url.startswith("file:") for url in requests), "Unexpected network request"
                        report["viewports"].append({"name": name, "width": width, "height": height,
                            "root_bridge_present": False, "page_errors": errors, "states": states})
                    finally:
                        page.close()
            finally:
                browser.close()
        report["result"] = "passed"
    except Exception as error:
        report["result"] = "failed"
        report["error"] = f"{type(error).__name__}: {error}"
        raise
    finally:
        (output / "render-report.json").write_text(json.dumps(report, indent=2) + "\n")
        print(json.dumps(report, indent=2), flush=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=Path("build/ui-review/webui"))
    arguments = parser.parse_args()
    capture(arguments.output)
