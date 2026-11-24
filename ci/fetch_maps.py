#!/usr/bin/env python3
"""CI : JourneyMap, Xaero's Minimap et Xaero's World Map, exactement les JARs fournis avec le cahier des charges
(retrouves sur Modrinth par leur empreinte SHA-1). Servent a la compilation (compileOnly, remappes par Loom) et au
test client. L'API JourneyMap v2 est extraite du JAR de JourneyMap (jar-in-jar). Les JARs restent dans libs/ (jamais publies)."""
import hashlib, io, json, os, sys, urllib.request, zipfile

LIBS = os.path.abspath("libs")
OUT = os.path.abspath(sys.argv[1] if len(sys.argv) > 1 else "out")
os.makedirs(LIBS, exist_ok=True)
os.makedirs(os.path.join(OUT, "maps"), exist_ok=True)
UA = {"User-Agent": "cobblepulse-claims-ci/1.0 (github actions)"}
JARS = {
    "journeymap": "9e6e669e4ed4e99d89af283e078b7ae95fd506c3",    # journeymap-fabric-1.21.1-6.0.10.jar
    "xaerominimap": "5fbb03dd3f22c0c3d5466d1366222d7d277a8df3",  # xaerominimap-fabric-1.21.1-26.6.0.jar
    "xaeroworldmap": "db7ad23fe512dbc13a8286844fd9277c08a7ef5a",  # xaeroworldmap-fabric-1.21.1-1.47.0.jar
}


def get(url):
    req = urllib.request.Request(url, headers=UA)
    with urllib.request.urlopen(req, timeout=300) as r:
        return r.read()


report = []
for name, sha1 in JARS.items():
    try:
        v = json.loads(get(f"https://api.modrinth.com/v2/version_file/{sha1}?algorithm=sha1"))
        f = next(f for f in v["files"] if f["hashes"].get("sha1") == sha1)
        data = get(f["url"])
        assert hashlib.sha1(data).hexdigest() == sha1, "empreinte differente"
        open(os.path.join(LIBS, name + ".jar"), "wb").write(data)
        report.append(f"{name}: {f['filename']} ({v['version_number']}) OK")
        if name == "journeymap":
            z = zipfile.ZipFile(io.BytesIO(data))
            api = next(n for n in z.namelist() if n.startswith("META-INF/jars/journeymap-api"))
            open(os.path.join(LIBS, "journeymap-api.jar"), "wb").write(z.read(api))
            report.append(f"journeymap-api: {api}")
    except Exception as e:
        report.append(f"{name}: ERREUR {e}")
print("\n".join(report))
open(os.path.join(OUT, "maps", "report.txt"), "w").write("\n".join(report) + "\n")
