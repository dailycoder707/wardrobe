# :feature:widget

Home-screen widget via Glance (`androidx.glance`), not standard Compose UI —
App Widgets are RemoteViews-backed and have no back stack, so this module
intentionally has no Navigation-Compose or `ui-tooling` dependency.

Nothing is implemented yet. When this is built (a low-priority row in Phase 1
Section 4's feature table), it will need its own `AndroidManifest.xml` declaring
the `AppWidgetProvider` receiver and widget-info XML, which no other module in
this project needs — that's the one exception to "library modules don't need a
manifest" mentioned in the root `PROJECT_STRUCTURE.md`.
