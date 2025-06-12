#!/bin/bash

# Copyright 2023-2025 Google LLC
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

check_required_commands() {
  local missing
  missing=()
  for cmd in "$@"; do
    #printf "checking %s\n" "$cmd"
    if ! command -v "$cmd" &>/dev/null; then
      missing+=("$cmd")
    fi
  done
  if [[ ${#missing[@]} -gt 0 ]]; then
    printf -v joined '%s,' "${missing[@]}"
    printf "\n\nThese commands are missing; they must be available on path: %s\nExiting.\n" "${joined%,}"
    exit 1
  fi
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

usage() {
  printf "pass a service account key file and a target audience:\n"
  printf "  get-id-token-for-service-account.sh service_acct_key_file.json  urn:target-aud\n"
}

# ====================================================================
check_required_commands curl base64 date sed tr openssl

(($# != 2)) && usage && exit 1
[[ ! -f "$1" ]] && usage && exit 1
[[ -z "$2" ]] && usage && exit 1

key_json_file="$1"
audience="${2}"
create_signed_jwt "$key_json_file" "$audience"

OUTFILE=$(mktemp /tmp/get-gcp-access-token.out.XXXXXX)
token_uri=$(extract_json_field "token_uri" $key_json_file)

printf "Assertion:\n"
printf '%s\n' "$jwt"

printf "\nRedemption:\n"
printf 'curl -s -X POST "%s" \\\n    --data-urlencode "grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer" \\\n    --data-urlencode "assertion=%s"\n' "$token_uri" "$jwt"

curl -s -X POST "$token_uri" \
  --data-urlencode 'grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer' \
  --data-urlencode "assertion=$jwt" >"$OUTFILE" 2>&1

printf "\nResponse:\n"
cat "$OUTFILE"

token=$(extract_json_field "id_token" "$OUTFILE")
[[ -f "$OUTFILE" ]] && rm "$OUTFILE"

printf "\n\ntoken:\n%s\n" "$token"

printf "\ntoken info:\n"
curl -s -X GET "https://www.googleapis.com/oauth2/v3/tokeninfo?id_token=${token}"
