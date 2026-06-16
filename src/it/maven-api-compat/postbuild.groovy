// Verify the build succeeded — Plexus DI wiring of Maven 3.x components worked
assert new File(basedir, 'target').exists() : 'target directory should exist after verify'
return true
