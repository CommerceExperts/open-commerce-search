Some notes about how development is meant to work in this repo.

# Branching Model

- Releases are built from 'master' and 'support/\*' branches, if the parent pom has a release version.
- Only bugfixes should go directly into master or support branches.
- All feature development should be done in 'develop'. Long-term-development in separate 'feature/\*' branches and merged back to 'develop'.

# Versioning

- Each maven-module has it's own version. It only needs to be increased if something changes at that module or a dependency needs to be updated.
- All module versions are also managed in the parent pom.xml so it always contains the latest module versions.
- If a module-version is updated, the parent pom must be updated with that new module version and with its own version bumped. That updated module needs to refer to that newest parent pom.
- Dependencies to other modules are updated by that automatically. However if a module changed where other modules are dependant on, those modules should also be updated.
- In develop branch the version updates can be prepared without being deployed. Modules without an updated version won't be deployed, even if they changed! (using 'maven-exists' plugin)


Unfortunately this process is a bit complicated and I didn't manage to build some nice scripting support. However it has several upsides:

- Modules are never deployed with different versions, if actually nothing changed (this would be the case for a unified version)
- This also leads to faster CI/CD pipeline executions
- Integration Tests test the current combination of module versions and if they are still compatible. So with the history of the parent pom.xml the compatiblity is always documented.
