#!/bin/bash

SEP=';'
QUOTE='"'
IDNR=0
SKIP=1
INDEXER_ADDR="http://localhost:8535"
INDEX_NAME="ocs_example"
LOCALE="de"

print_usage() {
cat <<EOF
  Read data from CSV file and index into Elasticsearch with OCS-Indexer.
  
  options:
    -h                print this help text and exit
    -v                enable verbose output
    -x                disable the deletion of the ES index in case of failure
    
*   -f <file>         required: specify the file you want to import
    -t <type>         either 'csv' or 'json' (default: csv)

for type csv:
    -s <separator>    define column separator (without quotes). default: $SEP
    -q <quote>        define character that is used for quoting. default: $QUOTE
    -k <k>            amount of rows to sKip (normally the only header row). default: $SKIP
    -i <id-field-nr>  define the index of the id column (starting from 0). default: $IDNR
*   -m <mapping>      required: define csv-to-json object mapping in jq style, exmpl: '{title:.[1],brand:.[3]}'
                      if a file is given, but no mapping, the first line is printed with indexed column

general
    -a <adress>       hostname of OCS indexer. default: $INDEXER_ADDR
    -n <index-name>   target index name. default: $INDEX_NAME
    -l <locale>       locale to use for index. default: $LOCALE
EOF
}

DELETE_ON_FAIL=1
VERBOSE=0
log() {
  if ((VERBOSE)) ; then
    echo "$*"
  fi
}
error_with_usage() {
	echo "ERROR: $*"
  print_usage
	exit 1
}
error() {
  echo "ERROR: $*"
  exit 1
}

TYPE=csv
while getopts ":hvxf:s:q:k:i:m:a:n:l:t:" OPTION
do
  case $OPTION in
    h) print_usage; exit ;;
    v) VERBOSE=1 ;;
    x) DELETE_ON_FAIL=0 ;;
    f) FILE="$OPTARG" ;;
    s) SEP="$OPTARG" ;;
    q) QUOTE="$OPTARG" ;;
    k) SKIP="$OPTARG" ;;
    i) IDNR="$OPTARG" ;;
    m) MAPPING="$OPTARG" ;;
    a) INDEXER_ADDR="$OPTARG" ;;
    n) INDEX_NAME="$OPTARG" ;;
    l) LOCALE="$OPTARG" ;;
    t) TYPE="$OPTARG" ;;
    *) error_with_usage "unknown option '$OPTION=$OPTARG'" ;;
  esac
done

if ! [ -e "$FILE" ]; then error_with_usage "file '$FILE' does not exist"; fi

if [[ "$TYPE" == "csv" ]] && [[ "$MAPPING" == "" ]]; then 
  echo "ERROR: no mapping defined. These are the columns of the given file:"
  head -n1 "$FILE" | sed 's/'"$SEP"'/\n/g' | nl -ba -v0
  exit 1
fi

INDEXER_URL="$INDEXER_ADDR/indexer-api/v1/full"
CTHJ="Content-Type: application/json"
INDEX_CREATED=0
INDEX_SESSION=""
function finish {
  if ((INDEX_CREATED)) && [[ "$INDEX_SESSION" != "" ]]; then
    echo -n "Indexation not completed... "
		if ((DELETE_ON_FAIL)); then
			echo "Will delete incomplete index"
			curl -s -H "$CTHJ" "$INDEXER_URL/cancel" -d "$INDEX_SESSION"
		else
			echo "Will complete indexation anyways"
			curl -s -H "$CTHJ" "$INDEXER_URL/done" -d "$INDEX_SESSION"
		fi
	fi
}
trap finish EXIT

function start_session {
    log "creating index $INDEX_NAME"
    resp="$(curl -v -w "%{http_code}\n" -H "$CTHJ" "$INDEXER_URL/start/$INDEX_NAME?locale=$LOCALE" -o .ocs-session 2>&1)"
    http_code="$(echo "$resp" | tail -n1)"
    if [[ "$http_code" != 200 ]]; then
      log "response: $resp"
        error "creating index failed with code $http_code. please check indexer log for errors. Use verbose flag -v to show http request/response"
    fi
    INDEX_CREATED=1
    export INDEX_CREATED
    INDEX_SESSION="$(<.ocs-session jq -c .)"
    export INDEX_SESSION
}

# enable exit on error
if [[ "$TYPE" == "csv" ]]; then
    set -e
    start_session

    FULLSEP="${QUOTE}${SEP}${QUOTE}"
    tail -n +"$((1+SKIP))" "$FILE" |  
        while read -r line; do 
            data="$(echo "$line" | sed 's/'"$FULLSEP"'/\n/g' | sed -r 's/^'"$QUOTE"'|'"$QUOTE"'$//' | jq --slurp --raw-input 'split("\n")|{id:.['"$IDNR"'],data:'"$MAPPING"'}')"
            curl -s -H "$CTHJ" "$INDEXER_URL/add" -d '{"session":'"$INDEX_SESSION"', "documents":['"$data"']}' -o /dev/null
        done
elif [[ "$TYPE" == "json" ]]; then
    set -e
    start_session

    bulk=""
    bulk_size=0
    progress=0
    total_size="$(<"$FILE" wc -l)"
    if ((VERBOSE)); then echo "going to index $total_size documents.."; fi
    while read -r jsonline; do
        if [ "$bulk_size" -gt 0 ]; then bulk="$bulk,"; fi
        bulk_size=$((bulk_size+1))
        progress=$((progress+1))
        bulk="${bulk}${jsonline}"
        if [[ "$bulk_size" -eq 100 ]]; then
            bulk_size=0
            curl -s -H "$CTHJ" "$INDEXER_URL/add" -d '{"session":'"$INDEX_SESSION"', "documents":['"$bulk"']}' -o /dev/null
            bulk=""
            if ((VERBOSE)); then echo -ne "$progress / $total_size\r"; fi
        fi
    done <"$FILE"

    if [ "$bulk_size" -gt 0 ]; then
            curl -s -H "$CTHJ" "$INDEXER_URL/add" -d '{"session":'"$INDEX_SESSION"', "documents":['"$bulk"']}' -o /dev/null
            if ((VERBOSE)); then echo -ne "$progress / $total_size\r"; fi
    fi
    if ((VERBOSE)); then echo "completing indexation.."; fi
else
    echo "unknown type '$TYPE' - must be one of 'csv' or 'json'"
    exit 1
fi

done="$(curl -s -H "$CTHJ" "$INDEXER_URL/done" -d "$INDEX_SESSION")"
log "indexation into index '$INDEX_NAME' completed: $done"
INDEX_SESSION=
