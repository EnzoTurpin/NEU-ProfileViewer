#!/usr/bin/env bash
# Rebuilds src/gametest/resources/fabric.mod.json from every *GameTest.java class (resolves merge conflicts).
cd "$(dirname "$0")"
classes=$(ls src/gametest/java/dev/neupv/test/*GameTest.java | xargs -n1 basename | sed 's/\.java$//' | sort | sed 's/^/\t\t\t"dev.neupv.test./;s/$/"/' | paste -sd, - | sed 's/,/,\n/g')
cat > src/gametest/resources/fabric.mod.json <<JSON
{
	"schemaVersion": 1,
	"id": "neupv-test",
	"version": "1.0.0",
	"name": "NeuPv tests",
	"environment": "client",
	"entrypoints": {
		"fabric-client-gametest": [
$classes
		]
	},
	"depends": {"neupv": "*"}
}
JSON
