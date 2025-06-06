# getIdToken.py
# ------------------------------------------------------------------
#
# A Python script that emulates the functionality of the provided
# Node.js script to obtain a Google Cloud ID token from a service
# account key.
#
# Copyright © 2025 Google LLC.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

import argparse
import json
import time
import sys
import os
import jwt
import requests
import traceback

def create_signed_jwt(key_data, audience):
    """Creates a signed JWT using the service account credentials."""

    now_in_seconds = int(time.time())

    # Construct the JWT claims
    claims = {
        "iss": key_data["client_email"],
        "aud": key_data["token_uri"],
        "iat": now_in_seconds,
        "exp": now_in_seconds + 60,  # Token expires in 60 seconds
        "target_audience": audience
    }

    print("JWT Payload:\n" + json.dumps(claims, indent=2))

    # Sign the JWT with the private key
    signed_jwt = jwt.encode(
        claims,
        key_data["private_key"],
        algorithm="RS256"
    )
    return signed_jwt


def redeem_jwt_for_id_token(key_data, signed_assertion):
    """Exchanges the signed JWT for a Google Cloud ID token."""

    print("\nSigned Assertion:\n" + signed_assertion)

    # Make the requets to the token URI
    response = requests.post(
        key_data["token_uri"],
        data={
            "grant_type": "urn:ietf:params:oauth:grant-type:jwt-bearer",
            "assertion": signed_assertion
        })

    response.raise_for_status()  # Raise an exception for bad status codes
    return response.json()

def get_token_info(id_token):
    """Fetches information about the obtained ID token."""

    response = requests.get(
        "https://www.googleapis.com/oauth2/v3/tokeninfo",
        params={"id_token": id_token}
    )
    response.raise_for_status()
    return response.json()


def main():
    """Main function to parse arguments and orchestrate token retrieval."""

    parser = argparse.ArgumentParser(description="Get a Google Cloud ID token for a service account.")
    parser.add_argument(
        "--keyfile",
        required=True,
        help="Path to the service account JSON key file.")
    parser.add_argument(
        "--audience",
        required=True,
        help="The desired audience for the ID token.")

    args = parser.parse_args()

    try:
        # Load the service account key file
        with open(args.keyfile, 'r') as f:
            key_data = json.load(f)

            # 1. Create a signed JWT
            signed_assertion = create_signed_jwt(key_data, args.audience)

            # 2. Redeem the JWT for an ID token
            token_response = redeem_jwt_for_id_token(key_data, signed_assertion)
            print("\nToken Response:\n" + json.dumps(token_response, indent=2))

            # 3. Get and display information about the token
            if "id_token" in token_response:
                info = get_token_info(token_response["id_token"])
                print("\nToken Info:\n" + json.dumps(info, indent=2))

    except FileNotFoundError:
        print(f"Error: Key file not found at '{args.keyfile}'", file=sys.stderr)
    except Exception as e:
        print(f"An error occurred: {e}", file=sys.stderr)
        stack_trace_string = traceback.format_exc()
        print(f"Stack Trace:\n{stack_trace_string}")

if __name__ == "__main__":
    main()
