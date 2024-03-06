#!/bin/bash

main_modules=("open-commerce-search-api" "ocs-plugin-spi" "ocs-commons" "indexer-service" "search-service" "ocs-java-client" "integration-tests")
suggest_modules=("smartsuggest-lib" "ocs-suggest-data-provider" "suggest-service")

echo "Choose bump level: "
echo " 1) fix (incremental version for bug fixes etc)"
echo " 2) minor (for new non-breaking features)"
read -r BL

case "$BL" in
    1) bump_level="incremental" ;;
    incremental) bump_level="incremental" ;;
    fix) bump_level="incremental" ;;
    2) bump_level="minor" ;;
    minor) bump_level="minor" ;;
    *) echo "Invalid bump level: $BL" && exit 1 ;;
esac

cd "$(dirname "$0")" || exit
orig_changed_modules="$(git diff --dirstat=0 origin/master | awk '{print $NF}' | grep -v docs | cut -d "/" -f1 | sort -u | xargs)"
changed_modules="$orig_changed_modules"

echo "found changes in modules $changed_modules"

function getDependants() {
    mod="$1";
    grep -l "<artifactId>$mod</artifactId>" ./*/pom.xml | cut -d "/" -f2 | grep -v "$mod"
}

function getLocalVersion() {
    mod="$1"
    if [[ "$mod" != "" ]]; then
        mod="-pl $mod"
    else
        #parent version - dont recurse into modules
        mod="-N"
    fi
    mvn -B -q $mod org.apache.maven.plugins:maven-help-plugin:3.1.0:evaluate -Dexpression=project.version -DforceStdout 2>/dev/null
}

function getRemoteVersion() {
    mod="$1"
    if [[ "$mod" != "" ]]; then
        echo "fetching remote version for $module" >&2
        mod="-pl $mod"
    else
        echo "fetching remote version for parent pom" >&2
        #parent version - dont recurse into modules
        mod="-N"
    fi
    mvn -B -q $mod build-helper:remove-project-artifact build-helper:released-version org.apache.maven.plugins:maven-help-plugin:3.1.0:evaluate -Dexpression=releasedVersion.version -DforceStdout 2>/dev/null
}

newVersionPattern='${parsedVersion.majorVersion}.${parsedVersion.minorVersion}.${parsedVersion.nextIncrementalVersion}'
if [ "$bump_level" = "minor" ]; then
    newVersionPattern='${parsedVersion.majorVersion}.${parsedVersion.nextMinorVersion}.0'
fi
function bumpVersion() {
    mod="$1"
    if [[ "$mod" != "" ]]; then
        mod="-pl $mod"
    else
        #parent version - dont recurse into modules
        mod="-N"
    fi
    mvn -q -B $mod build-helper:parse-version versions:set -DnewVersion="$newVersionPattern" -DgenerateBackupPoms=false -DprocessParent=false
}

suggest_service_version=""
update_dependants=""
function bumpModuleVersions() {
    for module in "$@"
    do
        if [[ "$changed_modules" == *"$module"* ]]; then
            released_mod_version="$(getRemoteVersion "$module")"
            local_mod_version="$(getLocalVersion "$module")"

            if [[ "$released_mod_version" == "$local_mod_version" ]]
            then
                echo "bumping version for $module"
                bumpVersion "$module"
                update_dependants+=" $(getDependants "$module")"
                export update_dependants

            elif [[ "$bump_level" == "minor" ]] && [[ "$local_mod_version" != *".0" ]]
            then
                echo "bumping version for $module to next minor version"
                bumpVersion "$module"
                update_dependants+=" $(getDependants "$module")"
                export update_dependants
            else
                echo "module $module has already a bumped version"
            fi

            # special case for suggest-service: remember its version to set it at the parent's parent, which is not possible here
            if [[ "$module" == "suggest-service" ]]; then
                suggest_service_version="$(getLocalVersion "$module")"
                export suggest_service_version
            fi
        fi
    done
}

bumpModuleVersions "${main_modules[@]}"
update_dependants="$(echo "$update_dependants" | sed 's/ /\n/g' | sort -u | xargs)"
if [[ "$update_dependants" != "" ]]; then
    echo "enforcing version bump of dependants: $update_dependants"
    changed_modules="$update_dependants" # enforce updates
    bumpModuleVersions $update_dependants
fi

# add changed modules to the list of modules that should get current parent version
main_dependants="$(echo "$update_dependants $orig_changed_modules" | sed -r 's/\s+/\n/g' | sort -u | paste -sd ',' -)"

if [[ "$orig_changed_modules" == *"suggest-service-parent"* ]]; then
    if cd suggest-service-parent; then
        echo "checking for changes in suggest modules..."

        # add suggest-service-parent to main_dependants
        if [[ "$update_dependants" == "" ]]; then
            main_dependants="suggest-service-parent"
        else
            main_dependants="$main_dependants,suggest-service-parent"
        fi

        # reset update_dependants and changed modules for suggest modules bump
        update_dependants=""
        changed_modules="$(git diff --dirstat=0 origin/master | grep "suggest-service-parent" | awk '{print $NF}' | cut -d "/" -f2 | sort -u | xargs)"

        bumpModuleVersions "${suggest_modules[@]}"
        suggest_update_dependants="$(echo "$update_dependants" | xargs | sed 's/ /\n/g' | sort -u | xargs)"
        if [[ "$suggest_update_dependants" != "" ]]; then
            echo "enforcing version bump of suggest dependants: $suggest_update_dependants"
            changed_modules="$suggest_update_dependants" # enforce updates
            bumpModuleVersions $suggest_update_dependants
        fi

        # add changed suggest modules to the list of modules that should get current parent version
        suggest_update_dependants="$(echo "$suggest_update_dependants $changed_modules" | sed 's/ /\n/g' | sort -u | paste -sd ',' -)"

        echo "...done"
        cd ..
    fi
fi

# if there are suggest_update_dependants, then main_dependants contains at least "suggest-service-parent",
# so no additional check is required here
if [[ "$main_dependants" != "" ]]
then

    if [[ "$suggest_service_version" != "" ]]; then
        echo "update suggest-service version in parent's parent"
        mvn -q -B -N versions:use-dep-version -DgenerateBackupPoms=false -Dincludes="io.cxp.ocs:suggest-service" -DdepVersion="$suggest_service_version"
    fi

    released_version="$(getRemoteVersion)"
    local_version="$(getLocalVersion)"
    new_version="$local_version"

    # update parent version only if it differs from remote master
    if [[ "$released_version" == "$local_version" ]]; then
        bumpVersion 
        new_version="$(getLocalVersion)"
    fi

    echo "updating parent version for modules ($main_dependants) to version $new_version"
    mvn -q versions:update-parent -DgenerateBackupPoms=false -DskipResolution=true -DparentVersion="$new_version" -pl "$main_dependants"

    if [[ "$suggest_update_dependants" != "" ]]; then
        cd suggest-service-parent || exit
        echo "updating parent version for suggest modules ($suggest_update_dependants) to version $new_version"
        mvn -q versions:update-parent -DgenerateBackupPoms=false -DskipResolution=true -DparentVersion="$new_version" -pl "$suggest_update_dependants"
        cd - || exit
    fi 

    echo "!! This script is limited to automatic bump of simple changes! Please verify manually if all is done properly!"
else
    echo "all versions are already bumped"
    echo "However this script is limited to automatic bump of simple changes! Please verify manually if all is done properly!"
fi

find . -name pom.properties | while read file; do eval "$(cat $file)"; echo -e "$artifactId\t$version"; done | column -t

