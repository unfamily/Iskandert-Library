# CurseForge reviewer notes (1.21.1)

- Mixin on `RecipeManager.apply` (priority 2000, `@Inject` HEAD) splits datapack recipe bundles into separate recipe ids (`path_N`) before KubeJS processes the map. Same purpose as NeoForge 26 `ModifyRecipeJsonsEvent` (not available on 1.21.1).
- No network payloads, class loaders, or embedded executable resources related to this path.
