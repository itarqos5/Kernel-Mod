# Weighted model selection

The restart-only **Block model rendering** control (`block_model`) includes direct weighted-model
quad dispatch on 1.21.4. Later targets keep their newer model APIs. Kernel preserves the original
weighted model and list objects, stored total, entries, selected model and returned quad-list ownership.
It does not precompute weights, cache a selected model, change model baking or replace random sources.

For exact native `WeightedBakedModel` and `SimpleWeightedRandomList` instances, Kernel reads the current
native total and immutable entries through a small access interface. A nonzero total still consumes
one `nextInt(total)` call. Entry weights are visited in their original order using the native signed
integer subtraction, including unusual values returned by custom random sources. Selection then calls
the chosen model with the same state, face and random source. Null model data, null delegate results
and missing selections retain the native empty-list result. Native model properties remain delegated.

Model/list subclasses and invalid negative totals retain the original call chain. The optimization
removes intermediate Optional values, selection iterators and mapping lambdas from its fast path;
it does not alter the general weighted-list API used elsewhere in Minecraft. Mods replacing internal
weighted-list dispatch can disable `block_model`; arbitrary Mixin ownership is not a tested guarantee.

## Validation status

Real Fabric/Mixin checks passed on 1.21.4 with the optimization both enabled and disabled, including
1,024 random weight lists, boundary tickets, zero weights, null data/results, exact random-state
consumption, delegate/list ownership, exceptions, nested calls, custom subclasses and concurrency.
The 26.2 endpoint also passed its unit and renderer smoke tasks with the version-specific adapter inactive.

The completed command was:

```powershell
.\gradlew.bat :mod:1.21.4:test :mod:1.21.4:vertexSortingSmoke :mod:1.21.4:rendererSettingsSmoke :mod:26.2:test :mod:26.2:vertexSortingSmoke :mod:26.2:rendererSettingsSmoke '-PkernelWeightedModelBenchmark=true' --project-cache-dir build/validation-cache --no-parallel --max-workers=1
```

It completed successfully in 48 seconds; the local log is `build/weighted-model-endpoints.log`.
In the isolated transformed-dispatch benchmark, the native reference allocated 72 bytes/query;
Kernel allocated 16 bytes for the single-model fixture and zero for the 2/4/16/64-model fixtures.
Disabled dispatch allocated 72 bytes/query. A separate fallback fixture measured 144 bytes/query
for a custom model subclass with the wrapper installed, versus 72 with it disabled. This wrapper
overhead is a tradeoff; these measurements do not establish an end-to-end frame-time improvement.

Work was paused at the user's request on 2026-09-11. World-render checks and the final nine-version
`buildAll` validation for this change remain pending. The existing release artifacts in `build/libs/`
were validated at commit `6380e41` and do not include this weighted-model change. Before producing a
new release, complete the remaining runtime/build checks and inspect all nine mod JARs and the Knot
Client JAR. Full renderer/game-logic parity and full-world shader support remain outstanding as
recorded in `PARITY_PLAN.md`; this optimization does not complete those milestones.
