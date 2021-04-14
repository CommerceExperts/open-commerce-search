#!/bin/bash

ES_HOST="127.0.0.1:9200";
OUTPUT_PATH="$PWD/tracks";
TRACK_NAME="ocss-track";
CLIENT_OPTS="";
VERBOSE=0;
INDEX="";
FILE="";

print_usage() {
cat <<EOF
  Create esrally track from given index and given real user queries.
  
  options:
    -h                    print this help text and exit
    -v                    enable verbose output
    
*   -i <index>            required: specify the index alias/name which should be used for the track
*   -f <searchlog-file>   required: specify the search log file which should be used for the challanges
    -s <es-socket>        elasticsearch target-host. Default: $ES_HOST
    -c <client_options>   python-elasticsearch client options used for the connection to elasticsearch
                          e.g. for ssl with auth:
                            "timeout:60,use_ssl:true,verify_certs:true,basic_auth_user:'elastic',basic_auth_password:'secret-password'"
    -o <output-path>      absolute path to export track. Default: $OUTPUT_PATH
    -t <track-name>       name of the track. Default: $TRACK_NAME
EOF
}

log() {
  if ((VERBOSE)) ; then
    echo "$*";
  fi
}

error_with_usage() {
	echo "ERROR: $*";
  print_usage;
	exit 1;
}

error() {
  echo "ERROR: $*";
  exit 1;
}

while getopts ":hvi:f:s:c:o:t:" OPTION
do
  case $OPTION in
    h) print_usage; exit ;;
    v) VERBOSE=1 ;;
    i) INDEX="$OPTARG" ;;
    f) FILE="$OPTARG" ;;
    s) ES_HOST="$OPTARG" ;;
    c) CLIENT_OPTS="$OPTARG" ;;
    o) OUTPUT_PATH="$OPTARG" ;;
    t) TRACK_NAME="$OPTARG" ;;
    *) error_with_usage "unknown option '$OPTION=$OPTARG'" ;;
  esac
done

if [[ $INDEX == "" ]]; then
  error_with_usage "Parameter index must be set.";
fi

if ! [ -e "$FILE" ]; then
  error_with_usage "file '$FILE' does not exist or file parameter (-f) is not set.";
fi

log "Creating output dir $OUTPUT_PATH ...";
mkdir -p $OUTPUT_PATH;
log "Output dir $OUTPUT_PATH created.";

# create track data from index
log "Creating rally data from index $INDEX ...";
docker run -v "$OUTPUT_PATH:/tracks" --network host elastic/rally create-track --track=$TRACK_NAME --target-hosts=$ES_HOST --client-options=$CLIENT_OPTS --indices=$INDEX --output-path=/tracks;
CREATE_TRACK_RC=$?;
if [[ $CREATE_TRACK_RC != "0" ]]; then
  error "Creating track from $INDEX failed with code $CREATE_TRACK_RC.";
else
  log "Rally data from index $INDEX in $OUTPUT_PATH created.";
fi

log "Manipulate generated $OUTPUT_PATH/$TRACK_NAME/track.json ...";
echo '{% import "rally.helpers" as rally with context %}' > $OUTPUT_PATH/$TRACK_NAME/track.json.tmp;
sed -i 's/{{/"/g' $OUTPUT_PATH/$TRACK_NAME/track.json;
sed -i 's/}}/"/g' $OUTPUT_PATH/$TRACK_NAME/track.json;
sed -i 's/{%.*%}//g' $OUTPUT_PATH/$TRACK_NAME/track.json;
sed -i 's/""/"/g' $OUTPUT_PATH/$TRACK_NAME/track.json;
cat $OUTPUT_PATH/$TRACK_NAME/track.json | jq '{ version: .version, description: .description, indices: [ .indices[0], (.indices[0].name += "-2" | .indices[0]) ], corpora: [ .corpora[0], (.corpora[0].documents[0]."target-index" += "-2" | .corpora[0].name += "-2" | .corpora[0]) ], challenges: [ "{{ rally.collect(parts=\"challenges/*.json\") }}" ] }' >> $OUTPUT_PATH/$TRACK_NAME/track.json.tmp;
sed -i 's/\\"/"/g' $OUTPUT_PATH/$TRACK_NAME/track.json.tmp;
sed -i 's/"{{/{{/g' $OUTPUT_PATH/$TRACK_NAME/track.json.tmp;
sed -i 's/}}"/}}/g' $OUTPUT_PATH/$TRACK_NAME/track.json.tmp;
mv -f $OUTPUT_PATH/$TRACK_NAME/track.json.tmp $OUTPUT_PATH/$TRACK_NAME/track.json;
log "Manipulated generated $OUTPUT_PATH/$TRACK_NAME/track.json.";

# generate challenges
log "Start with generating challenges...";
SOURCE_DIR=$( dirname "${BASH_SOURCE[0]}" );
mkdir -p $OUTPUT_PATH/$TRACK_NAME/challenges || log "$OUTPUT_PATH/$TRACK_NAME/challenges already exists.";

# challenges from template
cp -rf $SOURCE_DIR/challenges/* $OUTPUT_PATH/$TRACK_NAME/challenges/
cp -rf $FILE $OUTPUT_PATH/$TRACK_NAME/searches.json
sed -i "s/{{INDEX}}/${INDEX}/g" $OUTPUT_PATH/$TRACK_NAME/challenges/index.json;
sed -i "s/{{INDEX}}/${INDEX}/g" $OUTPUT_PATH/$TRACK_NAME/challenges/search-while-index.json;
sed -i "s/{{INDEX}}/${INDEX}/g" $OUTPUT_PATH/$TRACK_NAME/challenges/search.json;

# other resources
cp -rf $SOURCE_DIR/custom_runner $OUTPUT_PATH/$TRACK_NAME/;
cp -rf $SOURCE_DIR/track.py $OUTPUT_PATH/$TRACK_NAME/;
cp -rf $SOURCE_DIR/rally.ini $OUTPUT_PATH/$TRACK_NAME/;

log "Challenges from search log created.";

exit 0;