# EV Cockpit — Production Test Checklist

These are acceptance criteria, **not completed tests**.

## Build and installation

- [ ] JVM tests pass on the exact release commit.
- [ ] R8 minification and resource shrinking pass.
- [ ] Signed Release artifact is created with the original owner certificate.
- [ ] Install `app-release.apk`; relaunch and cold-start on the phone.

## Real hardware and null safety

- [ ] GPS permission denied: no crash; missing location shown as unavailable.
- [ ] GPS disabled/no signal: no route position or synthetic coordinates substituted.
- [ ] ELM327 disconnected: dash for battery percentage, speed and unmeasured values.
- [ ] Reconnect and disconnect adapter repeatedly; stale measurements expire.
- [ ] Route energy estimates never represent unavailable BMS data as measured charge.

## Audio and Android Auto

- [ ] Missing audio assets or playback error: no crash; motor sound fails silent.
- [ ] Navigation TTS obtains transient ducking audio focus.
- [ ] App-owned radio and engine sound reach 20% of prior volume during speech.
- [ ] TTS onDone, onStop, onError and screen stop restore audio as appropriate.
- [ ] Android Auto navigation and radio obey host safety restrictions.

## Documentation and release

- [ ] README and current guide have consistent EV Cockpit naming.
- [ ] GitHub repo rename and hardcoded URL migration validated separately.
- [ ] No unverified claims of complete vehicle acceptance.
