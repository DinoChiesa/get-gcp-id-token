#!/bin/bash

# Copyright 2023-2024 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

#
# requires: curl, sed, tr, base64, openssl, mktemp
#

set -euo pipefail

B64OPTION="-b0"
if [[ "$OSTYPE" != "darwin"* ]]; then
    B64OPTION='-w0'
fi

b64_nopadding() {
    local value="$1"
    printf "%s" "$value" | base64 "$B64OPTION" | sed 's/=//g'
}

extract_json_field() {
    local field_name=$1
    local json_file=$2
    sed -n 's/.*"'$field_name'": \{0,1\}"\([^"]*\)".*/\1/p' $json_file | sed 's/^[[:space:]]*//g' | sed 's/[[:space:]]*$//g'
}

create_signed_jwt() {
    local key_json_file="$1"
    local target_audience="$2"
    local valid_for_sec=60
    local private_key=$(extract_json_field "private_key" $key_json_file)

    local sa_email=$(extract_json_field "client_email" $key_json_file)
    local exp=$(($(date +%s) + $valid_for_sec))
    local iat=$(date +%s)
    local aud="https://www.googleapis.com/oauth2/v4/token"

    local payload=""
    payload="${payload}\"iss\":\"${sa_email}\","
    payload="${payload}\"target_audience\":\"${target_audience}\","
    payload="${payload}\"aud\":\"${aud}\","
    payload="${payload}\"exp\":${exp},"
    payload="${payload}\"iat\":${iat}"
    payload="{${payload}}"

    #printf "%s\n" "$payload"
    printf "JWT Payload:\n%s\n\n" "$payload" | sed 's/,/,\n  /g' | sed 's/{/{\n  /g' | sed 's/}/\n}/g'

    local header='{"alg":"RS256","typ":"JWT"}'
    local to_be_signed="$(b64_nopadding "$header").$(b64_nopadding "$payload")"

    local signature=$(openssl dgst -sha256 -sign <(printf -- "$private_key" "") <(printf "$to_be_signed") | base64 "$B64OPTION" | tr '/+' '_-' | tr -d '=')

    # not local
    jwt="$to_be_signed.$signature"
}

[[ -z "$1" || ! -f "$1" ]] && printf "pass a service account key file.\n" && exit 1
[[ -z "$2" ]] && printf "pass in a target audience.\n" && exit 1

key_json_file="$1"
audience="${2}"
create_signed_jwt "$key_json_file" "$audience"

OUTFILE=$(mktemp /tmp/get-gcp-access-token.out.XXXXXX)
curl -s -X POST https://www.googleapis.com/oauth2/v4/token \
    --data-urlencode 'grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer' \
    --data-urlencode "assertion=$jwt" >"$OUTFILE" 2>&1

token=$(extract_json_field "id_token" "$OUTFILE")
[[ -f "$OUTFILE" ]] && rm "$OUTFILE"

printf "token:\n%s\n" "$token"

printf "\ntoken info:\n"
curl -s -X GET "https://www.googleapis.com/oauth2/v3/tokeninfo?id_token=${token}"
