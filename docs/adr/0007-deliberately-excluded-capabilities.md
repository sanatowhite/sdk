# 0007 — What the template deliberately does not ship

## Context

Every capability added to a template has a maintenance cost paid by every fork, whether or not that fork needs it: a dependency to keep patched, a piece of API surface that can drift out of date with the ecosystem, a thing to explain in documentation. This template is scoped for the author's actual pattern of personal-project app development (single developer, sideloaded or small-scale distribution, no team to serve), and several commonly-templated capabilities were evaluated and explicitly rejected rather than silently omitted.

## Decision

- **No Room / no bundled database.** `:core-data` is DataStore Preferences only, sized for settings-shaped data. Real domain persistence needs vary too much between apps to have a one-size default be worth the dependency; `TEMPLATE.md` instead documents a 15-minute recipe for adding a `:core-database` module following the same pattern `:core-data` already establishes.
- **No authentication.** Out of scope entirely — this is a network-layer template (`:core-net`), not an identity-and-session template. Adding auth is left to the fork, since auth architecture (OAuth vs. custom, token refresh strategy, session storage) varies too much to templatize usefully.
- **No In-App Review API integration.** Rejected specifically because the primary distribution model this template assumes is sideloading via the paired `version_check` static-JSON update mechanism (see `updatechecker/README.md`), not the Play Store — In-App Review only functions for apps installed through Play, making it 100% dead code for the template's actual use case.
- **No WindowSizeClass / responsive layout system, no Paging3, no skeleton-loading components.** These are real capabilities with real value, but the template's own screens (settings, about, consent, feedback) are simple enough not to need them, and adding infrastructure with no first-party consumer inside the template itself tends to bit-rot silently — nobody notices it's broken until someone tries to use it.
- **No semantic-release / automatic version bumping.** See ADR 0005 — conflicts with the explicit `version.properties` + manual-tag release model.
- **No CODEOWNERS, no commitlint/Husky.** CODEOWNERS is meaningless for a personal/single-maintainer repository; Lefthook already covers the pre-commit/pre-push enforcement role commitlint+Husky would otherwise fill, and running two overlapping git-hook systems isn't worth the redundancy.

## Consequences

- Forks that need any of these capabilities pay the integration cost once, deliberately, when they actually need it — rather than every fork paying a smaller, perpetual maintenance cost for a capability most forks never exercise.
- This list is a live decision, not a permanent one: if a future capability's absence becomes a recurring point of friction across multiple forks, revisit it here rather than re-adding it silently without recording why the original exclusion happened.
