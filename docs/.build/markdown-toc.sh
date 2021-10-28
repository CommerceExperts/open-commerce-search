#!/usr/bin/env bash

# from https://github.com/Lirt/markdown-toc-bash

FILE=${1:?No file was specified as first argument}

declare -a TOC
CODE_BLOCK=0
CODE_BLOCK_REGEX='^```'
HEADING_REGEX='^#{1,}'

while read -r LINE; do
    # Treat code blocks
    if [[ "${LINE}" =~ $CODE_BLOCK_REGEX ]]; then
        # Ignore things until we see code block ending
        CODE_BLOCK=$((CODE_BLOCK + 1))
        if [[ "${CODE_BLOCK}" -eq 2 ]]; then
            # We hit the closing code block
            CODE_BLOCK=0
        fi
        continue
    fi

    # Treat normal line
    if [[ "${CODE_BLOCK}" == 0 ]]; then
        # If we see heading, we save it to ToC map
        if [[ "${LINE}" =~ ${HEADING_REGEX} ]]; then
            TOC+=("${LINE}")
        fi
    fi
done < <(grep -v '## Table of Contents' "${FILE}")

echo -e "## Table of Contents\n"
for LINE in "${TOC[@]}"; do
    case "${LINE}" in
        '#####'*)
          echo -n "        - "
          ;;
        '####'*)
          echo -n "      - "
          ;;
        '###'*)
          echo -n "    - "
          ;;
        '##'*)
          echo -n "  - "
          ;;
        '#'*)
          echo -n "- "
          ;;
    esac

    LINK=${LINE}
    # Detect markdown links in heading and remove link part from them
    if grep -qE "\[.*\]\(.*\)" <<< "${LINK}"; then
        LINK=$(sed 's/\(\]\)\((.*)\)/\1/' <<< "${LINK}")
    fi
    # Special characters (besides '-') in page links in markdown
    # are deleted and spaces are converted to dashes
    LINK=$(tr -dc "[:alnum:] _-" <<< "${LINK}")
    LINK=${LINK/ /}
    LINK=${LINK// /-}
    LINK=${LINK,,}
    LINK=$(tr -s "-" <<< "${LINK}")

    # Print in format [Very Special Heading](#very-special-heading)
    echo "[${LINE#\#* }](#${LINK})"
done

