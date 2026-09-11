# View visibility tests

**View visibility tests** in Kernel's Optimizations tab controls `frustum` in
`config/kernel-renderer.properties`. It defaults to enabled on all nine supported targets and changes
take effect after restarting. It is separate from the section-face visibility solver.

Minecraft's `Frustum.isVisible(AABB)` normally calculates whether a box is fully inside, partly inside,
or outside a view frustum, then discards the distinction between fully and partly inside. Kernel uses
the existing native JOML `testAab` operation for this boolean decision. It retains the exact native
double camera subtraction, float conversion and current plane data. It introduces no cache, additional
plane storage, GPU operation or new geometry algorithm. JOML documents the distinction between its
[boolean and classification queries](https://joml-ci.github.io/JOML/apidocs/org/joml/FrustumIntersection.html).

The adapter replaces only the private classification call inside native `isVisible`. It leaves its
entry/return hooks and original AABB field reads intact. Native callers that need the full category or
rejecting plane still use the original classifier. Custom Frustum subclasses and custom JOML
intersectors also delegate to that classifier; custom visibility overrides retain control. Bounding
boxes, matrices, camera positions and their lifetimes are unchanged. No bounds are made smaller and no
additional object is hidden. A competing modification of this same private call site may require the
feature to be disabled.

## Validation

Differential tests compare 1,048,576 boolean/classification cases per target: rotated perspective,
identity, zero and nonfinite matrices; ordinary, degenerate, inverted, extreme and nonfinite boxes.
Native Fabric processes additionally compare 32,768 AABBs against the untouched private classifier,
including copied frustums, changed cameras and coordinates near the world border. They check full
integer-box classifications separately, preserve null failures and custom intersector/visibility
behavior, and measure steady-state allocation with the feature enabled and disabled.

The native settings probe exercises Cancel, Apply and Done while confirming that the running Mixin
does not change until restart. Test helpers and benchmarks are excluded from release JARs.

The final `buildAll` completed with 1,331 tests in 385 suites and no failures, errors or skipped tests.
Enabled and disabled native visibility checks passed on all nine targets, as did all nine settings
and same-window bootstrap probes. Native shader world probes passed on 1.21.4 and 26.2. Release
inspection verified exactly nine version-specific mod JARs and one Knot Client JAR, including their
metadata, Java levels, Mixin registration, bundled bootstrap identity and production-only contents.
Both component versions remain 0.1.0. These runtime checks used the available Windows/AMD machine.

## Isolated measurements

On the available Ryzen 5 5600G, these medians came from nine alternating rounds of 1,000 passes over
4,096 existing AABBs, after 2,000 warmup passes. Each query includes native camera conversion.
The reference calls the unchanged classifier through a warmed constant MethodHandle and converts its
result to boolean; the public query passes through the actual Mixin. The game was not running and
Gradle was waiting for the single test process. These are CPU query costs, not gameplay FPS results.

| Workload | Java 21 native / Kernel | Java 25 native / Kernel |
| --- | --- | --- |
| All inside | 17.47 / 10.57 ns | 17.54 / 10.32 ns |
| All outside | 6.72 / 5.98 ns | 6.67 / 5.75 ns |
| Mixed, 743/4,096 visible | 10.32 / 7.31 ns | 11.62 / 6.37 ns |

With the feature disabled, the public/native reference costs closely matched: 17.82/17.69 ns and
17.30/17.27 ns for the inside workload on Java 21 and 25 respectively. This control checks that the
reference wrapper itself is not responsible for the measured difference. No end-to-end frame-time,
occlusion-system replacement, full renderer parity or physical GPU/OS compatibility claim follows.

```powershell
# Native enabled/disabled integration and optional query measurement, repeated on either endpoint:
.\gradlew.bat :mod:1.21.4:vertexSortingSmoke :mod:1.21.4:rendererSettingsSmoke -PkernelFrustumBenchmark=true
.\gradlew.bat :mod:26.2:vertexSortingSmoke :mod:26.2:rendererSettingsSmoke -PkernelFrustumBenchmark=true
# Lower-level JOML API comparison without Fabric:
.\gradlew.bat :mod:26.2:frustumBenchmark
```
