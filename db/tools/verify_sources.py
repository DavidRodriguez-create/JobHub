#!/usr/bin/env python3
"""Probe candidate crawler sources and report which actually return live jobs.

Greenhouse: GET https://boards-api.greenhouse.io/v1/boards/{token}/jobs
Lever:      GET https://api.lever.co/v0/postings/{token}?mode=json

Prints, for every candidate that returns 200 with >0 jobs, the total job count
and which of the 5 target countries (FR/ES/CH/US/NL) it has postings in.
"""
import json, sys, urllib.request, urllib.error, concurrent.futures as cf

TIMEOUT = 20

COUNTRY_KEYS = {
    "FR": ["france", "paris", "lyon", "toulouse", "bordeaux", "lille", "nantes", "marseille", "sophia antipolis", "montpellier", "grenoble"],
    "ES": ["spain", "españa", "espana", "madrid", "barcelona", "valencia", "málaga", "malaga", "sevilla", "seville", "bilbao", "zaragoza"],
    "CH": ["switzerland", "suisse", "schweiz", "svizzera", "zurich", "zürich", "geneva", "genève", "geneve", "lausanne", "basel", "bern", "zug", "lugano"],
    "US": ["united states", "usa", "u.s.", "new york", "san francisco", "seattle", "austin", "boston", "chicago", "denver", "los angeles", "palo alto", "mountain view", "sunnyvale", "atlanta", "miami", "washington", "san jose", "santa clara", "san diego", "bellevue", "remote - us", "remote, us", "remote (us"],
    "NL": ["netherlands", "nederland", "amsterdam", "rotterdam", "utrecht", "eindhoven", "the hague", "den haag", "haarlem", "delft", "groningen"],
}

def fetch(url):
    req = urllib.request.Request(url, headers={"User-Agent": "JobHub-crawler-verify/1.0"})
    with urllib.request.urlopen(req, timeout=TIMEOUT) as r:
        return r.status, r.read()

def countries_of(locations):
    hits = set()
    blob = " || ".join(locations).lower()
    for c, keys in COUNTRY_KEYS.items():
        if any(k in blob for k in keys):
            hits.add(c)
    return hits

def probe_greenhouse(token):
    url = f"https://boards-api.greenhouse.io/v1/boards/{token}/jobs?content=false"
    try:
        status, body = fetch(url)
    except urllib.error.HTTPError as e:
        return ("greenhouse", token, f"HTTP {e.code}", 0, set())
    except Exception as e:
        return ("greenhouse", token, type(e).__name__, 0, set())
    if status != 200:
        return ("greenhouse", token, f"HTTP {status}", 0, set())
    data = json.loads(body)
    jobs = data.get("jobs", [])
    locs = [ (j.get("location") or {}).get("name", "") for j in jobs ]
    return ("greenhouse", token, "OK", len(jobs), countries_of(locs))

def probe_lever(token):
    url = f"https://api.lever.co/v0/postings/{token}?mode=json"
    try:
        status, body = fetch(url)
    except urllib.error.HTTPError as e:
        return ("lever", token, f"HTTP {e.code}", 0, set())
    except Exception as e:
        return ("lever", token, type(e).__name__, 0, set())
    if status != 200:
        return ("lever", token, f"HTTP {status}", 0, set())
    data = json.loads(body)
    if not isinstance(data, list):
        return ("lever", token, "not-list", 0, set())
    locs = [ (j.get("categories") or {}).get("location", "") for j in data ]
    return ("lever", token, "OK", len(data), countries_of(locs))

def main():
    with open(sys.argv[1]) as f:
        cands = json.load(f)
    exclude = set(tuple(x) for x in cands.get("exclude", []))
    tasks = []
    for tok in cands.get("greenhouse", []):
        if ("greenhouse", tok) not in exclude:
            tasks.append(("greenhouse", tok))
    for tok in cands.get("lever", []):
        if ("lever", tok) not in exclude:
            tasks.append(("lever", tok))

    results = []
    with cf.ThreadPoolExecutor(max_workers=24) as ex:
        futs = []
        for prov, tok in tasks:
            futs.append(ex.submit(probe_greenhouse if prov == "greenhouse" else probe_lever, tok))
        for fu in cf.as_completed(futs):
            results.append(fu.result())

    working = [r for r in results if r[2] == "OK" and r[3] > 0]
    working.sort(key=lambda r: (r[0], -r[3]))
    print(f"\n=== WORKING ({len(working)} of {len(tasks)} probed) ===")
    print(f"{'provider':10} {'token':26} {'jobs':>5}  countries")
    for prov, tok, _, n, cs in working:
        print(f"{prov:10} {tok:26} {n:>5}  {','.join(sorted(cs)) or '-'}")
    # machine-readable for the next step
    with open(sys.argv[2], "w") as f:
        json.dump([{"provider": p, "token": t, "jobs": n, "countries": sorted(cs)}
                   for p, t, _, n, cs in working], f, indent=2)
    print(f"\nwrote {len(working)} working sources to {sys.argv[2]}")

if __name__ == "__main__":
    main()
